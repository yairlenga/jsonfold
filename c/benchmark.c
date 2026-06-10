/* benchmark.c - C benchmark matching benchmark.py test cases as closely as practical.
 *
 * Build example:
 *   cc -O2 -Wall -Wextra -o benchmark benchmark.c jsonfold.c
 *
 * Usage:
 *   ./benchmark
 *   ./benchmark 1000
 *   ./benchmark jsonfold.dump.default 1000
 *   ./benchmark --show 3
 *
 * Notes:
 *   - Uses a small hand-written JSON emitter for the exact benchmark.py data.
 *   - base.dumps.* builds one big string, then writes once to NullWriter.
 *   - base.dump.* streams directly to NullWriter.
 *   - jsonfold.dump.* streams pretty JSON through jsonfold_writer.
 *   - jsonfold.dumps.* builds pretty JSON, folds to a string, then writes once.
 *   - peak(kb) is a rough RSS delta / current approximation, not tracemalloc-equivalent.
 */

#include <ctype.h>
#include <errno.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#if defined(__unix__) || defined(__APPLE__)
#include <sys/resource.h>
#include <unistd.h>
#endif

#include "jsonfold.h"

#define REPEATS 3

/* ----------------------------- time helpers ----------------------------- */

static double now_wall(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double)ts.tv_sec + (double)ts.tv_nsec / 1000000000.0;
}

static double now_cpu(void) {
    return (double)clock() / (double)CLOCKS_PER_SEC;
}

static double round1(double x) {
    return (double)((long long)(x * 10.0 + 0.5)) / 10.0;
}

static long rss_kb(void) {
#if defined(__linux__)
    FILE *fp = fopen("/proc/self/statm", "r");
    long pages = 0;
    if (!fp) return 0;
    if (fscanf(fp, "%*s %ld", &pages) != 1) pages = 0;
    fclose(fp);
    long page_kb = sysconf(_SC_PAGESIZE) / 1024;
    return pages * page_kb;
#elif defined(__APPLE__)
    struct rusage ru;
    if (getrusage(RUSAGE_SELF, &ru) != 0) return 0;
    return (long)(ru.ru_maxrss / 1024);
#else
    return 0;
#endif
}

/* ------------------------------ NullWriter ------------------------------- */

typedef struct NullWriter {
    double t0;
    double first_write;
    size_t bytes;
    size_t writes;
} NullWriter;

static void null_init(NullWriter *w, double t0) {
    w->t0 = t0;
    w->first_write = -1.0;
    w->bytes = 0;
    w->writes = 0;
}

static ptrdiff_t null_write_cb(void *ctx, const char *buf, size_t len) {
    (void)buf;
    NullWriter *w = (NullWriter *)ctx;
    if (w->first_write < 0.0) w->first_write = now_wall();
    w->bytes += len;
    w->writes++;
    return (ptrdiff_t)len;
}

static double null_ttfb_ms(const NullWriter *w) {
    if (w->first_write < 0.0) return -1.0;
    return round1((w->first_write - w->t0) * 1000.0);
}

/* ------------------------------ String sink ------------------------------ */

typedef struct StrBuf {
    char *v;
    size_t n;
    size_t cap;
    size_t writes;
} StrBuf;

static void sb_init(StrBuf *sb) {
    sb->v = NULL;
    sb->n = 0;
    sb->cap = 0;
    sb->writes = 0;
}

static void sb_free(StrBuf *sb) {
    free(sb->v);
    sb->v = NULL;
    sb->n = sb->cap = sb->writes = 0;
}

static bool sb_reserve(StrBuf *sb, size_t add) {
    if (sb->n + add + 1 <= sb->cap) return true;
    size_t nc = sb->cap ? sb->cap * 2 : 4096;
    while (nc < sb->n + add + 1) nc *= 2;
    char *p = (char *)realloc(sb->v, nc);
    if (!p) return false;
    sb->v = p;
    sb->cap = nc;
    return true;
}

