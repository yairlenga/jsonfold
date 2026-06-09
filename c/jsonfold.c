#include "jsonfold.h"

#include <errno.h>
#include <stdlib.h>
#include <string.h>

#include <stddef.h>
#include <stdio.h>

#define JSONFOLD_MAX_ARRAY_ITEMS 1000
#define JSONFOLD_MAX_OBJ_ITEMS   1000
#define JSONFOLD_MAX_NESTING     10

typedef enum jsonfold_kind {
    JSONFOLD_KIND_NONE = 0,
    JSONFOLD_KIND_DICT = 1,
    JSONFOLD_KIND_LIST = 2
} jsonfold_kind, JFKind ;

typedef struct line {
    int indent;
    char *text;
    size_t len;
    jsonfold_kind parent_kind;
    int items;
    int leafs;
    int child_nesting;
    jsonfold_kind opener;
    jsonfold_kind closer;
    unsigned can_join:1;
    unsigned can_pack:1;
} line;

typedef struct line_vec {
    line *v;
    int n;
    int cap;
} line_vec;

typedef struct frame {
    jsonfold_kind kind;
    int depth;
    line_vec lines;
    int pack_limit;
    int fold_limit;
    int join_limit;
    int content_lines;
    int items;
    int leafs;
    int fold_ok;
    int child_nesting;
} frame;

typedef struct frame_vec {
    frame *v;
    int n;
    int cap;
} frame_vec;

typedef struct char_vec {
    char *v ;
    int n ;
    int cap ;
};

struct jsonfold_writer {
    jsonfold_write_fn write_fn;
    void *write_ctx;
    struct jsonfold_config cfg ;
    struct jsonfold_stats stats;
    char *pending;
    size_t pending_len;
    size_t pending_cap;
    frame_vec stack;
    int error;
};

typedef ptrdiff_t (*jsonfold_write_fn)(void *ctx, const char *buf, size_t len);

typedef struct jsonfold_writer jsonfold_writer;

////////// JSONFOLD_CONFIG

static const struct jsonfold_config CFG_NONE = {
    .width            = 80,
} ;

static const struct jsonfold_config CFG_DEFAULT = {
    .width            = 80,
    .pack_array_items = 8,
    .pack_obj_items   = 4,
    .pack_nesting     = 1,
    .fold_array_items = 8,
    .fold_obj_items   = 4,
    .fold_nesting     = 1,
    .join_array_items = 8,
    .join_obj_items   = 4,
    .join_nesting     = 1,
};

static const struct jsonfold_config CFG_LOW = {
    .width            = 80,
    .pack_array_items = 8,
    .pack_obj_items   = 4,
    .pack_nesting     = 1,
    .fold_array_items = 8,
    .fold_obj_items   = 4,
    .fold_nesting     = 0,
    .join_array_items = 8,
    .join_obj_items   = 4,
    .join_nesting     = 0,
};

static const struct jsonfold_config CFG_MED = {
    .width            = 80,
    .pack_array_items = 8,
    .pack_obj_items   = 4,
    .pack_nesting     = 1,
    .fold_array_items = 8,
    .fold_obj_items   = 4,
    .fold_nesting     = 1,
    .join_array_items = 8,
    .join_obj_items   = 4,
    .join_nesting     = 0,
};

static const struct jsonfold_config CFG_HIGH = {
    .width            = 80,
    .pack_array_items = 16,
    .pack_obj_items   = 8,
    .pack_nesting     = 4,
    .fold_array_items = 16,
    .fold_obj_items   = 8,
    .fold_nesting     = 4,
    .join_array_items = 16,
    .join_obj_items   = 8,
    .join_nesting     = 2,
};

static const struct jsonfold_config CFG_MAX = {
    .width            = 255,
    .pack_array_items = JSONFOLD_MAX_ARRAY_ITEMS,
    .pack_obj_items   = JSONFOLD_MAX_OBJ_ITEMS,
    .pack_nesting     = JSONFOLD_MAX_NESTING,
    .fold_array_items = JSONFOLD_MAX_ARRAY_ITEMS,
    .fold_obj_items   = JSONFOLD_MAX_OBJ_ITEMS,
    .fold_nesting     = JSONFOLD_MAX_NESTING,
    .join_array_items = JSONFOLD_MAX_ARRAY_ITEMS,
    .join_obj_items   = JSONFOLD_MAX_OBJ_ITEMS,
    .join_nesting     = JSONFOLD_MAX_NESTING,
};

