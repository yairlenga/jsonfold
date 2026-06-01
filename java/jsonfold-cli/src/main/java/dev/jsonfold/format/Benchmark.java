package dev.jsonfold.format;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Benchmark for jsonfold Java, intentionally matching benchmark.py.
 *
 * Usage from jsonfold-cli after wiring into the build:
 *
 *   java ... dev.jsonfold.format.Benchmark
 *   java ... dev.jsonfold.format.Benchmark 1000
 *   java ... dev.jsonfold.format.Benchmark jsonfold.dump.max 1000
 *   java ... dev.jsonfold.format.Benchmark 1000 jsonfold.dump.max jsonfold.dumps.max 10000
 *   java ... dev.jsonfold.format.Benchmark jsonfold.dump.max - 1000
 *
 * Arguments are processed like the Python benchmark:
 *   - integer: run current filter list at that row count
 *   - name: add test name to the active filter list
 *   - "-": clear active filter list
 */
public final class Benchmark {
    private static final int REPEATS = 3;

    private static final List<String> DEFAULT_TESTS = List.of(
            "base.dump.plain",
            "base.dump.pretty",
            "jsonfold.dump.off",
            "jsonfold.dump.none",
            "jsonfold.dump.default",
            "jsonfold.dump.low",
            "jsonfold.dump.med",
            "jsonfold.dump.high",
            "jsonfold.dump.max",
            "jsonfold.dump.pack",
            "jsonfold.dump.fold",
            "jsonfold.dump.join",
            "base.dumps.plain",
            "base.dumps.pretty",
            "jsonfold.dumps.none",
            "jsonfold.dumps.default",
            "jsonfold.dumps.high",
            "jsonfold.dumps.max"
    );

    static private ObjectMapper createMapper() {
        ObjectMapper mapper = JacksonJsonFold.configure(new ObjectMapper())
            .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        return mapper ;
    }

    private static final ObjectMapper MAPPER = createMapper() ;
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private Benchmark() {
    }

    public static void main(String[] args) throws Exception {
        List<String> filter = new ArrayList<>();
        Integer lastSize = null;
        boolean dump = false ;
        List<Map<String, Object>> results = new ArrayList<>();

        for (String arg : args) {
            if (arg.equals("-")) {
                filter.clear();
                continue;
            }

            if ( arg.equals("--show")) {
                dump = true ;
                continue ;
            }

            Integer rows = parseIntOrNull(arg);
            if (rows == null) {
                filter.add(arg);
                continue;
            }

            lastSize = rows;
            if ( dump ) {
                dump(System.out, rows, filter);
                continue ;
            }
            results.addAll(runOneSize(rows, filter));
        }

        if (lastSize == null) {
            if ( dump ) {
                dump(System.out, 1_000, filter);
            } else {
                results.addAll(runOneSize(1_000, filter));
            }
        }

        printTable(results);
    }


    private static void dump(OutputStream out, int rows, List<String> tests) throws Exception {
        ObjectNode data = makeData(rows);
        List<String> selected = tests.isEmpty() ? DEFAULT_TESTS : List.copyOf(tests);

        for (String name : selected) {
            System.err.print(name + " (" + rows + ")\n");
            writeString(out, "# Case:" + name + "\n");
            runCase(name, data, out) ;
            writeString(out, "\n---\n") ;
        }
    }

    private static List<Map<String, Object>> runOneSize(int rows, List<String> tests) throws Exception {
        ObjectNode data = makeData(rows);
        List<String> selected = tests.isEmpty() ? DEFAULT_TESTS : List.copyOf(tests);
        List<Map<String, Object>> results = new ArrayList<>();

        for (String name : selected) {
            System.err.print(name + " (" + rows + ")... ");
            System.err.flush();

            TimedResult speed = timeOne(name, data);
            double peakKb = memoryOne(name, data);

            System.err.println(Math.round(speed.bestMillis) + " ms");

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rows", rows);
            row.put("name", name);
            row.put("time(ms)", round1(speed.bestMillis));
            row.put("CPU(ms)", round1(speed.cpuMillis));
            row.put("ttfb(ms)", speed.ttfbMillis < 0 ? "" : round1(speed.ttfbMillis));
            row.put("out(kb)", round1(speed.bytes / 1024.0));
            row.put("writes", speed.writes);
            row.put("peak(kb)", round1(peakKb));
            results.add(row);
        }

        return results;
    }

