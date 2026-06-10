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

// count_t is integer which will never be negative. index, sizes, byte counts.
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
} *JFLine ;

typedef struct line_vec {
    JFLine v;
    int n;
    int cap;
} *JFLineVec;

typedef struct frame {
    jsonfold_kind kind;
    int depth;
    struct line_vec lines;
    int pack_limit;
    int fold_limit;
    int join_limit;
    int content_lines;
    int items;
    int leafs;
    int fold_ok;
    int child_nesting;
} *JFFrame;

typedef struct frame_vec {
    JFFrame v;
    int n;
    int cap;
} *JFFrameVec;

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

////////// JSONFOLD Utilitiles
typedef const struct jsonfold_writer *ConstWriter ;

static inline bool str_eq(const char *s1, const char *s2)
{
    return strcmp(s1, s2) == 0 ;
}

static count_t count_newlines(const char *s, int len) {
    int n = 0;
    for (int i=0;i<len;i++) if (s[i] == '\n') n++;
    return n;
}

// Same as BSD recallocarray
void *resize_vec(void *vec, count_t *p_count, count_t new_count, count_t sz)
{
    count_t old_count = *p_count ;
    vec = realloc(vec, new_count*sz) ;
    *p_count = new_count ;
    int change = new_count - old_count ;
    if ( change > 0 ) {
        memset( ((char *) vec) + (old_count * sz), 0, change * sz) ;
    }
    return vec ;
}

////////// JSONFOLD_CONFIG

// width = 0 indicate no formatting at all.
static const struct jsonfold_config CFG_OFF = {
    .width            = 0,
} ;


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
    { "off", &CFG_OFF },
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
        if ( str_eq(preset, presets[i].name)) return presets[i].config ;
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

static const struct line EMPTY_LINE = {0} ;

static jsonfold_kind closing_kind(const char *s, count_t len) {
    if (len == 1 && s[0] == '}') return JSONFOLD_KIND_DICT;
    if (len == 2 && s[0] == '}' && s[1] == ',') return JSONFOLD_KIND_DICT;
    if (len == 1 && s[0] == ']') return JSONFOLD_KIND_LIST;
    if (len == 2 && s[0] == ']' && s[1] == ',') return JSONFOLD_KIND_LIST;
    return JSONFOLD_KIND_NONE;
}

static count_t line_width(const JFLine l) {
    return l->indent + l->len;
}

static bool line_parse(JFLine out, const char *s, count_t len, jsonfold_kind parent_kind) {
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

    *out = (struct line){
        .indent = (int)start,
        .text = body,
        .len = body_len,
        .parent_kind = parent_kind,
        .items = 1,
        .leafs = 1,
        .child_nesting = -1,
        .opener = opener,
        .closer = closer,
        .can_join = is_body,
        .can_pack = is_body,
    };
    return true;
}

static void line_free(JFLine l) {
    free(l->text);
    *l = EMPTY_LINE ;
}

static bool lines_can_merge(JFConfig cfg, const JFLine prev, const JFLine  ln, int limit) {
    return prev->indent == ln->indent &&
        prev->items + ln->items <= limit &&
        prev->indent + prev->len + 1 + ln->len <= cfg->width;
}

