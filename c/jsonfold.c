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
#define JSONFOLD_DEFAULT_WIDTH   100
#define JSONFOLD_MAX_GRID_LINES  1000

#define PART_VEC_INLINE_ITEMS    8

// count_t is integer which will never be negative. index, sizes, byte counts.
typedef int count_t ;

typedef enum jsonfold_kind {
    JSONFOLD_KIND_NONE = 0,
    JSONFOLD_KIND_DICT = 1,
    JSONFOLD_KIND_LIST = 2
} jsonfold_kind, JFKind ;

typedef enum jsonfold_align {
    JSONFOLD_ALIGN_NONE = 0,
    JSONFOLD_ALIGN_LEFT = 1,
    JSONFOLD_ALIGN_CENTER = 2,
    JSONFOLD_ALIGN_RIGHT = 3,
} JFAlign ;

typedef struct line_part {
    count_t off ;
    count_t len ;
    count_t width ;
    JFAlign align:2 ;    
} *JFPart ;

typedef struct part_vec {
    bool is_dynamic:1 ;
    count_t n ;
    count_t cap ;
    int len ;            // Length of parts
    JFPart v ;
    struct line_part inline_parts[PART_VEC_INLINE_ITEMS] ;
} *JFPartVec ;

typedef struct line {
    int indent;
    char *text;
    count_t text_len;
    struct part_vec parts ;
    jsonfold_kind kind;
    count_t items;
    count_t leafs;
    count_t child_nesting;
    jsonfold_kind opener;
    jsonfold_kind closer;
    bool can_join:1;
    bool can_pack:1;
    bool can_grid:1;
} *JFLine ;

typedef struct line_vec {
    JFLine v;
    count_t n;
    count_t cap;
} *JFLineVec;

typedef struct frame {
    jsonfold_kind kind;
    count_t depth;
    struct line_vec lines;
    count_t pack_limit;
    count_t fold_limit;
    count_t join_limit;
    count_t grid_limit;
    count_t grid_min_items;
    count_t content_lines;
    count_t items;
    count_t leafs;
    count_t child_nesting;
    bool fold_ok:1;
    bool can_grid:1 ;
} *JFFrame;

typedef struct frame_vec {
    JFFrame v;
    count_t n;
    count_t cap;
} *JFFrameVec;