    private static ObjectNode makeData(int rows) {
        ObjectNode root = MAPPER.createObjectNode();

        ObjectNode meta = root.putObject("meta");
        meta.put("version", 1);
        meta.put("ok", true);
        meta.put("name", "jsonfold benchmark");

        ArrayNode longIds = root.putArray("long_ids");
        for (int i = 0; i < 100; i++) {
            longIds.add(i);
        }

        ObjectNode longObj = root.putObject("long_obj");
        for (int i = 0; i < 50; i++) {
            longObj.put("k" + i, i);
        }

        ArrayNode rowArray = root.putArray("rows");
        for (int i = 0; i < rows; i++) {
            ObjectNode r = rowArray.addObject();
            r.put("id", i);
            r.put("name", "name_" + i);
            r.put("active", i % 3 == 0);
            r.put("score", i * 1.25);

            ArrayNode tags = r.putArray("tags");
            tags.add("alpha");
            tags.add("beta");
            tags.add("gamma");
            tags.add("delta");

            ObjectNode pos = r.putObject("pos");
            pos.put("x", i);
            pos.put("y", i + 1);
            pos.put("z", i + 2);

            ArrayNode values = r.putArray("values");
            for (int j = 0; j < 5; j++) {
                values.add(i + j);
            }
        }

        return root;
    }

    private static TimedResult timeOne(String name, ObjectNode data) throws Exception {
        TimedResult best = null;

        for (int i = 0; i < REPEATS; i++) {
            gcQuietly();

            NullOutput out = new NullOutput();
            long t0 = System.nanoTime();
            long c0 = cpuNanos();
            runCase(name, data, out);
            long c1 = cpuNanos();
            long t1 = System.nanoTime();

            TimedResult current = new TimedResult();
            current.bestMillis = (t1 - t0) / 1_000_000.0;
            current.cpuMillis = c0 >= 0 && c1 >= 0 ? (c1 - c0) / 1_000_000.0 : current.bestMillis;
            current.ttfbMillis = out.firstWriteNanos < 0 ? -1 : (out.firstWriteNanos - t0) / 1_000_000.0;
            current.bytes = out.bytes;
            current.writes = out.writes;

            if (best == null || current.bestMillis < best.bestMillis) {
                best = current;
            }
        }

        return best;
    }

    /**
     * Rough Java equivalent of tracemalloc peak: run once, sample heap delta.
     * This is not exact, but keeps the table column comparable and useful.
     */
    private static double memoryOne(String name, ObjectNode data) throws Exception {
        gcQuietly();
        Runtime rt = Runtime.getRuntime();
        long before = usedHeap(rt);
        NullOutput out = new NullOutput();
        runCase(name, data, out);
        long after = usedHeap(rt);
        return Math.max(0, after - before) / 1024.0;
    }

    private static void runCase(String name, ObjectNode data, OutputStream out) throws Exception {
        switch (name) {
            case "base.dumps.plain" -> writeString(out, MAPPER.writeValueAsString(data)) ;
            case "base.dumps.pretty" -> writeString(out, prettyMapper().writeValueAsString(data));
            case "base.dump.plain" -> MAPPER.writeValue(out, data) ;
            case "base.dump.pretty" -> prettyMapper().writeValue(out, data);
            default -> runJsonFoldCase(name, data, out);
        }
    }

    private static void runJsonFoldCase(String name, ObjectNode data, OutputStream out) throws Exception {
        String[] parts = name.split("\\.");
        if (parts.length != 3 || !parts[0].equals("jsonfold")) {
            throw new IllegalArgumentException("unknown benchmark case: " + name);
        }

        String func = parts[1];
        String compact = parts[2];

        switch (func) {
            case "dump" -> writeJsonFoldDump(data, out, compact);
            case "dumps" -> writeString(out, jsonFoldString(data, compact));
            default -> throw new IllegalArgumentException("unknown benchmark case: " + name);
        }
    }

    private static void writeJsonFoldDump(
            ObjectNode data,
            OutputStream out,
            String compact) throws Exception {
        var cfg = JSONFold.preset(compact);

        var base = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        var folded = new JSONFoldWriter(base, cfg);

        MAPPER.writer(prettyPrinter("  "))
                .writeValue(folded, data);

        folded.flush();
    }

