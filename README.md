# jsonfold

> `jsonfold` makes pretty-printed JSON more compact without turning it back into unreadable one-line JSON.

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

```json
{
  "a": { "b": { "c": "abc" } },
  "x": { "y": { "z": "xyz" } }
}
```

## Examples

<table>
<tr>
<th align="left" width="25%">Standard Pretty Print</th>
<th align="left" width="75%">jsonfold</th>
</tr>

<tr>
<td valign="top">


<tr>
<td valign="top">

```json
"meta": {
  "version": 1,
  "ok": true
}
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
}
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
"list": [
  1,
  2,
  3,
  ...,
  28,
  29,
  30
]
```

</td>
<td valign="middle">

```json
"list": [
  1, 2, 3, 4, 5, 6, 7, 8,
  9, 10, 11, 12, 13, 14, 15, 16,
  17, 18, 19, 20, 21, 22, 23, 24,
  25, 26, 27, 28, 29
]
```

</td>
</tr>



</table>

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


| Language | Package | Status | CLI | Filter | Stream | Grid | Notes |
|---|---|---|---|---|---|---|---|
| Python | [python/](./python/) | Ready | YES | YES | YES | YES | wrapper for json.dump(s) API |
| JavaScript | [javascript/](./javascript/) | Ready | YES | YES | - | YES | wrapper for JSON.stringify |
| Java | [java/](./java/) | Ready | YES | YES | YES | - | Wrapper for Jackson ObjectMapper |
| Java | [java/](./java/) | Ready | YES | YES | YES | - | Wrapper for GSON ObjectMapper |
| Perl | [perl/](./perl/ ) | Beta | YES | YES | - | - | Streaming and wrapper around JSON::encode |
| C | [c/](./c/) | Beta | YES | YES | - | - | Process pretty-printed JSON text |
| dotnet | [dotnet/](./dotnet/) | Alpha | YES | YES | Yes | - | USing System.Text.Json.JsonSerializer  |
| Go | [go/](./go/) | Alpha | YES | YES | YES | - | Using json.NewEncoder |


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

## Processing

JSONFold works by applying three optional formatting phases:

- Pack – combine adjacent scalar values onto the same line.
- Fold – collapse small containers onto a single line.
- Grid - Place similar folded lines on a grid.
- Join – combine folded containers when they still fit within the configured width.

Each phase behavior is controlled by parameters. The output of the 1st phase is fed into the 2nd phase, which is then fed into the 3rd phase.

## Website

Browser based application, using the Javascript version is available on [jsonfold.dev](https://www.jsonfold.dev)

## Articles

- [Medium article (No Paywall): A Streaming JSON Formatter That Works With Existing Serializers](https://medium.com/@yair.lenga/a-streaming-json-formatter-that-works-with-existing-serializers-eced220da37d) provides background and information about the Python implementation. (May 2026)

## Related
