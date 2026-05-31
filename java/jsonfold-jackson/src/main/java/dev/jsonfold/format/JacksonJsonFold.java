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
    private static class JsonFoldPrettyPrinter extends DefaultPrettyPrinter {
        JsonFoldPrettyPrinter() {
            super();
        }

        JsonFoldPrettyPrinter(JsonFoldPrettyPrinter base) {
            super(base);
        }

        @Override
        public void writeObjectFieldValueSeparator(JsonGenerator g)
                throws IOException {
            g.writeRaw(": ");
        }

        @Override
        public DefaultPrettyPrinter createInstance() {
            return new JsonFoldPrettyPrinter(this);
        }
    }

    /**
     * Return a Jackson pretty printer suitable as input to JSONFold.
     */
    public static DefaultPrettyPrinter prettyPrinter(String indent) {
        DefaultPrettyPrinter pp = new JsonFoldPrettyPrinter();

        // Jackson's default keeps arrays inline. JSONFold works better when
        // arrays are first expanded, then folded back by JSONFold's rules.
        pp.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        DefaultIndenter indenter = new DefaultIndenter();
        if ( indent != null) indenter.withIndent(indent);
        pp.indentArraysWith(indenter);
        pp.indentObjectsWith(indenter);  

        return pp;
    }

    public static DefaultPrettyPrinter prettyPrinter() {
        return prettyPrinter(null) ;
    }

    public static DefaultPrettyPrinter prettyPrinter(int indent) {
        return prettyPrinter(" ".repeat(indent)) ;
    }
   
}