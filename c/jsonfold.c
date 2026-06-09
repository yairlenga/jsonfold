#include "jsonfold.h"

#include <errno.h>
#include <stdlib.h>
#include <string.h>

#include <stddef.h>
#include <stdio.h>
#include <stdbool.h>

#define JSONFOLD_MAX_ARRAY_ITEMS 1000
#define JSONFOLD_MAX_OBJ_ITEMS   1000
#define JSONFOLD_MAX_NESTING     10

// count_t is integer which will never be zero.
typedef int count_t ;

typedef enum jsonfold_kind {
    JSONFOLD_KIND_NONE = 0,
    JSONFOLD_KIND_DICT = 1,
    JSONFOLD_KIND_LIST = 2
} jsonfold_kind, JFKind ;

typedef struct line {
    int indent;
    char *text;
    count_t len;
    jsonfold_kind parent_kind;
    int items;
    int leafs;
    int child_nesting;
    jsonfold_kind opener;
    jsonfold_kind closer;
    bool can_join:1;
    bool can_pack:1;
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

struct frame_vec {
    frame *v;
    int n;
    int cap;
} ;

struct char_vec {
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
    count_t pending_len;
    count_t pending_cap;
    struct frame_vec stack;
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
        if ( !strcmp(preset, presets[i].name)) return presets[i].config ;
    }
    return NULL ;
}

struct jsonfold_config *jsonfold_config_create(JFConfig config)
{
    struct jsonfold_config *new_cfg = malloc(sizeof(*new_cfg)) ;
    *new_cfg = *config ;
    return new_cfg ;
}

void jsonfold_config_destroy( JFConfig config)
{
    free((void *) config) ;
}

////////// JSONFOLD_WRITER

static inline JFConfig writer_config(JFWriter w)
{
    return w->cfg.width ? &w->cfg : NULL ;
}

////////// JSONFOLD Line

static void line_free(line *l) { free(l->text); memset(l, 0, sizeof(*l)); }

////////// JSONFOLD Line Vector

static void line_vec_clear(line_vec *lv) { for (count_t i=0;i<lv->n;i++) line_free(&lv->v[i]); lv->n = 0; }
static void line_vec_free(line_vec *lv) { line_vec_clear(lv); free(lv->v); memset(lv,0,sizeof(*lv)); }

static line *line_vec_reserve(line_vec *vec) {
    // Check if memory already available
    int pos = vec->n++ ;
    if ( pos < vec->cap ) {
        return &vec->v[pos] ;
    }

    // Need to extend
    int new_cap = vec->cap ? vec->cap*2 : 8 ;
    vec->v = realloc(vec->v, new_cap * sizeof(*vec->v));
    line *entry = &vec->v[pos] ;
    memset(entry, 0, (new_cap-vec->cap)*sizeof(*vec->v)) ;
    vec->cap = new_cap ;
    return entry ;
}

static line * line_vec_append(line_vec *lv, line *l) {
    line *new_entry = line_vec_reserve(lv) ;
    *new_entry = *l ;
    *l = (struct line) { 0 } ;
    return new_entry ;
}

static line line_vec_pop(line_vec *lv) {
    line *p_last = &lv->v[--lv->n] ;
    line ln = *p_last ;
    *p_last = (struct line) {0} ;
    return ln;
}

////////// JSONFOLD frame Methods

static void frame_free(frame *f) { line_vec_free(&f->lines); memset(f,0,sizeof(*f)); }

////////// JSONFOLD frame vector

static frame * frame_vec_reserve(struct frame_vec *vec) {

    // Check if memory already available
    int pos = vec->n++ ;
    if ( pos < vec->cap ) {
        return &vec->v[pos] ;
    }

    // Need to extend
    int new_cap = vec->cap ? vec->cap*2 : 8 ;
    vec->v = realloc(vec->v, new_cap * sizeof(*vec->v));
    frame *entry = &vec->v[pos] ;
    memset(entry, 0, (new_cap-vec->cap)*sizeof(*vec->v)) ;
    vec->cap = new_cap ;
    return entry ;
}

static count_t count_newlines(const char *s, int len) {
    int n = 0;
    for (int i=0;i<len;i++) if (s[i] == '\n') n++;
    return n;
}

static jsonfold_kind closing_kind(const char *s, count_t len) {
    if (len == 1 && s[0] == '}') return JSONFOLD_KIND_DICT;
    if (len == 2 && s[0] == '}' && s[1] == ',') return JSONFOLD_KIND_DICT;
    if (len == 1 && s[0] == ']') return JSONFOLD_KIND_LIST;
    if (len == 2 && s[0] == ']' && s[1] == ',') return JSONFOLD_KIND_LIST;
    return JSONFOLD_KIND_NONE;
}