static ptrdiff_t sb_write_cb(void *ctx, const char *buf, size_t len) {
    StrBuf *sb = (StrBuf *)ctx;
    if (!sb_reserve(sb, len)) return -1;
    memcpy(sb->v + sb->n, buf, len);
    sb->n += len;
    sb->v[sb->n] = 0;
    sb->writes++;
    return (ptrdiff_t)len;
}

/* ------------------------------ Emit target ------------------------------ */

typedef ptrdiff_t (*WriteFn)(void *, const char *, size_t);

typedef struct Out {
    WriteFn write;
    void *ctx;
} Out;


static ptrdiff_t jf_write_cb(void *ctx, const char *buf, size_t len) {
    return jsonfold_write((JFWriter)ctx, buf, len);
}

static void out_s(Out *out, const char *s) {
    out->write(out->ctx, s, strlen(s));
}

static void out_n(Out *out, long long n) {
    char buf[64];
    int len = snprintf(buf, sizeof buf, "%lld", n);
    out->write(out->ctx, buf, (size_t)len);
}

static void out_score(Out *out, int i) {
    /* Python json emits 0.0, 1.25, 2.5, 3.75, ... */
    char buf[64];
    double v = (double)i * 1.25;
    int len;
    if ((i % 4) == 0) len = snprintf(buf, sizeof buf, "%.1f", v);
    else if ((i % 2) == 0) len = snprintf(buf, sizeof buf, "%.1f", v);
    else len = snprintf(buf, sizeof buf, "%.2f", v);
    out->write(out->ctx, buf, (size_t)len);
}

static void indent(Out *out, int n) {
    static const char spaces[] = "                                ";
    while (n > 0) {
        int k = n > 32 ? 32 : n;
        out->write(out->ctx, spaces, (size_t)k);
        n -= k;
    }
}

static void nl_indent(Out *out, int n) {
    out_s(out, "\n");
    indent(out, n);
}

/* ------------------------- Exact benchmark data -------------------------- */

static void emit_plain(Out *out, int rows) {
    out_s(out, "{");

    out_s(out, "\"meta\":{");
    out_s(out, "\"version\":1,\"ok\":true,\"name\":\"jsonfold benchmark\"}");

    out_s(out, ",\"long_ids\":[");
    for (int i = 0; i < 100; i++) {
        if (i) out_s(out, ",");
        out_n(out, i);
    }
    out_s(out, "]");

    out_s(out, ",\"long_obj\":{");
    for (int i = 0; i < 50; i++) {
        if (i) out_s(out, ",");
        out_s(out, "\"k"); out_n(out, i); out_s(out, "\":"); out_n(out, i);
    }
    out_s(out, "}");

    out_s(out, ",\"rows\":[");
    for (int i = 0; i < rows; i++) {
        if (i) out_s(out, ",");
        out_s(out, "{");
        out_s(out, "\"id\":"); out_n(out, i); out_s(out, ",");
        out_s(out, "\"name\":\"name_"); out_n(out, i); out_s(out, "\",");
        out_s(out, "\"active\":"); out_s(out, (i % 3 == 0) ? "true," : "false,");
        out_s(out, "\"score\":"); out_score(out, i); out_s(out, ",");
        out_s(out, "\"tags\":[\"alpha\",\"beta\",\"gamma\",\"delta\"],");
        out_s(out, "\"pos\":{");
        out_s(out, "\"x\":"); out_n(out, i); out_s(out, ",\"y\":"); out_n(out, i + 1); out_s(out, ",\"z\":"); out_n(out, i + 2); out_s(out, "},");
        out_s(out, "\"values\":[");
        for (int j = 0; j < 5; j++) { if (j) out_s(out, ","); out_n(out, i + j); }
        out_s(out, "],");
        out_s(out, "\"pairs\":[[[");
        out_n(out, i); out_s(out, ","); out_n(out, i + 1); out_s(out, ",[");
        out_n(out, i + 2); out_s(out, ","); out_n(out, i + 3); out_s(out, "],[");
        out_n(out, i + 4); out_s(out, ","); out_n(out, i + 5); out_s(out, "]]]]");
        out_s(out, "}");
    }
    out_s(out, "]}");
}

