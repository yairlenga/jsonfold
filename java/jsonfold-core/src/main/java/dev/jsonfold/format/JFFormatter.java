package dev.jsonfold.format;

import java.io.IOException;
import java.io.Writer;

/**
 * integration helpers for JSONFold.
 * subclasses of JSONFold (Jackson, GSON, ...) must implement
 * 
 * @hidden
 */


public interface JFFormatter {

    public String fold(String jsonText) throws IOException;
    public Stats fold_to(String jsonText, Writer writer) throws IOException;
    public String format(Object obj) throws IOException;
    public Stats format_to(Object obj, Writer writer) throws IOException ;

    // Static methods expected:
    // public static Writer createWriter(Writer base, int width, Config config, boolean close_fp);
    // public static Stats writeJson(Object obj, Writer writer, int width, Config config) throws IOException ;
    // public static Config.Builder config(Config baseConfig, Integer width) ;
    // public static Config.Builder config(String name, Integer width) ;
    // public static String formatJsonText(String jsonText, int width, Config config)

}