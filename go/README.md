# jsonfold-go

First Go port of JSONFold, based on the uploaded Python source.

The implementation includes:

- pack / fold / grid / join phases
- presets: off, none, low, med, classic, default, high, max, pack, fold, grid, join
- streaming `io.Writer` filter
- `FormatJSONWithConfig`, `FormatJSONWithPreset`
- `WriteJSONWithConfig`, `WriteJSONWithPreset`
- `FoldTextWithConfig`, `FoldTextWithPreset`
- CLI under `cmd/jsonfold`

Go does not support overloaded methods/functions, so Python-style flexible APIs were split into explicit functions.

Example:

```go
cfg := jsonfold.ConfigWithWidth(jsonfold.DefaultConfig(), 120)
s, err := jsonfold.FormatJSONWithConfig(data, cfg)
```

CLI:

```sh
go run ./cmd/jsonfold --demo --compact=default --width=100
```
