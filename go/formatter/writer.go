package jsonfold

import (
	"bytes"
	"io"
	"strings"
)

type Writer struct {
	fp      io.Writer
	stats   Stats
	cfg     Config
	enabled bool
	pending string
	stack   []Frame
}

func NewWriter(fp io.Writer, cfg Config) *Writer {
	return &Writer{fp: fp, cfg: cfg, enabled: true}
}

func NewOffWriter(fp io.Writer) *Writer {
	return &Writer{fp: fp, enabled: false}
}

func NewWriterPreset(fp io.Writer, preset string, width int) (*Writer, error) {
	cfg, enabled, err := PresetConfigWithWidth(preset, width)
	if err != nil {
		return nil, err
	}
	if !enabled {
		return NewOffWriter(fp), nil
	}
	return NewWriter(fp, cfg), nil
}

func (w *Writer) Write(p []byte) (int, error) {
	s := string(p)
	sLen := len(s)
	w.stats.BytesIn += sLen

	if !w.enabled {
		w.stats.LinesIn += strings.Count(s, "\n")
		_, err := w.writeString(s)
		if err != nil {
			return 0, err
		}
		return len(p), nil
	}

	nlPos := strings.IndexByte(s, '\n')
	if nlPos < 0 {
		w.pending += s
		return len(p), nil
	}

	nl2Pos := strings.IndexByte(s[nlPos+1:], '\n')
	if nl2Pos < 0 {
		w.stats.LinesIn++
		s2 := w.pending + s[:nlPos]
		w.pending = s[nlPos+1:]
		if err := w.feed(ParseLine(s2)); err != nil {
			return 0, err
		}
		return len(p), nil
	}

	parts := splitLinesKeepEnds(s)
	w.stats.LinesIn += len(parts) - 1

	if w.pending != "" {
		parts[0] = w.pending + parts[0]
		w.pending = ""
	}

	if len(parts) > 0 && !strings.HasSuffix(parts[len(parts)-1], "\n") {
		w.pending = parts[len(parts)-1]
		parts = parts[:len(parts)-1]
	}

	for _, part := range parts {
		if err := w.feed(ParseLine(strings.TrimSuffix(part, "\n"))); err != nil {
			return 0, err
		}
	}
	return len(p), nil
}

func (w *Writer) Finish() error {
	if w.pending != "" {
		if err := w.feed(ParseLine(w.pending)); err != nil {
			return err
		}
		w.pending = ""
	}

	for i := range w.stack {
		frame := &w.stack[i]
		for _, line := range frame.Lines {
			if err := w.writeLine(line); err != nil {
				return err
			}
		}
	}
	w.stack = nil
	return nil
}

func (w *Writer) Flush() error {
	if err := w.Finish(); err != nil {
		return err
	}
	if flusher, ok := w.fp.(interface{ Flush() error }); ok {
		return flusher.Flush()
	}
	return nil
}

func (w *Writer) Close() error {
	if err := w.Flush(); err != nil {
		return err
	}
	if closer, ok := w.fp.(io.Closer); ok {
		return closer.Close()
	}
	return nil
}

func (w *Writer) Stats() Stats {
	return w.stats
}

func (w *Writer) writeString(s string) (int, error) {
	n, err := io.WriteString(w.fp, s)
	w.stats.BytesOut += n
	w.stats.LinesOut += strings.Count(s[:n], "\n")
	return n, err
}

func (w *Writer) writeLine(line Line) error {
	_, err := w.writeString(line.Raw())
	return err
}

func (w *Writer) feed(line Line) error {
	if line.Opener != KindNone {
		frame := NewFrame(
			line.Opener,
			line.Indent,
			len(w.stack),
			w.packLimit(line.Opener),
			w.foldLimit(line.Opener),
			w.joinLimit(line.Opener),
			w.gridLimit(line.Opener),
			w.gridMinItems(line.Opener),
		)
		frame.AddLine(line)
		w.stack = append(w.stack, frame)
		return nil
	}

	if len(w.stack) == 0 {
		return w.writeLine(line)
	}

	frame := &w.stack[len(w.stack)-1]
	if line.Closer != KindNone {
		if frame.Kind != line.Closer {
			frame.FoldOk = false
			frame.GridOk = false
		}
		frame.AddLine(line)
		return w.closeFrame()
	}

	if line.Items >= frame.PackLimit {
		line.CanPack = false
	}
	if line.Items >= frame.JoinLimit {
		line.CanJoin = false
	}
	return w.addToFrame(frame, line)
}

