package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"os"
	"strconv"

	jsonfold "jsonfold/formatter"
)

type optionalInt struct {
	value *int
}

func (f *optionalInt) Set(s string) error {
	v, err := strconv.Atoi(s)
	if err != nil {
		return err
	}
	f.value = &v
	return nil
}

func (f *optionalInt) String() string {
	if f.value == nil {
		return ""
	}
	return strconv.Itoa(*f.value)
}

func (f optionalInt) IsSet() bool {
	return f.value != nil
}

func (f optionalInt) Value() int {
	return *f.value
}

type args struct {
	compact  string
	input    string
	output   string
	indent   int
	demo     bool
	verbose  bool
	sortKeys bool

	width optionalInt

	packItems      optionalInt
	packArrayItems optionalInt
	packObjItems   optionalInt
	packNesting    optionalInt

	foldItems      optionalInt
	foldArrayItems optionalInt
	foldObjItems   optionalInt
	foldNesting    optionalInt

	gridItems      optionalInt
	gridArrayItems optionalInt
	gridObjItems   optionalInt
	gridMinLines   optionalInt
	gridMaxLines   optionalInt
	gridArrayMin   optionalInt
	gridObjMin     optionalInt

	joinItems      optionalInt
	joinArrayItems optionalInt
	joinObjItems   optionalInt
	joinNesting    optionalInt
}

func parseArgs() args {
	var a args

	flag.StringVar(&a.compact, "compact", "default", "compact preset")
	flag.StringVar(&a.input, "input", "", "read JSON from file instead of stdin")
	flag.IntVar(&a.indent, "indent", 2, "JSON indentation")
	flag.BoolVar(&a.demo, "demo", false, "format demo data")
	flag.BoolVar(&a.verbose, "verbose", false, "print config and stats to stderr")
	flag.BoolVar(&a.sortKeys, "sort-keys", false, "accepted for compatibility; Go maps are encoded with sorted keys")

	flag.Var(&a.width, "width", "line width")

	flag.Var(&a.packItems, "pack-items", "pack item limit for arrays and objects")
	flag.Var(&a.packArrayItems, "pack-array-items", "pack array item limit")
	flag.Var(&a.packObjItems, "pack-obj-items", "pack object item limit")
	flag.Var(&a.packNesting, "pack-nesting", "pack nesting limit")

	flag.Var(&a.foldItems, "fold-items", "fold item limit for arrays and objects")
	flag.Var(&a.foldArrayItems, "fold-array-items", "fold array item limit")
	flag.Var(&a.foldObjItems, "fold-obj-items", "fold object item limit")
	flag.Var(&a.foldNesting, "fold-nesting", "fold nesting limit")

	flag.Var(&a.gridItems, "grid-items", "grid item limit for arrays and objects")
	flag.Var(&a.gridArrayItems, "grid-array-items", "grid array item limit")
	flag.Var(&a.gridObjItems, "grid-obj-items", "grid object item limit")
	flag.Var(&a.gridMinLines, "grid-min-lines", "grid minimum line count")
	flag.Var(&a.gridMaxLines, "grid-max-lines", "grid maximum line count")
	flag.Var(&a.gridArrayMin, "grid-array-min", "grid array minimum items")
	flag.Var(&a.gridObjMin, "grid-obj-min", "grid object minimum items")

	flag.Var(&a.joinItems, "join-items", "join item limit for arrays and objects")
	flag.Var(&a.joinArrayItems, "join-array-items", "join array item limit")
	flag.Var(&a.joinObjItems, "join-obj-items", "join object item limit")
	flag.Var(&a.joinNesting, "join-nesting", "join nesting limit")

	flag.Parse()
	return a
}

func applyOptional(dst *int, value optionalInt) {
	if value.IsSet() {
		*dst = value.Value()
	}
}

