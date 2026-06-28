package jsonfold

import (
	"bytes"
	"encoding/json"
	"strings"
	"testing"
)

func TestJsonfoldConfig(t *testing.T) {
	cfg := JsonfoldConfig("default")

	if cfg.Width == 0 {
		t.Fatalf("default config width should be set")
	}
}

func demoData() any {
	return map[string]any{
		"meta": map[string]any{
			"version": 1,
			"ok":      true,
		},
		"ids": []int{1, 2, 3},
		"items": []any{
			map[string]any{
				"id":   1,
				"name": "alpha",
			},
			map[string]any{
				"id":   2,
				"name": "beta",
			},
		},
	}
}

func TestWriteJSON(t *testing.T) {
	data := demoData()

	var buf bytes.Buffer
	stats, err := WriteJSON(&buf, data, 80, JsonfoldConfig("default"), nil)
	if err != nil {
		t.Fatalf("WriteJSON failed: %v", err)
	}

	out := buf.String()
	if out == "" {
		t.Fatalf("WriteJSON produced empty output")
	}

	if !json.Valid([]byte(out)) {
		t.Fatalf("WriteJSON produced invalid JSON:\n%s", out)
	}

	if stats.BytesIn == 0 || stats.BytesOut == 0 {
		t.Fatalf("expected non-zero stats, got %+v", stats)
	}
}

func TestFormatJSON(t *testing.T) {
	data := demoData()

	out, err := FormatJSON(data, 80, JsonfoldConfig("default"), nil)
	if err != nil {
		t.Fatalf("FormatJSON failed: %v", err)
	}

	if out == "" {
		t.Fatalf("FormatJSON produced empty output")
	}

	if !json.Valid([]byte(out)) {
		t.Fatalf("FormatJSON produced invalid JSON:\n%s", out)
	}
}

func TestFoldText(t *testing.T) {
	text := `{
  "ids": [
    1,
    2,
    3
  ]
}
`

	out, err := FoldText(text, 80, JsonfoldConfig("default"))
	if err != nil {
		t.Fatalf("FoldText failed: %v", err)
	}

	if out == "" {
		t.Fatalf("FoldText produced empty output")
	}

	if !json.Valid([]byte(out)) {
		t.Fatalf("FoldText produced invalid JSON:\n%s", out)
	}
}

func TestOptionsIndent(t *testing.T) {
	data := map[string]any{
		"a": []int{1, 2},
	}

	opt := &Options{Indent: 4}

	out, err := FormatJSON(data, 80, JsonfoldConfig("none"), opt)
	if err != nil {
		t.Fatalf("FormatJSON failed: %v", err)
	}

	if !strings.Contains(out, "\n    ") {
		t.Fatalf("expected 4-space indentation, got:\n%s", out)
	}
}
