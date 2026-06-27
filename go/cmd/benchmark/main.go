package main

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"runtime"
	"strconv"
	"strings"
	"time"

	jsonfold "jsonfold/formatter"
)

const repeats = 3

type NullWriter struct {
	t0         time.Time
	firstTime  time.Time
	bytes      int
	writes     int
	firstWrite bool
}

func NewNullWriter(t0 time.Time) *NullWriter {
	return &NullWriter{t0: t0}
}

func (w *NullWriter) Write(p []byte) (int, error) {
	if !w.firstWrite {
		w.firstWrite = true
		w.firstTime = time.Now()
	}
	w.bytes += len(p)
	w.writes++
	return len(p), nil
}

func (w *NullWriter) WriteString(s string) (int, error) {
	return w.Write([]byte(s))
}

func (w *NullWriter) TTFBMS() string {
	if !w.firstWrite {
		return ""
	}
	return fmt.Sprintf("%.1f", float64(w.firstTime.Sub(w.t0).Microseconds())/1000.0)
}

func makeData(rows int) map[string]any {
	items := make([]any, 0, rows)
	for i := 0; i < rows; i++ {
		items = append(items, map[string]any{
			"id":     i,
			"name":   fmt.Sprintf("name_%d", i),
			"active": i%3 == 0,
			"score":  float64(i) * 1.25,
			"tags":   []any{"alpha", "beta", "gamma", "delta"},
			"pos": map[string]any{
				"x": i,
				"y": i + 1,
				"z": i + 2,
			},
			"values": []any{i, i + 1, i + 2, i + 3, i + 4},
			"pairs": []any{
				[]any{i, i + 1, []any{i + 2, i + 3}, []any{i + 4, i + 5}},
			},
		})
	}

	longIDs := make([]any, 0, 100)
	for i := 0; i < 100; i++ {
		longIDs = append(longIDs, i)
	}

	longObj := map[string]any{}
	for i := 0; i < 50; i++ {
		longObj[fmt.Sprintf("k%d", i)] = i
	}

	return map[string]any{
		"meta": map[string]any{
			"version": 1,
			"ok":      true,
			"name":    "jsonfold benchmark",
		},
		"long_ids": longIDs,
		"long_obj": longObj,
		"rows":     items,
	}
}

func writeString(t0 time.Time, s string) *NullWriter {
	w := NewNullWriter(t0)
	_, _ = w.WriteString(s)
	return w
}

func runJSONDumpPlain(data any, t0 time.Time) *NullWriter {
	w := NewNullWriter(t0)
	enc := json.NewEncoder(w)
	enc.SetEscapeHTML(false)
	_ = enc.Encode(data)
	return w
}

func runJSONDumpPretty(data any, t0 time.Time) *NullWriter {
	w := NewNullWriter(t0)
	enc := json.NewEncoder(w)
	enc.SetEscapeHTML(false)
	enc.SetIndent("", "  ")
	_ = enc.Encode(data)
	return w
}

func runJSONFoldDump(data any, t0 time.Time, compact string) *NullWriter {
	w := NewNullWriter(t0)
	cfg, _, _ := jsonfold.PresetConfig(compact)
	_, _ = jsonfold.WriteJSONWithConfig(w, data, cfg)
	return w
}

func runCase(data any, name string, t0 time.Time) *NullWriter {
	switch name {
	case "base.dumps.plain":
		b, _ := json.Marshal(data)
		return writeString(t0, string(b))

	case "base.dumps.pretty":
		b, _ := json.MarshalIndent(data, "", "  ")
		return writeString(t0, string(b))

	case "base.dump.plain":
		return runJSONDumpPlain(data, t0)

	case "base.dump.pretty":
		return runJSONDumpPretty(data, t0)
	}

	parts := strings.Split(name, ".")
	if len(parts) == 3 && parts[0] == "jf" {
		compact := parts[2]

		if parts[1] == "dumps" {
			cfg, _, _ := jsonfold.PresetConfig(compact)
			s, _ := jsonfold.FormatJSONWithConfig(data, cfg)
			return writeString(t0, s)
		}

		if parts[1] == "dump" {
			return runJSONFoldDump(data, t0, compact)
		}
	}

	panic("unknown benchmark case: " + name)
}

type Result map[string]any

func timeOne(name string, data any) (time.Duration, Result) {
	var best Result
	var bestDT time.Duration

	for i := 0; i < repeats; i++ {
		runtime.GC()

		t0 := time.Now()
		w := runCase(data, name, t0)
		dt := time.Since(t0)

		row := Result{
			"time(ms)": round1(ms(dt)),
			"CPU(ms)":  round1(ms(dt)),
			"ttfb(ms)": w.TTFBMS(),
			"out(kb)":  round1(float64(w.bytes) / 1024.0),
			"writes":   w.writes,
		}

		if best == nil || dt < bestDT {
			best = row
			bestDT = dt
		}
	}

	return bestDT, best
}

