namespace JsonFold.Format;

public sealed class JsonFoldStats
{
    public long BytesIn { get; internal set; }
    public long BytesOut { get; internal set; }
    public long LinesIn { get; internal set; }
    public long LinesOut { get; internal set; }

    public override string ToString() =>
        $"JsonFoldStats {{ BytesIn = {BytesIn}, BytesOut = {BytesOut}, LinesIn = {LinesIn}, LinesOut = {LinesOut} }}";
}
