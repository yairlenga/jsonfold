#!/usr/bin/env python3
"""jsonfold.py - hybrid pretty/compact JSON output.

jsonfold wraps Python's standard json.dump/json.dumps output and keeps the
normal pretty-printed structure, but selectively compacts small containers and
runs of scalar items when they fit within a configured line width.

The goal is readable JSON:
    - large or complex structures stay expanded;
    - small lists and objects can stay on one line;
    - adjacent scalar items can be packed together;
    - nested folding is controlled by explicit depth limits.

Public API
----------
    dump(obj, fp, *, compact="", indent=2, **kwargs)
        Serialize obj to fp using json.dump(), then fold the generated stream.

    dumps(obj, *, compact="", indent=2, **kwargs) -> str
        Return the folded JSON as a string.

    JSONFold(...)
        Immutable configuration object controlling width, packing, and folding.

    JSONFoldWriter(fp, *, compact="")
        File-like wrapper used internally by dump(), but also usable directly
        with json.dump(obj, JSONFoldWriter(fp, compact=cfg), indent=2).

Configuration
-------------
    width
        Maximum target line width. Lines are only packed/folded when the result
        fits within this width.

    pack_array_items / pack_obj_items
        Maximum number of scalar list items or object properties that may be
        packed onto one physical line.

    pack_nesting
        Maximum container depth where scalar packing is allowed.

    fold_array_items / fold_obj_items
        Maximum number of items/properties allowed when folding a container
        onto one line.

    fold_nesting
        Maximum nested-container depth allowed in a folded line.

Presets
-------
    "default"
        Balanced default settings.

    "none"
        Disable all packing and folding.

    "max"
        Enable aggressive packing and folding, still subject to width.

    "pack"
        Enable packing only; disable folding.

    "fold"
        Enable folding only; disable packing.

Algorithm
---------
The writer receives the tokenized line stream produced by json.dump(...,
indent=N). It does not re-parse full JSON. Instead, it tracks pretty-printed
lines and container frames.

Phase 1: Pack
    Consecutive scalar lines inside the same container may be joined onto one
    output line, subject to:
        - width limit,
        - item limit,
        - nesting limit,
        - same indentation level.

Phase 2: Fold
    A container may be collapsed from:

        [
          1, 2, 3
        ]

    into:

        [ 1, 2, 3 ]

    only when it has exactly one content line after packing, and the folded
    result fits within the configured limits.

Streaming behavior
------------------
The implementation is designed as a streaming filter around json.dump().
It buffers only the currently open container frames needed to decide whether
packing/folding is still possible. Once a frame can no longer fold, older lines
are streamed forward.

Limitations
-----------
    - Input must be normal json.dump(..., indent=N) style output.
    - The filter assumes standard JSON syntax emitted by Python's json module.
    - It is a formatting filter, not a validating JSON parser.
    - Folding decisions are based on physical line structure, indentation,
      item counts, nesting limits, and width.

Example
-------
    from jsonfold import dumps, JSONFold

    data = {
        "ids": [1, 2, 3, 4],
        "meta": {"version": 1, "ok": True},
    }

    print(dumps(data, compact=JSONFold(width=80)))

CLI
---
    python jsonfold.py < input.json
    python jsonfold.py --preset=max --width=100 < input.json
    python jsonfold.py --pack-items=20 --fold-items=8 < input.json
"""

from __future__ import annotations

import io
import json
import sys
from dataclasses import dataclass, KW_ONLY, replace, field
from typing import Any, TextIO
from enum import IntEnum, auto


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

@dataclass(frozen=True)
class JSONFold:
    """Configuration for hybrid pretty/compact JSON formatting.

    A value of 0 disables the corresponding packing or folding rule.
    Larger values allow more aggressive compaction, but all output remains
    subject to the configured width limit.
    """
    width: int = 80
    _: KW_ONLY
    # Phase 1 – pack scalars N-per-line
    pack_array_items: int = 8       # max scalars per line inside a list
    pack_obj_items:   int = 4       # max scalars per line inside a dict
    pack_nesting:     int = 1       # max container nesting depth for packing
    # Phase 2 – fold single-content-line containers onto one line
    fold_array_items: int = 8       # max items allowed in a folded list
    fold_obj_items:   int = 4       # max items allowed in a folded dict
    fold_nesting:     int = 1       # max container nesting depth for folding


