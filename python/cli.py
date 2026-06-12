#!/usr/bin/env python3
"""jsonfold_cli.py - hybrid pretty/compact JSON output.

jsonfold wraps Python's standard json.dump/json.dumps output and keeps the
normal pretty-printed structure, but selectively compacts small containers and
runs of scalar items when they fit within a configured line width.

The goal is readable JSON:
    - large or complex structures stay expanded;
    - small lists and objects can stay on one line;
    - adjacent scalar items can be packed together;
    - nested folding is controlled by explicit depth limits.

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
    jsonfold --demo
    jsonfold < input.json
    jsonfold --compact=max --width=100 < input.json
    python jsonfold.py --pack-items=20 --fold-items=8 < input.json
"""

import sys
import argparse
from typing import Any
from dataclasses import replace
import json

from jsonfold import config, write_json, JSONFold

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
        "single-array": [ 1 ],
        "single-obj": [ 2 ],
        "wide_array": [f"abcdefghijklmnopqrstuvwxyz{i+1}" for i in range(9)],
        "wide_object": {f"abcdefghijk{i+1}": f"lmnopqrstuvwxyz{i+1}" for i in range(9)},
    }


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main(argv: list[str] | None = None) -> int:

    p = argparse.ArgumentParser(
        description="Read JSON from stdin; write folded JSON to stdout.")
    p.add_argument("--demo",   action="store_true")
    p.add_argument("--compact", choices=JSONFold.PRESETS.keys(), default="default")
    p.add_argument("--width",  type=int, default=None, help="line width limit (default: terminal width/80)")
    p.add_argument("--verbose", "-v", action="store_true", help="Enable verbose/debug output")
    p.add_argument("--input", "-i", metavar="FILE", help="Read JSON input from file instead of stdin")

    # Pack phase
    g = p.add_argument_group("pack phase (combine scalars N-per-line)")
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

    # Join phase
    g = p.add_argument_group("Join phase (combine scalars/folded containers)")
    g.add_argument("--join-items",       type=int, default=None,
                   help="set both --join-array-items and --join-obj-items")
    g.add_argument("--join-array-items", type=int, default=None)
    g.add_argument("--join-obj-items",   type=int, default=None)
    g.add_argument("--join-nesting",     type=int, default=None)


    p.add_argument("--indent",    type=int, default=2)
    p.add_argument("--sort-keys", action="store_true")
    args = p.parse_args(argv)

    overrides: dict[str, int] = {}

    # Convenience shorthands (lower priority than individual flags).
    if args.pack_items is not None:
        overrides["pack_array_items"] = args.pack_items
        overrides["pack_obj_items"]   = args.pack_items
    if args.fold_items is not None:
        overrides["fold_array_items"] = args.fold_items
        overrides["fold_obj_items"]   = args.fold_items
    if args.join_items is not None:
        overrides["join_array_items"] = args.join_items
        overrides["join_obj_items"]   = args.join_items

    # Individual flags (higher priority — applied after shorthands).
    for key in (
                  "pack_array_items", "pack_obj_items", "pack_nesting",
                  "fold_array_items", "fold_obj_items", "fold_nesting",
                  "join_array_items", "join_obj_items", "join_nesting",
    ):
        val = getattr(args, key)
        if val is not None:
            overrides[key] = val

    width = args.width
    if width is None:
        if sys.stdout.isatty():
            import shutil
            width = shutil.get_terminal_size(fallback=(24,80)).columns

    cfg = config(args.compact, **overrides)
        
    if args.verbose:
        print(cfg, file= sys.stderr)

    if args.demo:
        data = _demo()
    else:
        fp = open(args.input) if args.input else sys.stdin
        with fp:
            data = json.load(fp)

    info = write_json(data, sys.stdout, width = width, config=cfg, indent=args.indent,
         sort_keys=args.sort_keys)
    if args.verbose:
        print(info, file=sys.stderr)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