struct char_vec {
    char *v ;
    count_t n ;
    count_t cap ;
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

static char *str_ndup(const char *s, count_t len)
{
    char *copy = malloc(len+1) ;
    if ( !copy ) return copy ;
    memcpy(copy, s, len) ;
    copy[len] = 0 ;
    return copy ;
}

static count_t count_newlines(const char *s, int len) {
    int n = 0;
    for (int i=0;i<len;i++) if (s[i] == '\n') n++;
    return n;
}

// Same as BSD recallocarray
void *vec_resize(void *vec, count_t old_count, count_t new_count, count_t sz)
{
    vec = realloc(vec, new_count*sz) ;
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
    .width            = JSONFOLD_DEFAULT_WIDTH,
} ;

static const struct jsonfold_config CFG_DEFAULT = {
    .width            = JSONFOLD_DEFAULT_WIDTH,
    .pack_array_items = 10,
    .pack_obj_items   = 5,
    .pack_nesting     = 1,
    .fold_array_items = 10,
    .fold_obj_items   = 5,
    .fold_nesting     = 2,

    .grid_array_items = JSONFOLD_MAX_ARRAY_ITEMS,
    .grid_obj_items   = JSONFOLD_MAX_OBJ_ITEMS,
    .grid_min_lines   = 3,
    .grid_max_lines   = 100,
    .grid_array_min   = 3,
    .grid_obj_min     = 3,

    .join_array_items = 8,
    .join_obj_items   = 4,
    .join_nesting     = 1,
};

static const struct jsonfold_config CFG_CLASSIC = {
    .width            = JSONFOLD_DEFAULT_WIDTH,
    .pack_array_items = 10,
    .pack_obj_items   = 5,
    .pack_nesting     = 1,
    .fold_array_items = 10,
    .fold_obj_items   = 5,
    .fold_nesting     = 2,

    .join_array_items = 8,
    .join_obj_items   = 4,
    .join_nesting     = 1,
};


static const struct jsonfold_config CFG_LOW = {
    .width            = JSONFOLD_DEFAULT_WIDTH,
    .pack_array_items = 10,
    .pack_obj_items   = 5,
    .pack_nesting     = 1,
    .fold_array_items = 8,
    .fold_obj_items   = 4,
    .fold_nesting     = 0,


    .join_array_items = 8,
    .join_obj_items   = 4,
    .join_nesting     = 0,
};

static const struct jsonfold_config CFG_MED = {
    .width            = JSONFOLD_DEFAULT_WIDTH,
    .pack_array_items = 10,
    .pack_obj_items   = 5,
    .pack_nesting     = 1,
    .fold_array_items = 10,
    .fold_obj_items   = 5,
    .fold_nesting     = 2,
    .join_array_items = 8,
    .join_obj_items   = 4,
    .join_nesting     = 0,
};

static const struct jsonfold_config CFG_HIGH = {
    .width            = JSONFOLD_DEFAULT_WIDTH,
    .pack_array_items = 16,
    .pack_obj_items   = 8,
    .pack_nesting     = 4,
    .fold_array_items = 16,
    .fold_obj_items   = 8,
    .fold_nesting     = 4,

    .grid_array_items = JSONFOLD_MAX_ARRAY_ITEMS,
    .grid_obj_items   = JSONFOLD_MAX_OBJ_ITEMS,
    .grid_min_lines   = 3,
    .grid_max_lines   = 100,
    .grid_array_min   = 3,
    .grid_obj_min     = 3,

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

    .grid_array_items = JSONFOLD_MAX_ARRAY_ITEMS,
    .grid_obj_items   = JSONFOLD_MAX_OBJ_ITEMS,
    .grid_min_lines   = 3,
    .grid_max_lines   = JSONFOLD_MAX_GRID_LINES,
    .grid_array_min   = 3,
    .grid_obj_min     = 3,


    .join_array_items = JSONFOLD_MAX_ARRAY_ITEMS,
    .join_obj_items   = JSONFOLD_MAX_OBJ_ITEMS,
    .join_nesting     = JSONFOLD_MAX_NESTING,
};

static const struct jsonfold_config CFG_PACK = {
    .width            = JSONFOLD_DEFAULT_WIDTH,
    .pack_array_items = JSONFOLD_MAX_ARRAY_ITEMS,
    .pack_obj_items   = JSONFOLD_MAX_OBJ_ITEMS,
    .pack_nesting     = JSONFOLD_MAX_NESTING,
};

static const struct jsonfold_config CFG_FOLD = {
    .width            = JSONFOLD_DEFAULT_WIDTH,
    .fold_array_items = JSONFOLD_MAX_ARRAY_ITEMS,
    .fold_obj_items   = JSONFOLD_MAX_OBJ_ITEMS,
    .fold_nesting     = JSONFOLD_MAX_NESTING,
};

static const struct jsonfold_config CFG_JOIN = {
    .width            = JSONFOLD_DEFAULT_WIDTH,
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
    { "classic", &CFG_CLASSIC },
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

////////// JSONFOLD part vec

static void part_vec_init(JFPartVec v)
{
    *v = (struct part_vec) {} ;
    v->cap = PART_VEC_INLINE_ITEMS;
}

static void part_vec_free(JFPartVec pv)
{
    if (pv->is_dynamic ) {
        free(pv->v) ;
    } ;
    *pv = (struct part_vec) {} ;
}

static JFPart part_vec_item(JFPartVec pv, int pos)
{
    return pv->is_dynamic ? &pv->v[pos] : &pv->inline_parts[pos] ;
}

static JFPart part_vec_reserve(JFPartVec pv)
{
	int pos = pv->n++;

    if ( pos < pv->cap ) return part_vec_item(pv, pos) ;

    int new_cap = pv->cap ? pv->cap * 2 : PART_VEC_INLINE_ITEMS;
    pv->v = vec_resize(pv->v, pv->cap, new_cap, sizeof(*pv->v)) ;
    pv->cap = new_cap ;
    if ( ! pv->is_dynamic ) {
        memcpy(pv->v, pv->inline_parts, PART_VEC_INLINE_ITEMS*sizeof(*pv->v)) ;
        pv->is_dynamic = true ;
    }
	return &pv->v[pos];
}

__attribute__((unused))
static JFPart part_vec_append(JFPartVec v, count_t off, count_t len)
{
	JFPart p = part_vec_reserve(v);

	*p = (struct line_part) {
		.off = off,
		.len = len,
	};

	v->len += len;
	if (v->n > 1)
		v->len++;

	return p;
}

static void part_vec_concat(JFPartVec dest, JFPartVec src, int offset)
{
    for (int ip=0 ; ip < src->n ; ip++ ) {
        JFPart part = part_vec_reserve(dest) ;
        *part = *part_vec_item(src, ip) ;
        part->off = offset + part->off ;
    } ;
    if ( dest->len ) dest->len++ ;
    dest->len += src->len ;
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
    return l->indent + l->parts.len ;
}

static struct line line_parse(const char *s, count_t len) {
    count_t start = 0, end = len;
    while (start < len && (s[start] == ' ' || s[start] == '\t')) start++;
    while (end > start && (s[end-1] == ' ' || s[end-1] == '\t' || s[end-1] == '\r')) end--;
    count_t body_len = end - start;

    char *body = str_ndup(s+start, body_len) ;

    jsonfold_kind opener = JSONFOLD_KIND_NONE;
    if (body_len && body[body_len-1] == '{') opener = JSONFOLD_KIND_DICT;
    else if (body_len && body[body_len-1] == '[') opener = JSONFOLD_KIND_LIST;
    jsonfold_kind closer = closing_kind(body, body_len);
    int is_body = opener == JSONFOLD_KIND_NONE && closer == JSONFOLD_KIND_NONE;

    struct line out = (struct line){
        .indent = (int)start,
        .text = body,
        .text_len = body_len,
        .kind = JSONFOLD_KIND_NONE,
        .items = 1,
        .leafs = 1,
        .child_nesting = -1,
        .opener = opener,
        .closer = closer,
        .can_join = is_body,
        .can_pack = is_body,
    };
    part_vec_init(&out.parts) ;
    part_vec_append(&out.parts, 0, body_len) ;
    return out ;
}

static void line_free(JFLine l) {
    free(l->text);
    l->text = NULL ;
    part_vec_free(&l->parts) ;
    *l = EMPTY_LINE ;
}

static bool lines_can_merge(JFConfig cfg, const JFLine prev, const JFLine  ln, int limit) {
    return prev->indent == ln->indent &&
        prev->items + ln->items <= limit &&
        prev->indent + prev->parts.len + 1 + ln->parts.len <= cfg->width;
}

static void line_merge(JFLine dst, const JFLine src) {
    char *p = (char *)realloc(dst->text, dst->text_len + 1 + src->text_len + 1);
    dst->text = p;
    dst->text[dst->text_len++] = ' ';
    int new_off = dst->text_len ;
    memcpy(dst->text + new_off, src->text, src->text_len + 1);
    dst->text_len += src->text_len;
    dst->items += src->items;
    dst->leafs += src->leafs;
    if (src->child_nesting > dst->child_nesting) {
        dst->child_nesting = src->child_nesting;
        dst->can_pack = false;
    }
    part_vec_concat(&dst->parts, &src->parts, new_off) ;

    return ;
}

__attribute__((unused))
static void line_join(JFLine dst, const JFLine src) {
    int old_len = dst->text_len ;
    int total = old_len + (old_len ? 1 : 0) + src->text_len ;
	char *p = realloc(dst->text, total + 1);
	dst->text = p;

	if (old_len) dst->text[dst->text_len++] = ' ';
	count_t new_off = dst->text_len;

	memcpy(dst->text + dst->text_len, src->text, src->text_len + 1);
	dst->text_len += src->text_len;

	JFPartVec src_parts = &src->parts ;
	for (int i = 0 ; i < src->parts.n ; i++) {
        JFPart part = part_vec_item(src_parts, i) ;
		part_vec_append(&dst->parts, new_off + part->off, part->len);
	}

	dst->items += src->items;
	dst->leafs += src->leafs;

	if (src->child_nesting > dst->child_nesting) {
		dst->child_nesting = src->child_nesting;
		dst->can_pack = 0;
	}
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
    // Extend if needed:
    int pos = vec->n++ ;
    if ( pos >= vec->cap ) {
        int new_cap = vec->cap ? vec->cap*2 : 8 ;
        vec->v = vec_resize(vec->v, vec->cap, new_cap, sizeof((*vec->v)));
        vec->cap = new_cap ;
    }
    JFLine entry = &vec->v[pos] ;
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
    for (count_t i=0;i<f->lines.n;i++) total += f->lines.v[i].text_len ;
    return total + f->lines.n - 1 ;
}

static void frame_free(JFFrame f) {
    line_vec_free(&f->lines);
    *f = (struct frame) { 0 } ;
}

static struct line make_folded_line(JFFrame f) {
    count_t total = folded_frame_len(f) ;
    char *text = (char *)malloc(total + 1);

    JFLine first = &f->lines.v[0] ;
    struct line ln = (struct line){
        .indent=first->indent,
        .text=text,
        .text_len=total,
        .kind=f->kind,
        .items=1,
        .leafs=f->leafs,
        .child_nesting=f->child_nesting,
        .opener=JSONFOLD_KIND_NONE,
        .closer=JSONFOLD_KIND_NONE,
        .can_join=1,
        .can_pack=0};

    part_vec_init(&ln.parts) ;

    count_t pos = 0;
    for (count_t i=0;i<f->lines.n;i++) {
        JFLine line = &f->lines.v[i] ;
        if (i) text[pos++] = ' ';
        memcpy(text + pos, f->lines.v[i].text,line->text_len);
        // Copy the parts, then add offset based on where
        // the text is placed into the target buffer
        part_vec_concat(&ln.parts, &line->parts, pos) ;
        pos += f->lines.v[i].text_len;
    }
    text[pos] = 0;

    return ln ;
}

static void frame_fold(JFFrame f)
{
    if ( !f->lines.n) return ;

    struct line folded = make_folded_line(f) ;
    line_vec_clear(&f->lines);
    line_vec_append(&f->lines, &folded); 
} ;

////////// JSONFOLD frame vector

static JFFrame  frame_vec_reserve(JFFrameVec vec) {

    // Check if memory already available
    int pos = vec->n++ ;
    if ( pos >= vec->cap ) {
        int new_cap = vec->cap ? vec->cap*2 : 8 ;
        vec->v = vec_resize(vec->v, vec->cap, new_cap, sizeof(*vec->v));
        vec->cap = new_cap ;
    }
    return &vec->v[pos] ;
}


////////// JSONFOLD Writer

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
    for (int ip=0 ; ip < l->parts.n ; ip++ ) {
        int spaces = ip ? 1 : l->indent ;
        for (int i=0;i<spaces;i++) {
            if (!write_string(w, " ", 1) ) return false ;
        }
        JFPart p = part_vec_item(&l->parts, ip) ;
        if ( !write_string(w, l->text+p->off, p->len)) return false ;
    }
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
        w->stack.v[i].fold_ok = false;

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
    f->items += ln->items;
    f->leafs += ln->leafs;
    line_free(ln) ;

    if (prev->items >= f->pack_limit ) {
        prev->can_pack = false;
    }
    if (prev->items >= f->join_limit) {
        prev->can_join = false;
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
        prev->can_join = false ;
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

static void add_to_frame(JFWriter w, JFFrame f, JFLine line) {

    JFConfig cfg = writer_config(w);

    if (f->lines.n) {
//        line *prev = &f->lines.v[f->lines.n-1] ;
        if ( line->can_pack &&
            // prev->can_pack &&
            try_pack(w, f, line) )
            return ;
    
        if ( line->can_join &&
            // prev->can_join &&
            try_join(w, f, line) )
            return ;

    } else if (!f->fold_ok && !line->can_pack && !line->can_join) {
        write_line(w, line);
        line_free(line);
        return ;
    }

    // Transfer 'ln' content to 'stored', do not use it at this time.
    line = line_vec_append(&f->lines, line) ;

    if (f->fold_ok && line_width(line) > cfg->width) {
        mark_no_fold(w) ;
    }
    if (line->closer == JSONFOLD_KIND_NONE) {
        f->content_lines++;
        f->leafs += line->leafs;
        f->items += line->items;
        if (line->child_nesting >= f->child_nesting) f->child_nesting = line->child_nesting + 1;
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



static bool try_fold(JFWriter w, JFFrame f) {

    JFConfig cfg = writer_config(w);

    if (!f->fold_ok || f->content_lines != 1 || f->lines.n != 3)
        return false;

    count_t folded_len = folded_frame_len(f) ;
    if (f->lines.v[0].indent + folded_len > cfg->width)
        return false;

    frame_fold(f) ;
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
    if (f.kind != closing_kind) f.fold_ok = false;

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
            .fold_ok = true,
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
        if (ln->items >= f->pack_limit) ln->can_pack = false;
        if (ln->items >= f->join_limit) ln->can_join = false;
    }
    emit_line(w, ln);
}

static void append_to_pending(JFWriter w, const char *buf, count_t len) {
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

static JFWriter create_writer(jsonfold_write_fn write_fn, void *write_ctx, const JFConfig cfg, int width) {
    JFWriter w = calloc(1, sizeof(*w));
    w->write_fn = write_fn;
    w->write_ctx = write_ctx;
    w->cfg = *cfg;
    if ( width > 0) w->cfg.width = width ;
    return w;
}

static void finish_writer(JFWriter w)
{
    JFConfig cfg = writer_config(w);
    if ( w->pending_len ) {
        if ( cfg ) {
            struct line ln = line_parse(w->pending, w->pending_len) ;
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

JFWriter jsonfold_create(jsonfold_write_fn write_fn, void *write_ctx, int width, const JFConfig cfg) {
    return create_writer(write_fn, write_ctx, cfg, width) ;
}


bool jsonfold_write(JFWriter w, const char *buf, size_t len) {
    JFConfig cfg = writer_config(w);

    w->stats.bytes_in += len;
    w->stats.lines_in += count_newlines(buf, len);

    // If no config, just pass thru
    if ( !cfg ) {
        return write_string(w, buf, len) ;
    }
    append_to_pending(w, buf, len) ;

    count_t start = 0;
    for (;;) {
        void *p = memchr(w->pending + start, '\n', w->pending_len - start);
        if (!p) break;
        count_t nl = (char *)p - w->pending;
        struct line ln = line_parse(w->pending + start, nl - start) ;
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

bool jsonfold_flush(JFWriter w)
{
    return write_string(w, NULL, 0) ;
}


bool jsonfold_finish(JFWriter w) {
    finish_writer(w) ;
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
    FILE *out = ctx ;
    if ( !buf && !len ) {
        fflush(out) ;
    }
    return fwrite(buf, 1, len, out) == len ? (ptrdiff_t)len : -1;
}

JFWriter jsonfold_file_writer_create(FILE *fp, int width, JFConfig cfg) {
    return create_writer(file_write, fp, cfg, width);
}
