# Is there a Python JSON pretty-printer between compact and fully expanded output?

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

Relevant packages:

* `compact-json` - was the most promising, it has many dials to control output, but appears deprecated ("This package is now deprecated and you are strongly encouraged to migrate to"), and the replacement is based on .NET.
* `json.dumps(indent=N)` - the standard built-in module. Produces fully expanded output with configurable indentation, but no line width limits or folding. 
* `simplejson` - a drop-in `json` replacement, supports more types, but pretty-printing is limited to indentation, no line folding. 
* `orjson` - very fast, but pretty-printing options are minimal 
* `pprint` - pprint has width and compact controls (combine list items horizontally). Generates python syntax, not valid JSON. Good for debugging.
* `jsonfold` — my own incomplete attempt, included for context. Works as a streaming filter on top of existing serializers — no full parse or tree required. Supports configurable line width and limits (nesting, count) for packing and folding. 

Curious whether this approach makes sense or if I'm reinventing something

Links:

* [python docs: json](https://docs.python.org/3/library/json.html)
* [PyPI simplejson](https://pypi.org/project/simplejson/)
* [PyPI orjson](https://pypi.org/project/orjson/)
* [GitHub compact-json](https://github.com/masaccio/compact-json)
* [GitHub: jsonfold](https://github.com/yairlenga/jsonfold/python)