static bool parse_line(line *out, const char *s, count_t len, jsonfold_kind parent_kind) {
    count_t start = 0, end = len;
    while (start < len && (s[start] == ' ' || s[start] == '\t')) start++;
    while (end > start && (s[end-1] == ' ' || s[end-1] == '\t' || s[end-1] == '\r')) end--;
    count_t body_len = end - start;

    char *body = (char *)malloc(body_len + 1);
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
    return true;
}

static count_t line_width(const line *l) { return (count_t)l->indent + l->len; }

static void line_join(line *dst, const line *src) {
    char *p = (char *)realloc(dst->text, dst->len + 1 + src->len + 1);
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
    return ;
}

static jsonfold_kind parent_kind(const jsonfold_writer *w) {
    return w->stack.n ? w->stack.v[w->stack.n - 1].kind : JSONFOLD_KIND_NONE;
}

static count_t choose_limit(jsonfold_kind kind, int list_limit, int dict_limit) {
    return kind == JSONFOLD_KIND_LIST ? list_limit : kind == JSONFOLD_KIND_DICT ? dict_limit : 0;
}

static bool write_string(jsonfold_writer *w, const char *s, count_t len) {
    ptrdiff_t n = w->write_fn(w->write_ctx, s, len);
    if (n != len) return false ;
    w->stats.bytes_out += n;
    w->stats.lines_out += count_newlines(s, len);
    return true ;
}

static bool write_line(jsonfold_writer *w, const line *l) {
    for (int i=0;i<l->indent;i++) {
        if (!write_string(w, " ", 1) ) return false ;
    }
    if ( !write_string(w, l->text, l->len)) return false ;
    if ( !write_string(w, "\n", 1)) return false ;
    return true ;
}

static void emit_lines(jsonfold_writer *w, line_vec *lines, int depth);
static bool stream_frame(jsonfold_writer *w, frame *f);

static void mark_no_fold(jsonfold_writer *w) {
    for (count_t i=0;i<w->stack.n;i++)
        w->stack.v[i].fold_ok = 0;

    if (w->stack.n) stream_frame(w, &w->stack.v[w->stack.n - 1]);
}

static bool check_fold_limits(frame *f, JFConfig cfg) {
    if (!f->fold_ok) return false;
    if (f->content_lines > 1 || f->items > f->fold_limit || f->child_nesting >= cfg->fold_nesting) {
        f->fold_ok = 0;
    }
    return f->fold_ok;
}

static bool can_merge(JFConfig cfg, const line *prev, const line *ln, int limit) {
    return prev->indent == ln->indent && prev->items + ln->items <= limit &&
           prev->indent + prev->len + 1 + ln->len <= cfg->width;
}

static void update_after_merge(jsonfold_writer *w, frame *f, line *prev, const line *ln) {
    f->items += ln->items;
    f->leafs += ln->leafs;
    if (prev->items >= f->pack_limit) prev->can_pack = 0;
    if (prev->items >= f->join_limit) prev->can_join = 0;
    if (f->fold_ok && !check_fold_limits(f, writer_config(w))) {
        mark_no_fold(w);
    }
}

static bool try_pack(jsonfold_writer *w, frame *f, line *ln) {
    JFConfig cfg = writer_config(w);

    if (f->pack_limit <= 1 || !ln->can_pack || f->lines.n == 0) return false;

    line *prev = &f->lines.v[f->lines.n - 1];
    if (!prev->can_pack || prev->child_nesting >= cfg->pack_nesting || !can_merge(cfg, prev, ln, f->pack_limit)) return false;
    line_join(prev, ln) ;
    update_after_merge(w, f, prev, ln) ;
    if (!prev->can_pack) {
        prev->can_join = 0 ;
    }
    return true ;
}

static bool try_join(jsonfold_writer *w, frame *f, line *ln) {
    JFConfig cfg = writer_config(w);

    if (f->join_limit <= 1 || !ln->can_join || ln->child_nesting >= cfg->join_nesting || f->lines.n == 0)
        return false ;

    line *prev = &f->lines.v[f->lines.n - 1];
    if (!prev->can_join || prev->child_nesting >= cfg->join_nesting || !can_merge(cfg, prev, ln, f->join_limit)) return false ;

    line_join(prev, ln) ;
    update_after_merge(w, f, prev, ln) ;
    return true ;
}

