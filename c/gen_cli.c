#include <stdio.h>
#include <string.h>

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
    jsonfold_writer *w = jsonfold_file_writer_new(
        stdout,
        &JSONFOLD_CONFIG_DEFAULT);

    if (!w) {
        fprintf(stderr, "jsonfold: failed to create writer\n");
        return 1;
    }

    if (jsonfold_write(w, DEMO_JSON, strlen(DEMO_JSON)) < 0 ||
        jsonfold_finish(w) != 0) {
        fprintf(stderr, "jsonfold: write failed\n");
        jsonfold_writer_free(w);
        return 1;
    }

    jsonfold_writer_free(w);
    return 0;
}