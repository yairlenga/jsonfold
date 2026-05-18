#!/usr/bin/env python3
"""jsonfold.py - streaming compactor for json.dump(..., indent=N)."""

from __future__ import annotations

import io
import json
from dataclasses import dataclass, KW_ONLY, replace
from typing import Any, TextIO

import sys

@dataclass(frozen=True)
class JSONFold:
    width: int = 80
    _: KW_ONLY
    fold_items: int = 8
    fold_nesting: int = 1
    line_array_items: int = 8
    line_obj_items: int = 4
    line_nesting: int = 1

JSONFold.NONE = JSONFold(
    fold_items = 0,
    fold_nesting = 0,
    line_array_items = 0,
    line_obj_items = 0,
    line_nesting = 0,
)

JSONFold.DEFAULT = JSONFold()
JSONFold.PRESETS = {
    "default": JSONFold.DEFAULT,
    "none": JSONFold.NONE,
    "max": replace(JSONFold.NONE,
        fold_items= sys.maxsize,
        fold_nesting= sys.maxsize,
        line_array_items= sys.maxsize,
        line_obj_items= sys.maxsize,
        line_nesting= sys.maxsize,
    ),
    "fold": replace(JSONFold.NONE,
        fold_items = 8,
        fold_nesting = 1,
    ),
    "line": replace(JSONFold.NONE,
        line_array_items= sys.maxsize,
        line_obj_items= sys.maxsize,
        line_nesting= sys.maxsize,
    ),
}


@dataclass
class Line:
    indent: int
    text: str
    nesting: int = -1              # -1 scalar/unfolded, >=0 folded container
    parent: str | None = None      # "dict", "list", or None
    items: int = 1                 # sibling items represented by this line

    @classmethod
    def parse(cls, s: str, parent: str | None) -> "Line":
        stripped = s.lstrip(" ")
        return cls(len(s) - len(stripped), stripped.rstrip(), parent=parent)

    def raw(self) -> str:
        return " " * self.indent + self.text + "\n"

    def width(self) -> int:
        return self.indent + len(self.text)


@dataclass
class Frame:
    kind: str                      # "dict" or "list"
    start: int                     # index in buffer
    indent: int
    raw: bool = False              # raw frames do not block flushing
    items: int = 0                 # direct children, after sibling packing
    lines: int = 1                 # buffered lines in this frame
    nesting: int = 0               # max folded-child nesting + 1
    fold_nesting: int = -1         # max direct folded child nesting


class JSONFoldWriter:
    CLOSING_KIND = {"}": "dict", "},": "dict", "]": "list", "],": "list"}

    def __init__(self, fp: TextIO, *, compact: JSONFold | None = None, indent_step: int = 2):
        self.fp = fp
        self.cfg = compact or JSONFold.DEFAULT
        self.indent_step = indent_step
        self.pending = ""
        self.buffer: list[Line] = []
        self.stack: list[Frame] = []

    def write(self, s: str) -> int:
        n = len(s)
        if not s:
            return 0

        parts = s.splitlines(keepends=True)
        if self.pending:
            if parts:
                parts[0] = self.pending + parts[0]
            else:
                parts = [self.pending]
            self.pending = ""

        if parts and not parts[-1].endswith("\n"):
            self.pending = parts.pop()

        for part in parts:
            self._feed(Line.parse(part[:-1], self._parent_kind()))

        if self.pending and len(self.pending.rstrip()) > self.cfg.width:
            self._mark_raw()
            self._flush_safe_prefix()

        return n

    def flush(self) -> None:
        self.finish()
        self.fp.flush()

    def close(self) -> None:
        self.finish()

    def finish(self) -> None:
        if self.pending:
            self._feed(Line.parse(self.pending, self._parent_kind()))
            self.pending = ""
        self._flush_all()

    def __enter__(self) -> "JSONFoldWriter":
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.finish()

    def __getattr__(self, name: str) -> Any:
        return getattr(self.fp, name)

    def _feed(self, line: Line) -> None:
        self._append_or_pack(line)

        if self.stack:
            self._update_parent(self.stack[-1], self.buffer[-1])

        if self.buffer[-1].width() > self.cfg.width:
            self._mark_raw()

        opener = self._opening_kind(self.buffer[-1].text)
        if opener:
            self.stack.append(Frame(opener, len(self.buffer) - 1, self.buffer[-1].indent))

        closer = self._closing_kind(self.buffer[-1].text)
        if closer:
            self._close_frame(closer)

        self._flush_safe_prefix()

    def _append_or_pack(self, line: Line) -> None:
        if not self._packable(line) or not self.buffer:
            self.buffer.append(line)
            return

        prev = self.buffer[-1]
        limit = self._pack_limit(line.parent)
        joined = prev.text + " " + line.text

        if (self._packable(prev)
                and prev.parent == line.parent
                and prev.indent == line.indent
                and prev.items + line.items <= limit
                and line.indent + len(joined) <= self.cfg.width):
            prev.text = joined
            prev.items += line.items
            return

        self.buffer.append(line)

    def _update_parent(self, frame: Frame, line: Line) -> None:
        # Ignore the frame's own opener and any closing line.
        if len(self.buffer) - 1 == frame.start or self._closing_kind(line.text):
            frame.lines += 1
            return

        if line.indent == frame.indent + self.indent_step:
            frame.items += line.items

        frame.lines += 1
        if line.nesting >= 0:
            frame.nesting = max(frame.nesting, line.nesting + 1)
            frame.fold_nesting = max(frame.fold_nesting, line.nesting)

        if not self._frame_can_still_fold(frame):
            frame.raw = True

    def _frame_can_still_fold(self, f: Frame) -> bool:
        c = self.cfg
        if c.fold_items <= 0 or f.lines > c.fold_items:
            return False
        if f.nesting > c.line_nesting:
            return False
        if f.fold_nesting >= c.fold_nesting and f.fold_nesting >= 0:
            return False
        if f.kind == "dict" and f.items > c.line_obj_items:
            return False
        if f.kind == "list" and f.items > c.line_array_items:
            return False
        return True

    def _close_frame(self, closing_kind: str) -> None:
        if not self.stack:
            return

        frame = self.stack.pop()
        if frame.kind != closing_kind:
            frame.raw = True

        if not frame.raw and self._try_fold_frame(frame) and self.stack:
            self._update_parent(self.stack[-1], self.buffer[frame.start])

    def _try_fold_frame(self, frame: Frame) -> bool:
        start = frame.start
        end = len(self.buffer) - 1
        lines = self.buffer[start:end + 1]
        text = self._fold_text(lines)

        if frame.indent + len(text) > self.cfg.width:
            return False

        original = sum(len(ln.raw()) for ln in lines)
        if frame.indent + len(text) + 1 >= original:
            return False

        self.buffer[start:end + 1] = [Line(
            indent=frame.indent,
            text=text,
            nesting=frame.nesting,
            parent=self._parent_kind(),
            items=1,
        )]

        removed = end - start
        for f in self.stack:
            if f.start > start:
                f.start -= removed
        return True

    @staticmethod
    def _fold_text(lines: list[Line]) -> str:
        first = lines[0].text
        last = lines[-1].text
        comma = last.endswith(",")
        if comma:
            last = last[:-1]
        mid = " ".join(ln.text for ln in lines[1:-1])
        out = first + (" " + mid + " " if mid else "") + last
        return out + ("," if comma else "")

    def _flush_safe_prefix(self) -> None:
        if not self.stack:
            self._flush_all()
            return

        starts = [f.start for f in self.stack if not f.raw]
        if not starts:
            self._flush_all()
        else:
            self._flush_prefix(min(starts))

    def _flush_prefix(self, n: int) -> None:
        if n <= 0:
            return
        for ln in self.buffer[:n]:
            self.fp.write(ln.raw())
        del self.buffer[:n]
        for f in self.stack:
            f.start -= n

    def _flush_all(self) -> None:
        for ln in self.buffer:
            self.fp.write(ln.raw())
        self.buffer.clear()
        self.stack.clear()

    def _packable(self, line: Line) -> bool:
        return (line.nesting < 0
                and line.parent in {"dict", "list"}
                and self._pack_limit(line.parent) > 1
                and not self._opening_kind(line.text)
                and not self._closing_kind(line.text))

    def _pack_limit(self, kind: str | None) -> int:
        if kind == "list":
            return self.cfg.line_array_items
        if kind == "dict":
            return self.cfg.line_obj_items
        return 0

    @staticmethod
    def _opening_kind(text: str) -> str | None:
        if text.endswith("{"):
            return "dict"
        if text.endswith("["):
            return "list"
        return None

    def _closing_kind(self, text: str) -> str | None:
        return self.CLOSING_KIND.get(text)

    def _mark_raw(self) -> None:
        for f in self.stack:
            f.raw = True

    def _parent_kind(self) -> str | None:
        return self.stack[-1].kind if self.stack else None


