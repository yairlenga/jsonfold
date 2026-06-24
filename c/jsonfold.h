#ifndef JSONFOLD_H
#define JSONFOLD_H

#include <stddef.h>
#include <stdio.h>
#include <stdbool.h>

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

struct jsonfold_config {
    int width;

    /* Phase 1 - pack */
    int pack_array_items;
    int pack_obj_items;
    int pack_nesting;

    /* Phase 2 - fold */
    int fold_array_items;
    int fold_obj_items;
    int fold_nesting;

    /* Phase 3 - grid */
    int grid_array_items;
    int grid_obj_items;

    int grid_min_lines;
    int grid_max_lines;

    int grid_array_min;
    int grid_obj_min;

    /* Phase 4 - join */
    int join_array_items;
    int join_obj_items;
    int join_nesting;

} ;

// Immultable config object
typedef const struct jsonfold_config *JFConfig;


struct jsonfold_stats {
    int bytes_in;
    int bytes_out;
    int lines_in;
    int lines_out;
} ;

// Immutable stats object
typedef const struct jsonfold_stats *JFStats ;

typedef ptrdiff_t (*jsonfold_write_fn)(void *ctx, const char *buf, size_t len);

typedef struct jsonfold_writer *JFWriter;

////////// JSONFOLD CONFIG

// Return the default configuration
extern JFConfig JSONFOLD_CONFIG_DEFAULT;

// Return EMPTY configuration
extern JFConfig JSONFOLD_CONFIG_NONE;

    // Lookup preset configuration
JFConfig jsonfold_config_preset(const char *preset);

    // Clone configuration into mutable structure
struct jsonfold_config *jsonfold_config_create(JFConfig config) ;
    // Destroy cloned configuration.
void jsonfold_config_destroy(JFConfig config) ;

////////// JSONFOLD Writer

JFWriter jsonfold_create(jsonfold_write_fn write_fn,
                                      void *write_ctx,
                                      int width,
                                      const JFConfig cfg);

void jsonfold_destroy(JFWriter w);

bool jsonfold_write(JFWriter w, const char *buf, size_t len);
bool jsonfold_flush(JFWriter w);
bool jsonfold_finish(JFWriter w);

JFStats jsonfold_get_stats(JFWriter w);
void jsonfold_stats_destroy(JFStats stats) ;

/* Convenience adapter for FILE*. Does not close fp. */
JFWriter jsonfold_file_writer_create(FILE *fp, int width, JFConfig cfg);

#ifdef __cplusplus
}
#endif

#endif /* JSONFOLD_H */