func applyConfigArgs(cfg *jsonfold.Config, a args) {
	// Combined flags first, specific flags second.
	// Example: --pack-items=8 --pack-obj-items=4 means arrays=8, objects=4.

	applyOptional(&cfg.Width, a.width)

	applyOptional(&cfg.PackArrayItems, a.packItems)
	applyOptional(&cfg.PackObjItems, a.packItems)
	applyOptional(&cfg.PackArrayItems, a.packArrayItems)
	applyOptional(&cfg.PackObjItems, a.packObjItems)
	applyOptional(&cfg.PackNesting, a.packNesting)

	applyOptional(&cfg.FoldArrayItems, a.foldItems)
	applyOptional(&cfg.FoldObjItems, a.foldItems)
	applyOptional(&cfg.FoldArrayItems, a.foldArrayItems)
	applyOptional(&cfg.FoldObjItems, a.foldObjItems)
	applyOptional(&cfg.FoldNesting, a.foldNesting)

	applyOptional(&cfg.GridArrayItems, a.gridItems)
	applyOptional(&cfg.GridObjItems, a.gridItems)
	applyOptional(&cfg.GridArrayItems, a.gridArrayItems)
	applyOptional(&cfg.GridObjItems, a.gridObjItems)
	applyOptional(&cfg.GridMinLines, a.gridMinLines)
	applyOptional(&cfg.GridMaxLines, a.gridMaxLines)
	applyOptional(&cfg.GridArrayMin, a.gridArrayMin)
	applyOptional(&cfg.GridObjMin, a.gridObjMin)

	applyOptional(&cfg.JoinArrayItems, a.joinItems)
	applyOptional(&cfg.JoinObjItems, a.joinItems)
	applyOptional(&cfg.JoinArrayItems, a.joinArrayItems)
	applyOptional(&cfg.JoinObjItems, a.joinObjItems)
	applyOptional(&cfg.JoinNesting, a.joinNesting)
}

func demoData() map[string]any {
	longArray := make([]any, 50)
	for i := 0; i < 50; i++ {
		longArray[i] = fmt.Sprintf("a%d", i+1)
	}

	wideArray := make([]any, 9)
	wideObject := make(map[string]any, 9)
	for i := 1; i <= 9; i++ {
		wideArray[i-1] = fmt.Sprintf("abcdefghijklmnopqrstuvwxyz%d", i)
		wideObject[fmt.Sprintf("abcdefghijk%d", i)] = fmt.Sprintf("lmnopqrstuvwxyz%d", i)
	}

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
		"long_array":    longArray,
		"wide_array":    wideArray,
		"wide_object":   wideObject,
	}
}

func readData(a args) (any, error) {
	if a.demo {
		return demoData(), nil
	}

	var in io.Reader = os.Stdin
	if a.input != "" {
		fp, err := os.Open(a.input)
		if err != nil {
			return nil, err
		}
		defer fp.Close()
		in = fp
	}

	var data any
	dec := json.NewDecoder(in)
	err := dec.Decode(&data)
	return data, err
}

func openOutput(a args) (io.Writer, func() error, error) {
	if a.output == "" {
	}

	fp, err := os.Create(a.output)
	if err != nil {
		return nil, nil, err
	}

	closeFn := func() error {
		err1 := fp.Sync()
		err2 := fp.Close()
		if err1 != nil {
			return err1
		}
		return err2
	}

	return fp, closeFn, nil
}

func run(a args) error {
	data, err := readData(a)
	if err != nil {
		return err
	}

	cfg, err := jsonfold.LookupConfig(a.compact)
	if err != nil {
		fmt.Fprintf(os.Stderr, err.Error())
		return err
	}

	applyConfigArgs(&cfg, a)
	if a.verbose {
		fmt.Fprintf(os.Stderr, "config: %+v\n", cfg)
	}

	out := os.Stdout

	stats, err := jsonfold.WriteJSON(out, data, cfg.Width, cfg, nil)

	if err != nil {
		return err
	}

	if a.verbose {
		fmt.Fprintf(os.Stderr, "stats: %+v\n", stats)
	}

	return nil
}

func main() {
	a := parseArgs()

	if err := run(a); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