func memoryOne(name string, data any) float64 {
	runtime.GC()

	var before runtime.MemStats
	var after runtime.MemStats

	runtime.ReadMemStats(&before)
	t0 := time.Now()
	_ = runCase(data, name, t0)
	runtime.ReadMemStats(&after)

	var used uint64
	if after.TotalAlloc >= before.TotalAlloc {
		used = after.TotalAlloc - before.TotalAlloc
	}

	return round1(float64(used) / 1024.0)
}

func runOneSize(rows int, tests []string) []Result {
	data := makeData(rows)

	if len(tests) == 0 {
		tests = []string{
			"base.dump.plain",
			"base.dump.pretty",
			"jf.dump.off",
			"jf.dump.none",
			"jf.dump.default",
			"jf.dump.low",
			"jf.dump.med",
			"jf.dump.classic",
			"jf.dump.high",
			"jf.dump.max",
			"jf.dump.pack",
			"jf.dump.fold",
			"jf.dump.grid",
			"jf.dump.join",
			"base.dumps.plain",
			"base.dumps.pretty",
			"jf.dumps.none",
			"jf.dumps.default",
			"jf.dumps.high",
			"jf.dumps.max",
		}
	}

	results := []Result{}

	for _, name := range tests {
		fmt.Fprintf(os.Stderr, "%s (%d)... ", name, rows)

		t0 := time.Now()
		_, speed := timeOne(name, data)
		peakKB := memoryOne(name, data)
		dt := time.Since(t0)

		fmt.Fprintf(os.Stderr, "%.0f ms\n", ms(dt))

		row := Result{
			"rows": rows,
			"name": name,
		}

		for k, v := range speed {
			row[k] = v
		}
		row["peak(kb)"] = peakKB

		results = append(results, row)
	}

	return results
}

func showData(rows int) {
	data := makeData(rows)
	enc := json.NewEncoder(os.Stdout)
	enc.SetIndent("", "  ")
	enc.SetEscapeHTML(false)
	_ = enc.Encode(data)
}

func printTable(rows []Result) {
	if len(rows) == 0 {
		return
	}

	cols := []string{
		"rows",
		"name",
		"time(ms)",
		"CPU(ms)",
		"ttfb(ms)",
		"out(kb)",
		"writes",
		"peak(kb)",
	}

	widths := map[string]int{}
	for _, c := range cols {
		widths[c] = len(c)
		for _, r := range rows {
			s := fmt.Sprint(r[c])
			if len(s) > widths[c] {
				widths[c] = len(s)
			}
		}
	}

	line := "+"
	for _, c := range cols {
		line += strings.Repeat("-", widths[c]+2) + "+"
	}

	fmt.Println(line)
	fmt.Print("|")
	for _, c := range cols {
		fmt.Print(cell(c, c, widths[c], false), "|")
	}
	fmt.Println()
	fmt.Println(line)

	for _, r := range rows {
		fmt.Print("|")
		for _, c := range cols {
			v := r[c]
			_, isNum := v.(int)
			if !isNum {
				_, isNum = v.(float64)
			}
			fmt.Print(cell(c, fmt.Sprint(v), widths[c], isNum), "|")
		}
		fmt.Println()
	}

	fmt.Println(line)
}

func cell(_ string, v string, width int, numeric bool) string {
	if numeric {
		return " " + leftPad(v, width) + " "
	}
	return " " + rightPad(v, width) + " "
}

func leftPad(s string, width int) string {
	if len(s) >= width {
		return s
	}
	return strings.Repeat(" ", width-len(s)) + s
}

func rightPad(s string, width int) string {
	if len(s) >= width {
		return s
	}
	return s + strings.Repeat(" ", width-len(s))
}

func ms(d time.Duration) float64 {
	return float64(d.Microseconds()) / 1000.0
}

func round1(v float64) float64 {
	return float64(int(v*10+0.5)) / 10.0
}

func main() {
	args := os.Args[1:]

	if len(args) >= 2 && args[0] == "--show" {
		rows, err := strconv.Atoi(args[1])
		if err != nil {
			fmt.Fprintln(os.Stderr, "invalid --show value:", args[1])
			os.Exit(2)
		}
		showData(rows)
		return
	}

	t0 := time.Now()
	filter := []string{}
	lastSize := 0
	results := []Result{}

	for _, arg := range args {
		if arg == "-" {
			filter = []string{}
			continue
		}

		if n, err := strconv.Atoi(arg); err == nil {
			lastSize = n
			results = append(results, runOneSize(lastSize, filter)...)
		} else {
			filter = append(filter, arg)
		}
	}

	if lastSize == 0 {
		results = append(results, runOneSize(1000, filter)...)
	}

	printTable(results)
	fmt.Fprintf(os.Stderr, "completed in: %.1f\n", time.Since(t0).Seconds())
}

var _ io.Writer = (*NullWriter)(nil)
