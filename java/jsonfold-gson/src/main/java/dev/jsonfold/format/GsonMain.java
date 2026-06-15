package dev.jsonfold.format;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;

public final class GsonMain {

    private GsonMain() {
    }

    public static void main(String[] argv) throws Exception {
        int rc = run(argv);
        if (rc != 0) {
            System.exit(rc);
        }
    }

    static private Config buildConfig(Args args)
    {

        Config.Builder builder = JSONFold.config(args.compact, args.width) ;
        if ( builder == null ) return null ;

        if (args.width != null) builder.width(args.width);

        return builder.build() ;
    }

    static private Object getInput(boolean demo, String inputFile)
    throws FileNotFoundException
    {
        if ( demo ) {
            return demoData() ;
        }
        Gson gson = new GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create() ;
        Reader reader = inputFile != null ? new FileReader(new File(inputFile)) :
            new InputStreamReader(System.in) ;
        return gson.fromJson(reader, Object.class) ;
    }

    public static int run(String[] argv) throws Exception {
        Args args = parseArgs(argv);
        if (args.help) {
            usage();
            return 0;
        }

        Config cfg = buildConfig(args) ;

        if (args.verbose) {
            System.err.println(cfg);
        }

        Object value = getInput(args.demo, args.input) ;

        Gson prettyWriter = new GsonBuilder().setPrettyPrinting().create() ;

        // DefaultPrettyPrinter pp = args.gold ?
        //     JacksonJSONFold.goldPrettyPrinter(args.indent) : 
        //     JacksonJSONFold.prettyPrinter(args.indent) ;

        // JsonMapper.Builder builder = JsonMapper.builder()
        //     .defaultPrettyPrinter(pp)
        //     .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

        // if (args.sortKeys) {
        //     builder.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        //     builder.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        // }

        // ObjectMapper mapper = builder.build();
        // ObjectWriter prettyWriter = mapper.writerWithDefaultPrettyPrinter();

        Writer stdout = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
        JSONFoldWriter out = new JSONFoldWriter(stdout, cfg, true); // keep stdout open
        prettyWriter.toJson(value, out) ;       
        out.finish();
        stdout.flush();

        if (args.verbose) {
            System.err.println(out.getStats());
        }

        return 0;
    }

    private static final class Args {
        boolean help;
        boolean demo;
        boolean verbose;
        boolean sortKeys;
        boolean gold;    // Match Python/Javascript Style

        String input;
        String compact = "default";

        Integer width;
        Integer indent = 2;

    }

    private static Args parseArgs(String[] argv) {
        Args out = new Args();

        for (String arg : argv) {
            if (arg.equals("--help") || arg.equals("-h")) {
                out.help = true;
                continue;
            }

            if (arg.equals("--gold")) {
                out.gold = true;
                continue;
            }

            if (arg.equals("--demo")) {
                out.demo = true;
                continue;
            }

            if (arg.equals("--verbose") || arg.equals("-v")) {
                out.verbose = true;
                continue;
            }

            if (arg.equals("--sort-keys")) {
                out.sortKeys = true;
                continue;
            }

            if (arg.startsWith("--input=")) {
                out.input = stringValue(arg, "--input=");
                continue;
            }

            if (arg.startsWith("--compact=")) {
                out.compact = stringValue(arg, "--compact=");
                continue;
            }

            if (arg.startsWith("--width=")) {
                out.width = intValue(arg, "--width=");
                continue;
            }

            if (arg.startsWith("--indent=")) {
                out.indent = intValue(arg, "--indent=");
                continue;
            }

            if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Unknown option: " + arg);
            }

            if (out.input != null) {
                throw new IllegalArgumentException("Multiple input files specified");
            }

            out.input = arg;
        }

        return out;
    }

    private static String stringValue(String arg, String prefix) {
        String value = arg.substring(prefix.length());
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing value for " + prefix.substring(0, prefix.length() - 1));
        }
        return value;
    }

    private static int intValue(String arg, String prefix) {
        String value = stringValue(arg, prefix);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer for " + prefix + value);
        }
    }

    @SafeVarargs
    static <K,V> LinkedHashMap<K,V> mapOf(
        Map.Entry<? extends K, ? extends V>... entries) {
        LinkedHashMap<K,V> map = new LinkedHashMap<>();

        for (Map.Entry<? extends K, ? extends V> e : entries) {
            map.put(e.getKey(), e.getValue());
        }

        return map;
    }

    private static Object demoData() {
        Map<String, Object> root = new LinkedHashMap<>();

        root.put("meta", mapOf(
                Map.entry("version", 1),
                Map.entry("ok", true),
                Map.entry("name", "jsonfold demo"))
            );

        root.put("ids", List.of(1, 2, 3, 4, 5, 6));

        root.put("matrix", List.of(
                List.of(1, 2),
                List.of(3, 4),
                List.of(5, 6))
            );

        root.put("items", List.of(
                mapOf(
                    Map.entry("id", 1),
                    Map.entry("name", "alpha")
                ),
                mapOf(
                    Map.entry("id", 2),
                    Map.entry("name", "beta")
                    )
                )
            );

        root.put("long", List.of(
                "this is a long message that may force the block to stay expanded",
                "second",
                "third",
                "fourth"));

        root.put("single_array", List.of(1));
        root.put("single_object", Map.of("x", 2));


        root.put("long_array", IntStream.rangeClosed(1, 50)
                .mapToObj(i -> "a" + i)
                .collect(Collectors.toList()));
                
        root.put("wide_array", IntStream.rangeClosed(1, 9)
            .mapToObj(i -> "abcdefghijklmnopqrstuvwxyz" + i)
            .toList());
                
        root.put("wide_object", IntStream.rangeClosed(1, 9)
            .boxed()
            .collect(Collectors.toMap(
            i -> "abcdefghijk" + i,
            i -> "lmnopqrstuvwxyz" + i,
            (a, b) -> a,
            LinkedHashMap::new
        )));


        return root;
    }

    private static void usage() {
        System.err.println("""
            Usage:
              jsonfold [options] [input.json]

            Flags:
              -h, --help
              -v, --verbose
                  --demo
                  --sort-keys

            Options use --name=value form only:
                  --input=file.json
                  --compact=default|none|low|med|high|max|pack|fold|join
                  --width=N
                  --indent=2

            """);
    }
}