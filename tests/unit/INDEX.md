# jsonfold test index

| ID | Category | Description | Arguments |
|---:|---|---|---|
| 001 | common | Small scalar array, default preset | `--compact=default` |
| 002 | common | Small array with high preset | `--compact=high` |
| 003 | common | Boolean array packing | `--compact=default` |
| 004 | common | String array packing | `--compact=default` |
| 005 | common | Mixed scalar array | `--compact=high` |
| 006 | common | Small object folding | `--compact=default` |
| 007 | common | Small object with strings | `--compact=high` |
| 008 | common | Object with numeric fields | `--compact=high --width=60` |
| 009 | common | Object with sort_keys | `--compact=default --sort-keys` |
| 010 | common | Object using pack-only preset | `--compact=pack` |
| 011 | common | Ten numbers packed with pack preset | `--compact=pack --width=60` |
| 012 | common | Pack limit split | `--compact=pack --pack-array-items=4 --width=80` |
| 013 | common | Pack array with narrow width | `--compact=pack --width=30` |
| 014 | common | Pack object fields | `--compact=pack --pack-obj-items=4` |
| 015 | common | Pack nested array scalars | `--compact=pack --pack-nesting=3` |
| 016 | common | Fold simple array | `--compact=fold` |
| 017 | common | Fold simple object | `--compact=fold` |
| 018 | common | Fold blocked by item limit | `--compact=fold --fold-array-items=3` |
| 019 | common | Fold allowed by larger item limit | `--compact=fold --fold-array-items=8` |
| 020 | common | Fold with narrow width | `--compact=fold --width=28` |
| 021 | common | Join folded matrix rows | `--compact=join` |
| 022 | common | Join matrix inside object | `--compact=join` |
| 023 | common | Join blocked by width | `--compact=join --width=30` |
| 024 | common | Join larger matrix with max | `--compact=max --width=80` |
| 025 | common | Join nested arrays with high preset | `--compact=high --width=50` |
| 026 | common | Array of small objects | `--compact=med` |
| 027 | common | Array of records, high preset | `--compact=high` |
| 028 | common | Nested object with array value | `--compact=high` |
| 029 | common | Object containing multiple small arrays | `--compact=high --width=80` |
| 030 | common | Medium preset keeps some nesting expanded | `--compact=med` |
| 031 | common | Max preset on longer number array | `--compact=max --width=80` |
| 032 | common | Default preset on longer number array | `--compact=default --width=80` |
| 033 | common | High preset with explicit width | `--compact=high --width=60` |
| 034 | common | Pack items override | `--compact=default --pack-items=6 --width=80` |
| 035 | common | Fold items override | `--compact=default --fold-items=10 --width=80` |
| 036 | common | Deep nested object high preset | `--compact=high --join-nesting=2` |
| 037 | common | Deep nested object with default nesting | `--compact=default` |
| 038 | common | Mixed object realistic sample | `--compact=high` |
| 039 | common | Demo-like matrix and long field | `--compact=high --width=70` |
| 040 | common | Indent override | `--compact=high --indent=4` |
| 041 | edge | Empty object, no compaction | `--compact=none` |
| 042 | edge | Empty array, no compaction | `--compact=none` |
| 043 | edge | Very long string exceeds width | `--width=40` |
| 044 | edge | Unicode and emoji string | `--compact=high` |
| 045 | edge | Escaped quotes and backslashes | `--compact=default` |
| 046 | edge | Deeply nested empty arrays | `--compact=max` |
| 047 | edge | Large scalar array with narrow width | `--width=30` |
| 048 | edge | Mixed scalar/object array | `--compact=high` |
| 049 | edge | Width boundary candidate | `--width=20` |
| 050 | edge | Fold nesting disabled | `--fold-nesting=0` |
