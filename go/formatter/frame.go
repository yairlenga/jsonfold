package jsonfold

type Frame struct {
	Kind         Kind
	Indent       int
	Depth        int
	Lines        []Line
	PartsLength  int
	PackLimit    int
	FoldLimit    int
	JoinLimit    int
	GridLimit    int
	GridMinItems int

	ContentLines int
	Items        int
	Leafs        int

	FoldOk       bool
	GridOk       bool
	ChildNesting int
}

func NewFrame(kind Kind, indent int, depth int, packLimit int, foldLimit int, joinLimit int, gridLimit int, gridMinItems int) Frame {
	return Frame{
		Kind:         kind,
		Indent:       indent,
		Depth:        depth,
		PackLimit:    packLimit,
		FoldLimit:    foldLimit,
		JoinLimit:    joinLimit,
		GridLimit:    gridLimit,
		GridMinItems: gridMinItems,
		FoldOk:       true,
		ChildNesting: -1,
	}
}

func (f *Frame) UpdateStats(line Line) {
	f.Leafs += line.Leafs
	f.Items += line.Items
	if f.PartsLength == 0 {
		f.PartsLength += line.PartsLength
	} else {
		f.PartsLength += 1 + line.PartsLength
	}
	if line.ChildNesting >= f.ChildNesting {
		f.ChildNesting = line.ChildNesting + 1
	}
}

func (f *Frame) AddLine(line Line) {
	f.Lines = append(f.Lines, line)
	if line.Opener == KindNone && line.Closer == KindNone {
		f.ContentLines++
	}
	f.UpdateStats(line)
}

func (f Frame) CheckFoldLimits(config Config) bool {
	if f.PartsLength > config.Width {
		return false
	}
	if f.Items > f.FoldLimit {
		return false
	}
	if f.ChildNesting >= config.FoldNesting {
		return false
	}
	return true
}

func (f *Frame) FoldLines(cfg Config) {
	parts := make([]string, 0)
	for _, line := range f.Lines {
		parts = append(parts, line.Parts...)
	}

	line := Line{
		Indent:       f.Indent,
		Parts:        parts,
		PartsLength:  f.PartsLength,
		Kind:         f.Kind,
		Items:        1,
		Leafs:        f.Leafs,
		ChildNesting: f.ChildNesting,
		CanPack:      false,
		CanJoin:      f.ChildNesting < cfg.JoinNesting,
		CanGrid:      cfg.GridMaxLines > 0 && f.Items <= f.GridLimit,
	}
	f.Lines = []Line{line}
}

func (f *Frame) JoinLines(cfg Config) {
	lines := f.Lines
	n := len(lines)
	if n < 2 {
		return
	}

	prevIndex := 0
	writePos := 1

	for readPos := 1; readPos < n; readPos++ {
		line := lines[readPos]
		prev := &lines[prevIndex]
		if prev.CanJoin && line.CanJoin && prev.CanMerge(line, f.JoinLimit, cfg.Width) {
			prev.MergeLine(line)
			prev.CanPack = false
		} else {
			if readPos != writePos {
				lines[writePos] = line
			}
			prevIndex = writePos
			writePos++
		}
	}

	f.Lines = lines[:writePos]
	f.ContentLines -= n - writePos
}