# public helpers ------------------------------------------------------------


def _normalize(compact: JSONFold | bool | None) -> JSONFold | None:
    if compact is False:
        return None
    if compact is True or compact is None:
        return JSONFold.DEFAULT
    return compact


def dump(obj: Any, fp: TextIO, *, compact: JSONFold | bool | None = True,
         indent: int = 2, **kwargs: Any) -> None:
    cfg = _normalize(compact)
    if cfg is None:
        json.dump(obj, fp, indent=indent, **kwargs)
        return
    with JSONFoldWriter(fp, compact=cfg, indent_step=indent) as out:
        json.dump(obj, out, indent=indent, **kwargs)


def dumps(obj: Any, *, compact: JSONFold | bool | None = True,
          indent: int = 2, **kwargs: Any) -> str:
    out = io.StringIO()
    dump(obj, out, compact=compact, indent=indent, **kwargs)
    return out.getvalue()


def _demo() -> dict[str, Any]:
    return {
        "meta": {"version": 1, "ok": True},
        "items": [{"id": 1, "name": "alpha"}, {"id": 2, "name": "beta"}],
        "matrix": [[1, 2], [3, 4]],
        "long": [
            "this is a long message that may force the block to stay expanded",
            "second",
            "third",
            "fourth",
        ],
    }


def main(argv: list[str] | None = None) -> int:
    import argparse
    import sys

    d = JSONFold.DEFAULT
    p = argparse.ArgumentParser(description="Read JSON from stdin; write folded JSON to stdout.")
    p.add_argument("--demo", action="store_true")
    p.add_argument("--preset", choices=JSONFold.PRESETS.keys(), default="default")
    p.add_argument("--width", type=int, default=d.width)
    p.add_argument("--fold-items", type=int, default=d.fold_items)
    p.add_argument("--fold-nesting", type=int, default=d.fold_nesting)
    p.add_argument("--line-array-items", type=int, default=d.line_array_items)
    p.add_argument("--line-obj-items", type=int, default=d.line_obj_items)
    p.add_argument("--line-nesting", type=int, default=d.line_nesting)
    p.add_argument("--indent", type=int, default=2)
    p.add_argument("--sort-keys", action="store_true")
    args = p.parse_args(argv)

    cfg = JSONFold.PRESETS[args.preset]

    if args.demo:
        data = _demo()
    else:
        data = json.load(sys.stdin)

    dump(data, sys.stdout, compact=cfg, indent=args.indent, sort_keys=args.sort_keys)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
