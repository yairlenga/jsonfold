/**
 * Core JSONFold formatting API.
 *
 * <p>JSONFold provides hybrid pretty/compact JSON formatting. It preserves the
 * structure of pretty-printed JSON while selectively compacting small arrays,
 * objects, and adjacent scalar values when they fit within a configured line
 * width.
 *
 * <p>The main entry point is {@link dev.jsonfold.format.JSONFold}. Supporting
 * classes include {@link dev.jsonfold.format.Config} for formatter settings and
 * {@link dev.jsonfold.format.Stats} for formatting statistics.
 *
 * <h2>Quick Start</h2>
 *
 * <p>Format an existing JSON string:
 *
 * <pre>{@code
 * JSONFold jf = JSONFold.builder(100).build();
 *
 * String folded = jf.formatJson(prettyJson);
 * }</pre>
 *
 * <h2>Streaming Example</h2>
 *
 * <p>Filter pretty-printed JSON while copying it from one stream to another:
 *
 * <pre>{@code
 * JSONFold jf = JSONFold.builder(120).build();
 *
 * try (BufferedReader in = Files.newBufferedReader(inputFile);
 *      Writer out = jf.filterStream(
 *          Files.newBufferedWriter(outputFile))) {
 *
 *     String line;
 *     while ((line = in.readLine()) != null) {
 *         out.write(line);
 *         out.write('\n');
 *     }
 *     out.flush() ;
 * }
 * }</pre> 
 * 
 * <h2>Core vs. Integration Modules</h2>
 *
 * <p>The classes in this package operate on JSON text and streams. They do not
 * serialize Java objects directly. Instead, JSONFold is designed to be used as
 * a post-processing filter on the output of an existing JSON serializer.
 *
 * <p>Applications that already produce pretty-printed JSON can use
 * {@link dev.jsonfold.format.JSONFoldWriter} directly. For object serialization,
 * use one of the integration modules such as:
 *
 * <ul>
 *   <li>jsonfold-jackson — integration with Jackson ObjectMapper</li>
 *   <li>jsonfold-gson — integration with Gson</li>
 * </ul>
 *
 * <p>These integrations combine object serialization and JSONFold formatting
 * into a single API.
 */

package dev.jsonfold.format;
