# jsonfold

> Compact and readable JSON formatting for humans.

<table>
<tr>
<th align="left" width="25%">Standard Pretty Print</th>
<th align="left" width="75%">jsonfold</th>
</tr>

<tr>
<td valign="top">

```json
{
```

</td>
<td valign="top">

```json
{
```

</td>
</tr>

<tr>
<td valign="top">

```json
  "meta": {
    "version": 1,
    "ok": true
  },
```

</td>
<td valign="middle">

```json
  "meta": { "version": 1, "ok": true },
```

</td>
</tr>

<tr>
<td valign="top">

```json
  "bbox": {
    "min": {
      "x": 1,
      "y": 2
    },
    "max": {
      "x": 10,
      "y": 20
    }
  },
```

</td>
<td valign="middle">

```json
  "bbox": { "min": { "x": 1, "y": 2 }, "max": { "x": 10, "y": 20 } },
```

</td>
</tr>

<tr>
<td valign="top">

```json
  "ids": [
    1,
    2,
    3,
    4
  ],
```

</td>
<td valign="middle">

```json
  "ids": [ 1, 2, 3, 4 ],
```

</td>
</tr>

<tr>
<td valign="top">

```json
  "matrix": [
    [
      1,
      2
    ],
    [
      3,
      4
    ]
  ]
```

</td>
<td valign="middle">

```json
  "matrix": [ [1, 2], [3, 4] ]
```

</td>
</tr>

<tr>
<td valign="top">

```json
}
```

</td>
<td valign="top">

```json
}
```

</td>
</tr>
</table>

This repository contains the Python implementation of `jsonfold`, a streaming JSON formatting filter that makes pretty-printed JSON more compact and easier to read.

If you want the background story, design goals, implementation details, and examples, start with the article:

# 📖 Article

## [A Streaming JSON Formatter That Works With Existing Serializers](https://medium.com/@yair.lenga/a-streaming-json-formatter-that-works-with-existing-serializers-eced220da37d)

The article explains:
- why existing pretty-printing often becomes unreadable,
- the folding/packing approach,
- streaming constraints,
- bounded buffering,
- and how the formatter works internally.

---

# Features

- Streaming filter around existing JSON serializers
- Width-aware packing and folding
- Bounded buffering
- No full JSON tree reparsing
- Human-readable output for large nested documents
- Configurable compaction levels
- Multiple built-in presets
- Works with standard `json.dump(..., indent=2)` output

---

# Installation

```bash
pip install jsonfold
```

Or from source:

```bash
git clone https://github.com/yairlenga/jsonfold
cd jsonfold
pip install .
```

---

# Basic Usage

```python
from jsonfold import dumps

obj = {
    "meta": {"version": 1, "ok": True},
    "ids": [1, 2, 3, 4],
    "matrix": [[1, 2], [3, 4]],
}

print(dumps(obj))
```

Output:

```json
{
  "meta": { "version": 1, "ok": true },
  "ids": [ 1, 2, 3, 4 ],
  "matrix": [ [1, 2], [3, 4] ]
}
```

Streaming usage:

```python
import json
from jsonfold import JSONFoldWriter

with open("out.json", "w") as fp:
    writer = JSONFoldWriter(fp)
    json.dump(obj, writer, indent=2)
```

---

# Configuration Presets

## default

Balanced default settings.

```python
compact="default"
```

Typical behavior:
- fold small objects/lists,
- allow limited nested folding,
- preserve readability.

---

## low

Conservative compaction.

```python
compact="low"
```

Disables nested folding/joining.

---

## med

Moderate compaction.

```python
compact="med"
```

Allows folding while restricting nested joins.

---

## high

More aggressive packing and folding.

```python
compact="high"
```

Allows:
- larger packed scalar groups,
- deeper nesting,
- more aggressive joining.

---

## max

Maximum compaction subject only to width limits.

```python
compact="max"
```

Useful for very dense but still readable output.

---

# CLI Usage

## Read JSON from stdin

By default, jsonfold will read single JSON object from standard input (file or a pipe) using `json.load()`, and serialize it using the `json.dump(..., indent=2)`

```bash
python -m jsonfold < input.json
jq ... | python -m jsonfold
```

## Use a preset:
There are few preset values - see the section above.

```bash
python -m jsonfold --compact=max < input.json
```

## Control width:
When the output is terminal, the default width is the current terminal width. Otherwise, it will use the preset width (80 for most preset, 120 for `high`). The `--width` can override the default.

```bash
python -m jsonfold --width=100 < input.json
```

## Read from file:

```bash
python -m jsonfold --input data.json
```

## Sort keys:
Passed as-is to default serializer:

```bash
python -m jsonfold --sort-keys < input.json
```

## Verbose/debug output:

To help with debugging, the `verbose` mode can be used. It will print all the configuration parameters that will be used before the formatting, and after the formatting it will provide statistics from the processing. Both output go to `sys.stderr`. 

```bash
python -m jsonfold --verbose < input.json
```
---

## Repository/development usage:
jsonfold is a single file module. You can run it directly in development by specifying the full path name of the py file.

```bash
python jsonfold.py < input.json
```

---

# Algorithm Overview

The formatter operates directly on the pretty-printed line stream generated by:

```python
json.dump(..., indent=2)
```

It does not implement a full JSON parser.

Instead, it tracks container frames and applies three phases.

---

## Phase 1: Pack

Join consecutive scalar items onto the same line.

Example:

```json
[
  1,
  2,
  3,
  4
]
```

becomes:

```json
[ 1, 2, 3, 4 ]
```

subject to width and item limits.

---

## Phase 2: Fold

Collapse single-content-line containers.

Example:

```json
{
  "version": 1,
  "ok": true
}
```

becomes:

```json
{ "version": 1, "ok": true }
```

---

## Phase 3: Join

Merge folded containers together.

Example:

```json
[
  [1, 2],
  [3, 4]
]
```

becomes:

```json
[ [1, 2], [3, 4] ]
```

---

# Performance Notes

`jsonfold` is designed primarily for readability and streaming behavior.

The implementation:

- avoids reparsing full JSON trees,
- buffers only currently open frames,
- streams output once folding is no longer possible,
- and can operate incrementally on large documents.

The repository includes benchmark scripts comparing:
- `json.dumps`
- `json.dump`
- folded vs unfolded modes
- streaming vs string-building approaches

---

# License

MIT License