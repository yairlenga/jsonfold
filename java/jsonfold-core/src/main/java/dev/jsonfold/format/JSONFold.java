package dev.jsonfold.format;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public class JSONFold {

    protected Integer indent ;
    protected boolean sortKeys ;
    protected int width ;
    protected Config config ;
    protected boolean doClose ;

    public static Builder<?> builder(int width) {
        return new Builder<>(new JSONFold(width, Config.defaultConfig()));
    }

    protected JSONFold(int width, Config config) {
        this.width = width;
        this.config = config;
    }

    public JSONFold(JSONFold other) {
        this.indent = other.indent;
        this.sortKeys = other.sortKeys;
        this.width = other.width;
        this.config = other.config;
        this.doClose = other.doClose;        
    }

    public static class Builder<B extends Builder<B>> {
        protected final JSONFold target;

        protected Builder(JSONFold target) {
            this.target = target;
        }

        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }

        public B indent(Integer indent) {
            target.indent = indent;
            return self();
        }

        public B sortKeys(boolean sortKeys) {
            target.sortKeys = sortKeys;
            return self();
        }

        public B doClose(boolean doClose) {
            target.doClose = doClose;
            return self();
        }

        public JSONFold build() {
            int set_width = target.width ;
            if ( set_width > 0 && set_width != target.config.width )
                target.config = new Config(target.config, set_width) ;
            return target;
        }
    }

    public boolean isDoClose() {
        return doClose;
    }

    public int getWidth() {
        return width;
    }

    public Config getConfig() {
        return config;
    }


    public Integer getIndent() {
        return indent;
    }

    public boolean isSortKeys() {
        return sortKeys;
    }

    static private Config.Builder configBuilder(Config baseConfig, Integer width)
    {
        Config.Builder builder = baseConfig.builder() ;
        if ( width != null ) builder.width(width) ;
        return builder ;
    }

    static public Config.Builder config(Config baseConfig, Integer width) {
        return configBuilder(baseConfig, width) ;
    }

    static public Config.Builder config(String name, Integer width) {
        Config config = Config.preset(name) ;
        if ( config == null ) return null ;
        return configBuilder(config, width) ;
    }
 
    static Writer filter_stream(Writer base, int width, Config config, boolean close_fp)
    {
        JSONFoldWriter writer = new JSONFoldWriter(base, configBuilder(config, width).build(), close_fp) ;
        return writer ;
    }

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

    public static String formatJsonText(String jsonText, int width, Config config)
    throws IOException
    {
        JSONFold fmt = new JSONFold(width, config) ;
        return fmt.formatJsonText(jsonText) ;
    }


    // Java does not allow abstract static - the following must be implented in sub classes

    // abstract public Stats writeJson(Object obj, Writer writer) throws IOException ;
    // abstract public String formatJson(Object obj) throws IOException;

    // public static Stats writeJson(Object obj, Writer writer, int width, Config config, JacksonJsonFold options)
    // throws IOException

    // public static String formatJSON(Object obj, int width, Config config, JacksonJsonFold options)
    // throws IOException

}