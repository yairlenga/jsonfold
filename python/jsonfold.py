#!/usr/bin/env python3
"""jsonfold.py - hybrid pretty/compact JSON output.

jsonfold wraps Python's standard json.dump/json.dumps output and keeps the
normal pretty-printed structure, but selectively compacts small containers and
runs of scalar items when they fit within a configured line width.

Example
-------

    from jsonfold import dump, dumps
    import sys
    
    data = {
        "ids": [1, 2, 3, 4],
        "meta": {"version": 1, "ok": True},
    }

    # write Compacted JSON stdout, uses default width (100)
    dump(data, fp=sys.stdout)

    # Getting JSON String, use "high" compact level, width=120
    json_str = dumps(data, compact="high", width=120)


The goal is readable JSON:
    - large or complex structures stay expanded;
    - small lists and objects can stay on one line;
    - adjacent scalar items can be packed together;
    - nested folding is controlled by explicit depth limits.

Public API
----------
    config(base_config="", **overrides) -> JSONFold
        Build a JSONFold configuration from a preset or existing config.

    format_json(obj, width, config="", indent=2, **json_options) -> str
        Serialize obj and return folded JSON text.

    write_json(obj, fp, width, config="", indent=2, **json_options) -> JSONFoldStats
        Serialize obj to fp and return formatting statistics.

    filter_stream(fp, width, config="") -> JSONFoldWriter
        Wrap a text stream with a JSONFold formatting filter.

Compatibility API
-----------------
JSONFold also provides drop-in replacements for Python's standard
json.dump() and json.dumps() functions.

    dump(..., compact="default", width=N)
        Compatible with json.dump(), with additional JSONFold options:

    dumps(..., compact="default", width=N)
        Compatible with json.dumps(), with additional JSONFold options:

The compatibility API defaults to indent=2 and supports the
JSONFold-specific compact and width parameters.       

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
    "default" (also "")
        Balanced default settings.
        Up to 8 array elements, up to 4 key/value pairs, max nesting = 1

    "none"
        Disable all packing and folding.

    "low":
        Same as default, No nested structures in fold/join

    "med":
        Same as default, No nested structures in "join"

    "high":
        aggressive setting. Up to 16 array elements, up to key/value pairs, max nesting = 2

    "max"
        Enable aggressive packing and folding, still subject to width.

Test Presets
------------- 
    "pack"
        Enable packing only; disable folding.

    "fold"
        Enable folding only; disable packing.

    "join"
        Enable folding and joining.

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

Phase 3: Join
    Repeat the pack step, allowing folded lines to be joined with scalar items
    or other folded lines. subject to same limits

    A container may be collapsed from:
    [
        [ 1, 2, 3],
        [ 4, 5, 6]
    ]

    into:
    [ [ 1, 2, 3], [4, 5, 6] ]
    
        - 
    Consecutive scalar lines inside the same container may be joined onto one
    output line, subject to:
        - width limit,
        - item limit,
        - nesting limit,
        - same indentation level.

Example - Using jsonfold API:
-----------------------------

    import jsonfold
    import sys
    data = ...

    # Write to a stream/file
    jsonfold.write_json(data, sys.stdout, width=120, config="max")

    # Create a string, limit nesting in joined lines.
    cfg = jsonfold.config("high", join_nesting=2)
    json_str = jsonfold.format_json(data, width=120, config=cfg)

    # Format existing JSON string.
    json_in = ... # Pretty-printed JSON
    with jsonfold.filter_stream(sys.stdout, width=120, config="max") as out
        print(json_in, out)      

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

CLI
---
    python jsonfold.py < input.json
    python jsonfold.py --demo 
    python jsonfold.py --compact=max --width=100 < input.json
"""

from __future__ import annotations

import io
import json
import sys
from dataclasses import dataclass, KW_ONLY, replace, field
from typing import Any, TextIO
from enum import IntEnum, auto
import re
# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

MAX_ARRAY_ITEMS = 1000
MAX_OBJ_ITEMS = 1000
MAX_NESTING = 10
MAX_GRID_LINES = 1000
DEFAULT_WIDTH = 100

