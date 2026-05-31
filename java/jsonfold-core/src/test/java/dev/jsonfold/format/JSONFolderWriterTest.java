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

    private static String fold(String input, JSONFold cfg) throws Exception {
        StringWriter sw = new StringWriter();

        try (var out = new JSONFoldWriter(sw, cfg)) {
            out.write(input);
        }

        return sw.toString();
    }

    @Test
    void nonePresetLeavesInputUnchanged() throws Exception {
        assertEquals(INPUT, fold(INPUT, JSONFold.none())) ;
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

        assertEquals(expected, fold(INPUT, JSONFold.pack())) ;
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

        assertEquals(expected, fold(INPUT, JSONFold.fold()));
    }

    @Test
    void joinPresetFoldsAndJoinsNestedStructures() throws Exception {
        String expected = """
            {
              "small_array": [ 1 ], "small_obj": { "a": 1 }, "med_array": [ 1, 2 ],
              "med_obj": { "a": 1, "b": 2 }, "nested_array": [ [ 1 ] ],
              "nested_obj": { "child": { "a": 1 } }
            }
            """;

        assertEquals(expected, fold(INPUT, JSONFold.join()));
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

        assertEquals(expected, fold(input, JSONFold.max().withWidth(40)));
    }

    @Test
    void finishFlushesPendingLineWithoutNewline() throws Exception {
        String input = "{\n  \"x\": 1\n}";

        String expected = """
            {
              "x": 1
            }
            """;

        assertEquals(expected, fold(input, JSONFold.none()));
    }

    @Test
    void statsAreUpdated() throws Exception {
        StringWriter sw = new StringWriter();
        JSONFoldWriter out = new JSONFoldWriter(sw, new JSONFold(80));

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