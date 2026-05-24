Most JSON serializers give you only two choices:

- compact machine output:
```json
{"a":{"b":{"c":"abc"}},"x":{"y":{"z":"xyz"}}}
```

- or fully expanded “pretty-print”:
```json
{
  "a": {
    "b": {
      "c": "abc"
    }
  },
  "x": {
    "y": {
      "z": "xyz"
    }
  }
}
```

I wanted something in between: the first is hard for humans to scan, and the second becomes extremely verbose on real-world nested data.

## The Idea

I wrote a small Python module called `jsonfold`. Instead of replacing Python’s JSON serializer, it works as a lightweight post-processing filter on top of `json.dump()` output.

The formatter selectively:
- folds small containers back onto one line,
- packs short scalar sequences,
- keeps large or complex structures expanded.

Example output:

```json
{
  "a": { "b": { "c": "abc" } },
  "x": { "y": { "z": "xyz" } }
}
```

## Why This Approach?

I did not want to rebuild a serializer - there are many good serializers (including the built-in `json.dump()`) that can efficiently process anything from simple data structures (`list`/`dict`) to custom classes and Python `@dataclass` objects, perform transformations and customize the output layout.

The interesting part is that the formatter does **not** re-parse the JSON stream. It operates as a streaming wrapper around file-like objects:

```python
json.dump(obj, JSONFoldWriter(fp), indent=2)
```

That means that it can handle large documents with fixed memory usage and linear processing time. This approach works with most existing serializers. It also provides wrappers for `json.dump()`, `json.dumps()`.

```python
from jsonfold import dumps

data = {
    "a": {"b": {"c": "abc"}},
    "x": {"y": {"z": "xyz"}},
}

print(dumps(data))
```

## Customization

The formatter allows controlling:
- maximum line width,
- folding depth,
- packing aggressiveness,
- array/object limits.

So you can choose between conservative formatting and more aggressive compaction.

## Full Article:

Medium (no paywall): [A Streaming JSON Formatter That Works With Existing Serializers](https://medium.com/@yair.lenga/a-streaming-json-formatter-that-works-with-existing-serializers-eced220da37d)


## Minimal Usage

Pull `jsonfold.py` from [GitHub project](https://raw.githubusercontent.com/yairlenga/jsonfold/refs/heads/main/articles/01-python/jsonfold.py)

```python
import jsonfold
import sys
data = {
    "meta": {"version": 1, "ok": True},
    "ids": [1, 2, 3, 4, 5],
    "items": [{"id": 1, "name": "alpha"}, {"id": 2, "name": "beta"}],
}
# compact can be: default, low, med, high, max
jsonfold.dump(data, sys.stdout, compact="default")

```

## GitHub Project

Repository: https://github.com/yairlenga/jsonfold

Python implementation is under `python` directory.

Future articles will cover other implementations: JavaScript, Java, C, ... - please watch the GitHub project, or follow the articles on Medium.