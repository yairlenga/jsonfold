package dev.jsonfold.format;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Gson-based JSONFold formatter.
 *
 * <p>This class serializes Java objects with Gson using pretty printing,
 * then passes the generated JSON through {@link JSONFoldWriter}.</p>
 */
public final class GsonJSONFold extends JSONFold implements JFFormatter {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

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

    @Override
    public Stats writeJson(Object obj, Writer writer) throws IOException {
        String json = GSON.toJson(obj);

        try (JSONFoldWriter out =
                new JSONFoldWriter(writer, config, isDoClose())) {
            out.write(json);
            out.write("\n");
            return out.getStats();
        }
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