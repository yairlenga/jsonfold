package jsonfold

import (
	"regexp"
	"strings"
)

type Kind int

const (
	KindNone Kind = iota
	KindDict
	KindList
)

var keyRE = regexp.MustCompile(`^\s*(?:"[^"\\]*"|'[^'\\]*'|[A-Za-z_$][A-Za-z0-9_$]*|)\s*:`)

type Line struct {
	Indent       int
	Parts        []string
	PartsLength  int
	Kind         Kind
	Items        int
	Leafs        int
	ChildNesting int
	Opener       Kind
	Closer       Kind
	CanPack      bool
	CanJoin      bool
	CanGrid      bool
}

func ParseLine(s string) Line {
	stripped := strings.TrimLeft(s, " \t")
	body := strings.TrimRight(stripped, " \t\r")

	opener := KindNone
	if strings.HasSuffix(body, "{") {
		opener = KindDict
	} else if strings.HasSuffix(body, "[") {
		opener = KindList
	}

	closer := closingKind(body)
	isBodyLine := opener == KindNone && closer == KindNone
	items := 0
	leafs := 0
	if isBodyLine {
		items = 1
		leafs = 1
	}

	return Line{
		Indent:       len(s) - len(stripped),
		Parts:        []string{body},
		PartsLength:  len(body),
		Opener:       opener,
		Closer:       closer,
		CanJoin:      isBodyLine,
		CanPack:      isBodyLine,
		Items:        items,
		Leafs:        leafs,
		ChildNesting: -1,
	}
}

func closingKind(s string) Kind {
	switch s {
	case "}", "},":
		return KindDict
	case "]", "],":
		return KindList
	default:
		return KindNone
	}
}

func (l Line) Raw() string {
	return strings.Repeat(" ", l.Indent) + strings.Join(l.Parts, " ") + "\n"
}

func (l Line) Width() int {
	return l.Indent + l.PartsLength
}

func (l Line) CanMerge(other Line, itemLimit int, widthLimit int) bool {
	return l.Indent == other.Indent &&
		l.Items+other.Items <= itemLimit &&
		l.Indent+l.PartsLength+1+other.PartsLength <= widthLimit
}

func (l *Line) MergeLine(other Line) {
	l.Parts = append(l.Parts, other.Parts...)
	if len(other.Parts) > 0 {
		l.PartsLength += 1 + other.PartsLength
	}
	l.Items += other.Items
	l.Leafs += other.Leafs
	if other.ChildNesting > l.ChildNesting {
		l.ChildNesting = other.ChildNesting
		l.CanPack = false
	}
}

func (l *Line) SetParts(parts []string) {
	l.Parts = parts
	l.PartsLength = calcPartsLength(parts)
}

func (l Line) DictSignature() ([]string, bool) {
	if len(l.Parts) < 2 {
		return nil, false
	}
	signature := make([]string, 0, len(l.Parts)-2)
	for _, part := range l.Parts[1 : len(l.Parts)-1] {
		m := keyRE.FindString(part)
		if m == "" {
			return nil, false
		}
		signature = append(signature, m)
	}
	return signature, true
}

func (l *Line) ApplyGrid(widths []int) {
	parts := formatParts(l.Parts, widths)
	l.SetParts(parts)
}

func calcPartsLength(parts []string) int {
	if len(parts) == 0 {
		return 0
	}
	total := 0
	for _, part := range parts {
		total += len(part) + 1
	}
	return total - 1
}

func formatParts(parts []string, widths []int) []string {
	out := make([]string, len(parts))
	last := len(widths) - 1
	for i, part := range parts {
		if i >= len(widths) {
			out[i] = part
			continue
		}
		if isNumberStart(part) {
			out[i] = leftPad(part, widths[i])
		} else if i < last {
			out[i] = rightPad(part, widths[i])
		} else {
			out[i] = part
		}
	}
	return out
}

func isNumberStart(s string) bool {
	if s == "" {
		return false
	}
	return strings.ContainsRune("-0123456789", rune(s[0]))
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

func signaturesEqual(a []string, b []string) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}
