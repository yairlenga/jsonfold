#include <errno.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jsonfold.h"

static const char DEMO_JSON[] =
"{\n"
"  \"meta\": {\n"
"    \"version\": 1,\n"
"    \"ok\": true\n"
"  },\n"
"  \"items\": [\n"
"    {\n"
"      \"id\": 1,\n"
"      \"name\": \"alpha\"\n"
"    },\n"
"    {\n"
"      \"id\": 2,\n"
"      \"name\": \"beta\"\n"
"    }\n"
"  ],\n"
"  \"matrix\": [\n"
"    [\n"
"      1,\n"
"      2\n"
"    ],\n"
"    [\n"
"      3,\n"
"      4\n"
"    ]\n"
"  ]\n"
"}\n";

typedef struct {
    const char *compact;
    const char *input;
    int width;
    bool demo;
    bool verbose;
} Options;

static void usage(FILE *fp, const char *prog)
{
    fprintf(fp,
        "usage: %s [options]\n"
        "\n"
        "Read already pretty-printed JSON from stdin; write folded JSON to stdout.\n"
        "\n"
        "Options:\n"
        "  --demo                  use built-in demo JSON\n"
        "  --compact PRESET        off|none|low|med|default|high|max\n"
        "  --compact=PRESET\n"
        "  --width N               override line width\n"
        "  --width=N\n"
        "  --input FILE, -i FILE    read input from FILE instead of stdin\n"
        "  --verbose, -v           print basic settings to stderr\n"
        "  --indent N              accepted for compatibility; ignored\n"
        "  --sort-keys             accepted for compatibility; ignored\n"
        "  --gold                  accepted for compatibility; ignored\n"
        "  --help, -h              show this help\n",
        prog);
}

static int parse_int(const char *s, int *out)
{
    char *end = NULL;
    long v;

    errno = 0;
    v = strtol(s, &end, 10);

    if (errno || end == s || *end != '\0' || v < 0 || v > 1000000)
        return -1;

    *out = (int)v;
    return 0;
}

static int parse_args(int argc, char **argv, Options *opts)
{
    opts->compact = JSONFOLD_DEFAULT;
    opts->input = NULL;
    opts->width = 0;
    opts->demo = false;
    opts->verbose = false;

    for (int i = 1; i < argc; i++) {
        const char *arg = argv[i];

        if (!strcmp(arg, "--help") || !strcmp(arg, "-h")) {
            usage(stdout, argv[0]);
            exit(EXIT_SUCCESS);
        } else if (!strcmp(arg, "--demo")) {
            opts->demo = true;
        } else if (!strcmp(arg, "--verbose") || !strcmp(arg, "-v")) {
            opts->verbose = true;
        } else if (!strcmp(arg, "--sort-keys") || !strcmp(arg, "--gold")) {
            /* accepted for compatibility; ignored */
        } else if (!strncmp(arg, "--compact=", 10)) {
            opts->compact = arg + 10;
        } else if (!strcmp(arg, "--width")) {
            if (++i >= argc || parse_int(argv[i], &opts->width) != 0) {
                fprintf(stderr, "jsonfold: invalid --width\n");
                return -1;
            }
        } else if (!strncmp(arg, "-w", 2)) {
            arg += 2 ;
            if ( !*arg ) {
                if (++i >= argc) {
                    fprintf(stderr, "jsonfold: -i requires a file\n");
                    return -1;
                }
                arg = argv[i] ;
            }
            if (parse_int(arg, &opts->width) != 0) {
                fprintf(stderr, "jsonfold: invalid --width: %s\n", arg + 8);
                return -1;
            }
        } else if (!strncmp(arg, "--width=", 8)) {
            if (parse_int(arg + 8, &opts->width) != 0) {
                fprintf(stderr, "jsonfold: invalid --width: %s\n", arg + 8);
                return -1;
            }
        } else if (!strcmp(arg, "-i") || !strcmp(arg, "-i")) {
            arg += 2 ;
            if ( !*arg ) {
                if (++i >= argc) {
                    fprintf(stderr, "jsonfold: -i requires a file\n");
                    return -1;
                }
                arg = argv[i] ;
            }
            opts->input = arg;
        } else if (!strncmp(arg, "--input=", 8)) {
            opts->input = arg + 8;
        } else if (!strcmp(arg, "--indent")) {
            if (++i >= argc) {
                fprintf(stderr, "jsonfold: --indent requires a value\n");
                return -1;
            }
            /* accepted for compatibility; ignored */
        } else if (!strncmp(arg, "--indent=", 9)) {
            /* accepted for compatibility; ignored */
        } else {
            fprintf(stderr, "jsonfold: unknown option: %s\n", arg);
            usage(stderr, argv[0]);
            return -1;
        }
    }

    return 0;
}

