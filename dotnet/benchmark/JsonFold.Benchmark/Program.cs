using System.Text.Json;
using BenchmarkDotNet.Attributes;
using BenchmarkDotNet.Running;
using JsonFold;

BenchmarkSwitcher.FromAssembly(typeof(Program).Assembly).Run(args);

[MemoryDiagnoser]
public class JsonFoldBenchmarks
{
    private object _data = default!;
    private string _pretty = string.Empty;
    private JsonSerializerOptions _prettyOptions = default!;

    private JsonFoldConfig? _off = default!;
    private JsonFoldConfig? _none = default!;
    private JsonFoldConfig? _default = default!;
    private JsonFoldConfig? _high = default!;
    private JsonFoldConfig? _max = default!;

    [Params(100, 1000)]
    public int Rows { get; set; }

    [GlobalSetup]
    public void Setup()
    {
        _data = BuildData(Rows);
        _prettyOptions = new JsonSerializerOptions { WriteIndented = true };
        _pretty = JsonSerializer.Serialize(_data, _prettyOptions) + "\n";

        _off = JsonFoldConfig.Preset("off");
        _none = JsonFoldConfig.Preset("none");
        _default = JsonFoldConfig.Preset("default");
        _high = JsonFoldConfig.Preset("high");
        _max = JsonFoldConfig.Preset("max");
    }

    [Benchmark(Baseline = true)]
    public string BaseWritePlain()
    {
        return JsonSerializer.Serialize(_data);
    }

    [Benchmark]
    public string BaseWritePretty()
    {
        return JsonSerializer.Serialize(_data, _prettyOptions);
    }

    [Benchmark]
    public string JsonFoldOff()
    {
        return new JsonFoldFormatter(config: _off).FormatJson(_data);
    }

    [Benchmark]
    public string JsonFoldNone()
    {
        return new JsonFoldFormatter(config: _none).FormatJson(_data);
    }

    [Benchmark]
    public string JsonFoldDefault()
    {
        return new JsonFoldFormatter(config: _default).FormatJson(_data);
    }

    [Benchmark]
    public string JsonFoldHigh()
    {
        return new JsonFoldFormatter(config: _high).FormatJson(_data);
    }

    [Benchmark]
    public string JsonFoldMax()
    {
        return new JsonFoldFormatter(config: _max).FormatJson(_data);
    }

    [Benchmark]
    public string FoldExistingPrettyDefault()
    {
        return new JsonFoldFormatter(config: _default).FoldText(_pretty);
    }

    private static object BuildData(int rows)
    {
        var list = new List<object>(rows);
        for (int i = 0; i < rows; i++)
        {
            list.Add(new
            {
                id = i + 1,
                name = "row-" + (i + 1),
                ok = (i & 1) == 0,
                score = i * 3.25,
                tags = new[] { "alpha", "beta", "gamma" },
                point = new[] { i, i + 10, i + 20 },
                meta = new { group = i % 7, size = i % 5, active = i % 3 == 0 }
            });
        }

        return new
        {
            meta = new { version = 1, source = "JsonFold.Benchmark", rows },
            columns = new[] { "id", "name", "ok", "score", "tags", "point", "meta" },
            rows = list
        };
    }
}
