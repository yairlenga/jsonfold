# jsonfold: Making Pretty-Printed JSON Compact and Readable

Most JSON serializers give you only two choices:

* compact machine output:

&#8203;

    {"a":{"b":{"c":"abc"}},"x":{"y":{"z":"xyz"}}}

* or fully expanded “pretty-print”:

&#8203;

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

I wanted something in between: the first is hard for humans to scan, and the second becomes extremely verbose on real-world nested data.

I've experimented with a small Python formatter `jsonfold` that can selectively:

* Pack lists of scalars/simple objects
* Fold small containers back onto a single line
* Merge multiple small containers onto a single line

One interesting implementation detail is that it works as a streaming wrapper around `json.dump()` output rather than reparsing JSON or building another JSON tree.

    json.dump(obj, JSONFoldWriter(fp), indent=2)

So it works with fixed memory usage and linear processing time even for large documents.

# Minimal Usage

Pull `jsonfold.py` from [GitHub project](https://raw.githubusercontent.com/yairlenga/jsonfold/refs/heads/main/articles/01-python/jsonfold.py)

    import jsonfold
    import sys
    data = {
        "meta": {"version": 1, "ok": True},
        "ids": [1, 2, 3, 4, 5],
        "items": [{"id": 1, "name": "alpha"}, {"id": 2, "name": "beta"}],
    }
    # compact can be: default, low, med, high, max
    jsonfold.dump(data, sys.stdout, compact="default")

# References:

Repository: [https://github.com/yairlenga/jsonfold](https://github.com/yairlenga/jsonfold)

Python implementation is under `python` directory.

Article with implementation details: Medium (no paywall): [A Streaming JSON Formatter That Works With Existing Serializers](https://medium.com/@yair.lenga/a-streaming-json-formatter-that-works-with-existing-serializers-eced220da37d)