@dataclass(frozen=True, slots=True)
class JSONFoldConfig:
    """Configuration for hybrid pretty/compact JSON formatting.

    A value of 0 disables the corresponding packing or folding rule.
    Larger values allow more aggressive compaction, but all output remains
    subject to the configured width limit.
    """
    width: int = DEFAULT_WIDTH
# Commented out next line - trouble with kernprof
#    _: KW_ONLY
    # Phase 1 – pack scalars N-per-line
    pack_array_items: int = 8       # max scalars per line inside a list
    pack_obj_items:   int = 4       # max scalars per line inside a dict
    pack_nesting:     int = 1       # max container nesting depth for packing

    # Phase 2 – fold single-content-line containers onto one line
    fold_array_items: int = 8       # max items allowed in a folded list
    fold_obj_items:   int = 4       # max items allowed in a folded dict
    fold_nesting:     int = 1       # max container nesting depth for folding

    # Phase 3 - aligning packed lines
    grid_array_items: int = 0
    grid_obj_items: int  = 0
    grid_min_lines: int = 0
    grid_max_lines: int = 0

    # Phase 4 - joining folded lines.
    join_array_items: int = 8
    join_obj_items:   int = 4
    join_nesting:     int = 1


JSONFoldConfig.NONE = JSONFoldConfig(
    pack_array_items = 0,
    pack_obj_items   = 0,
    pack_nesting     = 0,

    fold_array_items = 0,
    fold_obj_items   = 0,
    fold_nesting     = 0,

    grid_array_items = 0,
    grid_obj_items   = 0,
    grid_min_lines   = 0,
    grid_max_lines   = 0,

    join_array_items = 0,
    join_obj_items = 0,
    join_nesting = 0,
)

JSONFoldConfig.DEFAULT = JSONFoldConfig()

