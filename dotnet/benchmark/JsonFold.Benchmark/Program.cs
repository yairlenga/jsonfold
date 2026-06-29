using System.Collections;
using System.Diagnostics;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using JsonFold;

internal static class Program
{
    private const int Repeats = 3;

    private static readonly string[] DefaultTests =
    [
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
    ];

    public static int Main(string[] args)
    {
        try
        {
            var filter = new List<string>();
            int? lastRows = null;
            var show = false;
            var results = new List<Dictionary<string, object>>();

            foreach (var arg in args)
            {
                if (arg == "-")
                {
                    filter.Clear();
                    continue;
                }

                if (arg == "--show")
                {
                    show = true;
                    continue;
                }

                if (!int.TryParse(arg, out var rows))
                {
                    filter.Add(arg);
                    continue;
                }

                lastRows = rows;
                if (show)
                    Dump(Console.OpenStandardOutput(), rows, filter);
                else
                    results.AddRange(RunOneSize(rows, filter));
            }

            if (lastRows is null)
            {
                if (show)
                    Dump(Console.OpenStandardOutput(), 1000, filter);
                else
                    results.AddRange(RunOneSize(1000, filter));
            }

            if (!show)
                PrintTable(results);

            return 0;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("benchmark: " + ex.Message);
            return 1;
        }
    }

    private static void Dump(Stream output, int rows, List<string> tests)
    {
        var data = MakeData(rows);
        var selected = tests.Count == 0 ? DefaultTests : tests.ToArray();

        foreach (var name in selected)
        {
            Console.Error.WriteLine($"{name} ({rows})");
            WriteString(output, "# Case:" + name + "\n");
            RunCase(name, data, output);
            WriteString(output, "\n---\n");
        }
    }

    private static List<Dictionary<string, object>> RunOneSize(int rows, List<string> tests)
    {
        var data = MakeData(rows);
        var selected = tests.Count == 0 ? DefaultTests : tests.ToArray();
        var results = new List<Dictionary<string, object>>();

        foreach (var name in selected)
        {
            Console.Error.Write($"{name} ({rows})... ");
            Console.Error.Flush();

            var speed = TimeOne(name, data);
            var peakKb = MemoryOne(name, data);

            Console.Error.WriteLine($"{Math.Round(speed.BestMillis)} ms");

            results.Add(new Dictionary<string, object>
            {
                ["rows"] = rows,
                ["name"] = name,
                ["time(ms)"] = Round1(speed.BestMillis),
                ["CPU(ms)"] = Round1(speed.CpuMillis),
                ["ttfb(ms)"] = speed.TtfbMillis < 0 ? "" : Round1(speed.TtfbMillis),
                ["out(kb)"] = Round1(speed.Bytes / 1024.0),
                ["writes"] = speed.Writes,
                ["peak(kb)"] = Round1(peakKb),
            });
        }

        return results;
    }

    private static JsonObject MakeData(int rows)
    {
        var root = new JsonObject();

        root["meta"] = new JsonObject
        {
            ["version"] = 1,
            ["ok"] = true,
            ["name"] = "jsonfold benchmark",
        };

        var longIds = new JsonArray();
        for (var i = 0; i < 100; i++) longIds.Add(i);
        root["long_ids"] = longIds;

        var longObj = new JsonObject();
        for (var i = 0; i < 50; i++) longObj["k" + i] = i;
        root["long_obj"] = longObj;

        var rowArray = new JsonArray();
        for (var i = 0; i < rows; i++)
        {
            var r = new JsonObject
            {
                ["id"] = i,
                ["name"] = "name_" + i,
                ["active"] = i % 3 == 0,
                ["score"] = i * 1.25,
                ["tags"] = new JsonArray("alpha", "beta", "gamma", "delta"),
                ["pos"] = new JsonObject
                {
                    ["x"] = i,
                    ["y"] = i + 1,
                    ["z"] = i + 2,
                },
            };

            var values = new JsonArray();
            for (var j = 0; j < 5; j++) values.Add(i + j);
            r["values"] = values;

            var pairs = new JsonArray();

            var p0 = new JsonArray();
            p0.Add(i);
            p0.Add(i + 1);

            var p1 = new JsonArray(i + 2, i + 3);
            var p2 = new JsonArray(i + 4, i + 5);

            p0.Add(p1);
            p0.Add(p2);
            pairs.Add(p0);

            r["pairs"] = pairs;
            rowArray.Add(r);
        }

        root["rows"] = rowArray;
        return root;
    }

