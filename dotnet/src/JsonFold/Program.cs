using System.Text.Json;
using System.Text.Json.Nodes;
using JsonFold.Format;

return JsonFoldMain.Run(args);


static class JsonFoldMain
{
    public static int Run(string[] args)
    {
        try
        {
            var opt = ParseArgs(args);

            if (opt.Help)
            {
                Usage();
                return 0;
            }

            JsonFoldConfig? cfg = JsonFoldConfig.Resolve(opt.Compact, opt.Width);

            if (opt.Verbose)
                Console.Error.WriteLine(cfg?.ToString() ?? "off");

            JsonNode? value = opt.Demo
                ? DemoData()
                : ReadInput(opt.Input);

            if (opt.SortKeys)
                value = SortKeys(value);

            var formatter = new JsonFoldFormatter(
                opt.Width,
                opt.Compact,
                indent: opt.Indent
            );

            var stats = formatter.FormatTo(value, Console.Out);

            if (opt.Verbose)
                Console.Error.WriteLine(stats);

            return 0;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("jsonfold: " + ex.Message);
            return 1;
        }
    }

    private sealed class Options
    {
        public bool Help;
        public bool Demo;
        public bool Verbose;
        public bool SortKeys;
        public string? Input;
        public string Compact = "default";
        public int? Width;
        public int Indent = 2;
    }

    private static Options ParseArgs(string[] args)
    {
        var opt = new Options();

        foreach (var arg in args)
        {
            if (arg is "-h" or "--help") opt.Help = true;
            else if (arg is "-v" or "--verbose") opt.Verbose = true;
            else if (arg == "--demo") opt.Demo = true;
            else if (arg == "--sort-keys") opt.SortKeys = true;
            else if (arg is "--gold" or "--native") { } // accepted for Java parity
            else if (arg.StartsWith("--input=")) opt.Input = Value(arg, "--input=");
            else if (arg.StartsWith("--compact=")) opt.Compact = Value(arg, "--compact=");
            else if (arg.StartsWith("--width=")) opt.Width = IntValue(arg, "--width=");
            else if (arg.StartsWith("--indent=")) opt.Indent = IntValue(arg, "--indent=");
            else if (arg.StartsWith("-")) throw new ArgumentException("Unknown option: " + arg);
            else if (opt.Input is null) opt.Input = arg;
            else throw new ArgumentException("Multiple input files specified");
        }

        return opt;
    }

    private static JsonNode? ReadInput(string? file)
    {
        var text = file is null
            ? Console.In.ReadToEnd()
            : File.ReadAllText(file);

        return JsonNode.Parse(text);
    }

    private static string Value(string arg, string prefix)
    {
        var value = arg[prefix.Length..];
        if (value.Length == 0)
            throw new ArgumentException("Missing value for " + prefix.TrimEnd('='));
        return value;
    }

    private static int IntValue(string arg, string prefix)
    {
        if (!int.TryParse(Value(arg, prefix), out var n))
            throw new ArgumentException("Invalid integer for " + prefix);
        return n;
    }

    private static JsonNode? SortKeys(JsonNode? node)
    {
        return node switch
        {
            JsonObject obj => new JsonObject(
                obj.OrderBy(kv => kv.Key)
                   .Select(kv => KeyValuePair.Create(kv.Key, SortKeys(kv.Value)))),

            JsonArray arr => new JsonArray(
                arr.Select(SortKeys).ToArray()),

            null => null,

            _ => node.DeepClone()
        };
    }

    private static JsonNode DemoData() => new JsonObject
    {
        ["meta"] = new JsonObject
        {
            ["version"] = 1,
            ["ok"] = true,
            ["name"] = "jsonfold demo"
        },
        ["ids"] = new JsonArray(1, 2, 3, 4, 5, 6),
        ["matrix"] = new JsonArray(
            new JsonArray(1, 2),
            new JsonArray(3, 4),
            new JsonArray(5, 6)
        ),
        ["items"] = new JsonArray(
            new JsonObject { ["id"] = 1, ["name"] = "alpha" },
            new JsonObject { ["id"] = 2, ["name"] = "beta" }
        ),
        ["long"] = new JsonArray(
            "this is a long message that may force the block to stay expanded",
            "second",
            "third",
            "fourth"
        ),
        ["single_array"] = new JsonArray(1),
        ["single_object"] = new JsonObject { ["x"] = 2 },
        ["long_array"] = new JsonArray(
            Enumerable.Range(1, 50).Select(i => JsonValue.Create("a" + i)!).ToArray()
        )
    };

    private static void Usage()
    {
        Console.Error.WriteLine("""
        Usage:
          jsonfold [options] [input.json]

        Flags:
          -h, --help
          -v, --verbose
              --demo
              --sort-keys
              --gold
              --native

        Options use --name=value form:
              --input=file.json
              --compact=default|off|none|low|med|classic|high|max|pack|fold|grid|join
              --width=N
              --indent=2
        """);
    }
}
