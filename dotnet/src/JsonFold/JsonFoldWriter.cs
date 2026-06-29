using System.Text;

namespace JsonFold;

public sealed class JsonFoldWriter : TextWriter
{
    private readonly TextWriter _writer;
    private readonly JsonFoldConfig? _cfg;
    private readonly bool _closeBase;
    private readonly StringBuilder _pending = new();
    private readonly List<Frame> _stack = new();
    private bool _finished;

    public JsonFoldWriter(TextWriter writer, JsonFoldConfig? config = null, bool closeBase = false)
    {
        _writer = writer;
        _cfg = config ?? JsonFoldConfig.Default;
        _closeBase = closeBase;
    }

    public JsonFoldStats Stats { get; } = new();
    public override Encoding Encoding => _writer.Encoding;

    public override void Write(char[] buffer, int index, int count) => Write(new string(buffer, index, count));

    public override void Write(string? value)
    {
        if (string.IsNullOrEmpty(value)) return;
        if (_finished) throw new ObjectDisposedException(nameof(JsonFoldWriter));

        Stats.BytesIn += value.Length;
        Stats.LinesIn += CountNewlines(value);

        if (_cfg is null || _cfg.Width == 0)
        {
            WriteString(value);
            return;
        }

        _pending.Append(value);
        var start = 0;
        while (true)
        {
            var nl = IndexOfNewline(_pending, start);
            if (nl < 0)
            {
                if (start > 0) _pending.Remove(0, start);
                if (_pending.Length > _cfg.Width) MarkNoFold();
                return;
            }

            Feed(Line.Parse(_pending.ToString(start, nl - start)));
            start = nl + 1;
        }
    }

    public void Finish()
    {
        if (_finished) return;

        if (_pending.Length > 0)
        {
            if (_cfg is null || _cfg.Width == 0) WriteString(_pending.ToString());
            else Feed(Line.Parse(_pending.ToString()));
            _pending.Clear();
        }

        foreach (var frame in _stack)
            foreach (var line in frame.Lines)
                WriteLineRaw(line);
        _stack.Clear();
        _finished = true;
    }

