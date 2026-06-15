package dev.jsonfold.format;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer ;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Jackson integration for JSONFold.
 *
 * <p>This class serializes Java objects with Jackson and applies JSONFold's
 * hybrid pretty/compact formatting to the generated JSON. It is the recommended
 * entry point when an application already uses Jackson for JSON serialization.</p>
 *
 * <p>The core {@link JSONFold} class only filters JSON text. This class provides
 * the full object-to-folded-JSON path by combining Jackson object serialization
 * with {@link JSONFoldWriter}.</p>
 *
 * <h2>Quick Start</h2>
 *
 * <pre>{@code
 * JacksonJSONFold jf = JacksonJSONFold.builder(120).build();
 *
 * String json = jf.formatJson(value);
 * }</pre>
 *
 * <h2>Writing to a Writer</h2>
 *
 * <pre>{@code
 * JacksonJSONFold jf = JacksonJSONFold.builder(120)
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
 * JacksonJSONFold jf = JacksonJSONFold.builder(120, config)
 *     .indent(2)
 *     .build();
 * }</pre>
 *
 * <h2>Configuring an ObjectMapper</h2>
 *
 * <p>JSONFold works best when Jackson first expands arrays and objects into
 * normal multi-line pretty JSON. The {@link #configure(ObjectMapper)} method
 * installs a JSONFold-friendly pretty printer on an existing mapper.</p>
 *
 * <pre>{@code
 * ObjectMapper mapper = JacksonJSONFold.configure(new ObjectMapper());
 *
 * mapper.writerWithDefaultPrettyPrinter()
 *       .writeValue(output, value);
 * }</pre>
 *
 * <p>Most callers should use {@link #formatJson(Object)} or
 * {@link #writeJson(Object, Writer)} rather than using {@link JSONFoldWriter}
 * directly.</p>
 */

public final class JacksonJSONFold extends JSONFold implements JFFormatter {

    /**
     * Whether to use the gold/test pretty-printer variant.
     *
     * <p>This is mainly useful for matching cross-language golden test output.</p>
     */
    protected boolean gold = false ;

    /**
     * Create a Jackson-backed formatter.
     *
     * @param width target maximum line width
     * @param config folding configuration
     */
    public JacksonJSONFold(int width, Config config)
    {
        super(width, config) ;
    }

    /**
     * Return whether gold/test formatting mode is enabled.
     *
     * @return {@code true} if gold mode is enabled
     */
    public boolean isGold() {
        return gold;
    }

    /**
     * Create a builder using the default configuration and the supplied width.
     *
     * @param width target maximum line width
     * @return a new Jackson formatter builder
     */
    public static Builder builder(int width) {
        return new Builder(width, Config.defaultConfig());
    }

    /**
     * Create a builder using the supplied width and configuration.
     *
     * @param width target maximum line width
     * @param config folding configuration
     * @return a new Jackson formatter builder
     */
    public static Builder builder(int width, Config config) {
        return new Builder(width, config);
    }

    /**
     * Builder for {@link JacksonJSONFold}.
     */
    public static class Builder extends JSONFold.Builder<Builder> {
        private final JacksonJSONFold target;

        /**
         * Create a builder with the supplied width and config.
         *
         * @param width target maximum line width
         * @param config folding configuration
         */
        private Builder(int width, Config config) {
            super(new JacksonJSONFold(width, config));
            this.target = (JacksonJSONFold) super.target;
        }

        /**
         * Enable or disable gold/test formatting mode.
         *
         * @param gold {@code true} to enable gold mode
         * @return this builder
         */
        public Builder gold(boolean gold) {
            target.gold = gold;
            return this;
        }

        /**
         * Build the configured formatter.
         *
         * @return configured Jackson formatter
         */
        @Override
        public JacksonJSONFold build() {
            super.build();
            return target;
        }
    }

    
    /**
     * Configure an existing {@link ObjectMapper} for use with JSONFold.
     *
     * <p>This method mutates the supplied mapper by setting a JSONFold-friendly
     * default pretty-printer, then returns the same mapper.</p>
     *
     * @param mapper mapper to configure
     * @return the same mapper instance
     */
    public static ObjectMapper configure(ObjectMapper mapper) {
        mapper.setDefaultPrettyPrinter(prettyPrinter());

        return mapper;
    }

    /**
     * Pretty-printer variant used by golden tests.
     *
     * <p>This printer keeps formatting behavior stable for expected-output
     * comparisons, especially around separators and empty containers.</p>
     */
    private static class GoldPrettyPrinter extends DefaultPrettyPrinter {
        GoldPrettyPrinter() {
            super();
        }

        GoldPrettyPrinter(GoldPrettyPrinter base) {
            super(base);
        }

        /**
         * Write object field/value separator.
         *
         * @param g JSON generator
         * @throws IOException if writing fails
         */
        @Override
        public void writeObjectFieldValueSeparator(JsonGenerator g)
                throws IOException {
            g.writeRaw(": ");
        }

        /**
         * Write object end marker.
         *
         * @param g JSON generator
         * @param nrOfEntries number of object entries
         * @throws IOException if writing fails
         */
        @Override
        public void writeEndObject(JsonGenerator g, int nrOfEntries)
                throws IOException {

            if (nrOfEntries == 0) {
                g.writeRaw('}');
                return;
            }

            super.writeEndObject(g, nrOfEntries);
        }

        /**
         * Write array end marker.
         *
         * @param g JSON generator
         * @param nrOfValues number of array values
         * @throws IOException if writing fails
         */
        @Override
        public void writeEndArray(JsonGenerator g, int nrOfValues)
                throws IOException {

            if (nrOfValues == 0) {
                g.writeRaw(']');
                return;
            }

            super.writeEndArray(g, nrOfValues);
        }

        /**
         * Create a reusable pretty-printer instance.
         *
         * @return copied pretty-printer instance
         */
        @Override
        public DefaultPrettyPrinter createInstance() {
            return new GoldPrettyPrinter(this);
        }
    }

    /**
     * Return a Jackson pretty-printer suitable as input to JSONFold.
     *
     * <p>Jackson's default pretty printer keeps arrays inline. JSONFold works
     * best when arrays and objects are first expanded into normal multi-line
     * pretty JSON, then folded back according to JSONFold rules.</p>
     *
     * @param indent indentation string, or {@code null} for Jackson's default
     * @return configured pretty-printer
     */
    public static DefaultPrettyPrinter prettyPrinter(String indent) {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();

        // Expand arrays and objects before JSONFold processes the stream.
        DefaultIndenter indenter = DefaultIndenter.SYSTEM_LINEFEED_INSTANCE;
        if (indent != null)
            indenter.withIndent(indent);
        pp.indentArraysWith(indenter);
        pp.indentObjectsWith(indenter);

        return pp;
    }

    /**
     * Return a JSONFold-friendly Jackson pretty-printer using default indentation.
     *
     * @return configured pretty-printer
     */
    public static DefaultPrettyPrinter prettyPrinter() {
        return prettyPrinter(null);
    }

    /**
     * Return a JSONFold-friendly Jackson pretty-printer using N-space indentation.
     *
     * @param indent number of spaces to use for indentation
     * @return configured pretty-printer
     */
    public static DefaultPrettyPrinter prettyPrinter(int indent) {
        return prettyPrinter(" ".repeat(indent));
    }

    /**
     * Return the gold/test pretty-printer using the supplied indentation string.
     *
     * @param indent indentation string, or {@code null} for Jackson's default
     * @return configured gold pretty-printer
     */
    public static DefaultPrettyPrinter goldPrettyPrinter(String indent) {
        DefaultPrettyPrinter pp = new GoldPrettyPrinter();

        // Expand arrays and objects before JSONFold processes the stream.
        pp.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        DefaultIndenter indenter = new DefaultIndenter();
        if (indent != null)
            indenter.withIndent(indent);
        pp.indentArraysWith(indenter);
        pp.indentObjectsWith(indenter);

        return pp;
    }

    /**
     * Return the gold/test pretty-printer using N-space indentation.
     *
     * @param indent number of spaces to use for indentation
     * @return configured gold pretty-printer
     */
    public static DefaultPrettyPrinter goldPrettyPrinter(int indent) {
        return goldPrettyPrinter(" ".repeat(indent));
    }

    /**
     * Create a shared mapper for normal or sorted-key output.
     *
     * @param sortKeys {@code true} to order map entries by key
     * @return configured mapper
     */
    private static ObjectMapper createMapper(boolean sortKeys)
    {
     ObjectMapper mapper = new ObjectMapper();

        JacksonJSONFold.configure(mapper);

        if (sortKeys) {
            mapper.enable(
                SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        }

        return mapper;    
    }

    /** Shared mapper for normal key order. */
    private static ObjectMapper DEFAULT_MAPPER = createMapper(false) ;

    /** Shared mapper for sorted map-entry key order. */
    private static ObjectMapper SORTED_MAPPER = createMapper(true) ;

    /**
     * Serialize an object as folded JSON and write it to a writer.
     *
     * @param obj object to serialize
     * @param writer destination writer
     * @return formatting statistics
     * @throws IOException if Jackson serialization or writing fails
     */
    public Stats writeJson(Object obj, Writer writer) throws IOException
    {
        ObjectMapper mapper = sortKeys ? SORTED_MAPPER : DEFAULT_MAPPER ;
        PrettyPrinter pp = gold ? goldPrettyPrinter(width) : prettyPrinter(width) ;
        try (JSONFoldWriter out = new JSONFoldWriter(writer, config, isDoClose())) {
            mapper.writer(pp).writeValue(out, obj) ;
            return out.getStats() ;
        }
    }

    /**
     * Serialize an object and return folded JSON text.
     *
     * @param obj object to serialize
     * @return folded JSON text
     * @throws IOException if serialization fails
     */
    public String formatJson(Object obj) 
    throws IOException
    {
        Writer sw = new StringWriter() ;
        Stats stats = writeJson(obj, sw) ;
        if ( stats == null ) throw new IOException("Failed to generate JSON string") ;
        return sw.toString();
    }

    /**
     * Serialize an object as folded JSON using an explicit width and config.
     *
     * @param obj object to serialize
     * @param writer destination writer
     * @param width target maximum line width
     * @param config folding configuration
     * @return formatting statistics
     * @throws IOException if Jackson serialization or writing fails
     */
    public static Stats writeJson(Object obj, Writer writer, int width, Config config)
    throws IOException
    {
        JacksonJSONFold fmt = new JacksonJSONFold(width, config) ;
        return fmt.writeJson(obj, writer);
    }


}