static const struct jsonfold_config CFG_PACK = {
    .width            = 80,
    .pack_array_items = JSONFOLD_MAX_ARRAY_ITEMS,
    .pack_obj_items   = JSONFOLD_MAX_OBJ_ITEMS,
    .pack_nesting     = JSONFOLD_MAX_NESTING,
};

static const struct jsonfold_config CFG_FOLD = {
    .width            = 80,
    .fold_array_items = JSONFOLD_MAX_ARRAY_ITEMS,
    .fold_obj_items   = JSONFOLD_MAX_OBJ_ITEMS,
    .fold_nesting     = JSONFOLD_MAX_NESTING,
};

static const struct jsonfold_config CFG_JOIN = {
    .width            = 80,
    .fold_array_items = JSONFOLD_MAX_ARRAY_ITEMS,
    .fold_obj_items   = JSONFOLD_MAX_OBJ_ITEMS,
    .fold_nesting     = JSONFOLD_MAX_NESTING,
    .join_array_items = JSONFOLD_MAX_ARRAY_ITEMS,
    .join_obj_items   = JSONFOLD_MAX_OBJ_ITEMS,
    .join_nesting     = JSONFOLD_MAX_NESTING,
};

static struct { const char *name; JFConfig config ; } presets [] = {
    { "default", &CFG_DEFAULT },
    { "", &CFG_DEFAULT },
    { "off", NULL },
    { "none", &CFG_NONE },
    { "low", &CFG_LOW },
    { "med", &CFG_MED },
    { "high", &CFG_HIGH },
    { "max", &CFG_MAX },
    { "pack", &CFG_PACK },
    { "fold", &CFG_FOLD },
    { "join", &CFG_JOIN },
    { NULL, NULL },
} ;

JFConfig JSONFOLD_CONFIG_NONE = &CFG_NONE ;
JFConfig JSONFOLD_CONFIG_DEFAULT = &CFG_DEFAULT ;

JFConfig jsonfold_config_preset(const char *preset)
{
    for (int i=0 ; presets[i].name ; i++) {
        if ( streq(preset, presets[i].name)) return presets[i].config ;
    }
}

struct jsonfold_config *jsonfold_config_create(JFConfig config)
{
    struct jsonfold_config *new_cfg = malloc(sizeof(*new_cfg)) ;
    memcpy(new_cfg, config) ;
    return new_cfg ;
}

void jsonfold_config_destroy( JFConfig config)
{
    free((void *) config) ;
}


////////// JSONFOLD_WRITER


jsonfold_writer *jsonfold_writer_new(jsonfold_write_fn write_fn,
                                      void *write_ctx,
                                      const jsonfold_config *cfg);
void jsonfold_writer_free(jsonfold_writer *w);

ptrdiff_t jsonfold_write(jsonfold_writer *w, const char *buf, size_t len);
int jsonfold_finish(jsonfold_writer *w);
int jsonfold_flush(jsonfold_writer *w);

const jsonfold_stats *jsonfold_get_stats(const jsonfold_writer *w);

/* Convenience adapter for FILE*. Does not close fp. */
jsonfold_writer *jsonfold_file_writer_new(FILE *fp, const jsonfold_config *cfg);


const jsonfold_config JSONFOLD_CONFIG_NONE = &CONFIG_NONE ;
const 
    80, 0, 0, 0, 0, 0, 0, 0, 0, 0
};

const jsonfold_config JSONFOLD_CONFIG_DEFAULT = {
    80, 8, 4, 1, 8, 4, 1, 8, 4, 1
};

static const jsonfold_config CFG_LOW  = {80, 8, 4, 1, 8, 4, 0, 8, 4, 0};
static const jsonfold_config CFG_MED  = {80, 8, 4, 1, 8, 4, 1, 8, 4, 0};
static const jsonfold_config CFG_HIGH = {80,16, 8, 4,16, 8, 4,16, 8, 2};
static const jsonfold_config CFG_MAX  = {255, JSONFOLD_MAX_ARRAY_ITEMS, JSONFOLD_MAX_OBJ_ITEMS, JSONFOLD_MAX_NESTING,
                                         JSONFOLD_MAX_ARRAY_ITEMS, JSONFOLD_MAX_OBJ_ITEMS, JSONFOLD_MAX_NESTING,
                                         JSONFOLD_MAX_ARRAY_ITEMS, JSONFOLD_MAX_OBJ_ITEMS, JSONFOLD_MAX_NESTING};