static void emit_pretty_array_ints(Out *out, int base, int count, int ind) {
    out_s(out, "[");
    for (int i = 0; i < count; i++) {
        nl_indent(out, ind + 2);
        out_n(out, base + i);
        if (i + 1 < count) out_s(out, ",");
    }
    nl_indent(out, ind);
    out_s(out, "]");
}

static void emit_pretty_pairs(Out *out, int i, int ind) {
    out_s(out, "[");
    nl_indent(out, ind + 2);
    out_s(out, "[");
    nl_indent(out, ind + 4); out_n(out, i); out_s(out, ",");
    nl_indent(out, ind + 4); out_n(out, i + 1); out_s(out, ",");
    nl_indent(out, ind + 4); emit_pretty_array_ints(out, i + 2, 2, ind + 4); out_s(out, ",");
    nl_indent(out, ind + 4); emit_pretty_array_ints(out, i + 4, 2, ind + 4);
    nl_indent(out, ind + 2);
    out_s(out, "]");
    nl_indent(out, ind);
    out_s(out, "]");
}

static void emit_pretty_row(Out *out, int i, int ind) {
    out_s(out, "{");
    nl_indent(out, ind + 2); out_s(out, "\"id\": "); out_n(out, i); out_s(out, ",");
    nl_indent(out, ind + 2); out_s(out, "\"name\": \"name_"); out_n(out, i); out_s(out, "\",");
    nl_indent(out, ind + 2); out_s(out, "\"active\": "); out_s(out, (i % 3 == 0) ? "true," : "false,");
    nl_indent(out, ind + 2); out_s(out, "\"score\": "); out_score(out, i); out_s(out, ",");

    nl_indent(out, ind + 2); out_s(out, "\"tags\": [");
    const char *tags[] = {"alpha", "beta", "gamma", "delta"};
    for (int j = 0; j < 4; j++) {
        nl_indent(out, ind + 4); out_s(out, "\""); out_s(out, tags[j]); out_s(out, "\"");
        if (j + 1 < 4) out_s(out, ",");
    }
    nl_indent(out, ind + 2); out_s(out, "],");

    nl_indent(out, ind + 2); out_s(out, "\"pos\": {");
    nl_indent(out, ind + 4); out_s(out, "\"x\": "); out_n(out, i); out_s(out, ",");
    nl_indent(out, ind + 4); out_s(out, "\"y\": "); out_n(out, i + 1); out_s(out, ",");
    nl_indent(out, ind + 4); out_s(out, "\"z\": "); out_n(out, i + 2);
    nl_indent(out, ind + 2); out_s(out, "},");

    nl_indent(out, ind + 2); out_s(out, "\"values\": "); emit_pretty_array_ints(out, i, 5, ind + 2); out_s(out, ",");
    nl_indent(out, ind + 2); out_s(out, "\"pairs\": "); emit_pretty_pairs(out, i, ind + 2);
    nl_indent(out, ind); out_s(out, "}");
}

