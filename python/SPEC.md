# jsonfold SPEC

## Goal

jsonfold is a streaming post-processor for Python
json.dump(..., indent=N) output.

It keeps JSON validity while selectively compacting
pretty-printed JSON.

Two independent transformations are applied:

1. Pack
   Merge consecutive scalar items onto fewer lines.

2. Fold
   Collapse small single-content-line containers into one line.

The implementation must be streaming and avoid buffering
large containers.

------------------------------------------------------------
Public API
------------------------------------------------------------

@dataclass(frozen=True)
class JSONFold:
    width: int = 80

    pack_array_items: int = 8
    pack_obj_items: int = 4
    pack_nesting: int = 1

    fold_array_items: int = 8
    fold_obj_items: int = 4
    fold_nesting: int = 1

Presets:

JSONFold.NONE
JSONFold.DEFAULT

JSONFold.PRESETS = {
    "default",
    "none",
    "max",
    "pack",
    "fold",
}

Helpers:

dump(obj, fp, *, compact="", indent=2, **kwargs)
dumps(obj, *, compact="", indent=2, **kwargs)

Behavior:

compact=str
    Uses the named preset

compact=JSONFold(...)
    Uses the supplied configuration.

------------------------------------------------------------
CLI
------------------------------------------------------------

Input:
    JSON from stdin

Output:
    Folded JSON to stdout

Options:

--demo
--preset {default,none,max,pack,fold}
--width N

Pack phase:
    --pack-items N
    --pack-array-items N
    --pack-obj-items N
    --pack-nesting N

Fold phase:
    --fold-items N
    --fold-array-items N
    --fold-obj-items N
    --fold-nesting N

Other:
    --indent N
    --sort-keys

Rules:

--pack-items
    Sets both:
        pack_array_items
        pack_obj_items

--fold-items
    Sets both:
        fold_array_items
        fold_obj_items

Specific options override shorthand options.

------------------------------------------------------------
Container Kind Enum
------------------------------------------------------------

Use IntEnum:

class Kind(IntEnum):
    NONE = 0
    DICT = auto()
    LIST = auto()

Kind.NONE must evaluate as false.

------------------------------------------------------------
Line Object
------------------------------------------------------------

Each parsed physical line is represented as:

@dataclass
class Line:
    indent: int
    text: str
    parent_kind: Kind = Kind.NONE
    items: int = 1
    child_nesting: int = -1
    opener: Kind = Kind.NONE
    closer: Kind = Kind.NONE

Line.parse(s, parent_kind) must:

1. Count leading spaces as indent
2. Strip trailing whitespace
3. Detect opener:
       endswith("{") => Kind.DICT
       endswith("[") => Kind.LIST
4. Detect closer:
       "}" / "}," => Kind.DICT
       "]" / "]," => Kind.LIST

------------------------------------------------------------
Frame Object
------------------------------------------------------------

Each active container has a frame:

@dataclass
class Frame:
    kind: Kind
    depth: int
    lines: list[Line]

    pack_limit: int
    fold_limit: int
    can_pack: bool

    content_lines: int
    items: int

    fold_ok: bool
    child_nesting: int

Important invariant:

A foldable frame buffers only what is required to
decide folding.

Once folding becomes impossible, the frame streams
old lines forward.

------------------------------------------------------------
Writer Architecture
------------------------------------------------------------

Use a JSONFoldWriter file-like wrapper.

It intercepts write(s) calls from json.dump().

Partial lines are handled using:

    self.pending: str

Pending fragments must not be modified before a
newline is seen.

The writer maintains:

    self.stack: list[Frame]

There is no global line buffer.

------------------------------------------------------------
Feed Logic
------------------------------------------------------------

For each completed line:

1. Parse to Line

2. If opener:
       Create Frame
       Push on stack
       Cache limits

3. If closer:
       Close current frame
       Attempt fold
       Emit folded or expanded result

4. Otherwise:
       Emit line to current frame or output

------------------------------------------------------------
Pack Phase
------------------------------------------------------------

A line is packable when:

    line.child_nesting < 0
    and line.parent_kind
    and not line.opener
    and not line.closer

Packing may occur only if:

    frame.can_pack
    frame.pack_limit > 1
    previous line is packable
    same indent
    combined item count <= frame.pack_limit
    combined width <= cfg.width

Pack operation:

    prev.text += " " + line.text
    prev.items += line.items

------------------------------------------------------------
Fold Phase
------------------------------------------------------------

A frame may fold only if:

    frame.fold_ok
    frame.content_lines == 1
    len(frame.lines) == 3

The three lines are:

    opener
    content
    closer

Folded form:

    opener + " " + content + " " + closer

Preserve trailing comma from closer.

Width must be validated before constructing
the folded line.

Folded line metadata:

    indent = opener.indent
    parent_kind = current parent kind
    items = 1
    child_nesting = max(0, frame.child_nesting)

------------------------------------------------------------
Streaming Rule
------------------------------------------------------------

When folding becomes impossible:

    frame.fold_ok = False

then old lines are streamed forward.

If keep_last=True:

    keep the last packable line

so future scalar lines may still pack into it.

This prevents buffering large containers.

------------------------------------------------------------
Fold Invalidation
------------------------------------------------------------

A frame becomes non-foldable when:

    content_lines > 1
    depth > cfg.fold_nesting
    items > fold_limit
    child_nesting > cfg.fold_nesting
    line width > cfg.width

When width exceeds cfg.width:

    mark all active frames non-foldable
    stream the current top frame

------------------------------------------------------------
Finish Behavior
------------------------------------------------------------

finish() must:

1. Feed pending line if present
2. Expect stack to be empty for valid json.dump()
3. Safely stream remaining frames if stack not empty

------------------------------------------------------------
Non-goals
------------------------------------------------------------

This is not a JSON parser.

The implementation relies on the formatting behavior
of Python json.dump(..., indent=N).

It does not support:
    comments
    trailing commas
    JSON5
    HOCON
    arbitrary pretty-printer formats