static const jsonfold_config CFG_PACK = {80, JSONFOLD_MAX_ARRAY_ITEMS, JSONFOLD_MAX_OBJ_ITEMS, JSONFOLD_MAX_NESTING,
                                         0, 0, 0, 0, 0, 0};
static const jsonfold_config CFG_FOLD = {80, 0, 0, 0,
                                         JSONFOLD_MAX_ARRAY_ITEMS, JSONFOLD_MAX_OBJ_ITEMS, JSONFOLD_MAX_NESTING,
                                         0, 0, 0};
static const jsonfold_config CFG_JOIN = {80, 0, 0, 0,
                                         JSONFOLD_MAX_ARRAY_ITEMS, JSONFOLD_MAX_OBJ_ITEMS, JSONFOLD_MAX_NESTING,
                                         JSONFOLD_MAX_ARRAY_ITEMS, JSONFOLD_MAX_OBJ_ITEMS, JSONFOLD_MAX_NESTING};


static int streq(const char *a, const char *b) { return strcmp(a, b) == 0; }

const jsonfold_config *jsonfold_preset_config(jsonfold_preset preset) {
    switch (preset) {
    case JSONFOLD_PRESET_OFF:     return NULL;
    case JSONFOLD_PRESET_NONE:    return &JSONFOLD_CONFIG_NONE;
    case JSONFOLD_PRESET_DEFAULT: return &JSONFOLD_CONFIG_DEFAULT;
    case JSONFOLD_PRESET_LOW:     return &CFG_LOW;
    case JSONFOLD_PRESET_MED:     return &CFG_MED;
    case JSONFOLD_PRESET_HIGH:    return &CFG_HIGH;
    case JSONFOLD_PRESET_MAX:     return &CFG_MAX;
    case JSONFOLD_PRESET_PACK:    return &CFG_PACK;
    case JSONFOLD_PRESET_FOLD:    return &CFG_FOLD;
    case JSONFOLD_PRESET_JOIN:    return &CFG_JOIN;
    }
    return &JSONFOLD_CONFIG_DEFAULT;
}

int jsonfold_preset_by_name(const char *name, jsonfold_preset *out) {
    if (!name || !*name || streq(name, "default")) *out = JSONFOLD_PRESET_DEFAULT;
    else if (streq(name, "off"))  *out = JSONFOLD_PRESET_OFF;
    else if (streq(name, "none")) *out = JSONFOLD_PRESET_NONE;
    else if (streq(name, "low"))  *out = JSONFOLD_PRESET_LOW;
    else if (streq(name, "med"))  *out = JSONFOLD_PRESET_MED;
    else if (streq(name, "high")) *out = JSONFOLD_PRESET_HIGH;
    else if (streq(name, "max"))  *out = JSONFOLD_PRESET_MAX;
    else if (streq(name, "pack")) *out = JSONFOLD_PRESET_PACK;
    else if (streq(name, "fold")) *out = JSONFOLD_PRESET_FOLD;
    else if (streq(name, "join")) *out = JSONFOLD_PRESET_JOIN;
    else return -1;
    return 0;
}

static void line_free(line *l) { free(l->text); memset(l, 0, sizeof(*l)); }
static void line_vec_clear(line_vec *lv) { for (size_t i=0;i<lv->n;i++) line_free(&lv->v[i]); lv->n = 0; }
static void line_vec_free(line_vec *lv) { line_vec_clear(lv); free(lv->v); memset(lv,0,sizeof(*lv)); }

static int line_vec_reserve(line_vec *lv, size_t need) {
    if (need <= lv->cap) return 0;
    size_t nc = lv->cap ? lv->cap * 2 : 4;
    while (nc < need) nc *= 2;
    line *nv = (line *)realloc(lv->v, nc * sizeof(*nv));
    if (!nv) return -1;
    lv->v = nv; lv->cap = nc; return 0;
}

static int line_vec_push_take(line_vec *lv, line *l) {
    if (line_vec_reserve(lv, lv->n + 1) != 0) return -1;
    lv->v[lv->n++] = *l;
    memset(l, 0, sizeof(*l));
    return 0;
}

static line line_vec_pop(line_vec *lv) {
    line out = lv->v[--lv->n];
    memset(&lv->v[lv->n], 0, sizeof(lv->v[lv->n]));
    return out;
}