static void emit_pretty(Out *out, int rows) {
    out_s(out, "{");
    nl_indent(out, 2); out_s(out, "\"meta\": {");
    nl_indent(out, 4); out_s(out, "\"version\": 1,");
    nl_indent(out, 4); out_s(out, "\"ok\": true,");
    nl_indent(out, 4); out_s(out, "\"name\": \"jsonfold benchmark\"");
    nl_indent(out, 2); out_s(out, "},");

    nl_indent(out, 2); out_s(out, "\"long_ids\": "); emit_pretty_array_ints(out, 0, 100, 2); out_s(out, ",");

    nl_indent(out, 2); out_s(out, "\"long_obj\": {");
    for (int i = 0; i < 50; i++) {
        nl_indent(out, 4); out_s(out, "\"k"); out_n(out, i); out_s(out, "\": "); out_n(out, i);
        if (i + 1 < 50) out_s(out, ",");
    }
    nl_indent(out, 2); out_s(out, "},");

    nl_indent(out, 2); out_s(out, "\"rows\": [");
    for (int i = 0; i < rows; i++) {
        nl_indent(out, 4); emit_pretty_row(out, i, 4);
        if (i + 1 < rows) out_s(out, ",");
    }
    nl_indent(out, 2); out_s(out, "]");
    nl_indent(out, 0); out_s(out, "}");
}

/* ------------------------------ run cases -------------------------------- */

static const char *default_tests[] = {
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
    "jsonfold.dumps.max",
    NULL
};

typedef struct CaseResult {
    int rows;
    const char *name;
    double time_ms;
    double cpu_ms;
    double ttfb_ms;
    double out_kb;
    size_t writes;
    double peak_kb;
} CaseResult;

static bool is_jsonfold_name(const char *name, char *func, size_t func_sz, char *compact, size_t compact_sz) {
    char kind[64] = {0};
    return sscanf(name, "%63[^.].%63[^.].%63s", kind, func, compact) == 3 &&
           strcmp(kind, "jsonfold") == 0 && func[0] && compact[0] &&
           strlen(func) < func_sz && strlen(compact) < compact_sz;
}

static NullWriter run_case_once(const char *name, int rows, double t0) {
    NullWriter nw;
    null_init(&nw, t0);

    if (strcmp(name, "base.dump.plain") == 0) {
        Out out = { null_write_cb, &nw };
        emit_plain(&out, rows);
        return nw;
    }

    if (strcmp(name, "base.dump.pretty") == 0) {
        Out out = { null_write_cb, &nw };
        emit_pretty(&out, rows);
        return nw;
    }

    if (strcmp(name, "base.dumps.plain") == 0 || strcmp(name, "base.dumps.pretty") == 0) {
        StrBuf sb; sb_init(&sb);
        Out out = { sb_write_cb, &sb };
        if (strstr(name, ".plain")) emit_plain(&out, rows);
        else emit_pretty(&out, rows);
        null_write_cb(&nw, sb.v ? sb.v : "", sb.n);
        sb_free(&sb);
        return nw;
    }

    char func[64], compact[64];
    if (is_jsonfold_name(name, func, sizeof func, compact, sizeof compact)) {
        const struct jsonfold_config *cfg = jsonfold_config_preset(compact);
        if (!cfg) {
            fprintf(stderr, "unknown compact preset: %s\n", compact);
            return nw;
        }

        if (strcmp(func, "dump") == 0) {
            JFWriter jf = jsonfold_create(null_write_cb, &nw, -1, cfg);
            Out out = { jf_write_cb, jf };
            emit_pretty(&out, rows);
            jsonfold_write(jf, "\n", 1);
            jsonfold_finish(jf);
            jsonfold_destroy(jf);
            return nw;
        }

        if (strcmp(func, "dumps") == 0) {
            StrBuf pretty; sb_init(&pretty);
            Out pout = { sb_write_cb, &pretty };
            emit_pretty(&pout, rows);
            sb_write_cb(&pretty, "\n", 1);

            StrBuf folded; sb_init(&folded);
            JFWriter jf = jsonfold_create(sb_write_cb, &folded, -1, cfg);
            jsonfold_write(jf, pretty.v ? pretty.v : "", pretty.n);
            jsonfold_finish(jf);
            jsonfold_destroy(jf);

            null_write_cb(&nw, folded.v ? folded.v : "", folded.n);
            sb_free(&folded);
            sb_free(&pretty);
            return nw;
        }
    }

    fprintf(stderr, "unknown case: %s\n", name);
    return nw;
}