JSONFold.NONE = JSONFold(
    pack_array_items = 0,
    pack_obj_items   = 0,
    pack_nesting     = 0,
    fold_array_items = 0,
    fold_obj_items   = 0,
    fold_nesting     = 0,
)

JSONFold.DEFAULT = JSONFold()

JSONFold.PRESETS = {
    "default": JSONFold.DEFAULT,
    "": JSONFold.DEFAULT,
    "none":    JSONFold.NONE,
    "max": replace(JSONFold.NONE,
        pack_array_items = sys.maxsize,
        pack_obj_items   = sys.maxsize,
        pack_nesting     = sys.maxsize,
        fold_array_items = sys.maxsize,
        fold_obj_items   = sys.maxsize,
        fold_nesting     = sys.maxsize,
    ),
    # pack only – no folding
    "pack": replace(JSONFold.NONE,
        pack_array_items = sys.maxsize,
        pack_obj_items   = sys.maxsize,
        pack_nesting     = sys.maxsize,
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

class Kind(IntEnum):
    NONE = 0
    DICT = auto()
    LIST = auto()

_CLOSING_KIND: dict[str, Kind] = {
    "}":  Kind.DICT, "},": Kind.DICT,
    "]":  Kind.LIST, "],": Kind.LIST,
}

@dataclass
class Line:
    indent: int
    text:   str
    parent_kind: Kind = Kind.NONE   # "dict", "list", or None
    items:  int        = 1      # packed scalar count (>=1)
    # nesting of the deepest folded child within this line (-1 = scalar)
    child_nesting: int = -1
    opener: Kind = Kind.NONE
    closer: Kind = Kind.NONE

    @classmethod
    def parse(cls, s: str, parent_kind: Kind) -> "Line":
        stripped = s.lstrip(" ")
        body=stripped.rstrip()
        opener= (
             Kind.DICT if body.endswith("{")
             else Kind.LIST if body.endswith("[")
             else Kind.NONE
        )
        closer=_CLOSING_KIND.get(body, Kind.NONE)

        return cls(indent=len(s) - len(stripped),
                   text=body,
                   parent_kind=parent_kind,
                   opener=opener,
                   closer=closer,
        )

    def raw(self) -> str:
        return " " * self.indent + self.text + "\n"

    def width(self) -> int:
        return self.indent + len(self.text)

@dataclass
class Frame:
    kind: Kind
    depth: int
    lines: list[Line] = field(default_factory=list)

    pack_limit: int = 0
    fold_limit: int = 0
    can_pack: bool = True

    content_lines: int = 0
    items: int = 0

    fold_ok: bool = True
    child_nesting: int = -1


class JSONFoldWriter:
    """File-like wrapper that folds pretty-printed JSON as it is written.

    JSONFoldWriter is intended to be passed to json.dump() as the output file.
    It intercepts write() calls, reconstructs complete pretty-printed lines,
    tracks open list/dict frames, and emits either the original lines or a
    packed/folded equivalent.

    Most callers should use dump() or dumps() instead of instantiating this
    class directly.
    """

    def __init__(self, fp: TextIO, *,
                 compact: JSONFold | str = ""):
        self.fp = fp
        if isinstance(compact, str):
            compact = JSONFold.PRESETS[compact]
        self.cfg = compact
        self.pending = ""
        self.stack: list[Frame] = []

    # ------------------------------------------------------------------ I/O

    def write(self, s: str) -> int:
        if not self.cfg:
            return self.fp.write(s)

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
        for frame in self.stack:
            for line in frame.lines:
                self.fp.write(line.raw())

        self.stack.clear()

    def __enter__(self) -> "JSONFoldWriter":
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.finish()

    def __getattr__(self, name: str) -> Any:
        return getattr(self.fp, name)

    # ------------------------------------------------------------ core feed

    def _feed(self, line: Line) -> None:
        opener = line.opener
        if opener:
            depth = len(self.stack)
            self.stack.append(Frame(
                kind=opener,
                depth=depth,
                lines=[line],
                pack_limit=self._pack_limit(opener),
                fold_limit=self._fold_limit(opener),
                can_pack=depth <= self.cfg.pack_nesting
                )
            )

            if line.width() > self.cfg.width:
                self._mark_no_fold()
            return

        closer = line.closer
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

        if frame.fold_ok and line.width() > self.cfg.width:
            self._mark_no_fold()

        if not frame.fold_ok:
            self._stream_frame(frame, keep_last=True)

    def _try_pack(self, frame: Frame, line: Line) -> bool:
        if (
            not frame.lines or
            not frame.can_pack or
            frame.pack_limit <= 1 or
            not self._packable(line)
        ):
            return False

        prev = frame.lines[-1]

        if not (
            self._packable(prev)
            and prev.indent == line.indent
            and prev.items + line.items <= frame.pack_limit
            and line.indent + len(prev.text) + 1 + len(line.text) <= self.cfg.width
        ):
            return False

        prev.text += " " + line.text
        prev.items += line.items

        if line.parent_kind == frame.kind:
            frame.items += line.items

        self._check_fold_limits(frame)
        return True

    def _packable(self, line: Line) -> bool:
        return (
            line.child_nesting < 0
            and line.parent_kind
            and not line.opener
            and not line.closer
        )

    def _pack_limit(self, kind: Kind) -> int:
        if kind == Kind.LIST:
            return self.cfg.pack_array_items
        if kind == Kind.DICT:
            return self.cfg.pack_obj_items
        return 0

    def _fold_limit(self, kind: Kind) -> int:
        if kind == Kind.LIST:
            return self.cfg.fold_array_items
        if kind == Kind.DICT:
            return self.cfg.fold_obj_items
        return 0

    # --------------------------------------------------------- frame tracking

    def _update_frame(self, frame: Frame, line: Line) -> None:
        if line.closer:
            return

        frame.content_lines += 1

        if line.parent_kind == frame.kind:
            frame.items += line.items

        if line.child_nesting >= 0:
            frame.child_nesting = max(frame.child_nesting, line.child_nesting + 1)

        self._check_fold_limits(frame)

    def _check_fold_limits(self, frame: Frame) -> None:
        if frame.content_lines > 1:
            frame.fold_ok = False

        if frame.depth > self.cfg.fold_nesting:
            frame.fold_ok = False

        if frame.items > frame.fold_limit:
            frame.fold_ok = False

        if frame.child_nesting > self.cfg.fold_nesting:
            frame.fold_ok = False

    # --------------------------------------------------------- phase 2: fold

    def _close_frame(self, closer: Line, closing_kind: Kind) -> None:
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
            parent_kind=self._parent_kind(),
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
        keep = 1 if keep_last and frame.lines and self._packable(frame.lines[-1]) else 0

        emit_lines = frame.lines[:-keep] if keep else frame.lines
        frame.lines = frame.lines[-keep:] if keep else []

        for line in emit_lines:
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

    def _parent_kind(self) -> Kind:
        return self.stack[-1].kind if self.stack else Kind.NONE
# ---------------------------------------------------------------------------
# Public helpers
# ---------------------------------------------------------------------------


def dump(obj: Any, fp: TextIO, *,
         compact: JSONFold | str = "",
         indent: int = 2, **kwargs: Any) -> None:

    with JSONFoldWriter(fp, compact=compact) as out:
        json.dump(obj, out, indent=indent, **kwargs)


def dumps(obj: Any, *,
          compact: JSONFold | str = "",
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
    p.add_argument("--verbose", "-v", action="store_true", help="Enable verbose/debug output")

    # Pack phase
    g = p.add_argument_group("pack phase (merge scalars N-per-line)")
    g.add_argument("--pack-items",       type=int, default=None,
                   help="set both --pack-array-items and --pack-obj-items")
    g.add_argument("--pack-array-items", type=int, default=None)
    g.add_argument("--pack-obj-items",   type=int, default=None)
    g.add_argument("--pack-nesting",     type=int, default=None)

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
    if args.pack_items is not None:
        overrides["pack_array_items"] = args.pack_items
        overrides["pack_obj_items"]   = args.pack_items
    if args.fold_items is not None:
        overrides["fold_array_items"] = args.fold_items
        overrides["fold_obj_items"]   = args.fold_items

    # Individual flags (higher priority — applied after shorthands).
    for key in ("width",
                  "pack_array_items", "pack_obj_items", "pack_nesting",
                  "fold_array_items", "fold_obj_items", "fold_nesting"):
        val = getattr(args, key)
        if val is not None:
            overrides[key] = val

    if overrides:
        cfg = replace(cfg, **overrides)
    if ( args.verbose):
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
