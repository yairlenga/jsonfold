#!/usr/bin/env python3
"""jsonfold.py - streaming compactor for json.dump(..., indent=N).

Two-phase pipeline
------------------
Phase 1 – Pack:  merge consecutive scalar lines N-per-line within a container,
                 subject to line_array_items / line_obj_items / line_nesting.

Phase 2 – Fold:  if a container was reduced to exactly one content line by
                 packing (or was already one line), collapse opener + content +
                 closer onto a single line, subject to fold_array_items /
                 fold_obj_items / fold_nesting.

The phases are independent: packing always runs; folding is a special case
that fires only when packing produced exactly one content line.
"""

from __future__ import annotations

import io
import json
import sys
from dataclasses import dataclass, KW_ONLY, replace, field
from typing import Any, TextIO


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class JSONFold:
    width: int = 80
    _: KW_ONLY
    # Phase 1 – pack scalars N-per-line
    line_array_items: int = 8       # max scalars per line inside a list
    line_obj_items:   int = 4       # max scalars per line inside a dict
    line_nesting:     int = 1       # max container nesting depth for packing
    # Phase 2 – fold single-content-line containers onto one line
    fold_array_items: int = 8       # max items allowed in a folded list
    fold_obj_items:   int = 4       # max items allowed in a folded dict
    fold_nesting:     int = 1       # max container nesting depth for folding


JSONFold.NONE = JSONFold(
    line_array_items = 0,
    line_obj_items   = 0,
    line_nesting     = 0,
    fold_array_items = 0,
    fold_obj_items   = 0,
    fold_nesting     = 0,
)

JSONFold.DEFAULT = JSONFold()

JSONFold.PRESETS = {
    "default": JSONFold.DEFAULT,
    "none":    JSONFold.NONE,
    "max": replace(JSONFold.NONE,
        line_array_items = sys.maxsize,
        line_obj_items   = sys.maxsize,
        line_nesting     = sys.maxsize,
        fold_array_items = sys.maxsize,
        fold_obj_items   = sys.maxsize,
        fold_nesting     = sys.maxsize,
    ),
    # pack only – no folding
    "pack": replace(JSONFold.NONE,
        line_array_items = sys.maxsize,
        line_obj_items   = sys.maxsize,
        line_nesting     = sys.maxsize,
    ),
    # fold only – no packing
    "fold": replace(JSONFold.NONE,
        fold_array_items = sys.maxsize,
        fold_obj_items   = sys.maxsize,
        fold_nesting     = sys.maxsize,
    ),
}


# ---------------------------------------------------------------------------
# Internal data structures
# ---------------------------------------------------------------------------

@dataclass
class Line:
    indent: int
    text:   str
    parent: str | None = None   # "dict", "list", or None
    items:  int        = 1      # packed scalar count (>=1)
    # nesting of the deepest folded child within this line (-1 = scalar)
    child_nesting: int = -1

    @classmethod
    def parse(cls, s: str, parent: str | None) -> "Line":
        stripped = s.lstrip(" ")
        return cls(indent=len(s) - len(stripped), text=stripped.rstrip(),
                   parent=parent)

    def raw(self) -> str:
        return " " * self.indent + self.text + "\n"

    def width(self) -> int:
        return self.indent + len(self.text)

    def is_opener(self) -> str | None:
        """Return container kind if this line ends with an opener, else None."""
        if self.text.endswith("{"):
            return "dict"
        if self.text.endswith("["):
            return "list"
        return None

    def is_closer(self) -> str | None:
        """Return container kind if this line is a bare closer, else None."""
        return _CLOSING_KIND.get(self.text)


_CLOSING_KIND: dict[str, str] = {
    "}":  "dict", "},": "dict",
    "]":  "list", "],": "list",
}


@dataclass
class Frame:
    kind: str
    depth: int
    lines: list[Line] = field(default_factory=list)

    content_lines: int = 0
    items: int = 0

    fold_ok: bool = True
    child_nesting: int = -1