static CaseResult time_one(const char *name, int rows) {
    CaseResult best = {0};
    double best_dt = 0.0;

    for (int r = 0; r < REPEATS; r++) {
        double t0 = now_wall();
        double p0 = now_cpu();
        NullWriter nw = run_case_once(name, rows, t0);
        double p1 = now_cpu();
        double t1 = now_wall();
        double dt = t1 - t0;

        CaseResult cr = {
            .rows = rows,
            .name = name,
            .time_ms = round1(dt * 1000.0),
            .cpu_ms = round1((p1 - p0) * 1000.0),
            .ttfb_ms = null_ttfb_ms(&nw),
            .out_kb = round1((double)nw.bytes / 1024.0),
            .writes = nw.writes,
            .peak_kb = 0.0,
        };

        if (r == 0 || dt < best_dt) {
            best = cr;
            best_dt = dt;
        }
    }

    return best;
}

static double memory_one(const char *name, int rows) {
    long before = rss_kb();
    double t0 = now_wall();
    (void)run_case_once(name, rows, t0);
    long after = rss_kb();
    long d = after - before;
    if (d < 0) d = 0;
    return round1((double)d);
}

/* ------------------------------- table ----------------------------------- */

static int digits_ll(long long v) {
    char b[64];
    return snprintf(b, sizeof b, "%lld", v);
}

static int fmt_double(char *buf, size_t n, double v) {
    if (v < 0) return snprintf(buf, n, "%s", "");
    return snprintf(buf, n, "%.1f", v);
}

static void print_table(CaseResult *rows, int n) {
    const char *cols[] = {"rows", "name", "time(ms)", "CPU(ms)", "ttfb(ms)", "out(kb)", "writes", "peak(kb)"};
    int w[8];
    for (int c = 0; c < 8; c++) w[c] = (int)strlen(cols[c]);

    for (int i = 0; i < n; i++) {
        char b[128];
        if ((int)strlen(rows[i].name) > w[1]) w[1] = (int)strlen(rows[i].name);
        if (digits_ll(rows[i].rows) > w[0]) w[0] = digits_ll(rows[i].rows);
        if (fmt_double(b, sizeof b, rows[i].time_ms) > w[2]) w[2] = (int)strlen(b);
        if (fmt_double(b, sizeof b, rows[i].cpu_ms) > w[3]) w[3] = (int)strlen(b);
        if (fmt_double(b, sizeof b, rows[i].ttfb_ms) > w[4]) w[4] = (int)strlen(b);
        if (fmt_double(b, sizeof b, rows[i].out_kb) > w[5]) w[5] = (int)strlen(b);
        if (digits_ll((long long)rows[i].writes) > w[6]) w[6] = digits_ll((long long)rows[i].writes);
        if (fmt_double(b, sizeof b, rows[i].peak_kb) > w[7]) w[7] = (int)strlen(b);
    }

    putchar('+');
    for (int c = 0; c < 8; c++) { for (int k = 0; k < w[c] + 2; k++) putchar('-'); putchar('+'); }
    putchar('\n');

    printf("| %-*s | %-*s | %*s | %*s | %*s | %*s | %*s | %*s |\n",
           w[0], cols[0], w[1], cols[1], w[2], cols[2], w[3], cols[3],
           w[4], cols[4], w[5], cols[5], w[6], cols[6], w[7], cols[7]);

    putchar('+');
    for (int c = 0; c < 8; c++) { for (int k = 0; k < w[c] + 2; k++) putchar('-'); putchar('+'); }
    putchar('\n');

    for (int i = 0; i < n; i++) {
        char t[64], cpu[64], ttfb[64], out[64], peak[64];
        fmt_double(t, sizeof t, rows[i].time_ms);
        fmt_double(cpu, sizeof cpu, rows[i].cpu_ms);
        fmt_double(ttfb, sizeof ttfb, rows[i].ttfb_ms);
        fmt_double(out, sizeof out, rows[i].out_kb);
        fmt_double(peak, sizeof peak, rows[i].peak_kb);
        printf("| %*d | %-*s | %*s | %*s | %*s | %*s | %*zu | %*s |\n",
               w[0], rows[i].rows, w[1], rows[i].name, w[2], t, w[3], cpu,
               w[4], ttfb, w[5], out, w[6], rows[i].writes, w[7], peak);
    }

    putchar('+');
    for (int c = 0; c < 8; c++) { for (int k = 0; k < w[c] + 2; k++) putchar('-'); putchar('+'); }
    putchar('\n');
}