JSONFoldConfig.PRESETS = {
    "off": None,
    "default": JSONFoldConfig.DEFAULT,
    "": JSONFoldConfig.DEFAULT,
    "none":    JSONFoldConfig.NONE,

    "low": replace(JSONFoldConfig.DEFAULT, 
        fold_nesting = 0,
        join_nesting = 0,
    ),

    "med": replace(JSONFoldConfig.DEFAULT,
        join_nesting = 0,
    ),

    "high": replace(JSONFoldConfig.DEFAULT,
        pack_array_items = 16,
        pack_obj_items   = 8,
        pack_nesting     = 4,
        fold_array_items = 16,
        fold_obj_items   = 8,
        fold_nesting     = 4,
        join_array_items = 16,
        join_obj_items   = 8,
        join_nesting     = 2,
    ),

    "max": replace(JSONFoldConfig.NONE,
        width = 255,
        pack_array_items = MAX_ARRAY_ITEMS,
        pack_obj_items   = MAX_OBJ_ITEMS,
        pack_nesting     = MAX_NESTING,
        fold_array_items = MAX_ARRAY_ITEMS,
        fold_obj_items   = MAX_OBJ_ITEMS,
        fold_nesting     = MAX_NESTING,
        join_array_items = MAX_ARRAY_ITEMS,
        join_obj_items = MAX_OBJ_ITEMS,
        join_nesting    = MAX_NESTING,
    ),

    # Grid is like default + grid
    "grid":  replace(JSONFoldConfig.NONE,
        pack_array_items = MAX_ARRAY_ITEMS,
        pack_obj_items   = MAX_OBJ_ITEMS,
        pack_nesting     = MAX_NESTING,

        fold_array_items = MAX_ARRAY_ITEMS,
        fold_obj_items   = MAX_OBJ_ITEMS,
        fold_nesting     = MAX_NESTING,

        grid_array_items = MAX_ARRAY_ITEMS,
        grid_obj_items   = MAX_OBJ_ITEMS,
        grid_min_lines   = 3,
        grid_max_lines   = MAX_GRID_LINES,
    ),

    # pack only – no folding
    "pack": replace(JSONFoldConfig.NONE,
        pack_array_items = MAX_ARRAY_ITEMS,
        pack_obj_items   = MAX_OBJ_ITEMS,
        pack_nesting     = MAX_NESTING,
    ),
    # fold only – no packing
    "fold": replace(JSONFoldConfig.NONE,
        fold_array_items = MAX_ARRAY_ITEMS,
        fold_obj_items   = MAX_OBJ_ITEMS,
        fold_nesting     = MAX_NESTING,
    ),
    "join": replace(JSONFoldConfig.NONE,
        fold_array_items = MAX_ARRAY_ITEMS,
        fold_obj_items   = MAX_OBJ_ITEMS,
        fold_nesting     = MAX_NESTING,
        join_array_items = MAX_ARRAY_ITEMS,
        join_obj_items   = MAX_OBJ_ITEMS,
        join_nesting     = MAX_NESTING,
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

KEY_RE = re.compile(
    r"""^\s*
        (?:
            "[^"\\]*" |
            '[^'\\]*' |
            [A-Za-z_$][A-Za-z0-9_$]* |
        )
        \s*:
    """,
    re.X
)

@dataclass(slots=True)
class Line:
    indent: int
    parts: list[str] | None = field(default_factory=list)
    length: int | None  = None      # Current length of text/parts
    kind: Kind = Kind.NONE          # When this is folded line.
    parent_kind: Kind = Kind.NONE   # "dict", "list", or None
    items:  int        = 1      # packed scalar count (>=1)
    leafs: int         = 1      # Total leaf items
    # nesting of the deepest folded child within this line (-1 = scalar)
    child_nesting: int = -1
    opener: Kind = Kind.NONE
    closer: Kind = Kind.NONE
    # Line state
    can_pack: bool = True        # Line is possible candidate for pack
    can_join: bool = True        # Line is possible candidate for join
    can_grid: bool = False       # Line is possible candidate for grid

    try:
        profile
    except NameError:
        def profile(func):
            return func

    @staticmethod
    def _parts_length(parts: list[str]) -> int:
        return sum(len(part)+1 for part in parts)-1        

    def __post_init__(self):
        if self.length == None:
            self.length = self._parts_length(self.parts) if len(self.parts) != 1 else len(self.parts[0])

    @classmethod
    @profile
    def parse(cls, s: str, parent_kind: Kind) -> "Line":
        stripped = s.lstrip()
        body=stripped.rstrip()
        opener= (
             Kind.DICT if body.endswith("{")
             else Kind.LIST if body.endswith("[")
             else Kind.NONE
        )
        closer=_CLOSING_KIND.get(body, Kind.NONE)

        is_body_line = bool(parent_kind and not opener and not closer)

        return cls(
            indent=len(s) - len(stripped),
            parts = [body],
            length = len(body),
            parent_kind=parent_kind,
            opener=opener,
            closer=closer,
            can_join=is_body_line,
            can_pack=is_body_line,
        )

    def raw(self) -> str:
        return " " * self.indent + " ".join(self.parts) + "\n"

    def width(self) -> int:
        return self.indent + self.length
       
    def join_line(self, other: Line) -> None:
        self.parts += other.parts
        if other.parts:
            self.length += 1 + other.length
        self.items += other.items
        self.leafs += other.leafs
        if other.child_nesting > self.child_nesting:
            self.child_nesting = other.child_nesting
            self.can_pack = False

    def set_parts(self, parts: list[str]) -> None:
        self.parts = parts
        self.length = self._parts_length(self.parts)

    def dict_signature(self) -> str:
        signature = []

        for part in self.parts[1:-1]:
            if not (m := KEY_RE.match(part)):
                return None
            signature.append(m[0])
            
        return tuple(signature) 

@dataclass(slots=True)
class Frame:
    kind: Kind
    depth: int
    lines: list[Line] = field(default_factory=list)

    pack_limit: int = 0
    fold_limit: int = 0
    join_limit: int = 0
    grid_limit: int = 0

    content_lines: int = 0
    items: int = 0
    leafs: int = 0

    fold_ok: bool = True
    grid_ok: bool = False
    child_nesting: int = -1

@dataclass(slots=True)
class JSONFoldStats:
    bytes_in:  int = 0
    bytes_out: int = 0
    lines_in:  int = 0
    lines_out: int = 0

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
                 config: JSONFoldConfig | str | None = "",
                 close_fp: bool= False,
    ) -> None:           
        self.fp = fp
        self.stats = JSONFoldStats()
        if isinstance(config, str):
            config = JSONFoldConfig.PRESETS[config]
        self.cfg = config
        self.pending = ""
        self.stack: list[Frame] = []
        self.close_fp = close_fp

    # ------------------------------------------------------------------ I/O
    try:
        profile
    except NameError:
        def profile(func):
            return func

    @profile
    def write(self, s: str) -> int:
        s_len = len(s)
        self.stats.bytes_in += s_len

        # If no config object, do nothing, just pass thru
        if not self.cfg:
            self.stats.lines_in += s.count("\n")
            return self._write_str(s)
        
        # Fast Path: No new line, just a a segment in the line
        nl_pos = s.find("\n")
        if nl_pos < 0:
            self.pending += s
            return s_len

        # Fast Path: line terminated with new line
        nl2_pos = s.find("\n", nl_pos+1)
        if ( nl2_pos < 0 ):
            self.stats.lines_in += 1
            s2 = self.pending + s[:nl_pos]
            self.pending = s[nl_pos+1:]
            line = Line.parse(s2, self._parent_kind())
            self._feed(line)

            return s_len

        # Unlikely case - multiple new lines.
        parts = s.splitlines(keepends=True)
        self.stats.lines_in += len(parts)-1

        if self.pending:
            parts[0] = self.pending + parts[0]
            self.pending = ""

        if not parts[-1].endswith("\n"):
            self.pending = parts.pop()

        for part in parts:
            self._feed(Line.parse(part[:-1], self._parent_kind()))

        return s_len

    def flush(self) -> None:
        self.finish()
        self.fp.flush()

    def close(self) -> None:
        if self.close_fp:
            self.fp.close()

    def finish(self) -> None:
        if self.pending:
            self._feed(Line.parse(self.pending, self._parent_kind()))
            self.pending = ""

        # Should not happen with valid json.dump output.
        # If it does, flush raw without further processing.
        for frame in self.stack:
            for line in frame.lines:
                self._write_line(line)
        self.stack.clear()


    def __enter__(self) -> "JSONFoldWriter":
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.finish()

    def __getattr__(self, name: str) -> Any:
        return getattr(self.fp, name)


    def _write_str(self, s: str):
        bytes = self.fp.write(s)
        self.stats.lines_out += 1
        self.stats.bytes_out += bytes
        return bytes

    def _write_line(self, line: Line):
        self._write_str(line.raw())

    # ------------------------------------------------------------ core feed
    @profile
    def _feed(self, line: Line) -> None:
        opener = line.opener
        if opener:
            self.stack.append(Frame(
                kind=opener,
                depth=len(self.stack),
                lines=[line],
                pack_limit=self._pack_limit(opener),
                fold_limit=self._fold_limit(opener),
                join_limit=self._join_limit(opener),
                grid_limit=self._grid_limit(opener),
                )
            )

            return

        closer = line.closer
        if closer:
            self._close_frame(line, closer)
            return

        # Fast body single line emit
        if self.stack:
            frame = self.stack[-1]
            if line.items >= frame.pack_limit:
               line.can_pack = False
            if line.items >= frame.join_limit:
                line.can_join = False
            self._add_to_frame(frame, line)
        else:
            self._write_line(line)

    @profile
    def _emit_lines(self, lines: list[Line], depth: int | None = None) -> None:
        if not lines:
            return
        
        if depth is None:
            depth = len(self.stack)-1

        if depth < 0:
            for line in lines:
                self._write_line(line)
            return
        
        frame = self.stack[depth]
        for line in lines:
            self._add_to_frame(frame, line)
        return
        
    def _choose_limit(self, kind: Kind, *, default: int =0, list_limit: int =0, dict_limit: int):
        return (
            list_limit if kind == Kind.LIST else
            dict_limit if kind == Kind.DICT else
            default
        )

    def _pack_limit(self, kind: Kind) -> int:
        return self._choose_limit(kind,
                                 list_limit = self.cfg.pack_array_items,
                                 dict_limit = self.cfg.pack_obj_items )


    def _fold_limit(self, kind: Kind) -> int:
        return self._choose_limit(kind,
                                 list_limit = self.cfg.fold_array_items,
                                 dict_limit = self.cfg.fold_obj_items)
    
    def _join_limit(self, kind: Kind) -> int:
        return self._choose_limit(kind,
                                 list_limit = self.cfg.join_array_items,
                                 dict_limit = self.cfg.join_obj_items)
    
    def _grid_limit(self, kind: Kind) -> int:
        return self._choose_limit(kind,
                                 list_limit = self.cfg.grid_array_items,
                                 dict_limit = self.cfg.grid_obj_items)

    # --------------------------------------------------------- phase 1: pack

    @profile
    def _add_to_frame(self, frame: Frame, line: Line) -> None:

        # pack/join relevant only if lines exists and grid not enabled
        if frame.lines:
            if not frame.grid_ok:
                # Consider adding the line to previous line
                prev = frame.lines[-1]
                if (line.can_pack and
                    prev.can_pack and
                    self._try_pack(frame, prev, line)
                ):
                    return
            
                if (line.can_join and
                    prev.can_join and
                    self._try_join(frame, prev, line)
                ):
                    return
            
        # If frame is empty, may be it's in "streaming" mode, which
        # mean that lines that can not be packed/joined can be sent
        # directly to the output:
        elif not frame.fold_ok and not line.can_pack and not line.can_join:
            self._write_line(line)
            return

        # Add as new line
        frame.lines.append(line)

        if frame.fold_ok and line.width() > self.cfg.width:
            self._mark_no_fold()

        if line.child_nesting >= frame.child_nesting:
            frame.child_nesting = line.child_nesting+1

        if not line.closer:
            frame.content_lines += 1
            frame.leafs += line.leafs
            frame.items += line.items

            if frame.fold_ok:
                if not self._check_fold_limits(frame):
                    self._mark_no_fold()

            if frame.grid_ok:
                if not line.can_grid:
                    frame.grid_ok = False

        if not frame.fold_ok and not frame.grid_ok:
            self._stream_frame(frame)
        return


    @profile
    def _can_merge(self, prev: Line, line: Line, limit: int) -> bool:
        return (
            prev.indent == line.indent
            and prev.items + line.items <= limit
            and prev.indent + prev.length + 1 + line.length <= self.cfg.width
        )

    @profile
    def _merge_into_frame(self, frame: Frame, prev: Line, line: Line) -> None:
        prev.join_line(line)

        if prev.items >= frame.pack_limit or prev.child_nesting >= self.cfg.pack_nesting:
            prev.can_pack = False
        
        if prev.items >= frame.join_limit or prev.child_nesting >= self.cfg.join_nesting:
            prev.can_join = False

        frame.items += line.items
        frame.leafs += line.leafs

        if frame.fold_ok:
            if not self._check_fold_limits(frame):
                self._mark_no_fold()
                self._stream_frame(frame)
        return

    @profile
    def _try_pack(self, frame: Frame, prev: Line, line: Line) -> bool:
        if ( frame.pack_limit <= 1 or
#            line.child_nesting >= self.cfg.pack_nesting or
#            prev.child_nesting >= self.cfg.pack_nesting or
            not self._can_merge(prev, line, frame.pack_limit)):
            return False

        self._merge_into_frame(frame, prev, line)
        if not prev.can_pack:
            prev.can_join = False

        return True
    
    @profile
    def _try_join(self, frame: Frame, prev, line: Line) -> bool:        
        if (
            frame.join_limit <= 1 or
#            line.child_nesting >= self.cfg.join_nesting or
#            prev.child_nesting >= self.cfg.join_nesting or
            not self._can_merge(prev, line, frame.join_limit)
        ):
            return False

        self._merge_into_frame(frame, prev, line)
        return True

    # --------------------------------------------------------- frame tracking

    @profile
    def _check_fold_limits(self, frame: Frame) -> bool:
        if frame.content_lines > 1:
            return False

        if frame.items > frame.fold_limit:
            return False

        if frame.child_nesting >= self.cfg.fold_nesting:
            return False

        return True

    # --------------------------------------------------------- phase 2: fold

    @profile
    def _close_frame(self, closer: Line, closing_kind: Kind) -> None:
        if not self.stack:
            self._write_line(closer)
            return

        # Frame is removed stack.
        frame = self.stack.pop()
        frame.lines.append(closer)

#       Need to handle mismatch between closing and opening.
#        if frame.kind != closing_kind: ...

        if frame.fold_ok:
            if self._try_fold(frame):
                # After successful fold, parent frame may support grid, based on the first child.
                if (self.stack and frame.lines[0].can_grid):
                    parent_frame = self.stack[-1]
                    if parent_frame.content_lines == 0:
                        parent_frame.grid_ok = True

        elif frame.grid_ok:
            if self._try_grid(frame):
                self._mark_no_grid()

        self._emit_lines(frame.lines)
        frame.lines.clear()
        return

    # Fold a frame with 3 lines into a single line:
    #   {
    #       "a": "b"
    #   }
    # To a single line:
    #   { "a" : "b" }
    # which is placed into the frame
    @profile
    def _try_fold(self, frame: Frame) -> bool:
        
        if (not frame.fold_ok or
            frame.content_lines != 1 or
            len(frame.lines) != 3 
        ):
            return False

        folded_length = sum(1 + line.length for line in frame.lines) - 1
        first_line = frame.lines[0]

        if first_line.indent + folded_length > self.cfg.width:
            return False

#        parts = [part for part in line.parts for line in frame.lines]
        parts = [part for line in frame.lines for part in line.parts]
        

        line = Line(
            indent=first_line.indent,
            parts = parts,
            kind = frame.kind,
            parent_kind=self._parent_kind(),
            items=1,
            leafs=frame.leafs,
            child_nesting=frame.child_nesting,
            can_pack=False,
            can_join=frame.child_nesting < self.cfg.join_nesting,
            can_grid=self.cfg.grid_max_lines > 0,
        )
        frame.lines = [ line ]
        return True

    @staticmethod
    def _format_parts(parts: list[str], widths: list[int]) -> list[str]:
        last = len(widths)-1
        return [
            (
                part.rjust(widths[i])
                if part[:1] in "-0123456789"
                else part.ljust(widths[i]) if i<last
                else part
            ) for i, part in enumerate(parts)
        ]

    def _try_grid(self, frame: Frame) -> bool:
        line_count = len(frame.lines)-2
        if ( line_count < 1 or
            line_count < self.cfg.grid_min_lines or
            line_count > self.cfg.grid_max_lines
        ):
            return False

        # Check that all rows have identical count
        lines = frame.lines[1:-1]
        first_line = lines[0]
        part_count = len(first_line.parts)
        if any(len(line.parts) != part_count for line in lines):
            return False

        # Check that all lines have identical signature if it's a dict
        if first_line.kind == Kind.DICT:
            dict_signature = first_line.dict_signature()
            if not dict_signature:
                return False
            if any(line.dict_signature() != dict_signature for line in lines):
                return False

        # Calculate max width for each part        
        widths = [
            max(len(line.parts[i]) for line in lines)
            for i in range(part_count)
        ]
        # Make sure all lines will fit.
        grided_length = sum(1 + width for width in widths)- 1
        if frame.lines[0].indent + grided_length > self.cfg.width:
            return False

        # Rebuild combined text from the parts, adjusting for width
        for line in lines:
            new_parts = self._format_parts(line.parts, widths)
            line.set_parts(new_parts)
            line.can_pack = False
            line.can_join = False
            line.can_grid = False

        return True

    # --------------------------------------------------------- streaming
    @profile
    def _stream_frame(self, frame: Frame) -> None:
        lines = frame.lines
        if not lines:
            return 

        last = lines[-1]
        keep_last = last.can_pack or last.can_join
        if keep_last:
            lines.pop()
        self._emit_lines(lines, frame.depth-1)
        lines.clear()
        if keep_last:
            lines.append(last)

    # --------------------------------------------------------- misc helpers
    @profile
    def _mark_no_fold(self) -> None:
        for frame in self.stack:
            frame.fold_ok = False

    def _mark_no_grid(self) -> None:
        for frame in self.stack:
            frame.grid_ok = False

    def _parent_kind(self) -> Kind:
        return self.stack[-1].kind if self.stack else Kind.NONE
# ---------------------------------------------------------------------------
# Public helpers
# ---------------------------------------------------------------------------

def _stream(fp: TextIO, config: JSONFoldConfig | str = "", *, close_fp: bool= False):
    return JSONFoldWriter(fp, config=config, close_fp=close_fp)

def _config(config: JSONFoldConfig | str, width: int | None = None, **overrides) -> JSONFoldConfig:
    if isinstance(config, str):
        config = JSONFoldConfig.PRESETS[config]
    if width is not None:
        overrides["width"] = width
    if overrides:
        config = replace(config, **overrides)
    return config

# Generic API

def jsonfold_config(base_config: JSONFoldConfig | str = "", width: int = None, **overrides):
    """Create a JSONFold configuration.

    Starts from a preset name or existing JSONFold object and applies
    any supplied overrides.

    Examples:
        jsonfold_config("high", width=120)
        jsonfold_config(JSONFold.DEFAULT, fold_nesting=2)
    """
    return _config(base_config, width, **overrides)

def format_json(obj: Any, width: int, config: JSONFoldConfig | str = "", indent = 2, **kwargs: Any) -> str:
    """Format an object as JSON and return the resulting string.

    The output is first pretty-printed using ``json.dump()`` and then
    compacted by JSONFold according to the selected configuration.

    Args:
        obj: Object to serialize.
        width: Maximum output line width.
        config: JSONFold preset name or configuration object.
        indent: JSON indentation level.
        **kwargs: Additional arguments passed to ``json.dump()``.

    Returns:
        Compacted formatted JSON text.
    """

    with io.StringIO() as str_io:
        with _stream(str_io, _config(config, width=width)) as out:
            json.dump(obj, out, indent=indent, **kwargs)
        return str_io.getvalue()

def write_json(obj: Any, fp : TextIO, width: int, config: JSONFoldConfig | str = "", indent=2, **kwargs: Any) -> JSONFoldStats:
    """Write compacted formatted JSON to a stream.

    The output is pretty-printed using ``json.dump()`` and then compacted
    by JSONFold according to the selected configuration.

    Args:
        obj: Object to serialize.
        fp: Output text stream.
        width: Maximum output line width.
        config: JSONFold preset name or configuration object.
        indent: JSON indentation level.
        **kwargs: Additional arguments passed to ``json.dump()``.

    Returns:
        Statistics describing the formatting operation.
    """
    with _stream(fp, _config(config, width=width)) as out:
        json.dump(obj, out, indent=indent, **kwargs)
    return out.stats

def filter_stream(fp: TextIO, width: int, config: JSONFoldConfig | str = "", *, close_fp: bool = False) -> JSONFoldWriter:
    """Create a JSONFold filtering stream.

    Returns a writable stream wrapper that accepts pretty-printed JSON
    and emits compacted JSONFold output to the underlying stream.

    Args:
        fp: Destination text stream.
        width: Maximum output line width.
        config: JSONFold preset name or configuration object.

    Returns:
        A JSONFoldWriter instance.
    """
    return _stream(fp, _config(config, width=width), close_fp=close_fp)

# Python json compatible API

def dump(
        obj: Any, fp: TextIO, *,
        compact: JSONFoldConfig | str = "",
        width: int | None = None,
        indent: int = 2,
        **kwargs: Any,
        ) -> None:
    """Serialize an object as JSON and write it to a stream.

    This function is compatible with ``json.dump()`` and accepts the
    same serialization options. Output is compacted using JSONFold.

    Args:
        obj: Object to serialize.
        fp: Output text stream.
        compact: JSONFold preset name or configuration object.
        width: Optional line width override.
        indent: JSON indentation level.
        **kwargs: Additional arguments passed to ``json.dump()``.
    """
    config = _config(compact, width=width)
    with _stream(fp, config) as out:
        json.dump(obj, out, indent=indent, **kwargs)
    return

def dumps(
        obj: Any, *,
        compact: JSONFoldConfig | str = "",
        width: int | None = None,
        indent: int = 2,
        **kwargs: Any) -> str:
    """Serialize an object to a JSON string.

    This function is compatible with ``json.dumps()`` and accepts the
    same serialization options. Output is compacted using JSONFold.

    Args:
        obj: Object to serialize.
        compact: JSONFold preset name or configuration object.
        width: Optional line width override.
        indent: JSON indentation level.
        **kwargs: Additional arguments passed to ``json.dump()``.

    Returns:
        compacted JSON text.
    """
    config = _config(compact, width=width)
    with io.StringIO() as str_io:
        with _stream(str_io, config) as out:
            json.dump(obj, out, indent=indent, **kwargs)
        return str_io.getvalue()

# ---------------------------------------------------------------------------
# Demo data
# ---------------------------------------------------------------------------

def _demo() -> dict[str, Any]:
    return {
        "meta":   {"version": 1, "ok": True, "name": "jsonfold demo"},
        "ids": [ 1, 2, 3, 4, 5, 6 ],
        "matrix": [[1, 20, "Red", 300], [4000, 50, "Yellow", 6], [ 70, 800, "Green", 9000]],
        "items":  [{"id": 1, "name": "alpha", "qty": 12, "size": "Medium"}, {"id": 20, "name": "beta", "qty": 3000, "size": "Large"}, { "id": 300, "name": "Charlie", "qty": 4, "size": "Tiny"}],
        "long": [
            "this is a long message that may force the block to stay expanded",
            "second", "third", "fourth",
        ],
        "single_array": [ 1 ],
        "single_object": { "x" : 2 },
        "long_array": [ f"a{i+1}" for i in range(50)],
        "wide_array": [f"abcdefghijklmnopqrstuvwxyz{i+1}" for i in range(9)],
        "wide_object": {f"abcdefghijk{i+1}": f"lmnopqrstuvwxyz{i+1}" for i in range(9)},
    }

# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main(argv: list[str] | None = None) -> int:
    import argparse

    p = argparse.ArgumentParser(
        description="Read JSON from stdin; write folded JSON to stdout.")
    p.add_argument("--demo",   action="store_true")
    p.add_argument("--compact", choices=JSONFoldConfig.PRESETS.keys(), default="default")
    p.add_argument("--width",  type=int, default=None, help=f"line width limit (default: terminal width/{DEFAULT_WIDTH})")
    p.add_argument("--verbose", "-v", action="store_true", help="Enable verbose/debug output")
    p.add_argument("--input", "-i", metavar="FILE", help="Read JSON input from file instead of stdin")

    p.add_argument("--indent",    type=int, default=2)
    p.add_argument("--sort-keys", action="store_true")
    args = p.parse_args(argv)

    # Start from preset, apply overrides where explicitly given.
    width = args.width
    if width is None:
        if sys.stdout.isatty():
            import shutil
            width = shutil.get_terminal_size(fallback=(24,DEFAULT_WIDTH)).columns

    cfg = config(args.compact)

    if args.verbose:
        print(cfg, file= sys.stderr)

    if args.demo:
        data = _demo()
    else:
        fp = open(args.input) if args.input else sys.stdin
        with fp:
            data = json.load(fp)

    info = write_json(data, sys.stdout, width, config=cfg, indent=args.indent,
         sort_keys=args.sort_keys)
    if args.verbose:
        print(info, file=sys.stderr)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