    private static TimedResult TimeOne(string name, JsonObject data)
    {
        TimedResult? best = null;

        for (var i = 0; i < Repeats; i++)
        {
            GcQuietly();

            var output = new NullTextWriter();
            var proc = Process.GetCurrentProcess();

            var t0 = Stopwatch.GetTimestamp();
            var c0 = proc.TotalProcessorTime;
            RunCase(name, data, output);
            var c1 = proc.TotalProcessorTime;
            var t1 = Stopwatch.GetTimestamp();

            var current = new TimedResult
            {
                BestMillis = ElapsedMs(t0, t1),
                CpuMillis = (c1 - c0).TotalMilliseconds,
                TtfbMillis = output.FirstWriteTimestamp < 0 ? -1 : ElapsedMs(t0, output.FirstWriteTimestamp),
                Bytes = output.Bytes,
                Writes = output.Writes,
            };

            if (best is null || current.BestMillis < best.BestMillis)
                best = current;
        }

        return best!;
    }

    private static double MemoryOne(string name, JsonObject data)
    {
        GcQuietly();
        var before = GC.GetTotalMemory(forceFullCollection: true);

        var output = new NullTextWriter();
        RunCase(name, data, output);

        var after = GC.GetTotalMemory(forceFullCollection: false);
        return Math.Max(0, after - before) / 1024.0;
    }

    private static void RunCase(string name, JsonObject data, TextWriter output)
    {
        switch (name)
        {
            case "base.dumps.plain":
            case "base.dump.plain":
                output.Write(JsonSerializer.Serialize(data));
                break;

            case "base.dumps.pretty":
            case "base.dump.pretty":
                output.Write(JsonSerializer.Serialize(data, PrettyOptions()));
                break;

            default:
                RunJsonFoldCase(name, data, output);
                break;
        }
    }

    private static void RunCase(string name, JsonObject data, Stream output)
    {
        using var writer = new StreamWriter(output, Encoding.UTF8, bufferSize: 8192, leaveOpen: true);
        RunCase(name, data, writer);
        writer.Flush();
    }

    private static void RunJsonFoldCase(string name, JsonObject data, TextWriter output)
    {
        var parts = name.Split('.');
        if (parts.Length != 3 || parts[0] != "jf")
            throw new ArgumentException("unknown benchmark case: " + name);

        var func = parts[1];
        var compact = parts[2];

        switch (func)
        {
            case "dump":
                WriteJsonFoldDump(data, output, compact);
                break;

            case "dumps":
                output.Write(JsonFoldString(data, compact));
                break;

            default:
                throw new ArgumentException("unknown benchmark case: " + name);
        }
    }

    private static void WriteJsonFoldDump(JsonObject data, TextWriter output, string compact)
    {
        var cfg = JsonFoldConfig.Preset(compact);
        var formatter = JsonFoldFormatter.FromPreset(compact, serializerOptions: PrettyOptions());
        formatter.WriteJson(data, output);
    }

    private static string JsonFoldString(JsonObject data, string compact)
    {
        var formatter = JsonFoldFormatter.FromPreset(compact, serializerOptions: PrettyOptions());
        return formatter.FormatJson(data);
    }

    private static JsonSerializerOptions PrettyOptions() => new()
    {
        WriteIndented = true,
    };

