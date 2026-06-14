package dev.jsonfold.format;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class Main {

    private Main() {
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
        if (args.packItems != null) builder.packArrayItems(args.packItems).packObjItems(args.packItems);

        if (args.foldItems != null) builder.foldArrayItems(args.foldItems).foldObjItems(args.foldItems);
        if (args.joinItems != null) builder.joinArrayItems(args.joinItems).joinObjItems(args.joinItems);

        if (args.packArrayItems != null) builder.packArrayItems(args.packArrayItems);
        if (args.packObjItems != null) builder.packObjItems(args.packObjItems);
        if (args.packNesting != null) builder.packNesting(args.packNesting);

        if (args.foldArrayItems != null) builder.foldArrayItems(args.foldArrayItems);
        if (args.foldObjItems != null) builder.foldObjItems(args.foldObjItems);
        if (args.foldNesting != null) builder.foldNesting(args.foldNesting);

        if (args.joinArrayItems != null) builder.joinArrayItems(args.joinArrayItems);
        if (args.joinObjItems != null) builder.joinObjItems(args.joinObjItems);
        if (args.joinNesting != null) builder.joinNesting(args.joinNesting);

        return builder.build() ;
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

        DefaultPrettyPrinter pp = args.gold ?
            JacksonJSONFold.goldPrettyPrinter(args.indent) : 
            JacksonJSONFold.prettyPrinter(args.indent) ;

        JsonMapper.Builder builder = JsonMapper.builder()
            .defaultPrettyPrinter(pp)
            .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

        if (args.sortKeys) {
            builder.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
            builder.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        }

        ObjectMapper mapper = builder.build();

        Object value;
        if (args.demo) {
            value = demoData();
        } else if (args.input != null) {
            value = mapper.readValue(new File(args.input), Object.class);
        } else {
            value = mapper.readValue(System.in, Object.class);
        }

        ObjectWriter prettyWriter = mapper.writerWithDefaultPrettyPrinter();

        Writer stdout = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
        JSONFoldWriter out = new JSONFoldWriter(stdout, cfg, true); // keep stdout open
        prettyWriter.writeValue(out, value);
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

        Integer packItems;
        Integer packArrayItems;
        Integer packObjItems;
        Integer packNesting;

        Integer foldItems;
        Integer foldArrayItems;
        Integer foldObjItems;
        Integer foldNesting;

        Integer joinItems;
        Integer joinArrayItems;
        Integer joinObjItems;
        Integer joinNesting;
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

            if (arg.startsWith("--pack-items=")) {
                out.packItems = intValue(arg, "--pack-items=");
                continue;
            }

            if (arg.startsWith("--pack-array-items=")) {
                out.packArrayItems = intValue(arg, "--pack-array-items=");
                continue;
            }

            if (arg.startsWith("--pack-obj-items=")) {
                out.packObjItems = intValue(arg, "--pack-obj-items=");
                continue;
            }

            if (arg.startsWith("--pack-nesting=")) {
                out.packNesting = intValue(arg, "--pack-nesting=");
                continue;
            }

            if (arg.startsWith("--fold-items=")) {
                out.foldItems = intValue(arg, "--fold-items=");
                continue;
            }

            if (arg.startsWith("--fold-array-items=")) {
                out.foldArrayItems = intValue(arg, "--fold-array-items=");
                continue;
            }

            if (arg.startsWith("--fold-obj-items=")) {
                out.foldObjItems = intValue(arg, "--fold-obj-items=");
                continue;
            }

            if (arg.startsWith("--fold-nesting=")) {
                out.foldNesting = intValue(arg, "--fold-nesting=");
                continue;
            }

            if (arg.startsWith("--join-items=")) {
                out.joinItems = intValue(arg, "--join-items=");
                continue;
            }

            if (arg.startsWith("--join-array-items=")) {
                out.joinArrayItems = intValue(arg, "--join-array-items=");
                continue;
            }

            if (arg.startsWith("--join-obj-items=")) {
                out.joinObjItems = intValue(arg, "--join-obj-items=");
                continue;
            }

            if (arg.startsWith("--join-nesting=")) {
                out.joinNesting = intValue(arg, "--join-nesting=");
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

    private static Object demoData() {
        Map<String, Object> root = new LinkedHashMap<>();

        root.put("meta", Map.of(
                "version", 1,
                "ok", true,
                "name", "jsonfold demo"));

        root.put("ids", List.of(1, 2, 3, 4, 5, 6));

        root.put("matrix", List.of(
                List.of(1, 2),
                List.of(3, 4),
                List.of(5, 6)));

        root.put("items", List.of(
                Map.of("id", 1, "name", "alpha"),
                Map.of("id", 2, "name", "beta")));

        root.put("long", List.of(
                "this is a long message that may force the block to stay expanded",
                "second",
                "third",
                "fourth"));

        root.put("wide_array", IntStream.rangeClosed(1, 50)
                .mapToObj(i -> "a" + i)
                .collect(Collectors.toList()));

        root.put("singleArray", List.of(1));
        root.put("singleObject", Map.of("x", 2));

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

                  --pack-items=N
                  --pack-array-items=N
                  --pack-obj-items=N
                  --pack-nesting=N

                  --fold-items=N
                  --fold-array-items=N
                  --fold-obj-items=N
                  --fold-nesting=N

                  --join-items=N
                  --join-array-items=N
                  --join-obj-items=N
                  --join-nesting=N
            """);
    }
}