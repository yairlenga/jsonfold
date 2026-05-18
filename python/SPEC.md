The project will allow hybrid/smart formatting of JSON. It will start with standard "pretty-print", and will compact the output based on configuration options.

The configuration parameters are:

* width = maximum line width
* fold-items - maximum number of lines that can be joined together (subject to width limit)
* fold-nesting: level of nesting allowed when folding lines. level 0 means no nested objects (list of object) are allowed.
* line-array-items - maximum number of items in a list that can be compressed into single line: '[ a, b, c ]'. Value of 0 mean that list will not be converted into a single line
* line-obj-items - maximum number of properties (key/value) that can be compressed into a single line
* line-nesting: level of nesting allowed when folding an object into a single line. 0 mean only list/objects without nesting are allowed.

Implementation should create a file-object - capture injected strings (from python dump calls). The new class will be called JSONFoldWriter

The configuration object will be JSONFold


Implementation notes:

Create a single-file Python module with:

- `JSONFold` configuration dataclass
- `JSONFoldWriter` file-like wrapper class
- optional helper functions `dump()` and `dumps()`
- optional CLI test/demo main

`JSONFoldWriter` should wrap an existing text file object and intercept only `write()`.

Efficiency requirements:

- Do not repeatedly append to a growing string and search for `\n`.
- In `write()`, split each incoming string once using `splitlines(keepends=True)`.
- Keep only one pending partial line between calls.
- Process complete lines as they arrive.

Nesting requirements:

- Do not rescan completed block text to estimate nesting.
- Track nesting incrementally while parsing lines.
- Each in-progress block should store:
  - `kind`
  - original `lines`
  - `child_fold_nesting`
  - `max_inner_nesting`
- When a child block completes, propagate its nesting information to the parent.

Simplification requirements:

- Avoid optional stream APIs unless clearly needed.
- Do not implement `detach()`.
- Keep the wrapper minimal:
  - `write()`
  - `flush()`
  - `close()`
  - `writable()`
  - `__getattr__()` delegation

Preservation requirements:

- Preserve original indentation from the pretty-printer output.
- Folding should only reduce output size.
- Folding must not rebuild JSON from Python objects.
- The normal JSON encoder remains the source of truth.

I would also add one sentence to the top-level goal:

The compactor is a streaming post-processor over pretty-printed JSON lines, not a replacement JSON encoder.