    private static void WriteString(Stream output, string s)
    {
        var bytes = Encoding.UTF8.GetBytes(s);
        output.Write(bytes, 0, bytes.Length);
    }

    private static double ElapsedMs(long start, long end) =>
        (end - start) * 1000.0 / Stopwatch.Frequency;

    private static void GcQuietly()
    {
        GC.Collect();
        GC.WaitForPendingFinalizers();
        GC.Collect();
        Thread.Sleep(20);
    }

    private static double Round1(double x) => Math.Round(x * 10.0) / 10.0;

    private static void PrintTable(List<Dictionary<string, object>> rows)
    {
        if (rows.Count == 0) return;

        var cols = rows[0].Keys.ToList();
        var widths = new Dictionary<string, int>();
        var numeric = new Dictionary<string, bool>();

        foreach (var c in cols)
        {
            var width = c.Length;
            var isNumeric = true;

            foreach (var row in rows)
            {
                var value = row[c];
                var s = Convert.ToString(value) ?? "";
                width = Math.Max(width, s.Length);

                if (value is not byte and not sbyte and not short and not ushort and
                    not int and not uint and not long and not ulong and
                    not float and not double and not decimal && s.Length > 0)
                {
                    isNumeric = false;
                }
            }

            widths[c] = width;
            numeric[c] = isNumeric;
        }

        var line = TableLine(cols, widths);
        Console.WriteLine(line);
        Console.WriteLine(TableRow(cols, widths, numeric, cols.Cast<object>().ToList()));
        Console.WriteLine(line);

        foreach (var row in rows)
            Console.WriteLine(TableRow(cols, widths, numeric, cols.Select(c => row[c]).ToList()));

        Console.WriteLine(line);
    }

    private static string TableLine(List<string> cols, Dictionary<string, int> widths)
    {
        var sb = new StringBuilder("+");
        foreach (var c in cols)
            sb.Append(new string('-', widths[c] + 2)).Append('+');
        return sb.ToString();
    }

    private static string TableRow(
        List<string> cols,
        Dictionary<string, int> widths,
        Dictionary<string, bool> numeric,
        List<object> values)
    {
        var sb = new StringBuilder("|");

        for (var i = 0; i < cols.Count; i++)
        {
            var c = cols[i];
            var s = Convert.ToString(values[i]) ?? "";
            var width = widths[c];

            if (numeric[c])
                sb.Append(' ').Append(new string(' ', width - s.Length)).Append(s).Append(' ');
            else
                sb.Append(' ').Append(s).Append(new string(' ', width - s.Length)).Append(' ');

            sb.Append('|');
        }

        return sb.ToString();
    }

    private sealed class TimedResult
    {
        public double BestMillis { get; init; }
        public double CpuMillis { get; init; }
        public double TtfbMillis { get; init; }
        public long Bytes { get; init; }
        public long Writes { get; init; }
    }

    private sealed class NullTextWriter : TextWriter
    {
        public override Encoding Encoding => Encoding.UTF8;

        public long FirstWriteTimestamp { get; private set; } = -1;
        public long Bytes { get; private set; }
        public long Writes { get; private set; }

        public override void Write(char value)
        {
            MarkWrite(1);
        }

        public override void Write(char[] buffer, int index, int count)
        {
            MarkWrite(Encoding.UTF8.GetByteCount(buffer, index, count));
        }

        public override void Write(string? value)
        {
            if (string.IsNullOrEmpty(value)) return;
            MarkWrite(Encoding.UTF8.GetByteCount(value));
        }

        public override void Write(ReadOnlySpan<char> buffer)
        {
            if (buffer.Length == 0) return;
            MarkWrite(Encoding.UTF8.GetByteCount(buffer));
        }

        private void MarkWrite(int byteCount)
        {
            if (byteCount <= 0) return;
            if (FirstWriteTimestamp < 0)
                FirstWriteTimestamp = Stopwatch.GetTimestamp();

            Bytes += byteCount;
            Writes++;
        }
    }
}