static void frame_free(frame *f) { line_vec_free(&f->lines); memset(f,0,sizeof(*f)); }

static int frame_vec_reserve(frame_vec *fv, size_t need) {
    if (need <= fv->cap) return 0;
    size_t nc = fv->cap ? fv->cap * 2 : 8;
    while (nc < need) nc *= 2;
    frame *nv = (frame *)realloc(fv->v, nc * sizeof(*nv));
    if (!nv) return -1;
    fv->v = nv; fv->cap = nc; return 0;
}

static int count_newlines(const char *s, size_t len) {
    int n = 0;
    for (size_t i=0;i<len;i++) if (s[i] == '\n') n++;
    return n;
}

static jsonfold_kind closing_kind(const char *s, size_t len) {
    if (len == 1 && s[0] == '}') return JSONFOLD_KIND_DICT;
    if (len == 2 && s[0] == '}' && s[1] == ',') return JSONFOLD_KIND_DICT;
    if (len == 1 && s[0] == ']') return JSONFOLD_KIND_LIST;
    if (len == 2 && s[0] == ']' && s[1] == ',') return JSONFOLD_KIND_LIST;
    return JSONFOLD_KIND_NONE;
}

static int parse_line(line *out, const char *s, size_t len, jsonfold_kind parent_kind) {
    size_t start = 0, end = len;
    while (start < len && (s[start] == ' ' || s[start] == '\t')) start++;
    while (end > start && (s[end-1] == ' ' || s[end-1] == '\t' || s[end-1] == '\r')) end--;
    size_t body_len = end - start;
    char *body = (char *)malloc(body_len + 1);
    if (!body) return -1;
    memcpy(body, s + start, body_len);
    body[body_len] = 0;

    jsonfold_kind opener = JSONFOLD_KIND_NONE;
    if (body_len && body[body_len-1] == '{') opener = JSONFOLD_KIND_DICT;
    else if (body_len && body[body_len-1] == '[') opener = JSONFOLD_KIND_LIST;
    jsonfold_kind closer = closing_kind(body, body_len);
    int is_body = parent_kind != JSONFOLD_KIND_NONE && opener == JSONFOLD_KIND_NONE && closer == JSONFOLD_KIND_NONE;

    *out = (line){
        .indent = (int)start, .text = body, .len = body_len, .parent_kind = parent_kind,
        .items = 1, .leafs = 1, .child_nesting = -1, .opener = opener, .closer = closer,
        .can_join = (unsigned)is_body, .can_pack = (unsigned)is_body
    };
    return 0;
}

static size_t line_width(const line *l) { return (size_t)l->indent + l->len; }

static int line_join(line *dst, const line *src) {
    char *p = (char *)realloc(dst->text, dst->len + 1 + src->len + 1);
    if (!p) return -1;
    dst->text = p;
    dst->text[dst->len++] = ' ';
    memcpy(dst->text + dst->len, src->text, src->len + 1);
    dst->len += src->len;
    dst->items += src->items;
    dst->leafs += src->leafs;
    if (src->child_nesting > dst->child_nesting) {
        dst->child_nesting = src->child_nesting;
        dst->can_pack = 0;
    }
    return 0;
}

static jsonfold_kind parent_kind(const jsonfold_writer *w) {
    return w->stack.n ? w->stack.v[w->stack.n - 1].kind : JSONFOLD_KIND_NONE;
}

static int choose_limit(jsonfold_kind kind, int list_limit, int dict_limit) {
    return kind == JSONFOLD_KIND_LIST ? list_limit : kind == JSONFOLD_KIND_DICT ? dict_limit : 0;
}

static int write_string(jsonfold_writer *w, const char *s, size_t len) {
    ptrdiff_t n = w->write_fn(w->write_ctx, s, len);
    if (n < 0) { w->error = 1; return -1; }
    w->stats.bytes_out += (size_t)n;
    w->stats.lines_out += (size_t)count_newlines(s, len);
    return 0;
}

static int write_line(jsonfold_writer *w, const line *l) {
    for (int i=0;i<l->indent;i++) if (write_string(w, " ", 1) != 0) return -1;
    if (write_string(w, l->text, l->len) != 0) return -1;
    return write_string(w, "\n", 1);
}

static int emit_lines(jsonfold_writer *w, line_vec *lines, int depth);
static int stream_frame(jsonfold_writer *w, frame *f);

