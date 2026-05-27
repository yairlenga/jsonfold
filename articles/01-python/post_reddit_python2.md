I recently worked on debugging calls to a financial service API. The API requests were deeply nested, and I had to review individual structures for hours. I modified my code to log pretty-printed JSON to help me with testing and validation.

At that point, I realized that most JSON serializers seem to offer only two extremes:

Compact output:

    {"a":{"b":{"c":"abc"}},"x":{"y":{"z":"xyz"}}}

Fully expanded pretty-printing:

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

For large requests, the compact version is difficult to scan, while the pretty-printed version can span a huge number of lines and often requires endless scrolling or expand/collapse in viewers.

I started experimenting with a “hybrid” formatter that tries to sit somewhere in the middle:

* preserve the readable pretty-printed structure,
* fold small containers onto one line,
* pack short scalar runs,
* still respect width limits.

Example:

    {
      "meta": { "version": 1, "ok": true },
      "ids": [ 1, 2, 3, 4 ],
      "matrix": [ [1, 2], [3, 4] ]
    }

I originally thought this would be a small extension around `json.dump(indent=2)`. It turned out to be much trickier than I expected once I tried to support:

* streaming behavior,
* bounded buffering,
* width-aware packing,
* nested folding,
* and incremental processing without reparsing full JSON trees.

I ended up building a separate streaming filter around normal serializer output (`jsonfold`).

Before I spend more time on it, I wanted to ask:

* Are there existing Python libraries that already support this kind of hybrid pretty-printing?
* Or do most people just live with the compact-vs-pretty tradeoff?

Relevant links:

* `compact-json` \- is the most promising ([GitHub compact-json](https://github.com/masaccio/compact-json)), it has many dials to control output, but marked "This package is now deprecated and you are strongly encouraged to migrate to", and the replacement is based on .net
* `json.dumps(indent=N)` \- the standard, built-in . Fully expanded, custom indent, No line size limits. [python docs: json](https://docs.python.org/3/library/json.html)
* `simplejson` \- drop-in `json` replacement, support more types, but pretty-printing is limited to indent, no line folder. [PyPL simplejson](https://pypi.org/project/simplejson/)
* `orjson` \- very fast, but pretty-printing is minimal [PyPL orjson](https://pypi.org/project/orjson/)
* `pprint` \- pprint has width and compact (combine list items horizontally). Generate python syntax, not valid JSON. Good for debugging.
* jsonfold — my own incomplete attempt, sharing for context. Works as a streaming filter on top of any existing serializer — no full parse or tree required. Supports line width and limits (nesting, count) on folding. [GitHub: jsonfold](https://github.com/yairlenga/jsonfold/python)

Curious whether this approach makes sense or if I'm reinventing something

* Medium Article: [jsonfold streaming JSON serializer](https://medium.com/@yair.lenga/a-streaming-json-formatter-that-works-with-existing-serializers-eced220da37d)