    public override void Flush()
    {
        Finish();
        _writer.Flush();
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            Finish();
            if (_closeBase) _writer.Dispose();
            else _writer.Flush();
        }
        base.Dispose(disposing);
    }

    private void Feed(Line line)
    {
        var cfg = _cfg!;

        if (line.Opener != Kind.None)
        {
            var frame = new Frame(
                line.Opener,
                line.Indent,
                _stack.Count,
                PackLimit(line.Opener),
                FoldLimit(line.Opener),
                JoinLimit(line.Opener),
                GridLimit(line.Opener),
                GridMinItems(line.Opener));
            frame.AddLine(line);
            _stack.Add(frame);
            if (line.Width() > cfg.Width) MarkNoFold();
            return;
        }

        if (_stack.Count == 0)
        {
            WriteLineRaw(line);
            return;
        }

        var top = _stack[^1];
        if (line.Closer != Kind.None)
        {
            if (top.Kind != line.Closer)
            {
                top.FoldOk = false;
                top.GridOk = false;
            }
            top.AddLine(line);
            CloseFrame();
            return;
        }

        if (line.Items >= top.PackLimit) line.CanPack = false;
        if (line.Items >= top.JoinLimit) line.CanJoin = false;
        AddToFrame(top, line);
    }

    private void AddToFrame(Frame frame, Line line)
    {
        var cfg = _cfg!;
        if (!frame.IsEmpty)
        {
            if (!frame.GridOk)
            {
                var prev = frame.LastLine!;
                if (line.CanPack && prev.CanPack && TryPack(frame, prev, line)) return;
                if (line.CanJoin && prev.CanJoin && TryJoin(frame, prev, line)) return;
            }
        }
        else if (!frame.FoldOk && !line.CanPack && !line.CanJoin)
        {
            WriteLineRaw(line);
            return;
        }

        frame.AddLine(line);

        if (frame.FoldOk && line.Width() > cfg.Width) MarkNoFold();

        if (line.Closer == Kind.None)
        {
            if (frame.FoldOk && !frame.CheckFoldLimits(cfg)) MarkNoFold();

            if (frame.GridOk && !line.CanGrid)
            {
                MarkNoGrid();
                frame.JoinLines(cfg);
            }
        }

        if (!frame.FoldOk && !frame.GridOk) StreamFrame(frame);
    }

    private bool TryPack(Frame frame, Line prev, Line line)
    {
        if (frame.PackLimit <= 1 || !prev.CanMerge(line, frame.PackLimit, _cfg!.Width)) return false;
        MergeIntoFrame(frame, prev, line);
        if (!prev.CanPack) prev.CanJoin = false;
        return true;
    }

    private bool TryJoin(Frame frame, Line prev, Line line)
    {
        if (frame.JoinLimit <= 1 || !prev.CanMerge(line, frame.JoinLimit, _cfg!.Width)) return false;
        MergeIntoFrame(frame, prev, line);
        return true;
    }

    private void MergeIntoFrame(Frame frame, Line prev, Line line)
    {
        var cfg = _cfg!;
        frame.MergeLine(prev, line);
        if (prev.Items >= frame.PackLimit || prev.ChildNesting >= cfg.PackNesting) prev.CanPack = false;
        if (prev.Items >= frame.JoinLimit || prev.ChildNesting >= cfg.JoinNesting) prev.CanJoin = false;
        if (frame.FoldOk && !frame.CheckFoldLimits(cfg))
        {
            MarkNoFold();
            StreamFrame(frame);
        }
    }

    private void CloseFrame()
    {
        var cfg = _cfg!;
        var frame = _stack[^1];
        _stack.RemoveAt(_stack.Count - 1);

        if (frame.GridOk)
        {
            if (!TryGrid(frame))
            {
                MarkNoGrid();
                frame.JoinLines(cfg);
                frame.FoldOk = frame.CheckFoldLimits(cfg);
            }
            else
            {
                MarkNoGrid();
            }
        }

        if (frame.FoldOk && TryFold(frame) && _stack.Count > 0 && frame.Lines[0].CanGrid)
        {
            var parent = _stack[^1];
            if (parent.ContentLines == 0) parent.GridOk = true;
        }

        EmitLines(frame.Lines);
        frame.Lines.Clear();
    }

    private bool TryFold(Frame frame)
    {
        if (!frame.FoldOk || frame.ContentLines != 1 || frame.Lines.Count != 3 || frame.Indent + frame.PartsLength > _cfg!.Width)
            return false;
        frame.FoldLines(_cfg!);
        return true;
    }

    private bool TryGrid(Frame frame)
    {
        var cfg = _cfg!;
        if (frame.Kind != Kind.List) return false;
        var lineCount = frame.Lines.Count - 2;
        if (lineCount < 2 || lineCount < cfg.GridMinLines || lineCount > cfg.GridMaxLines) return false;

        var rows = frame.Lines.Skip(1).Take(lineCount).ToList();
        var first = rows[0];
        var partCount = first.Parts.Count;
        if (partCount < 4 || partCount - 2 < frame.GridMinItems) return false;
        if (rows.Any(row => row.Parts.Count != partCount)) return false;

        if (first.Kind == Kind.Dict)
        {
            var sig = first.DictSignature();
            if (sig is null) return false;
            foreach (var row in rows)
            {
                var rowSig = row.DictSignature();
                if (rowSig is null || !sig.SequenceEqual(rowSig)) return false;
            }
        }

        var widths = new int[partCount];
        for (var i = 0; i < partCount; i++) widths[i] = rows.Max(row => row.Parts[i].Length);
        var griddedLength = widths.Sum(w => w + 1) - 1;
        if (frame.Lines[0].Indent + griddedLength > cfg.Width) return false;

        foreach (var row in rows)
        {
            row.ApplyGrid(widths);
            row.CanPack = false;
            row.CanJoin = false;
            row.CanGrid = false;
        }
        return true;
    }

    private void StreamFrame(Frame frame)
    {
        if (frame.Lines.Count == 0) return;
        Line? keep = null;
        var last = frame.Lines[^1];
        if (last.CanPack || last.CanJoin)
        {
            keep = last;
            frame.Lines.RemoveAt(frame.Lines.Count - 1);
        }

        EmitLines(frame.Lines, frame.Depth - 1);
        frame.Lines.Clear();
        if (keep is not null) frame.Lines.Add(keep);
    }

    private void EmitLines(List<Line> lines, int? depth = null)
    {
        if (lines.Count == 0) return;
        var targetDepth = depth ?? _stack.Count - 1;
        if (targetDepth < 0)
        {
            foreach (var line in lines) WriteLineRaw(line);
            return;
        }

        var frame = _stack[targetDepth];
        foreach (var line in lines.ToArray()) AddToFrame(frame, line);
    }

    private void MarkNoFold()
    {
        foreach (var frame in _stack) frame.FoldOk = false;
    }

    private void MarkNoGrid()
    {
        foreach (var frame in _stack) frame.GridOk = false;
    }

    private void WriteLineRaw(Line line) => WriteString(line.Raw());

    private void WriteString(string s)
    {
        _writer.Write(s);
        Stats.BytesOut += s.Length;
        Stats.LinesOut += CountNewlines(s);
    }

    private int PackLimit(Kind kind) => ChooseLimit(kind, _cfg!.PackArrayItems, _cfg.PackObjItems);
    private int FoldLimit(Kind kind) => ChooseLimit(kind, _cfg!.FoldArrayItems, _cfg.FoldObjItems);
    private int JoinLimit(Kind kind) => ChooseLimit(kind, _cfg!.JoinArrayItems, _cfg.JoinObjItems);
    private int GridLimit(Kind kind) => ChooseLimit(kind, _cfg!.GridArrayItems, _cfg.GridObjItems);
    private int GridMinItems(Kind kind) => ChooseLimit(kind, _cfg!.GridArrayMin, _cfg.GridObjMin);

    private static int ChooseLimit(Kind kind, int listLimit, int dictLimit) => kind switch
    {
        Kind.List => listLimit,
        Kind.Dict => dictLimit,
        _ => 0,
    };

    private static int CountNewlines(string s) => s.Count(ch => ch == '\n');

    private static int IndexOfNewline(StringBuilder sb, int start)
    {
        for (var i = start; i < sb.Length; i++) if (sb[i] == '\n') return i;
        return -1;
    }
}