static int feed_text(JFWriter w, const char *s)
{
    return jsonfold_write(w, s, strlen(s)) < 0 ? -1 : 0;
}

static int feed_file(JFWriter w, FILE *fp)
{
    char buf[16 * 1024];

    for (;;) {
        size_t n = fread(buf, 1, sizeof buf, fp);

        if (n > 0 && jsonfold_write(w, buf, n) < 0)
            return -1;

        if (n < sizeof buf) {
            if (ferror(fp))
                return -1;
            return 0;
        }
    }
}

void show_stats(JFStats s)
{
    if (s) {
        fprintf(stderr,
            "jsonfold(stats):"
            " bytes_in=%u"
            " bytes_out=%u"
            " lines_in=%u"
            " lines_out=%u",
            s->bytes_in,
            s->bytes_out,
            s->lines_in,
            s->lines_out);

        if (s->bytes_in > 0) {
            fprintf(stderr,
                " compression=%.1f%%",
                100.0 * (double)s->bytes_out / (double)s->bytes_in);
        }

        fprintf(stderr, "\n");

        jsonfold_stats_free(s);
    }
}

int main(int argc, char **argv)
{
    Options opts;

    if (parse_args(argc, argv, &opts) != 0)
        return EXIT_FAILURE;

    JFConfig base = jsonfold_config_preset(opts.compact);
    if (!base) {
        fprintf(stderr, "jsonfold: unknown preset '%s'\n", opts.compact);
        return EXIT_FAILURE;
    }

    struct jsonfold_config *cfg = NULL;
    JFConfig use_cfg = base;

    if (opts.width > 0) {
        cfg = jsonfold_config_create(base);
        if (!cfg) {
            fprintf(stderr, "jsonfold: failed to create config\n");
            return EXIT_FAILURE;
        }

        cfg->width = opts.width;
        use_cfg = cfg;
    }

    JFWriter w = jsonfold_file_writer_create(stdout, use_cfg);
    if (!w) {
        fprintf(stderr, "jsonfold: failed to create writer\n");
        if (cfg)
            jsonfold_config_destroy(cfg);
        return EXIT_FAILURE;
    }

    if (opts.verbose) {
        fprintf(stderr, "jsonfold: compact=%s", opts.compact);
        if (opts.width > 0)
            fprintf(stderr, " width=%d", opts.width);
        if (opts.demo)
            fprintf(stderr, " demo=true");
        if (opts.input)
            fprintf(stderr, " input=%s", opts.input);
        fputc('\n', stderr);
    }

    int ok = 1;

    if (opts.demo) {
        if (feed_text(w, DEMO_JSON) != 0)
            ok = 0;
    } else {
        FILE *fp = stdin;

        if (opts.input) {
            fp = fopen(opts.input, "rb");
            if (!fp) {
                fprintf(stderr, "jsonfold: cannot open %s: %s\n",
                        opts.input, strerror(errno));
                ok = 0;
            }
        }

        if (ok && feed_file(w, fp) != 0) {
            fprintf(stderr, "jsonfold: read/write failed\n");
            ok = 0;
        }

        if (fp && fp != stdin)
            fclose(fp);
    }

    if (ok && jsonfold_finish(w) != 0) {
        fprintf(stderr, "jsonfold: finish failed\n");
        ok = 0;
    }

    JFStats s = jsonfold_get_stats(w);

    jsonfold_destroy(w);

    if (cfg)
        jsonfold_config_destroy(cfg);

    if ( opts.verbose) {
        show_stats(s) ;
    }

    if (ok && fflush(stdout) != 0) {
        perror("jsonfold: stdout");
        ok = 0;
    }

    return ok ? EXIT_SUCCESS : EXIT_FAILURE;
}