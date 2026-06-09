#ifndef JSONFOLD_H
#define JSONFOLD_H

#include <stddef.h>
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

#define JSONFOLD_OFF      "off"
#define JSONFOLD_DEFAULT  "default"
#define JSONFOLD_NONE     "none"
#define JSONFOLD_LOW      "low"
#define JSONFOLD_MED      "med"
#define JSONFOLD_HIGH     "high"
#define JSONFOLD_MAX      "max"

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
} ;

typedef const struct jsonfold_config *JFConfig;

typedef struct jsonfold_stats {
    size_t bytes_in;
    size_t bytes_out;
    size_t lines_in;
    size_t lines_out;
} *JFStats ;

typedef ptrdiff_t (*jsonfold_write_fn)(void *ctx, const char *buf, size_t len);

typedef struct jsonfold_writer jsonfold_writer, *JFWriter;

extern JFConfig JSONFOLD_CONFIG_NONE;
extern JFConfig JSONFOLD_CONFIG_DEFAULT;

JFConfig jsonfold_config_preset(const char *preset);
struct jsonfold_config *jsonfold_config_create(JFConfig config) ;
void jsonfold_config_destroy( JFConfig config) ;


JFWriter jsonfold_create(jsonfold_write_fn write_fn,
                                      void *write_ctx,
                                      const JFConfig cfg);
void jsonfold_destroy(jsonfold_writer *w);

ptrdiff_t jsonfold_write(jsonfold_writer *w, const char *buf, size_t len);
int jsonfold_finish(jsonfold_writer *w);
int jsonfold_flush(jsonfold_writer *w);

JFStats jsonfold_get_stats(const jsonfold_writer *w);
void jsonfold_stats_free(JFStats stats) ;

/* Convenience adapter for FILE*. Does not close fp. */
jsonfold_writer *jsonfold_file_writer_new(FILE *fp, JFConfig cfg);

#ifdef __cplusplus
}
#endif

#endif /* JSONFOLD_H */
