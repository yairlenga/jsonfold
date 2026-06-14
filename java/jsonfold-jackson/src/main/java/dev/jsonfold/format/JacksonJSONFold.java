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
 * Jackson integration helpers for JSONFold.
 */
public final class JacksonJSONFold extends JSONFold {

    protected boolean gold = false ;

    public JacksonJSONFold(int width, Config config)
    {
        super(width, config) ;
    }

    public boolean isGold() {
        return gold;
    }

    public static class Builder extends JSONFold.Builder<Builder> {
        private final JacksonJSONFold target;

        private Builder(int width, Config config) {
            super(new JacksonJSONFold(width, config));
            this.target = (JacksonJSONFold) super.target;
        }

        public Builder gold(boolean gold) {
            target.gold = gold;
            return this;
        }

        @Override
        public JacksonJSONFold build() {
            super.build();
            return target;
        }
    }

    
    /**
     * Configure an existing ObjectMapper for use with JSONFold.
     *
     * This method mutates the supplied mapper and returns it.
     */
    public static ObjectMapper configure(ObjectMapper mapper) {
        mapper.setDefaultPrettyPrinter(prettyPrinter());

        return mapper;
    }

    /**
     * Configure an existing ObjectMapper for use with JSONFold.
     *
     * This method mutates the supplied mapper and returns it.
     */
    private static class GoldPrettyPrinter extends DefaultPrettyPrinter {
        GoldPrettyPrinter() {
            super();
        }

        GoldPrettyPrinter(GoldPrettyPrinter base) {
            super(base);
        }

        @Override
        public void writeObjectFieldValueSeparator(JsonGenerator g)
                throws IOException {
            g.writeRaw(": ");
        }

        @Override
        public void writeEndObject(JsonGenerator g, int nrOfEntries)
                throws IOException {

            if (nrOfEntries == 0) {
                g.writeRaw('}');
                return;
            }

            super.writeEndObject(g, nrOfEntries);
        }

        @Override
        public void writeEndArray(JsonGenerator g, int nrOfValues)
                throws IOException {

            if (nrOfValues == 0) {
                g.writeRaw(']');
                return;
            }

            super.writeEndArray(g, nrOfValues);
        }

        @Override
        public DefaultPrettyPrinter createInstance() {
            return new GoldPrettyPrinter(this);
        }
    }

    /**
     * Return a Jackson pretty printer suitable as input to JSONFold.
     */
    public static DefaultPrettyPrinter prettyPrinter(String indent) {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();

        // Jackson's default keeps arrays inline. JSONFold works better when
        // arrays are first expanded, then folded back by JSONFold's rules.
        DefaultIndenter indenter = DefaultIndenter.SYSTEM_LINEFEED_INSTANCE;
        if (indent != null)
            indenter.withIndent(indent);
        pp.indentArraysWith(indenter);
        pp.indentObjectsWith(indenter);

        return pp;
    }

    public static DefaultPrettyPrinter prettyPrinter() {
        return prettyPrinter(null);
    }

    public static DefaultPrettyPrinter prettyPrinter(int indent) {
        return prettyPrinter(" ".repeat(indent));
    }

    public static DefaultPrettyPrinter goldPettyPrinter(String indent) {
        DefaultPrettyPrinter pp = new GoldPrettyPrinter();

        // Jackson's default keeps arrays inline. JSONFold works better when
        // arrays are first expanded, then folded back by JSONFold's rules.
        pp.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        DefaultIndenter indenter = new DefaultIndenter();
        if (indent != null)
            indenter.withIndent(indent);
        pp.indentArraysWith(indenter);
        pp.indentObjectsWith(indenter);

        return pp;
    }

    public static DefaultPrettyPrinter goldPettyPrinter(int indent) {
        return goldPettyPrinter(" ".repeat(indent));
    }

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

    private static ObjectMapper DEFAULT_MAPPER = createMapper(false) ;
    private static ObjectMapper SORTED_MAPPER = createMapper(true) ;

    public Stats writeJson(Object obj, Writer writer) throws IOException
    {
        ObjectMapper mapper = sortKeys ? SORTED_MAPPER : DEFAULT_MAPPER ;
        PrettyPrinter pp = gold ? goldPettyPrinter(width) : prettyPrinter(width) ;
        try (JSONFoldWriter out = new JSONFoldWriter(writer, config, isDoClose())) {
            mapper.writer(pp).writeValue(out, obj) ;
            return out.getStats() ;
        }
    }

    public String formatJson(Object obj) 
    throws IOException
    {
        Writer sw = new StringWriter() ;
        Stats stats = writeJson(obj, sw) ;
        if ( stats == null ) throw new IOException("Failed to generate JSON string") ;
        return sw.toString();
    }

    public static Stats writeJson(Object obj, Writer writer, int width, Config config)
    throws IOException
    {
        JacksonJSONFold fmt = new JacksonJSONFold(width, config) ;
        return fmt.writeJson(obj, writer);
    }

    public static String formatJSON(Object obj, int width, Config config)
    throws IOException
    {
        JacksonJSONFold fmt = new JacksonJSONFold(width, config) ;
        return fmt.formatJson(obj);
    }

}