static void line_merge(JFLine dst, const JFLine src) {
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

////////// JSONFOLD Line Vector

static void line_vec_clear(JFLineVec lv) { 
    for (count_t i=0;i<lv->n;i++) line_free(&lv->v[i]);
    lv->n = 0;
}

static void line_vec_free(JFLineVec lv) {
    line_vec_clear(lv);
    free(lv->v);
    *lv = (struct line_vec) { 0 } ;
}

static JFLine line_vec_reserve(JFLineVec vec) {
    // Check if memory already available
    int pos = vec->n++ ;
    if ( pos < vec->cap ) {
        return &vec->v[pos] ;
    }

    // Need to extend
    int new_cap = vec->cap ? vec->cap*2 : 8 ;
    vec->v = resize_vec(vec->v, &vec->cap, new_cap, sizeof((*vec->v)));
    JFLine entry = &vec->v[pos] ;
    memset(entry, 0, (new_cap-vec->cap)*sizeof(*vec->v)) ;
    vec->cap = new_cap ;
    return entry ;
}

static JFLine line_vec_append(JFLineVec lv, JFLine l) {
    JFLine new_entry = line_vec_reserve(lv) ;
    *new_entry = *l ;
    *l = EMPTY_LINE ;
    return new_entry ;
}

static struct line line_vec_pop(JFLineVec lv) {
    JFLine p_last = &lv->v[--lv->n] ;
    struct line ln = *p_last ;
    *p_last = EMPTY_LINE ;
    return ln;
}

////////// JSONFOLD frame Methods

static count_t folded_frame_len(JFFrame f)
{
    count_t total = 0;
    for (count_t i=0;i<f->lines.n;i++) total += f->lines.v[i].len ;
    return total + f->lines.n - 1 ;
}

static void frame_free(JFFrame f) {
    line_vec_free(&f->lines);
    *f = (struct frame) { 0 } ;
}

////////// JSONFOLD frame vector

static JFFrame  frame_vec_reserve(JFFrameVec vec) {

    // Check if memory already available
    int pos = vec->n++ ;
    if ( pos < vec->cap ) {
        return &vec->v[pos] ;
    }

    // Need to extend
    int new_cap = vec->cap ? vec->cap*2 : 8 ;
    vec->v = realloc(vec->v, new_cap * sizeof(*vec->v));
    JFFrame entry = &vec->v[pos] ;
    memset(entry, 0, (new_cap-vec->cap)*sizeof(*vec->v)) ;
    vec->cap = new_cap ;
    return entry ;
}


////////// JSONFOLD Writer

static jsonfold_kind parent_kind(JFWriter w) {
    return w->stack.n ? w->stack.v[w->stack.n - 1].kind : JSONFOLD_KIND_NONE;
}

static count_t choose_limit(jsonfold_kind kind, int list_limit, int dict_limit) {
    return kind == JSONFOLD_KIND_LIST ? list_limit : kind == JSONFOLD_KIND_DICT ? dict_limit : 0;
}

static bool write_string(JFWriter w, const char *s, count_t len) {
    ptrdiff_t n = w->write_fn(w->write_ctx, s, len);
    if (n != len) return false ;
    w->stats.bytes_out += n;
    w->stats.lines_out += count_newlines(s, len);
    return true ;
}

static bool write_line(JFWriter w, const JFLine l) {
    for (int i=0;i<l->indent;i++) {
        if (!write_string(w, " ", 1) ) return false ;
    }
    if ( !write_string(w, l->text, l->len)) return false ;
    if ( !write_string(w, "\n", 1)) return false ;
    return true ;
}

static bool write_lines(JFWriter w, JFLineVec lv)
{
    for (count_t i=0;i<lv->n;i++) {
        if ( !write_line(w, &lv->v[i]) ) return false ;
    }
    return true ;
}

static bool stream_frame(JFWriter w, JFFrame f);

static void mark_no_fold(JFWriter w) {
    for (count_t i=0;i<w->stack.n;i++)
        w->stack.v[i].fold_ok = 0;

}

static bool frame_can_fold(JFFrame f, JFConfig cfg) {
    if (f->content_lines > 1 ||
        f->items > f->fold_limit ||
        f->child_nesting >= cfg->fold_nesting) {
        return false ;
    }
    return true ;
}



static void merge_into_frame(JFWriter w, JFFrame f, JFLine prev, const JFLine ln) {
    line_merge(prev, ln) ;
    line_free(ln) ;

    f->items += ln->items;
    f->leafs += ln->leafs;
    if (prev->items >= f->pack_limit ) {
        prev->can_pack = 0;
    }
    if (prev->items >= f->join_limit) {
        prev->can_join = 0;
    }

    if (f->fold_ok ) {
        if ( !frame_can_fold(f, writer_config(w))) {
            mark_no_fold(w);
            stream_frame(w, f) ;
        }
    }
}

static bool try_pack(JFWriter w, JFFrame f, JFLine ln) {
    JFConfig cfg = writer_config(w);

    if (f->pack_limit <= 1 || !ln->can_pack || f->lines.n == 0)
        return false;

    JFLine prev = &f->lines.v[f->lines.n - 1];
    if (!prev->can_pack || prev->child_nesting >= cfg->pack_nesting || !lines_can_merge(cfg, prev, ln, f->pack_limit))
        return false;

    merge_into_frame(w, f, prev, ln) ;
    if (!prev->can_pack) {
        prev->can_join = 0 ;
    }
    return true ;
}

static bool try_join(JFWriter w, JFFrame f, JFLine ln) {
    JFConfig cfg = writer_config(w);

    if (f->join_limit <= 1 || !ln->can_join || ln->child_nesting >= cfg->join_nesting || f->lines.n == 0)
        return false ;

    JFLine prev = &f->lines.v[f->lines.n - 1];
    if (!prev->can_join || prev->child_nesting >= cfg->join_nesting || !lines_can_merge(cfg, prev, ln, f->join_limit)) {
        return false ;
    }

    merge_into_frame(w, f, prev, ln) ;
    return true ;
}

static void add_to_frame(JFWriter w, JFFrame f, JFLine ln) {

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

    JFLine stored = line_vec_append(&f->lines, ln) ;

    if (f->fold_ok && line_width(stored) > cfg->width) {
        mark_no_fold(w) ;
    }
    if (stored->closer == JSONFOLD_KIND_NONE) {
        f->content_lines++;
        f->leafs += stored->leafs;
        f->items += stored->items;
        if (stored->child_nesting >= f->child_nesting) f->child_nesting = stored->child_nesting + 1;
        if (f->fold_ok && !frame_can_fold(f, cfg)) {
            mark_no_fold(w) ;
        }
    }
    if (!f->fold_ok) {
        stream_frame(w, f);
    }
    return ;
}

static void emit_line(JFWriter w, JFLine ln) {
    if (!w->stack.n) {
        write_line(w, ln);
        line_free(ln);
        return;
    }
    add_to_frame(w, &w->stack.v[w->stack.n - 1], ln);
}

static void emit_lines(JFWriter w, JFLineVec lines, int depth) {
    if (!lines->n) return ;
    if (depth < 0) {
        write_lines(w, lines) ;
        line_vec_clear(lines);
        return ;
    }
    JFFrame f = &w->stack.v[depth];
    for (count_t i=0;i<lines->n;i++) {
        struct line tmp = lines->v[i];
        lines->v[i] = EMPTY_LINE ;
        add_to_frame(w, f, &tmp);
    }
    lines->n = 0;
}

static bool stream_frame(JFWriter w, JFFrame f) {
    if (!f->lines.n) return false;
    struct line keep = EMPTY_LINE;
    bool has_keep = false;
    JFLine last = &f->lines.v[f->lines.n - 1];
    if (last->can_pack || last->can_join) {
        keep = line_vec_pop(&f->lines);
        has_keep = true;
    }
    emit_lines(w, &f->lines, f->depth - 1) ;
    if (has_keep) line_vec_append(&f->lines, &keep) ;
    return true ;
}

static struct line make_folded_line(JFFrame f, jsonfold_kind pk) {
    count_t total = folded_frame_len(f) ;
    char *text = (char *)malloc(total + 1);
    count_t pos = 0;
    for (count_t i=0;i<f->lines.n;i++) {
        if (i) text[pos++] = ' ';
        memcpy(text + pos, f->lines.v[i].text, f->lines.v[i].len);
        pos += f->lines.v[i].len;
    }
    text[pos] = 0;
    struct line ln = (struct line){
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

static bool try_fold(JFWriter w, JFFrame f) {

    JFConfig cfg = writer_config(w);

    if (!f->fold_ok || f->content_lines != 1 || f->lines.n != 3)
        return false;

    count_t folded_len = folded_frame_len(f) ;
    if (f->lines.v[0].indent + folded_len > cfg->width)
        return false;

    struct line folded = make_folded_line(f, parent_kind(w)) ;
    line_vec_clear(&f->lines);
    line_vec_append(&f->lines, &folded); 
    return true;
}

static void close_frame(JFWriter w, JFLine closer, jsonfold_kind closing_kind) {
    if (!w->stack.n) {
        write_line(w, closer);
        line_free(closer);
        return ;
    } ;

    // Remove last frame
    int pos = --w->stack.n ;
    struct frame f = w->stack.v[pos];
    w->stack.v[pos] = (struct frame) { 0 } ;

    closer = line_vec_append(&f.lines, closer) ;
    if (f.kind != closing_kind) f.fold_ok = 0;

    try_fold(w, &f) ;
    emit_lines(w, &f.lines, f.depth-1);
    frame_free(&f);
}

static void feed(JFWriter w, JFLine ln) {

    JFConfig cfg = writer_config(w);

    if (ln->opener != JSONFOLD_KIND_NONE) {
        JFFrame f = frame_vec_reserve(&w->stack) ;
        *f = (struct frame) {
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
        JFFrame f = &w->stack.v[w->stack.n - 1];
        if (ln->items >= f->pack_limit) ln->can_pack = 0;
        if (ln->items >= f->join_limit) ln->can_join = 0;
    }
    emit_line(w, ln);
}

static void pending_append(JFWriter w, const char *buf, count_t len) {
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

static JFWriter create_writer(jsonfold_write_fn write_fn, void *write_ctx, const JFConfig cfg) {
    JFWriter w = calloc(1, sizeof(*w));
    w->write_fn = write_fn;
    w->write_ctx = write_ctx;
    w->cfg = *cfg;
    return w;
}

static void flush_writer(JFWriter w)
{
    JFConfig cfg = writer_config(w);
    if ( w->pending_len ) {
        if ( cfg ) {
            struct line ln = EMPTY_LINE ;
            line_parse(&ln, w->pending, w->pending_len, parent_kind(w)) ;
            w->pending_len = 0;
            feed(w, &ln) ;
        } else {
            write_string(w, w->pending, w->pending_len) ;
        }
    }

    for (count_t i=0;i<w->stack.n;i++) {
        JFFrame f = &w->stack.v[i] ;
        write_lines(w, &f->lines) ;
        frame_free(&w->stack.v[i]);
    }
    w->stack.n = 0;
}

JFWriter jsonfold_create(jsonfold_write_fn write_fn, void *write_ctx, const JFConfig cfg) {
    return create_writer(write_fn, write_ctx, cfg) ;
}


ptrdiff_t jsonfold_write(JFWriter w, const char *buf, size_t len) {
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
        struct line ln = EMPTY_LINE ;
        line_parse(&ln, w->pending + start, nl - start, parent_kind(w)) ;
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

bool jsonfold_finish(JFWriter w) {
    flush_writer(w) ;
    return w->error == 0 ;
}

JFStats jsonfold_get_stats(JFWriter w) {
    struct jsonfold_stats *stats = malloc(sizeof(*stats)) ;
    *stats = w->stats ;
    return stats ;
}

void jsonfold_stats_destroy(JFStats stats) {
    free((void *) stats) ;
}

void jsonfold_destroy(JFWriter w) {
    for (count_t i=0;i<w->stack.n;i++) frame_free(&w->stack.v[i]);
    free(w->stack.v);
    free(w->pending);
    *w = (struct jsonfold_writer) { 0 } ;
    free(w);
}

static ptrdiff_t file_write(void *ctx, const char *buf, size_t len) {
    return fwrite(buf, 1, len, (FILE *)ctx) == len ? (ptrdiff_t)len : -1;
}

JFWriter jsonfold_file_writer_create(FILE *fp, JFConfig cfg) {
    return create_writer(file_write, fp, cfg);
}