static int mark_no_fold(jsonfold_writer *w) {
    for (size_t i=0;i<w->stack.n;i++) w->stack.v[i].fold_ok = 0;
    if (w->stack.n) return stream_frame(w, &w->stack.v[w->stack.n - 1]);
    return 0;
}

static int check_fold_limits(frame *f, const jsonfold_config *cfg) {
    if (!f->fold_ok) return 0;
    if (f->content_lines > 1 || f->items > f->fold_limit || f->child_nesting >= cfg->fold_nesting) f->fold_ok = 0;
    return f->fold_ok;
}

static int can_merge(const jsonfold_config *cfg, const line *prev, const line *ln, int limit) {
    return prev->indent == ln->indent && prev->items + ln->items <= limit &&
           (size_t)prev->indent + prev->len + 1 + ln->len <= (size_t)cfg->width;
}

static int update_after_merge(jsonfold_writer *w, frame *f, line *prev, const line *ln) {
    f->items += ln->items;
    f->leafs += ln->leafs;
    if (prev->items >= f->pack_limit) prev->can_pack = 0;
    if (prev->items >= f->join_limit) prev->can_join = 0;
    if (f->fold_ok && !check_fold_limits(f, w->cfg)) return mark_no_fold(w);
    return 0;
}

static int try_pack(jsonfold_writer *w, frame *f, line *ln) {
    if (f->pack_limit <= 1 || !ln->can_pack || f->lines.n == 0) return 0;
    line *prev = &f->lines.v[f->lines.n - 1];
    if (!prev->can_pack || prev->child_nesting >= w->cfg->pack_nesting || !can_merge(w->cfg, prev, ln, f->pack_limit)) return 0;
    if (line_join(prev, ln) != 0) return -1;
    return update_after_merge(w, f, prev, ln) == 0 ? 1 : -1;
}

static int try_join(jsonfold_writer *w, frame *f, line *ln) {
    if (f->join_limit <= 1 || !ln->can_join || ln->child_nesting >= w->cfg->join_nesting || f->lines.n == 0) return 0;
    line *prev = &f->lines.v[f->lines.n - 1];
    if (!prev->can_join || prev->child_nesting >= w->cfg->join_nesting || !can_merge(w->cfg, prev, ln, f->join_limit)) return 0;
    if (line_join(prev, ln) != 0) return -1;
    return update_after_merge(w, f, prev, ln) == 0 ? 1 : -1;
}

static int add_to_frame(jsonfold_writer *w, frame *f, line *ln) {
    if (f->lines.n) {
        int r = ln->can_pack ? try_pack(w, f, ln) : 0;
        if (r < 0) return -1;
        if (r) { line_free(ln); return 0; }
        r = ln->can_join ? try_join(w, f, ln) : 0;
        if (r < 0) return -1;
        if (r) { line_free(ln); return 0; }
    } else if (!f->fold_ok && !ln->can_pack && !ln->can_join) {
        int rc = write_line(w, ln); line_free(ln); return rc;
    }

    if (line_vec_push_take(&f->lines, ln) != 0) return -1;
    line *stored = &f->lines.v[f->lines.n - 1];

    if (f->fold_ok && line_width(stored) > (size_t)w->cfg->width) {
        if (mark_no_fold(w) != 0) return -1;
    }
    if (stored->closer == JSONFOLD_KIND_NONE) {
        f->content_lines++;
        f->leafs += stored->leafs;
        f->items += stored->items;
        if (stored->child_nesting >= f->child_nesting) f->child_nesting = stored->child_nesting + 1;
        if (f->fold_ok && !check_fold_limits(f, w->cfg)) {
            if (mark_no_fold(w) != 0) return -1;
        }
    }
    if (!f->fold_ok) return stream_frame(w, f);
    return 0;
}

static int emit_line(jsonfold_writer *w, line *ln) {
    if (!w->stack.n) { int rc = write_line(w, ln); line_free(ln); return rc; }
    return add_to_frame(w, &w->stack.v[w->stack.n - 1], ln);
}

