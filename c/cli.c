#include <errno.h>
#include <getopt.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jsonfold.h"

enum {
    OPT_INDENT = 1000,
    OPT_SORT_KEYS,
    OPT_GOLD,

    OPT_PACK_ITEMS,
    OPT_PACK_ARRAY_ITEMS,
    OPT_PACK_OBJ_ITEMS,
    OPT_PACK_NESTING,

    OPT_FOLD_ITEMS,
    OPT_FOLD_ARRAY_ITEMS,
    OPT_FOLD_OBJ_ITEMS,
    OPT_FOLD_NESTING,

    OPT_JOIN_ITEMS,
    OPT_JOIN_ARRAY_ITEMS,
    OPT_JOIN_OBJ_ITEMS,
    OPT_JOIN_NESTING
};

typedef struct {
    const char *compact;
    const char *input;

    int width;
    bool demo;
    bool verbose;

    int pack_items;
    int pack_array_items;
    int pack_obj_items;
    int pack_nesting;

    int fold_items;
    int fold_array_items;
    int fold_obj_items;
    int fold_nesting;

    int join_items;
    int join_array_items;
    int join_obj_items;
    int join_nesting;
} Options;

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

static void usage(FILE *fp, const char *prog)
{
    fprintf(fp,
        "usage: %s [options]\n"
        "\n"
        "Read already pretty-printed JSON from stdin; write folded JSON to stdout.\n"
        "\n"
        "Options:\n"
        "  --demo\n"
        "  --compact PRESET\n"
        "  --width N, -w N\n"
        "  --input FILE, -i FILE\n"
        "  --verbose, -v\n"
        "  --indent N              accepted; ignored\n"
        "  --sort-keys             accepted; ignored\n"
        "  --gold                  accepted; ignored\n"
        "\n"
        "Pack options:\n"
        "  --pack-items N\n"
        "  --pack-array-items N\n"
        "  --pack-obj-items N\n"
        "  --pack-nesting N\n"
        "\n"
        "Fold options:\n"
        "  --fold-items N\n"
        "  --fold-array-items N\n"
        "  --fold-obj-items N\n"
        "  --fold-nesting N\n"
        "\n"
        "Join options:\n"
        "  --join-items N\n"
        "  --join-array-items N\n"
        "  --join-obj-items N\n"
        "  --join-nesting N\n"
        "\n"
        "  --help, -h\n",
        prog);
}

static int parse_int_or_die(const char *opt, const char *s)
{
    char *end = NULL;
    long v;

    errno = 0;
    v = strtol(s, &end, 10);

    if (errno || end == s || *end != '\0' || v < 0 || v > 1000000) {
        fprintf(stderr, "jsonfold: invalid %s: %s\n", opt, s);
        exit(EXIT_FAILURE);
    }

    return (int)v;
}

static void options_init(Options *opts)
{
    memset(opts, 0, sizeof(*opts));

    opts->compact = JSONFOLD_DEFAULT;

    opts->width = -1;

    opts->pack_items = -1;
    opts->pack_array_items = -1;
    opts->pack_obj_items = -1;
    opts->pack_nesting = -1;

    opts->fold_items = -1;
    opts->fold_array_items = -1;
    opts->fold_obj_items = -1;
    opts->fold_nesting = -1;

    opts->join_items = -1;
    opts->join_array_items = -1;
    opts->join_obj_items = -1;
    opts->join_nesting = -1;
}