class JSONFoldWriter:

    def __init__(self, fp: TextIO, *,
                 compact: JSONFold | None = None):
        self.fp = fp
        self.cfg = compact
        self.pending = ""
        self.stack: list[Frame] = []

    # ------------------------------------------------------------------ I/O

    def write(self, s: str) -> int:
        if not self.cfg:
            return self.fp.write(s)

        parts = s.splitlines(keepends=True)

        if self.pending:
            parts[0] = self.pending + parts[0] if parts else self.pending
            self.pending = ""

        if parts and not parts[-1].endswith("\n"):
            self.pending = parts.pop()

        for part in parts:
            self._feed(Line.parse(part[:-1], self._parent_kind()))

        if self.pending and len(self.pending.rstrip()) > self.cfg.width:
            self._mark_no_fold()

        return len(s)

    def flush(self) -> None:
        self.finish()
        self.fp.flush()

    def close(self) -> None:
        self.finish()

    def finish(self) -> None:
        if self.pending:
            self._feed(Line.parse(self.pending, self._parent_kind()))
            self.pending = ""

        # Valid json.dump output should leave the stack empty.
        # If not, stream whatever remains.
        while self.stack:
            frame = self.stack.pop()
            for line in frame.lines:
                self._emit_line(line)

    def __enter__(self) -> "JSONFoldWriter":
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.finish()

    def __getattr__(self, name: str) -> Any:
        return getattr(self.fp, name)

    # ------------------------------------------------------------ core feed

    def _feed(self, line: Line) -> None:
        opener = line.is_opener()
        if opener:
            self.stack.append(Frame(
                kind=opener,
                depth=len(self.stack),
                lines=[line],
            ))

            if line.width() > self.cfg.width:
                self._mark_no_fold()
            return

        closer = line.is_closer()
        if closer:
            self._close_frame(line, closer)
            return

        self._emit_line(line)

    def _emit_line(self, line: Line) -> None:
        if self.stack:
            self._add_to_frame(self.stack[-1], line)
        else:
            self.fp.write(line.raw())

    # --------------------------------------------------------- phase 1: pack

    def _add_to_frame(self, frame: Frame, line: Line) -> None:
        if self._try_pack(frame, line):
            return

        frame.lines.append(line)
        self._update_frame(frame, line)

        if line.width() > self.cfg.width:
            self._mark_no_fold()

        if not frame.fold_ok:
            self._stream_frame(frame, keep_last=True)

    def _try_pack(self, frame: Frame, line: Line) -> bool:
        if not frame.lines:
            return False

        if frame.depth > self.cfg.line_nesting:
            return False

        if not self._packable(line):
            return False

        prev = frame.lines[-1]
        limit = self._pack_limit(line.parent)

        if not (
            self._packable(prev)
            and prev.parent == line.parent
            and prev.indent == line.indent
            and prev.items + line.items <= limit
            and line.indent + len(prev.text) + 1 + len(line.text) <= self.cfg.width
        ):
            return False

        prev.text += " " + line.text
        prev.items += line.items

        if line.parent == frame.kind:
            frame.items += line.items

        self._check_fold_limits(frame)
        return True

    def _packable(self, line: Line) -> bool:
        return (
            line.child_nesting < 0
            and line.parent in ("dict", "list")
            and self._pack_limit(line.parent) > 1
            and line.is_opener() is None
            and line.is_closer() is None
        )

    def _pack_limit(self, kind: str | None) -> int:
        if kind == "list":
            return self.cfg.line_array_items
        if kind == "dict":
            return self.cfg.line_obj_items
        return 0

    # --------------------------------------------------------- frame tracking

    def _update_frame(self, frame: Frame, line: Line) -> None:
        if line.is_closer():
            return

        frame.content_lines += 1

        if line.parent == frame.kind:
            frame.items += line.items

        if line.child_nesting >= 0:
            frame.child_nesting = max(frame.child_nesting, line.child_nesting + 1)

        self._check_fold_limits(frame)

    def _check_fold_limits(self, frame: Frame) -> None:
        if frame.content_lines > 1:
            frame.fold_ok = False

        if frame.depth > self.cfg.fold_nesting:
            frame.fold_ok = False

        limit = (
            self.cfg.fold_array_items
            if frame.kind == "list"
            else self.cfg.fold_obj_items
        )

        if frame.items > limit:
            frame.fold_ok = False

        if frame.child_nesting > self.cfg.fold_nesting:
            frame.fold_ok = False

    # --------------------------------------------------------- phase 2: fold

    def _close_frame(self, closer: Line, closing_kind: str) -> None:
        if not self.stack:
            self.fp.write(closer.raw())
            return

        frame = self.stack.pop()
        frame.lines.append(closer)

        if frame.kind != closing_kind:
            frame.fold_ok = False

        folded = self._try_fold(frame)

        if folded is not None:
            self._emit_line(folded)
        else:
            for line in frame.lines:
                self._emit_line(line)

    def _try_fold(self, frame: Frame) -> Line | None:
        if not frame.fold_ok:
            return None

        if frame.content_lines != 1:
            return None

        if len(frame.lines) != 3:
            return None

        folded_length = (
            len(frame.lines[0].text)
            + len(frame.lines[1].text)
            + len(frame.lines[2].text)
            + 2
        )

        if frame.lines[0].indent + folded_length > self.cfg.width:
            return None

        return Line(
            indent=frame.lines[0].indent,
            text=self._fold_text(frame.lines),
            parent=self._parent_kind(),
            items=1,
            child_nesting=max(0, frame.child_nesting),
        )

    @staticmethod
    def _fold_text(lines: list[Line]) -> str:
        opener = lines[0].text
        content = lines[1].text
        closer = lines[2].text

        comma = closer.endswith(",")
        if comma:
            closer = closer[:-1]

        text = opener + " " + content + " " + closer
        return text + ("," if comma else "")

    # --------------------------------------------------------- streaming

    def _stream_frame(self, frame: Frame, *, keep_last: bool) -> None:
        keep = 0

        if keep_last and frame.lines and self._packable(frame.lines[-1]):
            keep = 1

        if keep:
            emit_lines = frame.lines[:-1]
            frame.lines = frame.lines[-1:]
        else:
            emit_lines = frame.lines
            frame.lines = []

        for line in emit_lines:
            self._emit_to_parent(frame, line)

    def _emit_to_parent(self, frame: Frame, line: Line) -> None:
        if frame.depth == 0:
            self.fp.write(line.raw())
        else:
            self._add_to_frame(self.stack[frame.depth - 1], line)

    # --------------------------------------------------------- misc helpers

    def _mark_no_fold(self) -> None:
        for frame in self.stack:
            frame.fold_ok = False

        if self.stack:
            self._stream_frame(self.stack[-1], keep_last=True)

    def _parent_kind(self) -> str | None:
        return self.stack[-1].kind if self.stack else None
