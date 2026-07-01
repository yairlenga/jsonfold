package dev.jsonfold.format;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;

class JacksonApiTest {

    private static Map<String, Object> sampleData() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", 1);
        root.put("name", "alpha");
        root.put("items", java.util.List.of(1, 2, 3));
        return root;
    }

    @Test
    void configPublicApiSmokeTest() {
        Config cfg = new Config();
        assertNotNull(cfg);

        Config copy = new Config(cfg);
        assertNotNull(copy);

        Config withWidth = new Config(cfg, 120);
        assertEquals(120, withWidth.getWidth());

        Config preset = Config.preset(Config.PRESET_DEFAULT);
        assertNotNull(preset);

        assertNull(Config.preset("off"));

        Config custom = Config.preset(Config.PRESET_HIGH)
                .builder()
                .width(140)
                .packArrayItems(10)
                .packObjItems(5)
                .packNesting(2)
                .foldArrayItems(10)
                .foldObjItems(5)
                .foldNesting(2)
                .joinArrayItems(10)
                .joinObjItems(5)
                .joinNesting(2)
                .build();

        assertEquals(140, custom.getWidth());
        assertEquals(10, custom.getPackArrayItems());
        assertEquals(5, custom.getPackObjItems());
        assertEquals(2, custom.getPackNesting());
        assertEquals(10, custom.getFoldArrayItems());
        assertEquals(5, custom.getFoldObjItems());
        assertEquals(2, custom.getFoldNesting());
        assertEquals(10, custom.getJoinArrayItems());
        assertEquals(5, custom.getJoinObjItems());
        assertEquals(2, custom.getJoinNesting());

        assertNotNull(Config.preset(Config.PRESET_NONE));
        assertNotNull(Config.preset(Config.PRESET_LOW));
        assertNotNull(Config.preset(Config.PRESET_MED));
        assertNotNull(Config.preset(Config.PRESET_HIGH));
        assertNotNull(Config.preset(Config.PRESET_MAX));
        assertNotNull(Config.defaultConfig());

        assertNotNull(custom.toString());
    }

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

        Config.Builder cfgBuilder1 = JSONFold.configBuilder(cfg, 120);
        assertNotNull(cfgBuilder1);
        assertEquals(120, cfgBuilder1.build().getWidth());

        Config.Builder cfgBuilder2 = JSONFold.configBuilder(Config.PRESET_HIGH, 130);
        assertNotNull(cfgBuilder2);
        assertEquals(130, cfgBuilder2.build().getWidth());

        assertNull(JSONFold.configBuilder("off", 100));

        String jsonText = """
            {
              "a": [
                1,
                2,
                3
              ]
            }
            """;

        String folded1 = fmt.fold(jsonText);
        assertNotNull(folded1);
        assertTrue(folded1.contains("\"a\""));

        String folded2 = JSONFold.foldText(jsonText, 100, cfg);
        assertNotNull(folded2);

        StringWriter sw = new StringWriter();
        Writer filter = JSONFold.create_writer(sw, 100, cfg, false);
        filter.write(jsonText);
        filter.close();

        assertFalse(sw.toString().isEmpty());
    }

    @Test
    void jacksonJsonFoldPublicApiSmokeTest() throws Exception {
        Map<String, Object> data = sampleData();
        Config cfg = Config.defaultConfig();

        JacksonJSONFold fmt = JacksonJSONFold.builder(100)
                .indent(2)
                .sortKeys(true)
                .doClose(false)
                .gold(false)
                .build();

        assertEquals(100, fmt.getWidth());
        assertNotNull(fmt.getConfig());
        assertEquals(2, fmt.getIndent());
        assertTrue(fmt.isSortKeys());
        assertFalse(fmt.isDoClose());
        assertFalse(fmt.isGold());

        String json = fmt.format(data);
        assertNotNull(json);
        assertTrue(json.contains("\"id\""));

        StringWriter sw = new StringWriter();
        Stats stats = fmt.format_to(data, sw);
        assertNotNull(stats);
        assertFalse(sw.toString().isEmpty());
        assertTrue(stats.getBytesIn() > 0);
        assertTrue(stats.getBytesOut() > 0);

        StringWriter sw2 = new StringWriter();
        Stats stats2 = JacksonJSONFold.writeJson(data, sw2, 100, cfg);
        assertNotNull(stats2);
        assertFalse(sw2.toString().isEmpty());

        ObjectMapper mapper = JacksonJSONFold.configureMapper(new ObjectMapper());
        assertNotNull(mapper);

        DefaultPrettyPrinter pp2 = JacksonJSONFold.prettyPrinter(false, 2);
        DefaultPrettyPrinter pp3 = JacksonJSONFold.prettyPrinter(false, 4)  ;

        assertNotNull(pp2);
        assertNotNull(pp3);

        DefaultPrettyPrinter gold1 = JacksonJSONFold.prettyPrinter() ;
        DefaultPrettyPrinter gold2 = JacksonJSONFold.prettyPrinter(true, 2) ;
        DefaultPrettyPrinter gold3 = JacksonJSONFold.prettyPrinter(true, 4) ;

        assertNotNull(gold1);
        assertNotNull(gold2);
        assertNotNull(gold3);

        JacksonJSONFold goldFmt = JacksonJSONFold.builder(100)
                .gold(true)
                .build();

        assertTrue(goldFmt.isGold());
        assertNotNull(goldFmt.format(data));
  
    }

              // Check Static Methods
    @Test
    @SuppressWarnings("static-access")
    void staticApiTest() throws Exception {
        Map<String, Object> data = sampleData();

        JacksonJSONFold static_fmt = new JacksonJSONFold(100, null) ;
        Config cfg = Config.defaultConfig();

        StringWriter sw2 = new StringWriter();
        Stats stats2 = static_fmt.writeJson(data, sw2, 100, cfg);
        assertNotNull(stats2);
        assertFalse(sw2.toString().isEmpty());

        String json2 = static_fmt.formatJson(data, 100, cfg);
        assertNotNull(json2);
        assertTrue(json2.contains("\"id\""));
    }
}