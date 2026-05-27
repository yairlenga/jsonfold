# jsonfold

`jsonfold` makes pretty-printed JSON more compact without turning it back into unreadable one-line JSON.

Most JSON serializers offer two extremes:

- compact machine output:

```json
{"a":{"b":{"c":"abc"}},"x":{"y":{"z":"xyz"}}}
```
- fully expanded pretty-printing for humans

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

`jsonfold` sits in the middle. It keeps the readable structure of pretty JSON, but selectively folds small containers and packs short scalar runs when they fit within a target line width.

## Example

### Standard pretty-print

```json
{
  "meta": {
    "version": 1,
    "ok": true
  },
  "ids": [
    1,
    2,
    3,
    4
  ],
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
}
```

### jsonfold output

```json
{
  "meta": { "version": 1, "ok": true },
  "ids": [ 1, 2, 3, 4 ],
  "matrix": [ [ 1, 2 ], [ 3, 4 ] ]
}
```

## What it does

`jsonfold` is a formatting filter for JSON output.

It can:

- keep large or complex structures expanded
- fold small arrays and objects onto one line
- pack adjacent scalar items onto fewer lines
- respect a configurable target line width
- control folding depth and item limits
- preserve valid JSON output

It is useful when ordinary pretty-printing is too verbose, but compact JSON is too hard to scan.

## Implementations 

This repository contains language-specific implementations in subdirectories:

| Language | Package | Status | Notes |
|---|---|---|---|
| Python | [python/](./python/) | Beta | Streaming filter implementation with CLI and configurable folding/packing. Following the `json.dump()` API |
| JavaScript | [javascript/](./javascript/) | Alpha | Wrapper around `JSON.stringify()` with an incremental encoder following Python `json.dump()` style |
| Java | — | TODO | — |
| C | — | TODO | — |

## Configuration

The formatter is controlled by options such as:

- target line width
- maximum items to pack per line
- maximum container size to fold
- maximum nesting depth for packing/folding
- presets such as:
  - `default`
  - `none`
  - `low`
  - `med`
  - `high`
  - `max`

Exact option names may differ slightly between implementations.

## Design goals

`jsonfold` is designed to:

- improve readability of large JSON documents
- reduce vertical space without destroying structure
- work as a post-processing formatting step
- avoid reparsing or rebuilding full JSON trees when possible
- support streaming-friendly implementations

## Status

`jsonfold` is experimental, but usable.

The goal is to explore a practical middle ground between compact JSON and fully expanded pretty-printing.

## Articles

- [Medium article (No Paywall): A Streaming JSON Formatter That Works With Existing Serializers](https://medium.com/@yair.lenga/a-streaming-json-formatter-that-works-with-existing-serializers-eced220da37d) provides background and information about the Python implementation. (May 2026)
