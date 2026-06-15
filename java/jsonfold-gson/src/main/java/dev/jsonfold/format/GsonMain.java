package dev.jsonfold.format;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;

public final class GsonMain {

    private GsonMain() {
    }

    public Object getInput(boolean demo, String inputFile)
    throws FileNotFoundException
    {
        if ( demo ) {
            return CliUtils.demoData() ;
        }
        Gson gson = new GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create() ;
        Reader reader = inputFile != null ? new FileReader(new File(inputFile)) :
            new InputStreamReader(System.in) ;
        return gson.fromJson(reader, Object.class) ;
    }

    public Stats writeTo(Writer out, Object value, Config config, int indent, boolean gold )
    throws IOException {
        if ( out == null ) out =  new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8)); 
        JSONFoldWriter jfw = JSONFold.filter_stream(out, config.getWidth(), config, false) ;

        GsonJSONFold.writeJson(value, jfw, config.getWidth(), config) ;

        jfw.flush() ;
        return jfw.getStats();
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

    private static Object sortKeys(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            TreeMap<String,Object> sorted = new TreeMap<>();

            for (var e : map.entrySet()) {
                sorted.put(
                    (String) e.getKey(),
                    sortKeys(e.getValue()));
            }

            return sorted;
        }

        if (obj instanceof List<?> list) {
            ArrayList<Object> out = new ArrayList<>(list.size());

            for (Object item : list) {
                out.add(sortKeys(item));
            }

            return out;
        }

        return obj;
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

        GsonMain self = new GsonMain() ;
        Object value = self.getInput(args.demo, args.input) ;

        if ( args.sortKeys ) value = sortKeys(value);

        Stats stats = self.writeTo(null, value, cfg, args.indent, args.gold) ;

        if (args.verbose) {
            System.err.println(stats);
        }

        return 0;
    }

    private static final class Args {
        boolean help;
        boolean demo;
        boolean verbose;

        boolean sortKeys;

        // Not needed - GSON PP output identical to Python/JavaScript/...
        @SuppressWarnings("unused")
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