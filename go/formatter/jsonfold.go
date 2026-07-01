package jsonfold

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"strings"
)

type Stats struct {
	BytesIn  int
	BytesOut int
	LinesIn  int
	LinesOut int
}

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
	Indent    int
	DoClose   bool
	Prefix    string // NYI
	IndentStr string // NYI
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

func LookupConfig(preset string) (Config, error) {
	config, ok := findPreset(preset)
	if !ok {
		return defaultConfig, fmt.Errorf("unknown JSONFold preset: %s", preset)
	}
	return config, nil
}

func JsonfoldConfig(preset string) Config {
	config, ok := findPreset(preset)
	if !ok {
		return defaultConfig
	}
	return config
}

func CreateWriter(fp io.WriteCloser, width int, cfg Config) (*Writer, error) {
	setupConfig(&cfg, width, nil)
	return createStream(fp, cfg, nil)
}

func FormatJSON(data any, width int, cfg Config, opt *Options) (string, error) {
	setupConfig(&cfg, width, opt)
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
	return fp.String(), err
}

func WriteJSON(fp io.Writer, data any, width int, cfg Config, opt *Options) (Stats, error) {
	setupConfig(&cfg, width, opt)
	jfw, err := createStream(fp, cfg, opt)
	var stats Stats

	if jfw == nil {
		return stats, err
	}
	defer jfw.Close()

	err = encodeJSON(jfw, data, nil)
	jfw.Finish()

	stats = jfw.Stats()
	return stats, err
}

func FoldText(text string, width int, cfg Config) (string, error) {
	setupConfig(&cfg, width, nil)
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
	return fp.String(), err
}

func WriteFolded(text string, fp io.Writer, width int, cfg Config, opt *Options) (Stats, error) {
	setupConfig(&cfg, width, opt)
	jfw, err := createStream(fp, cfg, opt)
	var stats Stats

	if jfw == nil {
		return stats, err
	}
	defer jfw.Close()

	jfw.Write([]byte(text))
	jfw.Finish()
	stats = jfw.Stats()
	jfw.Close()

	return stats, err
}