    private static String jsonFoldString(ObjectNode data, String compact) throws Exception {
        var cfg = JSONFold.preset(compact);

        StringWriter sw = new StringWriter();
        var folded = new JSONFoldWriter(sw, cfg);

        MAPPER.writer(prettyPrinter("  "))
                .writeValue(folded, data);

        folded.flush();
        return sw.toString();
    }

    private static ObjectMapper prettyMapper() {
        return MAPPER.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Adapter point for the current Java implementation.
     *
     * If your public API already exposes JacksonJsonFold.prettyPrinter(String),
     * this method should compile unchanged. If the method has a different name,
     * keep the benchmark unchanged and modify only this adapter.
     */
    private static PrettyPrinter prettyPrinter(String compact) {
        return JacksonJsonFold.goldPettyPrinter(compact);
    }

    private static void writeString(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
    }

    private static long cpuNanos() {
        if (!THREADS.isCurrentThreadCpuTimeSupported()) {
            return -1;
        }
        if (!THREADS.isThreadCpuTimeEnabled()) {
            try {
                THREADS.setThreadCpuTimeEnabled(true);
            } catch (UnsupportedOperationException ignored) {
                return -1;
            }
        }
        return THREADS.getCurrentThreadCpuTime();
    }

    private static long usedHeap(Runtime rt) {
        return rt.totalMemory() - rt.freeMemory();
    }

    private static void gcQuietly() {
        System.gc();
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Integer parseIntOrNull(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double round1(double x) {
        return Math.round(x * 10.0) / 10.0;
    }

    private static void printTable(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return;
        }

        List<String> cols = new ArrayList<>(rows.get(0).keySet());
        Map<String, Integer> widths = new LinkedHashMap<>();
        Map<String, Boolean> numeric = new LinkedHashMap<>();

        for (String c : cols) {
            int w = c.length();
            boolean isNumeric = true;
            for (Map<String, Object> row : rows) {
                Object v = row.get(c);
                String s = String.valueOf(v);
                w = Math.max(w, s.length());
                if (!(v instanceof Number) && !s.isEmpty()) {
                    isNumeric = false;
                }
            }
            widths.put(c, w);
            numeric.put(c, isNumeric);
        }

        String line = tableLine(cols, widths);
        System.out.println(line);
        System.out.println(tableRow(cols, widths, numeric, cols));
        System.out.println(line);
        for (Map<String, Object> row : rows) {
            List<Object> vals = new ArrayList<>();
            for (String c : cols) {
                vals.add(row.get(c));
            }
            System.out.println(tableRow(cols, widths, numeric, vals));
        }
        System.out.println(line);
    }

    private static String tableLine(List<String> cols, Map<String, Integer> widths) {
        StringBuilder sb = new StringBuilder("+");
        for (String c : cols) {
            sb.append("-".repeat(widths.get(c) + 2)).append('+');
        }
        return sb.toString();
    }

    private static String tableRow(List<String> cols, Map<String, Integer> widths,
                                   Map<String, Boolean> numeric, List<?> values) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < cols.size(); i++) {
            String c = cols.get(i);
            String s = String.valueOf(values.get(i));
            int width = widths.get(c);
            if (numeric.get(c)) {
                sb.append(' ').append(" ".repeat(width - s.length())).append(s).append(' ');
            } else {
                sb.append(' ').append(s).append(" ".repeat(width - s.length())).append(' ');
            }
            sb.append('|');
        }
        return sb.toString();
    }

    private static final class TimedResult {
        double bestMillis;
        double cpuMillis;
        double ttfbMillis;
        long bytes;
        long writes;
    }

    private static final class NullOutput extends OutputStream {
        long firstWriteNanos = -1;
        long bytes;
        long writes;

        @Override
        public void write(int b) {
            if (firstWriteNanos < 0) {
                firstWriteNanos = System.nanoTime();
            }
            bytes++;
            writes++;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            if (len <= 0) {
                return;
            }
            if (firstWriteNanos < 0) {
                firstWriteNanos = System.nanoTime();
            }
            bytes += len;
            writes++;
        }
    }
}