static void add_to_frame(jsonfold_writer *w, frame *f, line *ln) {

    JFConfig cfg = writer_config(w);

    if (f->lines.n) {
//        line *prev = &f->lines.v[f->lines.n-1] ;
        if ( ln->can_pack &&
            // prev->can_pack &&
            try_pack(w, f, ln) )
            return ;
    
        if ( ln->can_join &&
            // prev->can_join &&
            try_join(w, f, ln) )
            return ;

    } else if (!f->fold_ok && !ln->can_pack && !ln->can_join) {
        write_line(w, ln);
        line_free(ln);
        return ;
    }

    line *stored = line_vec_append(&f->lines, ln) ;

    if (f->fold_ok && line_width(stored) > cfg->width) {
        mark_no_fold(w) ;
    }
    if (stored->closer == JSONFOLD_KIND_NONE) {
        f->content_lines++;
        f->leafs += stored->leafs;
        f->items += stored->items;
        if (stored->child_nesting >= f->child_nesting) f->child_nesting = stored->child_nesting + 1;
        if (f->fold_ok && !check_fold_limits(f, cfg)) {
            mark_no_fold(w) ;
        }
    }
    if (!f->fold_ok) {
        stream_frame(w, f);
    }
    return ;
}

static void emit_line(jsonfold_writer *w, line *ln) {
    if (!w->stack.n) {
        write_line(w, ln);
        line_free(ln);
        return;
    }
    add_to_frame(w, &w->stack.v[w->stack.n - 1], ln);
}

static void emit_lines(jsonfold_writer *w, line_vec *lines, int depth) {
    if (!lines->n) return ;
    if (depth < 0) {
        for (count_t i=0;i<lines->n;i++) write_line(w, &lines->v[i]) ;
        line_vec_clear(lines);
        return ;
    }
    frame *f = &w->stack.v[depth];
    for (count_t i=0;i<lines->n;i++) {
        line tmp = lines->v[i]; memset(&lines->v[i], 0, sizeof(lines->v[i]));
        add_to_frame(w, f, &tmp);
    }
    lines->n = 0;
}

static bool stream_frame(jsonfold_writer *w, frame *f) {
    if (!f->lines.n) return false;
    line keep = {0}; int has_keep = 0;
    line *last = &f->lines.v[f->lines.n - 1];
    if (last->can_pack || last->can_join) { keep = line_vec_pop(&f->lines); has_keep = 1; }
    emit_lines(w, &f->lines, f->depth - 1) ;
    if (has_keep) line_vec_append(&f->lines, &keep) ;
    return true ;
}

static count_t folded_frame_len(frame *f)
{
    count_t total = 0;
    for (count_t i=0;i<f->lines.n;i++) total += f->lines.v[i].len ;
    return total + f->lines.n - 1 ;
}

static line make_folded_line(frame *f, jsonfold_kind pk) {
    count_t total = folded_frame_len(f) ;
    char *text = (char *)malloc(total + 1);
    count_t pos = 0;
    for (count_t i=0;i<f->lines.n;i++) {
        if (i) text[pos++] = ' ';
        memcpy(text + pos, f->lines.v[i].text, f->lines.v[i].len);
        pos += f->lines.v[i].len;
    }
    text[pos] = 0;
    line ln = (line){
        .indent=f->lines.v[0].indent,
        .text=text,
        .len=pos,
        .parent_kind=pk,
        .items=1,
        .leafs=f->leafs,
        .child_nesting=f->child_nesting,
        .opener=JSONFOLD_KIND_NONE,
        .closer=JSONFOLD_KIND_NONE,
        .can_join=1,
        .can_pack=0};
    return ln ;
}

static bool try_fold(jsonfold_writer *w, frame *f) {

    JFConfig cfg = writer_config(w);

    if (!f->fold_ok || f->content_lines != 1 || f->lines.n != 3)
        return false;

    count_t folded_len = folded_frame_len(f) ;
    if (f->lines.v[0].indent + folded_len > cfg->width)
        return false;

    line folded = make_folded_line(f, parent_kind(w)) ;
    line_vec_clear(&f->lines);
    line_vec_append(&f->lines, &folded); 
    return true;
}

static void close_frame(jsonfold_writer *w, line *closer, jsonfold_kind closing_kind) {
    if (!w->stack.n) {
        write_line(w, closer);
        line_free(closer);
    } ;

    // Remove last frame
    frame f = w->stack.v[--w->stack.n];
    memset(&w->stack.v[w->stack.n], 0, sizeof(w->stack.v[w->stack.n]));

    closer = line_vec_append(&f.lines, closer) ;
    if (f.kind != closing_kind) f.fold_ok = 0;

    try_fold(w, &f) ;
    int depth = (int)w->stack.n - 1;
    emit_lines(w, &f.lines, depth);
    frame_free(&f);
}

