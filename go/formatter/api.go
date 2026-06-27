package jsonfold

import (
	"bytes"
	"encoding/json"
	"io"
)

func FormatJSONWithConfig(obj any, cfg Config) (string, error) {
	var buf bytes.Buffer
	_, err := WriteJSONWithConfig(&buf, obj, cfg)
	if err != nil {
		return "", err
	}
	return buf.String(), nil
}

func FormatJSONWithPreset(obj any, preset string, width int) (string, error) {
	cfg, enabled, err := PresetConfigWithWidth(preset, width)
	if err != nil {
		return "", err
	}
	if !enabled {
		b, err := json.MarshalIndent(obj, "", "  ")
		if err != nil {
			return "", err
		}
		return string(append(b, '\n')), nil
	}
	return FormatJSONWithConfig(obj, cfg)
}

func WriteJSONWithConfig(fp io.Writer, obj any, cfg Config) (Stats, error) {
	text, err := json.MarshalIndent(obj, "", "  ")
	if err != nil {
		return Stats{}, err
	}
	out := NewWriter(fp, cfg)
	if _, err := out.Write(text); err != nil {
		return out.Stats, err
	}
	if err := out.Finish(); err != nil {
		return out.Stats, err
	}
	return out.Stats, nil
}

func WriteJSONWithPreset(fp io.Writer, obj any, preset string, width int) (Stats, error) {
	cfg, enabled, err := PresetConfigWithWidth(preset, width)
	if err != nil {
		return Stats{}, err
	}
	text, err := json.MarshalIndent(obj, "", "  ")
	if err != nil {
		return Stats{}, err
	}
	if !enabled {
		n, err := fp.Write(append(text, '\n'))
		return Stats{BytesIn: len(text) + 1, BytesOut: n, LinesIn: bytes.Count(text, []byte("\n")) + 1, LinesOut: bytes.Count(text, []byte("\n")) + 1}, err
	}
	out := NewWriter(fp, cfg)
	if _, err := out.Write(text); err != nil {
		return out.Stats, err
	}
	if err := out.Finish(); err != nil {
		return out.Stats, err
	}
	return out.Stats, nil
}

func FoldTextWithConfig(text string, cfg Config) (string, error) {
	out, _, err := FoldPrettyText(text, cfg)
	return out, err
}

func FoldTextWithPreset(text string, preset string, width int) (string, error) {
	cfg, enabled, err := PresetConfigWithWidth(preset, width)
	if err != nil {
		return "", err
	}
	if !enabled {
		return text, nil
	}
	return FoldTextWithConfig(text, cfg)
}