func (w *Writer) emitLines(lines []Line, depth *int) error {
	if len(lines) == 0 {
		return nil
	}
	targetDepth := len(w.stack) - 1
	if depth != nil {
		targetDepth = *depth
	}
	if targetDepth < 0 {
		for _, line := range lines {
			if err := w.writeLine(line); err != nil {
				return err
			}
		}
		return nil
	}
	for _, line := range lines {
		if err := w.addToFrame(&w.stack[targetDepth], line); err != nil {
			return err
		}
	}
	return nil
}

func (w *Writer) chooseLimit(kind Kind, defaultValue int, listLimit int, dictLimit int) int {
	if kind == KindList {
		return listLimit
	}
	if kind == KindDict {
		return dictLimit
	}
	return defaultValue
}

func (w *Writer) packLimit(kind Kind) int {
	return w.chooseLimit(kind, 0, w.cfg.PackArrayItems, w.cfg.PackObjItems)
}

func (w *Writer) foldLimit(kind Kind) int {
	return w.chooseLimit(kind, 0, w.cfg.FoldArrayItems, w.cfg.FoldObjItems)
}

func (w *Writer) joinLimit(kind Kind) int {
	return w.chooseLimit(kind, 0, w.cfg.JoinArrayItems, w.cfg.JoinObjItems)
}

func (w *Writer) gridLimit(kind Kind) int {
	return w.chooseLimit(kind, 0, w.cfg.GridArrayItems, w.cfg.GridObjItems)
}

func (w *Writer) gridMinItems(kind Kind) int {
	return w.chooseLimit(kind, 0, w.cfg.GridArrayMin, w.cfg.GridObjMin)
}

func (w *Writer) addToFrame(frame *Frame, line Line) error {
	if len(frame.Lines) > 0 {
		if !frame.GridOk {
			prev := &frame.Lines[len(frame.Lines)-1]
			if line.CanPack && prev.CanPack && w.tryPack(frame, prev, line) {
				return nil
			}
			if line.CanJoin && prev.CanJoin && w.tryJoin(frame, prev, line) {
				return nil
			}
		}
	} else if !frame.FoldOk && !line.CanPack && !line.CanJoin {
		return w.writeLine(line)
	}

	frame.AddLine(line)

	if frame.FoldOk && line.Width() > w.cfg.Width {
		w.markNoFold()
	}

	if line.Closer == KindNone {
		if frame.FoldOk && !frame.CheckFoldLimits(w.cfg) {
			w.markNoFold()
		}

		if frame.GridOk && !line.CanGrid {
			w.markNoGrid()
			frame.JoinLines(w.cfg)
		}
	}

	if !frame.FoldOk && !frame.GridOk {
		return w.streamFrame(frame)
	}
	return nil
}

func (w *Writer) mergeIntoFrame(frame *Frame, prev *Line, line Line) error {
	prev.MergeLine(line)

	if prev.Items >= frame.PackLimit || prev.ChildNesting >= w.cfg.PackNesting {
		prev.CanPack = false
	}
	if prev.Items >= frame.JoinLimit || prev.ChildNesting >= w.cfg.JoinNesting {
		prev.CanJoin = false
	}

	frame.UpdateStats(line)

	if frame.FoldOk && !frame.CheckFoldLimits(w.cfg) {
		w.markNoFold()
		return w.streamFrame(frame)
	}
	return nil
}

func (w *Writer) tryPack(frame *Frame, prev *Line, line Line) bool {
	if frame.PackLimit <= 1 || !prev.CanMerge(line, frame.PackLimit, w.cfg.Width) {
		return false
	}
	_ = w.mergeIntoFrame(frame, prev, line)
	if !prev.CanPack {
		prev.CanJoin = false
	}
	return true
}

func (w *Writer) tryJoin(frame *Frame, prev *Line, line Line) bool {
	if frame.JoinLimit <= 1 || !prev.CanMerge(line, frame.JoinLimit, w.cfg.Width) {
		return false
	}
	_ = w.mergeIntoFrame(frame, prev, line)
	return true
}