static void feed(jsonfold_writer *w, line *ln) {

    JFConfig cfg = writer_config(w);

    if (ln->opener != JSONFOLD_KIND_NONE) {
        frame *f = frame_vec_reserve(&w->stack) ;
        *f = (frame) {
            .kind = ln->opener,
            .depth = w->stack.n - 1,
            .pack_limit = choose_limit(ln->opener, cfg->pack_array_items, cfg->pack_obj_items),
            .fold_limit = choose_limit(ln->opener, cfg->fold_array_items, cfg->fold_obj_items),
            .join_limit = choose_limit(ln->opener, cfg->join_array_items, cfg->join_obj_items),
            .fold_ok = 1,
            .child_nesting = -1,
        } ;

        line_vec_append(&f->lines, ln) ;
        return ;
    }

    if (ln->closer != JSONFOLD_KIND_NONE) {
        close_frame(w, ln, ln->closer);
        return ;
    }

    if (w->stack.n) {
        frame *f = &w->stack.v[w->stack.n - 1];
        if (ln->items >= f->pack_limit) ln->can_pack = 0;
        if (ln->items >= f->join_limit) ln->can_join = 0;
    }
    emit_line(w, ln);
}

static void pending_append(jsonfold_writer *w, const char *buf, count_t len) {
    if (w->pending_len + len + 1 > w->pending_cap) {
        count_t nc = w->pending_cap ? w->pending_cap * 2 : 256;
        while (nc < w->pending_len + len + 1) nc *= 2;
        char *p = (char *)realloc(w->pending, nc);
        w->pending = p; w->pending_cap = nc;
    }
    memcpy(w->pending + w->pending_len, buf, len);
    w->pending_len += len;
    w->pending[w->pending_len] = 0;
}

jsonfold_writer *jsonfold_writer_new(jsonfold_write_fn write_fn, void *write_ctx, const JFConfig cfg) {
    if (!write_fn) return NULL;
    jsonfold_writer *w = (jsonfold_writer *)calloc(1, sizeof(*w));
    if (!w) return NULL;
    w->write_fn = write_fn;
    w->write_ctx = write_ctx;
    w->cfg = *cfg;
    return w;
}

void jsonfold_destroy(jsonfold_writer *w) {
    if (!w) return;
    for (count_t i=0;i<w->stack.n;i++) frame_free(&w->stack.v[i]);
    free(w->stack.v);
    free(w->pending);
    free(w);
}

ptrdiff_t jsonfold_write(jsonfold_writer *w, const char *buf, size_t len) {
    JFConfig cfg = writer_config(w);

    w->stats.bytes_in += len;
    w->stats.lines_in += count_newlines(buf, len);

    // If no config, just pass thru
    if ( !cfg ) {
        return write_string(w, buf, len) == 0 ? (ptrdiff_t)len : -1;
    }
    pending_append(w, buf, len) ;

    count_t start = 0;
    for (;;) {
        void *p = memchr(w->pending + start, '\n', w->pending_len - start);
        if (!p) break;
        count_t nl = (char *)p - w->pending;
        line ln = {0};
        parse_line(&ln, w->pending + start, nl - start, parent_kind(w)) ;
        feed(w, &ln) ;
        start = nl + 1;
    }
    if (start) {
        memmove(w->pending, w->pending + start, w->pending_len - start);
        w->pending_len -= start;
        w->pending[w->pending_len] = 0;
    }
    if (w->pending_len > cfg->width) mark_no_fold(w);
    return (ptrdiff_t)len;
}

int jsonfold_finish(jsonfold_writer *w) {
    JFConfig cfg = writer_config(w);

    if (cfg && w->pending_len) {
        line ln = {0};
        if (parse_line(&ln, w->pending, w->pending_len, parent_kind(w)) != 0) return -1;
        w->pending_len = 0;
        feed(w, &ln) ;
    } else if (!cfg && w->pending_len) {
        if (write_string(w, w->pending, w->pending_len) != 0) return -1;
        w->pending_len = 0;
    }
    for (count_t i=0;i<w->stack.n;i++) {
        for (count_t j=0;j<w->stack.v[i].lines.n;j++) if (write_line(w, &w->stack.v[i].lines.v[j]) != 0) return -1;
        frame_free(&w->stack.v[i]);
    }
    w->stack.n = 0;
    return w->error ? -1 : 0;
}

int jsonfold_flush(jsonfold_writer *w) { return jsonfold_finish(w); }

JFStats jsonfold_get_stats(JFWriter w) {
    struct jsonfold_stats *stats = malloc(sizeof(*stats)) ;
    *stats = w->stats ;
    return stats ;
}

void jsonfold_stats_destroy(JFStats stats) {
    free((void *) stats) ;
}


static ptrdiff_t file_write(void *ctx, const char *buf, size_t len) {
    return fwrite(buf, 1, len, (FILE *)ctx) == len ? (ptrdiff_t)len : -1;
}

jsonfold_writer *jsonfold_file_writer_create(FILE *fp, JFConfig cfg) {
    return jsonfold_writer_new(file_write, fp, cfg);
}