static bool is_int_arg(const char *s, int *out) {
    if (!s || !*s) return false;
    char *end = NULL;
    long v = strtol(s, &end, 10);
    if (*end) return false;
    *out = (int)v;
    return true;
}

static void show_data(int rows) {
    StrBuf sb; sb_init(&sb);
    Out sout = { sb_write_cb, &sb };
    emit_pretty(&sout, rows);
    fwrite(sb.v, 1, sb.n, stdout);
    putchar('\n');
    sb_free(&sb);
}

int main(int argc, char **argv) {
    double t_all0 = now_wall();

    int results_cap = 64, results_n = 0;
    CaseResult *results = (CaseResult *)calloc((size_t)results_cap, sizeof *results);
    const char *filter[128];
    int filter_n = 0;
    int last_sz = -1;

    for (int ai = 1; ai < argc; ai++) {
        if (strcmp(argv[ai], "--show") == 0 && ai + 1 < argc) {
            int rows = atoi(argv[++ai]);
            show_data(rows);
            free(results);
            return 0;
        }
    }

    for (int ai = 1; ai < argc; ai++) {
        if (strcmp(argv[ai], "--show") == 0) { ai++; continue; }
        if (strcmp(argv[ai], "-") == 0) { filter_n = 0; continue; }

        int rows;
        if (!is_int_arg(argv[ai], &rows)) {
            if (filter_n < (int)(sizeof filter / sizeof filter[0])) filter[filter_n++] = argv[ai];
            continue;
        }

        last_sz = rows;
        const char **tests = filter_n ? filter : default_tests;
        int test_n = filter_n;
        if (!filter_n) { while (default_tests[test_n]) test_n++; }

        for (int ti = 0; ti < test_n; ti++) {
            const char *name = tests[ti];
            fprintf(stderr, "%s (%d)... ", name, rows);
            fflush(stderr);
            double t0 = now_wall();
            CaseResult cr = time_one(name, rows);
            cr.peak_kb = memory_one(name, rows);
            double t1 = now_wall();
            fprintf(stderr, "%.0f ms\n", (t1 - t0) * 1000.0);
            if (results_n == results_cap) {
                results_cap *= 2;
                results = (CaseResult *)realloc(results, (size_t)results_cap * sizeof *results);
            }
            results[results_n++] = cr;
        }
    }

    if (last_sz < 0) {
        int rows = 1000;
        int test_n = filter_n;
        const char **tests = filter_n ? filter : default_tests;
        if (!filter_n) { while (default_tests[test_n]) test_n++; }
        for (int ti = 0; ti < test_n; ti++) {
            const char *name = tests[ti];
            fprintf(stderr, "%s (%d)... ", name, rows);
            fflush(stderr);
            double t0 = now_wall();
            CaseResult cr = time_one(name, rows);
            cr.peak_kb = memory_one(name, rows);
            double t1 = now_wall();
            fprintf(stderr, "%.0f ms\n", (t1 - t0) * 1000.0);
            if (results_n == results_cap) {
                results_cap *= 2;
                results = (CaseResult *)realloc(results, (size_t)results_cap * sizeof *results);
            }
            results[results_n++] = cr;
        }
    }

    if (results_n) print_table(results, results_n);
    fprintf(stderr, "completed in: %.1f\n", round1(now_wall() - t_all0));
    free(results);
    return 0;
}
