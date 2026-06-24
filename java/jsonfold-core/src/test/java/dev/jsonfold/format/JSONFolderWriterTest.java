package dev.jsonfold.format;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringWriter;

import org.junit.jupiter.api.Test;

class JSONFoldWriterTest {

    private static final String INPUT = """
        {
          "small_array": [
            1
          ],
          "small_obj": {
            "a": 1
          },
          "med_array": [
            1,
            2
          ],
          "med_obj": {
            "a": 1,
            "b": 2
          },
          "nested_array": [
            [
              1
            ]
          ],
          "nested_obj": {
            "child": {
              "a": 1
            }
          }
        }
        """;

    private static String fold(String input, Config config) throws Exception {
        StringWriter sw = new StringWriter();

        try (var out = new JSONFoldWriter(sw, config)) {
            out.write(input);
        }

        return sw.toString();
    }

    private static String fold(String input, String name) throws Exception {
      return fold(input, Config.preset(name)) ;
    }

    @Test
    void nonePresetLeavesInputUnchanged() throws Exception {
        assertEquals(INPUT, fold(INPUT, Config.PRESET_NONE)) ;
    }

    @Test
    void packPresetPacksOnlyScalarRuns() throws Exception {
        String expected = """
            {
              "small_array": [
                1
              ],
              "small_obj": {
                "a": 1
              },
              "med_array": [
                1, 2
              ],
              "med_obj": {
                "a": 1, "b": 2
              },
              "nested_array": [
                [
                  1
                ]
              ],
              "nested_obj": {
                "child": {
                  "a": 1
                }
              }
            }
            """;

        assertEquals(expected, fold(INPUT, Config.PRESET_PACK)) ;
    }

    @Test
    void foldPresetFoldsSingleContentLineContainers() throws Exception {
        String expected = """
            {
              "small_array": [ 1 ],
              "small_obj": { "a": 1 },
              "med_array": [
                1,
                2
              ],
              "med_obj": {
                "a": 1,
                "b": 2
              },
              "nested_array": [ [ 1 ] ],
              "nested_obj": { "child": { "a": 1 } }
            }
            """;

        assertEquals(expected, fold(INPUT, Config.PRESET_FOLD));
    }

    @Test
    void joinPresetFoldsAndJoinsNestedStructures() throws Exception {
        String expected = """
        {
          "small_array": [ 1 ], "small_obj": { "a": 1 }, "med_array": [ 1, 2 ],
          "med_obj": { "a": 1, "b": 2 }, "nested_array": [ [ 1 ] ], "nested_obj": { "child": { "a": 1 } }
        }
        """;

        assertEquals(expected, fold(INPUT, Config.PRESET_JOIN));
    }

    @Test
    void widthLimitPreventsFolding() throws Exception {
        String input = """
            {
              "wide_array": [
                "abcdefghijklmnopqrstuvwxyz1",
                "abcdefghijklmnopqrstuvwxyz2"
              ],
              "wide_object": {
                "abcdefghijk1": "lmnopqrstuvwxyz1",
                "abcdefghijk2": "lmnopqrstuvwxyz2"
              }
            } 
            """;

        String expected = """
            {
              "wide_array": [
                "abcdefghijklmnopqrstuvwxyz1",
                "abcdefghijklmnopqrstuvwxyz2"
              ],
              "wide_object": {
                "abcdefghijk1": "lmnopqrstuvwxyz1",
                "abcdefghijk2": "lmnopqrstuvwxyz2"
              }
            }
            """;

        assertEquals(expected, fold(input, Config.preset(Config.PRESET_MAX).builder().width(40).build()));
    }

    @Test
    void finishFlushesPendingLineWithoutNewline() throws Exception {
        String input = "{\n  \"x\": 1\n}";

        String expected = """
            {
              "x": 1
            }
            """;

        assertEquals(expected, fold(input, Config.PRESET_NONE));
    }

    @Test
    void statsAreUpdated() throws Exception {
        StringWriter sw = new StringWriter();
        JSONFoldWriter out = new JSONFoldWriter(sw, new Config(Config.defaultConfig(), 80));

        try (out) {
            out.write("""
                {
                  "x": 1
                }
                """);
        }

        Stats stats = out.getStats();

        assertTrue(stats.getBytesIn() > 0);
        assertTrue(stats.getBytesOut() > 0);
        assertTrue(stats.getLinesIn() > 0);
        assertTrue(stats.getLinesOut() > 0);
    }
}