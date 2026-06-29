using System.Text.Json;
using System.Text.Json.Nodes;
using JsonFold.Format;

internal static class Program
{
    static int Main(string[] args)
    {
        try
        {
            var options = CliOptions.Parse(args);
            if (options.ShowHelp)
            {
                CliOptions.PrintHelp(Console.Out);
                return 0;
            }

            if (options.Indent != 2)
            {
                Console.Error.WriteLine("jsonfold: --indent is accepted for API parity, but System.Text.Json on net8.0 uses fixed 2-space indentation.");
            }


            string input = options.Demo ? DemoJson() : ReadInput(options.InputPath);
            JsonNode? node = JsonNode.Parse(input);
            if (node is null)
            {
                Console.Error.WriteLine("jsonfold: input is empty or not valid JSON");
                return 2;
            }

            if (options.SortKeys)
            {
                node = SortNode(node);
            }

            JsonFoldConfig? config = BuildConfig(options);
            if (options.Verbose)
            {
                Console.Error.WriteLine(config);
            }
            var formatter = new JsonFoldFormatter(options.Width, config, indent: options.Indent);

            using TextWriter writer = OpenOutput(options.OutputPath);
            JsonFoldStats stats = formatter.WriteJson(node, writer);

            if (options.Verbose)
            {
                Console.Error.WriteLine(stats);
            }

            return 0;
        }
        catch (ArgumentException ex)
        {
            Console.Error.WriteLine("jsonfold: " + ex.Message);
            return 2;
        }
        catch (JsonException ex)
        {
            Console.Error.WriteLine("jsonfold: invalid JSON: " + ex.Message);
            return 2;
        }
        catch (IOException ex)
        {
            Console.Error.WriteLine("jsonfold: I/O error: " + ex.Message);
            return 1;
        }
    }

    static string ReadInput(string? path)
    {
        if (string.IsNullOrEmpty(path) || path == "-")
        {
            return Console.In.ReadToEnd();
        }
        return File.ReadAllText(path);
    }

    static TextWriter OpenOutput(string? path)
    {
        if (string.IsNullOrEmpty(path) || path == "-")
        {
            return Console.Out;
        }
        return new StreamWriter(File.Create(path));
    }

    static JsonFoldConfig? BuildConfig(CliOptions options)
    {
        JsonFoldConfig? config = JsonFoldConfig.Resolve(options.Compact, options.Width);
        if (config is null) return null;

        return config with
        {
            PackArrayItems = options.PackArrayItems ?? options.PackItems ?? config.PackArrayItems,
            PackObjItems = options.PackObjItems ?? options.PackItems ?? config.PackObjItems,
            PackNesting = options.PackNesting ?? config.PackNesting,

            FoldArrayItems = options.FoldArrayItems ?? options.FoldItems ?? config.FoldArrayItems,
            FoldObjItems = options.FoldObjItems ?? options.FoldItems ?? config.FoldObjItems,
            FoldNesting = options.FoldNesting ?? config.FoldNesting,

            GridArrayItems = options.GridArrayItems ?? options.GridItems ?? config.GridArrayItems,
            GridObjItems = options.GridObjItems ?? options.GridItems ?? config.GridObjItems,
            GridMinLines = options.GridMinLines ?? config.GridMinLines,
            GridMaxLines = options.GridMaxLines ?? config.GridMaxLines,
            GridArrayMin = options.GridArrayMin ?? config.GridArrayMin,
            GridObjMin = options.GridObjMin ?? config.GridObjMin,

            JoinArrayItems = options.JoinArrayItems ?? options.JoinItems ?? config.JoinArrayItems,
            JoinObjItems = options.JoinObjItems ?? options.JoinItems ?? config.JoinObjItems,
            JoinNesting = options.JoinNesting ?? config.JoinNesting,
        };
    }

    static JsonNode SortNode(JsonNode node)
    {
        if (node is JsonArray array)
        {
            var sortedArray = new JsonArray();
            foreach (JsonNode? item in array)
            {
                sortedArray.Add(item is null ? null : SortNode(item.DeepClone()));
            }
            return sortedArray;
        }

        if (node is JsonObject obj)
        {
            var sortedObject = new JsonObject();
            foreach (var kv in obj.OrderBy(kv => kv.Key, StringComparer.Ordinal))
            {
                sortedObject[kv.Key] = kv.Value is null ? null : SortNode(kv.Value.DeepClone());
            }
            return sortedObject;
        }

        return node.DeepClone();
    }

    static string DemoJson() => """
{
  "meta": { "version": 1, "ok": true, "name": "jsonfold demo" },
  "ids": [1, 2, 3, 4, 5, 6],
  "matrix": [[1, 20, "Red", 300], [4000, 50, "Yellow", 6], [70, 800, "Green", 9000]],
  "items": [
    { "id": 1, "name": "alpha", "qty": 12, "size": "Medium" },
    { "id": 20, "name": "beta", "qty": 3000, "size": "Large" },
    { "id": 300, "name": "Charlie", "qty": 4, "size": "Tiny" }
  ],
  "long": ["this is a long message that may force the block to stay expanded", "second", "third", "fourth"]
}
""";

    sealed class CliOptions
    {
        public bool ShowHelp { get; set; }
        public bool Demo { get; set; }
        public bool SortKeys { get; set; }
        public bool Verbose { get; set; }
        public string Compact { get; set; } = "default";
        public string? InputPath { get; set; }
        public string? OutputPath { get; set; }
        public int? Width { get; set; }
        public int Indent { get; set; } = 2;


