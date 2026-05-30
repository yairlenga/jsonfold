package dev.jsonfold.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.jsonfold.format.JSONFold;
import dev.jsonfold.format.JSONFoldWriter;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Command-line entry point for jsonfold.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String inputFile = null;
        String compact = "default";
        Integer width = null;
        int indent = 2;
        boolean sortKeys = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input", "-i" -> inputFile = args[++i];
                case "--compact" -> compact = args[++i];
                case "--width" -> width = Integer.parseInt(args[++i]);
                case "--indent" -> indent = Integer.parseInt(args[++i]);
                case "--sort-keys" -> sortKeys = true;
                case "--help", "-h" -> {
                    usage();
                    return;
                }
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    usage();
                    System.exit(2);
                }
            }
        }

        JSONFold cfg = JSONFold.preset(compact);
        if (width != null) {
            cfg = cfg.withWidth(width);
        }

        ObjectMapper mapper = new ObjectMapper();
        if (sortKeys) {
            mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        }

        Object data;
        try (InputStream in = inputFile == null
                ? System.in
                : new FileInputStream(inputFile)) {
            data = mapper.readValue(in, Object.class);
        }

        Writer stdout = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
        JSONFoldWriter foldWriter = new JSONFoldWriter(stdout, cfg);

        mapper.writer()
                .withDefaultPrettyPrinter()
                .writeValue(foldWriter, data);

        foldWriter.finish();
        stdout.flush();
    }

    private static void usage() {
        System.err.println("""
                Usage: jsonfold [options]

                Options:
                  -i, --input FILE       Read JSON from file instead of stdin
                      --compact NAME     Preset: default, none, low, med, high, max, pack, fold, join
                      --width N          Target line width
                      --indent N         Pretty-print indent, currently accepted but Jackson default is used
                      --sort-keys        Sort object keys
                  -h, --help             Show help
                """);
    }
}