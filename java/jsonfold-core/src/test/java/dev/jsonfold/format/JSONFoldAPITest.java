package dev.jsonfold.format;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringWriter;
import java.io.Writer;

import org.junit.jupiter.api.Test;

class JSONFoldApiTest {

    @Test
    void jsonFoldPublicApiSmokeTest() throws Exception {
        Config cfg = Config.defaultConfig();

        JSONFold fmt = JSONFold.builder(100)
                .indent(2)
                .sortKeys(true)
                .doClose(false)
                .build();

        assertEquals(100, fmt.getWidth());
        assertNotNull(fmt.getConfig());
        assertEquals(2, fmt.getIndent());
        assertTrue(fmt.isSortKeys());
        assertFalse(fmt.isDoClose());

        JSONFold copy = new JSONFold(fmt);
        assertEquals(fmt.getWidth(), copy.getWidth());
        assertEquals(fmt.getIndent(), copy.getIndent());
        assertEquals(fmt.isSortKeys(), copy.isSortKeys());
        assertEquals(fmt.isDoClose(), copy.isDoClose());

        Config.Builder cfgBuilder1 = JSONFold.config(cfg, 120);
        assertNotNull(cfgBuilder1);
        assertEquals(120, cfgBuilder1.build().getWidth());

        Config.Builder cfgBuilder2 = JSONFold.config(Config.PRESET_HIGH, 130);
        assertNotNull(cfgBuilder2);
        assertEquals(130, cfgBuilder2.build().getWidth());

        assertNull(JSONFold.config("off", 100));

        String jsonText = """
            {
              "a": [
                1,
                2,
                3
              ]
            }
            """;

        String folded1 = fmt.formatJsonText(jsonText);
        assertNotNull(folded1);
        assertTrue(folded1.contains("\"a\""));

        String folded2 = JSONFold.formatJsonText(jsonText, 100, cfg);
        assertNotNull(folded2);
        assertTrue(folded2.contains("\"a\""));

        StringWriter sw = new StringWriter();
        Writer filter = JSONFold.filter_stream(sw, 100, cfg, false);
        filter.write(jsonText);
        filter.close();

        assertFalse(sw.toString().isEmpty());
        assertTrue(sw.toString().contains("\"a\""));
    }
}