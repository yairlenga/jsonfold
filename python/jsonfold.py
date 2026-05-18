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
from dataclasses import dataclass, KW_ONLY, replace
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
    kind:   str         # "dict" or "list"
    start:  int         # index of opener line in buffer
    indent: int
    # --- pack tracking ---
    pack_ok:      bool = True   # False once packing rules are violated
    content_lines: int = 0      # non-opener, non-closer buffer lines (post-pack)
    items:         int = 0      # direct child item count (post-pack)
    # --- fold tracking ---
    fold_ok:      bool = True   # False once fold rules are violated
    child_nesting: int = -1     # deepest folded-child nesting seen


# ---------------------------------------------------------------------------
# Writer
# ---------------------------------------------------------------------------

class JSONFoldWriter:

    def __init__(self, fp: TextIO, *,
                 compact: JSONFold | None = None,
                 indent_step: int = 2):
        self.fp           = fp
        self.cfg          = compact or JSONFold.DEFAULT
        self.indent_step  = indent_step
        self.pending      = ""
        self.buffer: list[Line]  = []
        self.stack:  list[Frame] = []

    # ------------------------------------------------------------------ I/O

    def write(self, s: str) -> int:
        n = len(s)
        if not s:
            return 0

        parts = s.splitlines(keepends=True)
        if self.pending:
            parts[0] = self.pending + parts[0] if parts else self.pending
            self.pending = ""

        if parts and not parts[-1].endswith("\n"):
            self.pending = parts.pop()

        for part in parts:
            self._feed(Line.parse(part[:-1], self._parent_kind()))

        # If a pending line is already over-width, no folding can save it.
        if self.pending and len(self.pending.rstrip()) > self.cfg.width:
            self._mark_no_fold()
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

    # ------------------------------------------------------------ core feed

    def _feed(self, line: Line) -> None:
        # Phase 1: try to pack this line onto the previous one.
        prev_len = len(self.buffer)
        self._pack(line)
        appended = len(self.buffer) > prev_len   # False if packed into prev line

        # Update the parent frame only when a new buffer line was created.
        # (Packing into an existing line doesn't change content_lines count.)
        if self.stack and appended:
            self._update_frame(self.stack[-1], self.buffer[-1])

        # A line that is already over-width can never be folded.
        if self.buffer[-1].width() > self.cfg.width:
            self._mark_no_fold()

        # Push a new frame on opener.
        opener = self.buffer[-1].is_opener()
        if opener:
            self.stack.append(Frame(
                kind=opener,
                start=len(self.buffer) - 1,
                indent=self.buffer[-1].indent,
            ))

        # On closer: attempt phase 2 fold, then pop.
        closer = self.buffer[-1].is_closer()
        if closer:
            self._close_frame(closer)

        self._flush_safe_prefix()

    # --------------------------------------------------------- phase 1: pack

    def _pack(self, line: Line) -> None:
        """Append line to buffer, merging with previous line if pack rules allow."""
        if not self.buffer or not self._packable(line):
            self.buffer.append(line)
            return

        prev  = self.buffer[-1]
        limit = self._pack_limit(line.parent)

        if (self._packable(prev)
                and prev.parent == line.parent
                and prev.indent == line.indent
                and prev.items + line.items <= limit
                and line.indent + len(prev.text) + 1 + len(line.text) <= self.cfg.width):
            prev.text  = prev.text + " " + line.text
            prev.items += line.items
            return

        self.buffer.append(line)

    def _packable(self, line: Line) -> bool:
        """True if line is a candidate for scalar packing."""
        return (line.child_nesting < 0          # scalar (not a folded container)
                and line.parent in ("dict", "list")
                and self._pack_limit(line.parent) > 1
                and line.is_opener() is None
                and line.is_closer() is None)

    def _pack_limit(self, kind: str | None) -> int:
        c = self.cfg
        if kind == "list": return c.line_array_items
        if kind == "dict": return c.line_obj_items
        return 0

    # --------------------------------------------------------- frame tracking

    def _update_frame(self, frame: Frame, line: Line) -> None:
        """Update frame statistics with the latest buffer line."""
        buf_idx = len(self.buffer) - 1

        # Opener line: already counted in frame.start; nothing to track.
        if buf_idx == frame.start:
            return

        # Closer line: does not count as content.
        if line.is_closer():
            return

        # Content line.
        frame.content_lines += 1

        if line.indent == frame.indent + self.indent_step:
            frame.items += line.items    # one new buffer line at direct-child depth

        # Track deepest folded child for fold eligibility.
        if line.child_nesting >= 0:
            frame.child_nesting = max(frame.child_nesting, line.child_nesting + 1)

        # Pack eligibility: nesting exceeded?
        if frame.pack_ok:
            depth = self._stack_depth_of(frame)
            if depth > self.cfg.line_nesting:
                frame.pack_ok = False

        # Fold eligibility checks (conservative: mark False as soon as violated).
        if frame.fold_ok:
            if line.child_nesting >= 0:
                # Contains a folded child — check fold_nesting.
                if frame.child_nesting > self.cfg.fold_nesting:
                    frame.fold_ok = False
            limit = (self.cfg.fold_array_items if frame.kind == "list"
                     else self.cfg.fold_obj_items)
            if frame.items > limit:
                frame.fold_ok = False
            depth = self._stack_depth_of(frame)
            if depth > self.cfg.fold_nesting:
                frame.fold_ok = False

    def _stack_depth_of(self, frame: Frame) -> int:
        """Nesting depth of frame (0 = top-level)."""
        return self.stack.index(frame)

    # --------------------------------------------------------- phase 2: fold

    def _close_frame(self, closing_kind: str) -> None:
        if not self.stack:
            return

        frame = self.stack.pop()

        # Mismatched closer — should not happen with valid JSON.
        if frame.kind != closing_kind:
            frame.fold_ok = False

        if frame.fold_ok and frame.content_lines == 1:
            if self._try_fold(frame):
                # Notify parent frame about the newly folded line.
                if self.stack:
                    self._update_frame(self.stack[-1], self.buffer[frame.start])
                return

        # Not folded: notify parent about each content line already in buffer.
        # (parent was updated incrementally, nothing extra needed.)

    def _try_fold(self, frame: Frame) -> bool:
        """Attempt to collapse opener + 1 content line + closer to one line."""
        start = frame.start
        end   = len(self.buffer) - 1
        lines = self.buffer[start:end + 1]

        # Must be exactly: opener, one content line, closer.
        if len(lines) != 3:
            return False

        text = self._fold_text(lines)

        if frame.indent + len(text) > self.cfg.width:
            return False

        # Replace the three lines with one folded line.
        folded = Line(
            indent        = frame.indent,
            text          = text,
            parent        = self._parent_kind(),
            items         = 1,
            child_nesting = max(0, frame.child_nesting),
        )
        self.buffer[start:end + 1] = [folded]

        removed = end - start          # = 2
        for f in self.stack:
            if f.start > start:
                f.start -= removed

        return True

    @staticmethod
    def _fold_text(lines: list[Line]) -> str:
        """Join opener, content, closer into one line."""
        opener  = lines[0].text
        content = lines[1].text
        closer  = lines[2].text
        comma = closer.endswith(",")
        if comma:
            closer = closer[:-1]
        result = opener + " " + content + " " + closer
        return result + ("," if comma else "")

    # --------------------------------------------------------- flush helpers

    def _flush_safe_prefix(self) -> None:
        """Flush lines that are no longer inside any open frame."""
        if not self.stack:
            self._flush_all()
            return
        # All open frames (raw or not) hold their content in the buffer so
        # packing can accumulate. Only flush lines before the earliest frame.
        self._flush_prefix(min(f.start for f in self.stack))

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

    # --------------------------------------------------------- misc helpers

    def _mark_no_fold(self) -> None:
        for f in self.stack:
            f.fold_ok = False

    def _parent_kind(self) -> str | None:
        return self.stack[-1].kind if self.stack else None


# ---------------------------------------------------------------------------
# Public helpers
# ---------------------------------------------------------------------------

def _normalize(compact: JSONFold | bool | None) -> JSONFold | None:
    if compact is False:
        return None
    if compact is True or compact is None:
        return JSONFold.DEFAULT
    return compact


def dump(obj: Any, fp: TextIO, *,
         compact: JSONFold | bool | None = True,
         indent: int = 2, **kwargs: Any) -> None:
    cfg = _normalize(compact)
    if cfg is None:
        json.dump(obj, fp, indent=indent, **kwargs)
        return
    with JSONFoldWriter(fp, compact=cfg, indent_step=indent) as out:
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
