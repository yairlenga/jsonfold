package dev.jsonfold.format;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Base formatter configuration and text-filtering API for JSONFold.
 *
 * <p>{@code JSONFold} stores common formatting options such as width,
 * indentation, key sorting, and the folding {@link Config}. Subclasses may add
 * serializer-specific behavior, such as Jackson object serialization.</p>
 *
 * <p>This class can also fold already pretty-printed JSON text through
 * {@link #formatJsonText(String)}.</p>
 */
public class JSONFold {

    /** Pretty-print indentation level, or {@code null} to use serializer default. */
    protected Integer indent ;

    /** Whether object keys should be sorted before formatting. */
    protected boolean sortKeys ;

    /** Target maximum line width. */
    protected int width ;

    /** Folding configuration. */
    protected Config config ;

    /** Whether the underlying writer should be closed by the folding writer. */
    protected boolean doClose ;

    /**
     * Create a builder using the default configuration and the supplied width.
     *
     * @param width target maximum line width
     * @return a new formatter builder
     */
    public static Builder<?> builder(int width) {
        return new Builder<>(new JSONFold(width, Config.defaultConfig()));
    }

    /**
     * Create a formatter with the supplied width and configuration.
     *
     * @param width target maximum line width
     * @param config folding configuration
     */
    protected JSONFold(int width, Config config) {
        this.width = width;
        this.config = config;
    }

    /**
     * Copy constructor.
     *
     * @param other formatter to copy
     */
    public JSONFold(JSONFold other) {
        this.indent = other.indent;
        this.sortKeys = other.sortKeys;
        this.width = other.width;
        this.config = other.config;
        this.doClose = other.doClose;        
    }

    /**
     * Builder for {@link JSONFold} and subclasses.
     *
     * @param <B> concrete builder type, used for fluent subclass builders
     */
    public static class Builder<B extends Builder<B>> {
        /** Formatter instance being configured. */
        protected final JSONFold target;

        /**
         * Create a builder around an existing formatter target.
         *
         * @param target formatter instance to configure
         */
        protected Builder(JSONFold target) {
            this.target = target;
        }

        /**
         * Return this builder using the concrete builder type.
         *
         * @return this builder
         */
        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }

        /**
         * Set the pretty-print indentation level.
         *
         * @param indent indentation level, or {@code null} for serializer default
         * @return this builder
         */
        public B indent(Integer indent) {
            target.indent = indent;
            return self();
        }

        /**
         * Enable or disable key sorting.
         *
         * @param sortKeys {@code true} to sort object keys
         * @return this builder
         */
        public B sortKeys(boolean sortKeys) {
            target.sortKeys = sortKeys;
            return self();
        }

        /**
         * Set whether the folding writer should close the underlying writer.
         *
         * @param doClose {@code true} to close the underlying writer
         * @return this builder
         */
        public B doClose(boolean doClose) {
            target.doClose = doClose;
            return self();
        }

        /**
         * Build the configured formatter.
         *
         * <p>If the builder width differs from the configuration width, a copied
         * configuration with the builder width is installed.</p>
         *
         * @return configured formatter
         */
        public JSONFold build() {
            int set_width = target.width ;
            if ( set_width > 0 && set_width != target.config.width )
                target.config = new Config(target.config, set_width) ;
            return target;
        }
    }

    /**
     * Return whether the folding writer should close the underlying writer.
     *
     * @return {@code true} if the underlying writer should be closed
     */
    public boolean isDoClose() {
        return doClose;
    }

    /**
     * Return the target maximum line width.
     *
     * @return configured line width
     */
    public int getWidth() {
        return width;
    }

    /**
     * Return the folding configuration.
     *
     * @return folding configuration
     */
    public Config getConfig() {
        return config;
    }

    /**
     * Return the configured indentation level.
     *
     * @return indentation level, or {@code null}
     */
    public Integer getIndent() {
        return indent;
    }

    /**
     * Return whether key sorting is enabled.
     *
     * @return {@code true} if keys should be sorted
     */
    public boolean isSortKeys() {
        return sortKeys;
    }

    /**
     * Create a configuration builder from a base configuration and optional width.
     *
     * @param baseConfig base configuration
     * @param width optional width override
     * @return configuration builder
     */
    static private Config.Builder configBuilder(Config baseConfig, Integer width)
    {
        Config.Builder builder = baseConfig.builder() ;
        if ( width != null ) builder.width(width) ;
        return builder ;
    }

    /**
     * Create a configuration builder from a base configuration and optional width.
     *
     * @param baseConfig base configuration
     * @param width optional width override
     * @return configuration builder
     */
    static public Config.Builder config(Config baseConfig, Integer width) {
        return configBuilder(baseConfig, width) ;
    }

    /**
     * Create a configuration builder from a preset name and optional width.
     *
     * @param name preset name
     * @param width optional width override
     * @return configuration builder, or {@code null} for the {@code off} preset
     */
    static public Config.Builder config(String name, Integer width) {
        Config config = Config.preset(name) ;
        if ( config == null ) return null ;
        return configBuilder(config, width) ;
    }
 
    /**
     * Create a folding writer around another writer.
     *
     * @param base underlying writer
     * @param width target maximum line width
     * @param config folding configuration
     * @param close_fp whether closing the folding writer should close {@code base}
     * @return folding writer
     */
    static public Writer filter_stream(Writer base, int width, Config config, boolean close_fp)
    {
        JSONFoldWriter writer = new JSONFoldWriter(base, configBuilder(config, width).build(), close_fp) ;
        return writer ;
    }

    /**
     * Fold already pretty-printed JSON text.
     *
     * @param jsonText pretty-printed JSON text
     * @return folded JSON text
     * @throws IOException if writing fails
     */
    public String formatJsonText(String jsonText)
    throws IOException
    {
        try (StringWriter sw = new StringWriter() ;
            Writer out = filter_stream(sw, width, config, false) ;
            ) {
            out.write(jsonText) ;
            return sw.toString() ;
        }
    }

    /**
     * Fold already pretty-printed JSON text using an explicit width and config.
     *
     * @param jsonText pretty-printed JSON text
     * @param width target maximum line width
     * @param config folding configuration
     * @return folded JSON text
     * @throws IOException if writing fails
     */
    public static String formatJsonText(String jsonText, int width, Config config)
    throws IOException
    {
        JSONFold fmt = new JSONFold(width, config) ;
        return fmt.formatJsonText(jsonText) ;
    }

    // The complete API includes 2 additional methods, and 2 additional static helpers.
    // Those are implemented in the Modules that connect with an actual JSON encoder (Jackson, ...)


    // Java does not allow abstract static - the following must be implented in sub classes

    // abstract public Stats writeJson(Object obj, Writer writer) throws IOException ;
    // abstract public String formatJson(Object obj) throws IOException;

    // public static Stats writeJson(Object obj, Writer writer, int width, Config config, JacksonJsonFold options)
    // throws IOException

    // public static String formatJSON(Object obj, int width, Config config, JacksonJsonFold options)
    // throws IOException

}