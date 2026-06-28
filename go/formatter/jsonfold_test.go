package jsonfold

import (
	"encoding/json"
	"testing"
)

func TestFormatJSONParsesBack(t *testing.T) {
	data := map[string]any{
		"ids":    []any{1, 2, 3, 4, 5, 6},
		"meta":   map[string]any{"version": 1, "ok": true},
		"matrix": []any{[]any{1, 20, "Red", 300}, []any{4000, 50, "Yellow", 6}, []any{70, 800, "Green", 9000}},
	}

	out, err := FormatJSON(data, 100, DefaultConfig(), nil)
	if err != nil {
		t.Fatal(err)
	}

	var got map[string]any
	if err := json.Unmarshal([]byte(out), &got); err != nil {
		t.Fatalf("invalid JSON output: %v\n%s", err, out)
	}
}

func TestFoldTextBasic(t *testing.T) {
	cfg := ConfigWithWidth(DefaultConfig(), 80)
	out, err := FoldText("[\n  1,\n  2,\n  3\n]", 0, cfg)
	if err != nil {
		t.Fatal(err)
	}
	want := "[ 1, 2, 3 ]\n"
	if out != want {
		t.Fatalf("got %q want %q", out, want)
	}
}

func TestPresetOff(t *testing.T) {
	_, enabled, err := PresetConfig("off")
	if err != nil {
		t.Fatal(err)
	}
	if enabled {
		t.Fatal("off preset should return enabled=false")
	}
}