# ---------------------------------------------------------------------------
# Public helpers
# ---------------------------------------------------------------------------


def dump(obj: Any, fp: TextIO, *,
         compact: JSONFold | bool | None = True,
         indent: int = 2, **kwargs: Any) -> None:

    if compact is True:
        compact = JSONFold.DEFAULT
    with JSONFoldWriter(fp, compact=compact) as out:
        json.dump(obj, out, indent=indent, **kwargs)


def dumps(obj: Any, *,
          compact: JSONFold | bool | None = True,
          indent: int = 2, **kwargs: Any) -> str:
    out = io.StringIO()
    dump(obj, out, compact=compact, indent=indent, **kwargs)
    return out.getvalue()


# ---------------------------------------------------------------------------
# Demo data
# ---------------------------------------------------------------------------

def _demo() -> dict[str, Any]:
    return {
        "meta":   {"version": 1, "ok": True},
        "items":  [{"id": 1, "name": "alpha"}, {"id": 2, "name": "beta"}],
        "matrix": [[1, 2], [3, 4]],
        "long": [
            "this is a long message that may force the block to stay expanded",
            "second", "third", "fourth",
        ],
    }


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main(argv: list[str] | None = None) -> int:
    import argparse

    p = argparse.ArgumentParser(
        description="Read JSON from stdin; write folded JSON to stdout.")
    p.add_argument("--demo",   action="store_true")
    p.add_argument("--preset", choices=JSONFold.PRESETS.keys(), default="default")
    p.add_argument("--width",  type=int, default=None, help="line width limit (default: 80)")

    # Pack phase
    g = p.add_argument_group("pack phase (merge scalars N-per-line)")
    g.add_argument("--line-items",       type=int, default=None,
                   help="set both --line-array-items and --line-obj-items")
    g.add_argument("--line-array-items", type=int, default=None)
    g.add_argument("--line-obj-items",   type=int, default=None)
    g.add_argument("--line-nesting",     type=int, default=None)

    # Fold phase
    g = p.add_argument_group("fold phase (collapse single-content-line containers)")
    g.add_argument("--fold-items",       type=int, default=None,
                   help="set both --fold-array-items and --fold-obj-items")
    g.add_argument("--fold-array-items", type=int, default=None)
    g.add_argument("--fold-obj-items",   type=int, default=None)
    g.add_argument("--fold-nesting",     type=int, default=None)

    p.add_argument("--indent",    type=int,          default=2)
    p.add_argument("--sort-keys", action="store_true")
    args = p.parse_args(argv)

    # Start from preset, apply overrides where explicitly given.
    cfg = JSONFold.PRESETS[args.preset]

    overrides: dict[str, int] = {}

    # Convenience shorthands (lower priority than individual flags).
    if args.line_items is not None:
        overrides["line_array_items"] = args.line_items
        overrides["line_obj_items"]   = args.line_items
    if args.fold_items is not None:
        overrides["fold_array_items"] = args.fold_items
        overrides["fold_obj_items"]   = args.fold_items

    # Individual flags (higher priority — applied after shorthands).
    for field in ("width",
                  "line_array_items", "line_obj_items", "line_nesting",
                  "fold_array_items", "fold_obj_items", "fold_nesting"):
        val = getattr(args, field)
        if val is not None:
            overrides[field] = val

    if overrides:
        cfg = replace(cfg, **overrides)
    print(cfg, file= sys.stderr)

    if args.demo:
        data = _demo()
    else:
        data = json.load(sys.stdin)

    dump(data, sys.stdout, compact=cfg, indent=args.indent,
         sort_keys=args.sort_keys)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