static void parse_args(int argc, char **argv, Options *opts)
{
    static const struct option longopts[] = {
        { "help",             no_argument,       NULL, 'h' },
        { "demo",             no_argument,       NULL, 'd' },
        { "verbose",          no_argument,       NULL, 'v' },
        { "input",            required_argument, NULL, 'i' },
        { "width",            required_argument, NULL, 'w' },
        { "compact",          required_argument, NULL, 'c' },

        { "indent",           required_argument, NULL, OPT_INDENT },
        { "sort-keys",        no_argument,       NULL, OPT_SORT_KEYS },
        { "gold",             no_argument,       NULL, OPT_GOLD },

        { "pack-items",       required_argument, NULL, OPT_PACK_ITEMS },
        { "pack-array-items", required_argument, NULL, OPT_PACK_ARRAY_ITEMS },
        { "pack-obj-items",   required_argument, NULL, OPT_PACK_OBJ_ITEMS },
        { "pack-nesting",     required_argument, NULL, OPT_PACK_NESTING },

        { "fold-items",       required_argument, NULL, OPT_FOLD_ITEMS },
        { "fold-array-items", required_argument, NULL, OPT_FOLD_ARRAY_ITEMS },
        { "fold-obj-items",   required_argument, NULL, OPT_FOLD_OBJ_ITEMS },
        { "fold-nesting",     required_argument, NULL, OPT_FOLD_NESTING },

        { "join-items",       required_argument, NULL, OPT_JOIN_ITEMS },
        { "join-array-items", required_argument, NULL, OPT_JOIN_ARRAY_ITEMS },
        { "join-obj-items",   required_argument, NULL, OPT_JOIN_OBJ_ITEMS },
        { "join-nesting",     required_argument, NULL, OPT_JOIN_NESTING },

        { NULL, 0, NULL, 0 }
    };

    int c;

    options_init(opts);

    while ((c = getopt_long(argc, argv, "hvi:w:", longopts, NULL)) != -1) {
        switch (c) {
        case 'h':
            usage(stdout, argv[0]);
            exit(EXIT_SUCCESS);

        case 'd':
            opts->demo = true;
            break;

        case 'v':
            opts->verbose = true;
            break;

        case 'i':
            opts->input = optarg;
            break;

        case 'w':
            opts->width = parse_int_or_die("--width", optarg);
            break;

        case 'c':
            opts->compact = optarg;
            break;

        case OPT_INDENT:
        case OPT_SORT_KEYS:
        case OPT_GOLD:
            break;

        case OPT_PACK_ITEMS:
            opts->pack_items = parse_int_or_die("--pack-items", optarg);
            break;
        case OPT_PACK_ARRAY_ITEMS:
            opts->pack_array_items = parse_int_or_die("--pack-array-items", optarg);
            break;
        case OPT_PACK_OBJ_ITEMS:
            opts->pack_obj_items = parse_int_or_die("--pack-obj-items", optarg);
            break;
        case OPT_PACK_NESTING:
            opts->pack_nesting = parse_int_or_die("--pack-nesting", optarg);
            break;

        case OPT_FOLD_ITEMS:
            opts->fold_items = parse_int_or_die("--fold-items", optarg);
            break;
        case OPT_FOLD_ARRAY_ITEMS:
            opts->fold_array_items = parse_int_or_die("--fold-array-items", optarg);
            break;
        case OPT_FOLD_OBJ_ITEMS:
            opts->fold_obj_items = parse_int_or_die("--fold-obj-items", optarg);
            break;
        case OPT_FOLD_NESTING:
            opts->fold_nesting = parse_int_or_die("--fold-nesting", optarg);
            break;

        case OPT_JOIN_ITEMS:
            opts->join_items = parse_int_or_die("--join-items", optarg);
            break;
        case OPT_JOIN_ARRAY_ITEMS:
            opts->join_array_items = parse_int_or_die("--join-array-items", optarg);
            break;
        case OPT_JOIN_OBJ_ITEMS:
            opts->join_obj_items = parse_int_or_die("--join-obj-items", optarg);
            break;
        case OPT_JOIN_NESTING:
            opts->join_nesting = parse_int_or_die("--join-nesting", optarg);
            break;

        default:
            usage(stderr, argv[0]);
            exit(EXIT_FAILURE);
        }
    }

    if (optind != argc) {
        fprintf(stderr, "jsonfold: unexpected argument: %s\n", argv[optind]);
        usage(stderr, argv[0]);
        exit(EXIT_FAILURE);
    }
}

