package dev.jsonfold.format;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Jackson integration helpers for JSONFold.
 */
public final class JacksonJsonFold {

    private JacksonJsonFold() {
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

}