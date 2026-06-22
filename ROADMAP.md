# Roadmap

JSONFold aims to provide compact, readable JSON formatting while preserving the familiar structure produced by standard pretty printers.

The focus is on streaming operation, low memory usage, and portable implementations across multiple languages.

## Current

### 0.2

* Grid alignment for arrays and objects.
* Improved and unified APIs.
* Revised presets.
* Expanded test coverage.
* Documentation improvements.

## Short Term

### Additional languages

* Go
* Rust
* C#

### Formatting improvements

* Additional alignment options.
* Better handling of deeply nested structures.
* Width-aware formatting heuristics.
* Improved readability metrics.

### Tool integration

* jq integration.
* PostgreSQL extension or function.
* VS Code extension.
* Command-line enhancements.

## Medium Term

### Ecosystem support

* Additional JSON libraries and serializers.
* Framework integrations.
* More examples and tutorials.

### Performance

* Reduce buffering overhead.
* Improve throughput.
* Maintain streaming behavior and low memory usage.

### Quality

* Larger regression test suite.
* Cross-language conformance tests.
* Additional benchmark datasets.

## Long Term

### Extended JSON support

Potential support for:

* JSON5 formatting.
* Comments and trailing commas.
* Relaxed JSON syntax.

### Advanced formatting

Potential future features include:

* Additional column alignment modes.
* Optional key ordering.
* User-defined formatting policies.
* Custom alignment and layout rules.

### New environments

* Browser and WASM support.
* Editor integrations.
* Database and pipeline components.

## Principles

JSONFold will continue to emphasize:

* Readability over maximum compression.
* Streaming operation.
* Low memory usage.
* Backward compatibility.
* Consistent behavior across languages.
* Minimal dependencies.
* Leveraging existing JSON serializers rather than replacing them.