static void apply_options(struct jsonfold_config *cfg, const Options *opts)
{
    if (opts->width >= 0)
        cfg->width = opts->width;

    if (opts->pack_items >= 0) {
        cfg->pack_array_items = opts->pack_items;
        cfg->pack_obj_items = opts->pack_items;
    }
    if (opts->pack_array_items >= 0)
        cfg->pack_array_items = opts->pack_array_items;
    if (opts->pack_obj_items >= 0)
        cfg->pack_obj_items = opts->pack_obj_items;
    if (opts->pack_nesting >= 0)
        cfg->pack_nesting = opts->pack_nesting;

    if (opts->fold_items >= 0) {
        cfg->fold_array_items = opts->fold_items;
        cfg->fold_obj_items = opts->fold_items;
    }
    if (opts->fold_array_items >= 0)
        cfg->fold_array_items = opts->fold_array_items;
    if (opts->fold_obj_items >= 0)
        cfg->fold_obj_items = opts->fold_obj_items;
    if (opts->fold_nesting >= 0)
        cfg->fold_nesting = opts->fold_nesting;

    if (opts->join_items >= 0) {
        cfg->join_array_items = opts->join_items;
        cfg->join_obj_items = opts->join_items;
    }
    if (opts->join_array_items >= 0)
        cfg->join_array_items = opts->join_array_items;
    if (opts->join_obj_items >= 0)
        cfg->join_obj_items = opts->join_obj_items;
    if (opts->join_nesting >= 0)
        cfg->join_nesting = opts->join_nesting;
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

void jsonfold_show_config(FILE *fp, JFConfig cfg)
{
    fprintf(fp,
        "JSONFold(config): "
        "width=%d"
        ", pack=(array=%d/obj=%d, nesting=%d), "
        ", fold=(array=%d/obj=%d, nesting=%d), "
        ", join=(array=%d/obj=%d, nesting=%d)"
        ")\n",
        cfg->width,

        cfg->pack_array_items,
        cfg->pack_obj_items,
        cfg->pack_nesting,

        cfg->fold_array_items,
        cfg->fold_obj_items,
        cfg->fold_nesting,

        cfg->join_array_items,
        cfg->join_obj_items,
        cfg->join_nesting);
}

void show_stats(JFStats s)
{
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

}

int main(int argc, char **argv)
{
    Options opts;
    parse_args(argc, argv, &opts);

    JFConfig base = jsonfold_config_preset(opts.compact);
    if (!base) {
        fprintf(stderr, "jsonfold: unknown preset '%s'\n", opts.compact);
        return EXIT_FAILURE;
    }

    struct jsonfold_config *cfg = jsonfold_config_create(base);
    if (!cfg) {
        fprintf(stderr, "jsonfold: failed to create config\n");
        return EXIT_FAILURE;
    }

    apply_options(cfg, &opts);

    if (opts.verbose) {
        jsonfold_show_config(stderr, cfg);
    } 

    JFWriter w = jsonfold_file_writer_create(stdout, cfg);
    if (!w) {
        fprintf(stderr, "jsonfold: failed to create writer\n");
        jsonfold_config_destroy(cfg);
        return EXIT_FAILURE;
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

    if (ok && !jsonfold_finish(w)) {
        fprintf(stderr, "jsonfold: finish failed\n");
        ok = 0;
    }

    JFStats stat = jsonfold_get_stats(w);

    jsonfold_destroy(w);

    if (cfg)
        jsonfold_config_destroy(cfg);

    if ( opts.verbose) {
        show_stats(stat) ;
    }
    jsonfold_stats_destroy(stat);

    if (ok && fflush(stdout) != 0) {
        perror("jsonfold: stdout");
        ok = 0;
    }

    return ok ? EXIT_SUCCESS : EXIT_FAILURE;
}