#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdbool.h>

#include "jsonfold.h"

static const char DEMO_JSON[] =
"{\n"
"  \"a\": [\n"
"    1,\n"
"    2,\n"
"    3\n"
"  ],\n"
"  \"b\": {\n"
"    \"x\": true,\n"
"    \"y\": false\n"
"  }\n"
"}\n";

int main(void)
{
    JFWriter w = jsonfold_file_writer_create(
        stdout,
        JSONFOLD_CONFIG_DEFAULT);

    if (!w) {
        fprintf(stderr, "jsonfold: failed to create writer\n");
        return 1;
    }

    bool ok = true ;
    if (jsonfold_write(w, DEMO_JSON, strlen(DEMO_JSON)) < 0 ||
        jsonfold_finish(w) != 0) {
        fprintf(stderr, "jsonfold: write failed\n");
        ok = false ;
    }

    jsonfold_destroy(w);
    return ok ? EXIT_SUCCESS : EXIT_FAILURE ;
}