func (w *Writer) closeFrame() error {
	frame := w.stack[len(w.stack)-1]
	w.stack = w.stack[:len(w.stack)-1]

	if frame.GridOk {
		if w.tryGrid(&frame) {
			w.markNoGrid()
		} else {
			w.markNoGrid()
			frame.JoinLines(w.cfg)
			frame.FoldOk = frame.CheckFoldLimits(w.cfg)
		}
	}

	if frame.FoldOk && w.tryFold(&frame) {
		if len(w.stack) > 0 && frame.Lines[0].CanGrid {
			parentFrame := &w.stack[len(w.stack)-1]
			if parentFrame.ContentLines == 0 {
				parentFrame.GridOk = true
			}
		}
	}

	return w.emitLines(frame.Lines, nil)
}

func (w *Writer) tryFold(frame *Frame) bool {
	if !frame.FoldOk ||
		frame.ContentLines != 1 ||
		len(frame.Lines) != 3 ||
		frame.Indent+frame.PartsLength > w.cfg.Width {
		return false
	}
	frame.FoldLines(w.cfg)
	return true
}

func (w *Writer) tryGrid(frame *Frame) bool {
	if frame.Kind != KindList {
		return false
	}
	lineCount := len(frame.Lines) - 2
	if lineCount < 2 || lineCount < w.cfg.GridMinLines || lineCount > w.cfg.GridMaxLines {
		return false
	}

	lines := frame.Lines[1 : len(frame.Lines)-1]
	firstLine := lines[0]
	partCount := len(firstLine.Parts)
	if partCount < 4 || partCount-2 < frame.GridMinItems {
		return false
	}

	for _, line := range lines {
		if len(line.Parts) != partCount {
			return false
		}
	}

	if firstLine.Kind == KindDict {
		dictSignature, ok := firstLine.DictSignature()
		if !ok {
			return false
		}
		for _, line := range lines {
			lineSignature, ok := line.DictSignature()
			if !ok || !signaturesEqual(lineSignature, dictSignature) {
				return false
			}
		}
	}

	widths := make([]int, partCount)
	for i := 0; i < partCount; i++ {
		maxWidth := 0
		for _, line := range lines {
			if len(line.Parts[i]) > maxWidth {
				maxWidth = len(line.Parts[i])
			}
		}
		widths[i] = maxWidth
	}

	gridedLength := 0
	for _, width := range widths {
		gridedLength += 1 + width
	}
	gridedLength--
	if frame.Lines[0].Indent+gridedLength > w.cfg.Width {
		return false
	}

	for i := 1; i < len(frame.Lines)-1; i++ {
		frame.Lines[i].ApplyGrid(widths)
		frame.Lines[i].CanPack = false
		frame.Lines[i].CanJoin = false
		frame.Lines[i].CanGrid = false
	}
	return true
}

func (w *Writer) streamFrame(frame *Frame) error {
	lines := frame.Lines
	if len(lines) == 0 {
		return nil
	}

	last := lines[len(lines)-1]
	keepLast := last.CanPack || last.CanJoin
	if keepLast {
		lines = lines[:len(lines)-1]
	}

	depth := frame.Depth - 1
	if err := w.emitLines(lines, &depth); err != nil {
		return err
	}
	frame.Lines = frame.Lines[:0]
	if keepLast {
		frame.Lines = append(frame.Lines, last)
	}
	return nil
}

func (w *Writer) markNoFold() {
	for i := range w.stack {
		w.stack[i].FoldOk = false
	}
}

func (w *Writer) markNoGrid() {
	for i := range w.stack {
		w.stack[i].GridOk = false
	}
}

func splitLinesKeepEnds(s string) []string {
	if s == "" {
		return nil
	}
	var lines []string
	start := 0
	for i := 0; i < len(s); i++ {
		if s[i] == '\n' {
			lines = append(lines, s[start:i+1])
			start = i + 1
		}
	}
	if start < len(s) {
		lines = append(lines, s[start:])
	}
	return lines
}

func FoldPrettyText(text string, cfg Config) (string, Stats, error) {
	var buf bytes.Buffer
	out := NewWriter(&buf, cfg)
	if _, err := out.Write([]byte(text)); err != nil {
		return "", out.Stats(), err
	}
	if err := out.Finish(); err != nil {
		return "", out.Stats(), err
	}
	return buf.String(), out.Stats(), nil
}
