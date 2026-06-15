package dev.jsonfold.format;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;

/**
 * Gson integration for JSONFold.
 *
 * <p>This class serializes Java objects with Gson and applies JSONFold's
 * hybrid pretty/compact formatting to the generated JSON. It is the recommended
 * entry point when an application already uses Gson for JSON serialization.</p>
 *
 * <p>The core {@link JSONFold} class only filters JSON text. This class provides
 * the full object-to-folded-JSON path by combining Gson object serialization
 * with {@link JSONFoldWriter}.</p>
 *
 * <h2>Quick Start</h2>
 *
 * <pre>{@code
 * GsonJSONFold jf = GsonJSONFold.builder(120).build();
 *
 * String json = jf.formatJson(value);
 * }</pre>
 *
 * <h2>Writing to a Writer</h2>
 *
 * <pre>{@code
 * GsonJSONFold jf = GsonJSONFold.builder(120)
 *     .sortKeys(true)
 *     .build();
 *
 * try (Writer out = Files.newBufferedWriter(outputFile)) {
 *     jf.writeJson(value, out);
 * }
 * }</pre>
 *
 * <h2>Custom Configuration</h2>
 *
 * <pre>{@code
 * Config config = Config.defaultConfig()
 *     .toBuilder()
 *     .foldArrayItems(12)
 *     .foldObjItems(6)
 *     .build();
 *
 * GsonJSONFold jf = GsonJSONFold.builder(120, config)
 *     .indent(2)
 *     .build();
 * }</pre>
 *
 * <p>Most callers should use {@link #formatJson(Object)} or
 * {@link #writeJson(Object, Writer)} rather than using {@link JSONFoldWriter}
 * directly.</p>
 */
public final class GsonJSONFold extends JSONFold implements JFFormatter {

    public GsonJSONFold(int width, Config config) {
        super(width, config);
    }

    public static Builder builder(int width) {
        return new Builder(width, Config.defaultConfig());
    }

    public static Builder builder(int width, Config config) {
        return new Builder(width, config);
    }

    public static class Builder extends JSONFold.Builder<Builder> {
        private final GsonJSONFold target;

        private Builder(int width, Config config) {
            super(new GsonJSONFold(width, config));
            this.target = (GsonJSONFold) super.target;
        }

        @Override
        public GsonJSONFold build() {
            super.build();
            return target;
        }
    }

    public Stats writeJson(Object value, Writer out)
    throws IOException {

        JSONFoldWriter jfw = JSONFold.filter_stream(out, config.getWidth(), config, doClose) ;

        if ( indent != null && indent > 0 ) {
            Gson prettyWriter = new GsonBuilder().create();
            JsonWriter jw = new JsonWriter(jfw) ;
            jw.setIndent(" ".repeat(indent)) ;
            prettyWriter.toJson(value, Object.class, jw) ;
        } else {
            // Use built-in Pretty printer
            Gson prettyWriter = new GsonBuilder().setPrettyPrinting().create() ;
            prettyWriter.toJson(value, Object.class, jfw);
        }
        jfw.flush() ;
        out.write("\n");
        return jfw.getStats();
    }

    @Override
    public String formatJson(Object obj) throws IOException {
        StringWriter sw = new StringWriter();
        Stats stats = writeJson(obj, sw);
        if (stats == null) {
            throw new IOException("Failed to generate JSON string");
        }
        return sw.toString();
    }

    public static Stats writeJson(
            Object obj,
            Writer writer,
            int width,
            Config config)
    throws IOException {
        GsonJSONFold fmt = new GsonJSONFold(width, config);
        return fmt.writeJson(obj, writer);
    }

    public static String formatJson(
            Object obj,
            int width,
            Config config)
    throws IOException {
        GsonJSONFold fmt = new GsonJSONFold(width, config);
        return fmt.formatJson(obj);
    }
}