        public int? PackItems { get; set; }

        public int? PackArrayItems { get; set; }
        public int? PackObjItems { get; set; }
        public int? PackNesting { get; set; }

        public int? FoldItems { get; set; }
        public int? FoldArrayItems { get; set; }
        public int? FoldObjItems { get; set; }
        public int? FoldNesting { get; set; }

        public int? GridItems { get; set; }
        public int? GridArrayItems { get; set; }
        public int? GridObjItems { get; set; }
        public int? GridMinLines { get; set; }
        public int? GridMaxLines { get; set; }
        public int? GridArrayMin { get; set; }
        public int? GridObjMin { get; set; }

        public int? JoinItems { get; set; }
        public int? JoinArrayItems { get; set; }
        public int? JoinObjItems { get; set; }
        public int? JoinNesting { get; set; }

        public static CliOptions Parse(string[] args)
        {
            var o = new CliOptions();
            for (int i = 0; i < args.Length; i++)
            {
                string arg = args[i];
                string value;

                if (arg is "-h" or "--help") { o.ShowHelp = true; continue; }
                if (arg is "--demo") { o.Demo = true; continue; }
                if (arg is "--sort-keys") { o.SortKeys = true; continue; }
                if (arg is "-v" or "--verbose") { o.Verbose = true; continue; }

                if (arg.StartsWith("--", StringComparison.Ordinal))
                {
                    string name;
                    int eq = arg.IndexOf('=');
                    if (eq >= 0)
                    {
                        name = arg[..eq];
                        value = arg[(eq + 1)..];
                    }
                    else
                    {
                        name = arg;
                        if (++i >= args.Length) throw new ArgumentException($"missing value for {name}");
                        value = args[i];
                    }

                    switch (name)
                    {
                        case "--compact": o.Compact = value; break;
                        case "--input": case "-i": o.InputPath = value; break;
                        case "--output": case "-o": o.OutputPath = value; break;
                        case "--width": o.Width = ParseInt(name, value); break;
                        case "--indent": o.Indent = ParseInt(name, value); break;

                        case "--pack-items": o.PackItems = ParseInt(name, value); break;
                        case "--pack-array-items": o.PackArrayItems = ParseInt(name, value); break;
                        case "--pack-obj-items": o.PackObjItems = ParseInt(name, value); break;
                        case "--pack-nesting": o.PackNesting = ParseInt(name, value); break;

                        case "--fold-items": o.FoldItems = ParseInt(name, value); break;
                        case "--fold-array-items": o.FoldArrayItems = ParseInt(name, value); break;
                        case "--fold-obj-items": o.FoldObjItems = ParseInt(name, value); break;
                        case "--fold-nesting": o.FoldNesting = ParseInt(name, value); break;

                        case "--grid-items": o.GridItems = ParseInt(name, value); break;
                        case "--grid-array-items": o.GridArrayItems = ParseInt(name, value); break;
                        case "--grid-obj-items": o.GridObjItems = ParseInt(name, value); break;
                        case "--grid-min-lines": o.GridMinLines = ParseInt(name, value); break;
                        case "--grid-max-lines": o.GridMaxLines = ParseInt(name, value); break;
                        case "--grid-array-min": o.GridArrayMin = ParseInt(name, value); break;
                        case "--grid-obj-min": o.GridObjMin = ParseInt(name, value); break;

                        case "--join-items": o.JoinItems = ParseInt(name, value); break;
                        case "--join-array-items": o.JoinArrayItems = ParseInt(name, value); break;
                        case "--join-obj-items": o.JoinObjItems = ParseInt(name, value); break;
                        case "--join-nesting": o.JoinNesting = ParseInt(name, value); break;
                        default: throw new ArgumentException($"unknown option: {name}");
                    }
                    continue;
                }

                if (o.InputPath is null) o.InputPath = arg;
                else throw new ArgumentException($"unexpected argument: {arg}");
            }
            return o;
        }

        static int ParseInt(string name, string value) =>
            int.TryParse(value, out int n) ? n : throw new ArgumentException($"{name} expects an integer, got '{value}'");

        public static void PrintHelp(TextWriter w)
        {
            w.WriteLine("""
jsonfold-cli - full JSONFold command line tool for .NET

Usage:
  jsonfold-cli [options] [input.json]
  dotnet run --project cli/JsonFold.Cli -- [options] [input.json]

Input/output:
  --input, -i FILE             Read input from FILE instead of stdin
  --output, -o FILE            Write output to FILE instead of stdout
  --demo                       Use built-in demo JSON
  --sort-keys                  Sort object keys recursively before formatting
  --verbose, -v                Print JSONFoldStats to stderr

Base formatting:
  --compact NAME               Preset: off, none, default, classic, low, med, high, max, pack, fold, grid, join
  --width N                    Override target width
  --indent N                   Accepted for API parity; net8 System.Text.Json pretty output is 2 spaces

Config overrides:
  --pack-array-items N         --pack-obj-items N         --pack-nesting N
  --fold-array-items N         --fold-obj-items N         --fold-nesting N
  --grid-array-items N         --grid-obj-items N         --grid-min-lines N
  --grid-max-lines N           --grid-array-min N         --grid-obj-min N
  --join-array-items N         --join-obj-items N         --join-nesting N

Examples:
  dotnet run --project cli/JsonFold.Cli -- --demo --compact high --width 120
  dotnet run --project cli/JsonFold.Cli -- --compact max --sort-keys input.json
  cat input.json | dotnet run --project cli/JsonFold.Cli -- --compact default > out.json
""");
        }
    }
}