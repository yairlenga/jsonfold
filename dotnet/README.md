# JsonFold for .NET

Single-package .NET implementation of JSONFold using the standard `System.Text.Json` serializer.

It mirrors the Java/Python/JavaScript design:

- `JsonFoldConfig`: immutable config and presets (`default`, `none`, `classic`, `low`, `med`, `high`, `max`, `pack`, `fold`, `grid`, `join`).
- `JsonFoldWriter`: `TextWriter` filter for already pretty-printed JSON.
- `JsonFoldFormatter`: object-to-JSON API using `System.Text.Json` with `WriteIndented = true`, then JSONFold filtering.
- `JsonFoldStats`: bytes/lines in/out.

## Quick start

```csharp
using JsonFold;

var data = new {
    meta = new { version = 1, ok = true, name = "jsonfold demo" },
    ids = new[] { 1, 2, 3, 4, 5, 6 },
    matrix = new[] {
        new object[] { 1, 20, "Red", 300 },
        new object[] { 4000, 50, "Yellow", 6 },
        new object[] { 70, 800, "Green", 9000 },
    }
};

string text = JsonFold.JsonFold.FormatJson(data, width: 100, config: JsonFoldConfig.Preset("default"));
Console.WriteLine(text);
```

## Write to a stream

```csharp
var stats = JsonFold.JsonFold.WriteJson(data, Console.Out, width: 120, config: JsonFoldConfig.Preset("high"));
Console.Error.WriteLine(stats);
```

## Fold existing pretty JSON

```csharp
string folded = JsonFold.JsonFold.FoldText(prettyJson, width: 100, config: JsonFoldConfig.Preset("max"));
```

## Notes

This package intentionally does not split into Jackson/Gson-style modules. .NET has one standard serializer target here: `System.Text.Json`.

Unlike Java's serializer integrations, this first serializes through `JsonSerializer.Serialize(..., WriteIndented = true)` and then folds the text. The `JsonFoldWriter` itself is streaming for text input, but `System.Text.Json` object serialization is currently string-based in this small package.
