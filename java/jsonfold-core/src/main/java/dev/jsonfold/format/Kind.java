package dev.jsonfold.format;

/**
 * Type of JSON container currently being processed.
 *
 * <p>Used by Line and Frame to distinguish object and array
 * formatting rules.
 */
enum Kind {
    /** Not inside a container. */
    NONE,

    /** JSON object ({ ... }). */
    DICT,

    /** JSON array ([ ... ]). */
    LIST
}