static int emit_lines(jsonfold_writer *w, line_vec *lines, int depth) {
    if (!lines->n) return 0;
    if (depth < 0) {
        for (size_t i=0;i<lines->n;i++) if (write_line(w, &lines->v[i]) != 0) return -1;
        line_vec_clear(lines);
        return 0;
    }
    frame *f = &w->stack.v[depth];
    for (size_t i=0;i<lines->n;i++) {
        line tmp = lines->v[i]; memset(&lines->v[i], 0, sizeof(lines->v[i]));
        if (add_to_frame(w, f, &tmp) != 0) { line_free(&tmp); return -1; }
    }
    lines->n = 0;
    return 0;
}

static int stream_frame(jsonfold_writer *w, frame *f) {
    if (!f->lines.n) return 0;
    line keep = {0}; int has_keep = 0;
    line *last = &f->lines.v[f->lines.n - 1];
    if (last->can_pack || last->can_join) { keep = line_vec_pop(&f->lines); has_keep = 1; }
    if (emit_lines(w, &f->lines, f->depth - 1) != 0) { line_free(&keep); return -1; }
    if (has_keep && line_vec_push_take(&f->lines, &keep) != 0) { line_free(&keep); return -1; }
    return 0;
}

static int make_folded_line(line *out, frame *f, jsonfold_kind pk) {
    size_t total = 0;
    for (size_t i=0;i<f->lines.n;i++) total += f->lines.v[i].len + (i ? 1 : 0);
    char *text = (char *)malloc(total + 1);
    if (!text) return -1;
    size_t pos = 0;
    for (size_t i=0;i<f->lines.n;i++) {
        if (i) text[pos++] = ' ';
        memcpy(text + pos, f->lines.v[i].text, f->lines.v[i].len);
        pos += f->lines.v[i].len;
    }
    text[pos] = 0;
    *out = (line){.indent=f->lines.v[0].indent, .text=text, .len=pos, .parent_kind=pk,
                  .items=1, .leafs=f->leafs, .child_nesting=f->child_nesting < 0 ? 0 : f->child_nesting,
                  .opener=JSONFOLD_KIND_NONE, .closer=JSONFOLD_KIND_NONE, .can_join=1, .can_pack=0};
    return 0;
}

static int try_fold(jsonfold_writer *w, frame *f, line *out, int *did_fold) {
    *did_fold = 0;
    if (!f->fold_ok || f->content_lines != 1 || f->lines.n != 3) return 0;
    size_t folded_len = 0;
    for (size_t i=0;i<f->lines.n;i++) folded_len += 1 + f->lines.v[i].len;
    folded_len--;
    if ((size_t)f->lines.v[0].indent + folded_len > (size_t)w->cfg->width) return 0;
    if (make_folded_line(out, f, parent_kind(w)) != 0) return -1;
    *did_fold = 1;
    return 0;
}

static int close_frame(jsonfold_writer *w, line *closer, jsonfold_kind closing_kind) {
    if (!w->stack.n) { int rc = write_line(w, closer); line_free(closer); return rc; }
    frame f = w->stack.v[--w->stack.n]; memset(&w->stack.v[w->stack.n], 0, sizeof(w->stack.v[w->stack.n]));
    if (line_vec_push_take(&f.lines, closer) != 0) { frame_free(&f); return -1; }
    if (f.kind != closing_kind) f.fold_ok = 0;
    line folded = {0}; int did_fold = 0;
    if (try_fold(w, &f, &folded, &did_fold) != 0) { frame_free(&f); return -1; }
    if (did_fold) { line_vec_clear(&f.lines); line_vec_push_take(&f.lines, &folded); }
    int depth = (int)w->stack.n - 1;
    int rc = emit_lines(w, &f.lines, depth);
    frame_free(&f);
    return rc;
}

static int feed(jsonfold_writer *w, line *ln) {
    if (ln->opener != JSONFOLD_KIND_NONE) {
        if (frame_vec_reserve(&w->stack, w->stack.n + 1) != 0) { line_free(ln); return -1; }
        frame *f = &w->stack.v[w->stack.n++];
        memset(f, 0, sizeof(*f));
        f->kind = ln->opener; f->depth = (int)w->stack.n - 1;
        f->pack_limit = choose_limit(ln->opener, w->cfg->pack_array_items, w->cfg->pack_obj_items);
        f->fold_limit = choose_limit(ln->opener, w->cfg->fold_array_items, w->cfg->fold_obj_items);
        f->join_limit = choose_limit(ln->opener, w->cfg->join_array_items, w->cfg->join_obj_items);
        f->fold_ok = 1; f->child_nesting = -1;
        if (line_vec_push_take(&f->lines, ln) != 0) return -1;
        if (line_width(&f->lines.v[0]) > (size_t)w->cfg->width) return mark_no_fold(w);
        return 0;
    }
    if (ln->closer != JSONFOLD_KIND_NONE) return close_frame(w, ln, ln->closer);

    if (w->stack.n) {
        frame *f = &w->stack.v[w->stack.n - 1];
        if (ln->items >= f->pack_limit) ln->can_pack = 0;
        if (ln->items >= f->join_limit) ln->can_join = 0;
    }
    return emit_line(w, ln);
}

