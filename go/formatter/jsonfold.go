package jsonfold

import (
	"bytes"
	"encoding/json"
	"io"
	"strings"
)

func setupConfig(cfg *Config, width int, opt *Options) {
	_ = opt // Unused for now
	if width != 0 {
		cfg.Width = width
	}
}

func createStream(fp io.Writer, cfg Config, opt *Options) (*Writer, error) {
	_ = opt // Unused for now
	jfw := NewWriter(fp, cfg)
	return jfw, nil
}

// Serialization Options
type Options struct {
	Indent  int
	DoClose bool
}

var defaultOptions = &Options{Indent: 2}

func encodeJSON(fp io.Writer, data any, opt *Options) error {
	enc := json.NewEncoder(fp)
	if opt == nil {
		opt = defaultOptions
	}
	if opt.Indent > 0 {
		enc.SetIndent("", strings.Repeat(" ", opt.Indent))
	} else {
		enc.SetIndent("", "  ")
	}
	return enc.Encode(data)
}

func FormatJSON(data any, width int, cfg Config, opt *Options) (string, error) {
	setupConfig(&cfg, width, opt)
	var buf bytes.Buffer
	fp := new(bytes.Buffer)
	jfw, err := createStream(fp, cfg, opt)
	if jfw != nil {
		defer jfw.Close()
	}
	if err != nil {
		return "", err
	}
	err = encodeJSON(jfw, data, nil)
	if err != nil {
		return "", err
	}
	return buf.String(), err
}

func WriteJSON(fp io.Writer, data any, width int, cfg Config, opt *Options) (Stats, error) {
	setupConfig(&cfg, width, opt)
	jfw, err := createStream(fp, cfg, opt)
	if jfw != nil {
		defer jfw.Close()
	}
	var stats Stats
	if err != nil {
		return stats, err
	}

	err = encodeJSON(jfw, data, nil)
	stats = jfw.Stats()
	return stats, err
}

func FoldText(text string, width int, cfg Config) (string, error) {
	setupConfig(&cfg, width, nil)
	var buf bytes.Buffer
	fp := new(bytes.Buffer)
	jfw, err := createStream(fp, cfg, nil)
	if jfw != nil {
		defer jfw.Close()
	}
	if err != nil {
		return "", err
	}
	jfw.Write([]byte(text))
	jfw.Close()
	return buf.String(), err
}
