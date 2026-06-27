package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"os"
	"strings"

	jsonfold "jsonfold/formatter"
)

type intFlag struct {
	value int
	set   bool
}

func (f *intFlag) Set(s string) error {
	var v int
	_, err := fmt.Sscanf(s, "%d", &v)
	if err != nil {
		return err
	}
	f.value = v
	f.set = true
	return nil
}

func (f *intFlag) String() string {
	return fmt.Sprint(f.value)
}

func applyInt(dst *int, f intFlag) {
	if f.set {
		*dst = f.value
	}
}

func demoData() any {
	return map[string]any{
		"meta": map[string]any{
			"version": 1,
			"ok":      true,
			"name":    "jsonfold demo",
		},
		"ids": []any{1, 2, 3, 4, 5, 6},
		"matrix": []any{
			[]any{1, 20, "Red", 300},
			[]any{4000, 50, "Yellow", 6},
			[]any{70, 800, "Green", 9000},
		},
		"items": []any{
			map[string]any{"id": 1, "name": "alpha", "qty": 12, "size": "Medium"},
			map[string]any{"id": 20, "name": "beta", "qty": 3000, "size": "Large"},
			map[string]any{"id": 300, "name": "Charlie", "qty": 4, "size": "Tiny"},
		},
		"long": []any{
			"this is a long message that may force the block to stay expanded",
			"second", "third", "fourth",
		},
		"single_array":  []any{1},
		"single_object": map[string]any{"x": 2},
	}
}

func main() {
	var (
		compact  = flag.String("compact", "default", "compact preset: off, none, low, med, classic, default, high, max, pack, fold, grid, join")
		input    = flag.String("input", "", "read JSON from file instead of stdin")
		indent   = flag.Int("indent", 2, "JSON indentation")
		demo     = flag.Bool("demo", false, "format demo data")
		verbose  = flag.Bool("verbose", false, "print config and stats to stderr")
		sortKeys = flag.Bool("sort-keys", false, "accepted for compatibility; Go maps are encoded with sorted keys")
	)

	var width intFlag
	flag.Var(&width, "width", "line width")

	var packArrayItems, packObjItems, packNesting intFlag
	var foldArrayItems, foldObjItems, foldNesting intFlag
	var gridArrayItems, gridObjItems, gridMinLines, gridMaxLines, gridArrayMin, gridObjMin intFlag
	var joinArrayItems, joinObjItems, joinNesting intFlag

	flag.Var(&packArrayItems, "pack-array-items", "pack array item limit")
	flag.Var(&packObjItems, "pack-obj-items", "pack object item limit")
	flag.Var(&packNesting, "pack-nesting", "pack nesting limit")

	flag.Var(&foldArrayItems, "fold-array-items", "fold array item limit")
	flag.Var(&foldObjItems, "fold-obj-items", "fold object item limit")
	flag.Var(&foldNesting, "fold-nesting", "fold nesting limit")

	flag.Var(&gridArrayItems, "grid-array-items", "grid array item limit")
	flag.Var(&gridObjItems, "grid-obj-items", "grid object item limit")
	flag.Var(&gridMinLines, "grid-min-lines", "grid minimum line count")
	flag.Var(&gridMaxLines, "grid-max-lines", "grid maximum line count")
	flag.Var(&gridArrayMin, "grid-array-min", "grid array minimum items")
	flag.Var(&gridObjMin, "grid-obj-min", "grid object minimum items")

	flag.Var(&joinArrayItems, "join-array-items", "join array item limit")
	flag.Var(&joinObjItems, "join-obj-items", "join object item limit")
	flag.Var(&joinNesting, "join-nesting", "join nesting limit")

	flag.Parse()
	_ = sortKeys

	widthValue := 0
	if width.set {
		widthValue = width.value
	}

	cfg, enabled, err := jsonfold.PresetConfigWithWidth(*compact, widthValue)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(2)
	}

	applyInt(&cfg.PackArrayItems, packArrayItems)
	applyInt(&cfg.PackObjItems, packObjItems)
	applyInt(&cfg.PackNesting, packNesting)

	applyInt(&cfg.FoldArrayItems, foldArrayItems)
	applyInt(&cfg.FoldObjItems, foldObjItems)
	applyInt(&cfg.FoldNesting, foldNesting)

	applyInt(&cfg.GridArrayItems, gridArrayItems)
	applyInt(&cfg.GridObjItems, gridObjItems)
	applyInt(&cfg.GridMinLines, gridMinLines)
	applyInt(&cfg.GridMaxLines, gridMaxLines)
	applyInt(&cfg.GridArrayMin, gridArrayMin)
	applyInt(&cfg.GridObjMin, gridObjMin)

	applyInt(&cfg.JoinArrayItems, joinArrayItems)
	applyInt(&cfg.JoinObjItems, joinObjItems)
	applyInt(&cfg.JoinNesting, joinNesting)

	var data any

	if *demo {
		data = demoData()
	} else {
		var in io.Reader = os.Stdin
		if *input != "" {
			fp, err := os.Open(*input)
			if err != nil {
				fmt.Fprintln(os.Stderr, err)
				os.Exit(1)
			}
			defer fp.Close()
			in = fp
		}

		dec := json.NewDecoder(in)
		if err := dec.Decode(&data); err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
	}

	var out io.Writer = os.Stdout

	var fw *jsonfold.Writer
	if enabled {
		fw = jsonfold.NewWriter(out, cfg)
	} else {
		fw = jsonfold.NewOffWriter(out)
	}

	enc := json.NewEncoder(fw)
	enc.SetEscapeHTML(false)
	enc.SetIndent("", strings.Repeat(" ", *indent))

	if err := enc.Encode(data); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	if err := fw.Finish(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	if *verbose {
		fmt.Fprintf(os.Stderr, "config: %+v\n", cfg)
		fmt.Fprintf(os.Stderr, "stats: %+v\n", fw.Stats)
	}
}