static int pending_append(jsonfold_writer *w, const char *buf, size_t len) {
    if (w->pending_len + len + 1 > w->pending_cap) {
        size_t nc = w->pending_cap ? w->pending_cap * 2 : 256;
        while (nc < w->pending_len + len + 1) nc *= 2;
        char *p = (char *)realloc(w->pending, nc);
        if (!p) return -1;
        w->pending = p; w->pending_cap = nc;
    }
    memcpy(w->pending + w->pending_len, buf, len);
    w->pending_len += len;
    w->pending[w->pending_len] = 0;
    return 0;
}

jsonfold_writer *jsonfold_writer_new(jsonfold_write_fn write_fn, void *write_ctx, const jsonfold_config *cfg) {
    if (!write_fn) return NULL;
    jsonfold_writer *w = (jsonfold_writer *)calloc(1, sizeof(*w));
    if (!w) return NULL;
    w->write_fn = write_fn;
    w->write_ctx = write_ctx;
    w->cfg = cfg;
    return w;
}

void jsonfold_writer_free(jsonfold_writer *w) {
    if (!w) return;
    for (size_t i=0;i<w->stack.n;i++) frame_free(&w->stack.v[i]);
    free(w->stack.v);
    free(w->pending);
    free(w);
}

ptrdiff_t jsonfold_write(jsonfold_writer *w, const char *buf, size_t len) {
    if (!w || (!buf && len)) return -1;
    w->stats.bytes_in += len;
    w->stats.lines_in += (size_t)count_newlines(buf, len);
    if (!w->cfg) {
        return write_string(w, buf, len) == 0 ? (ptrdiff_t)len : -1;
    }
    if (pending_append(w, buf, len) != 0) return -1;

    size_t start = 0;
    for (;;) {
        void *p = memchr(w->pending + start, '\n', w->pending_len - start);
        if (!p) break;
        size_t nl = (char *)p - w->pending;
        line ln = {0};
        if (parse_line(&ln, w->pending + start, nl - start, parent_kind(w)) != 0) return -1;
        if (feed(w, &ln) != 0) return -1;
        start = nl + 1;
    }
    if (start) {
        memmove(w->pending, w->pending + start, w->pending_len - start);
        w->pending_len -= start;
        w->pending[w->pending_len] = 0;
    }
    if (w->pending_len > (size_t)w->cfg->width) mark_no_fold(w);
    return (ptrdiff_t)len;
}

int jsonfold_finish(jsonfold_writer *w) {
    if (!w) return -1;
    if (w->cfg && w->pending_len) {
        line ln = {0};
        if (parse_line(&ln, w->pending, w->pending_len, parent_kind(w)) != 0) return -1;
        w->pending_len = 0;
        if (feed(w, &ln) != 0) return -1;
    } else if (!w->cfg && w->pending_len) {
        if (write_string(w, w->pending, w->pending_len) != 0) return -1;
        w->pending_len = 0;
    }
    for (size_t i=0;i<w->stack.n;i++) {
        for (size_t j=0;j<w->stack.v[i].lines.n;j++) if (write_line(w, &w->stack.v[i].lines.v[j]) != 0) return -1;
        frame_free(&w->stack.v[i]);
    }
    w->stack.n = 0;
    return w->error ? -1 : 0;
}

int jsonfold_flush(jsonfold_writer *w) { return jsonfold_finish(w); }
const jsonfold_stats *jsonfold_get_stats(const jsonfold_writer *w) { return w ? &w->stats : NULL; }

static ptrdiff_t file_write(void *ctx, const char *buf, size_t len) {
    return fwrite(buf, 1, len, (FILE *)ctx) == len ? (ptrdiff_t)len : -1;
}

jsonfold_writer *jsonfold_file_writer_new(FILE *fp, const jsonfold_config *cfg) {
    return jsonfold_writer_new(file_write, fp, cfg);
}
