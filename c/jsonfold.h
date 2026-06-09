#ifndef JSONFOLD_H
#define JSONFOLD_H

#include <stddef.h>
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

#define JSONFOLD_MAX_ARRAY_ITEMS 1000
#define JSONFOLD_MAX_OBJ_ITEMS   1000
#define JSONFOLD_MAX_NESTING     10

typedef enum jsonfold_kind {
    JSONFOLD_KIND_NONE = 0,
    JSONFOLD_KIND_DICT = 1,
    JSONFOLD_KIND_LIST = 2
} jsonfold_kind;

typedef enum jsonfold_preset {
    JSONFOLD_PRESET_OFF = 0,
    JSONFOLD_PRESET_NONE,
    JSONFOLD_PRESET_DEFAULT,
    JSONFOLD_PRESET_LOW,
    JSONFOLD_PRESET_MED,
    JSONFOLD_PRESET_HIGH,
    JSONFOLD_PRESET_MAX,
    JSONFOLD_PRESET_PACK,
    JSONFOLD_PRESET_FOLD,
    JSONFOLD_PRESET_JOIN
} jsonfold_preset;

typedef struct jsonfold_config {
    int width;
    int pack_array_items;
    int pack_obj_items;
    int pack_nesting;
    int fold_array_items;
    int fold_obj_items;
    int fold_nesting;
    int join_array_items;
    int join_obj_items;
    int join_nesting;
} jsonfold_config;

typedef struct jsonfold_stats {
    size_t bytes_in;
    size_t bytes_out;
    size_t lines_in;
    size_t lines_out;
} jsonfold_stats;

typedef ptrdiff_t (*jsonfold_write_fn)(void *ctx, const char *buf, size_t len);

typedef struct jsonfold_writer jsonfold_writer;

extern const jsonfold_config JSONFOLD_CONFIG_NONE;
extern const jsonfold_config JSONFOLD_CONFIG_DEFAULT;

const jsonfold_config *jsonfold_preset_config(jsonfold_preset preset);
int jsonfold_preset_by_name(const char *name, jsonfold_preset *out);

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

#ifdef __cplusplus
}
#endif

#endif /* JSONFOLD_H */
