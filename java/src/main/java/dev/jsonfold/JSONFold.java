package dev.jsonfold;

import java.util.Map;

/**
 * Immutable formatting configuration.
 *
 * <p>Controls line width, packing, folding and joining behavior.
 *
 * <p>Instances are thread-safe and may be reused.
 */
public final class JSONFold {

    /** Target maximum output line width. */
    public final int width;

    /** Maximum packed array items per line. */
    public final int packArrayItems;

    /** Maximum packed object properties per line. */
    public final int packObjItems;

    /** Maximum packing nesting depth. */
    public final int packNesting;

    /** Maximum folded array items. */
    public final int foldArrayItems;

    /** Maximum folded object properties. */
    public final int foldObjItems;

    /** Maximum folding nesting depth. */
    public final int foldNesting;

    /** Maximum joined array items. */
    public final int joinArrayItems;

    /** Maximum joined object properties. */
    public final int joinObjItems;

    /** Maximum join nesting depth. */
    public final int joinNesting;

    /**
     * Default balanced configuration.
     */
    public static final JSONFold DEFAULT =
            new JSONFold(
                    80,
                    8, 4, 1,
                    8, 4, 1,
                    8, 4, 1);

    /**
     * Disable all packing and folding.
     */
    public static final JSONFold NONE =
            new JSONFold(
                    80,
                    0, 0, 0,
                    0, 0, 0,
                    0, 0, 0);

    /**
     * Built-in preset configurations.
     */
    public static final Map<String, JSONFold> PRESETS = Map.of(
            "", DEFAULT,
            "default", DEFAULT,
            "none", NONE
    );

    /**
     * Create a configuration instance.
     */
    public JSONFold(
            int width,
            int packArrayItems,
            int packObjItems,
            int packNesting,
            int foldArrayItems,
            int foldObjItems,
            int foldNesting,
            int joinArrayItems,
            int joinObjItems,
            int joinNesting) {

        this.width = width;
        this.packArrayItems = packArrayItems;
        this.packObjItems = packObjItems;
        this.packNesting = packNesting;
        this.foldArrayItems = foldArrayItems;
        this.foldObjItems = foldObjItems;
        this.foldNesting = foldNesting;
        this.joinArrayItems = joinArrayItems;
        this.joinObjItems = joinObjItems;
        this.joinNesting = joinNesting;
    }

    /**
     * Lookup a predefined configuration.
     *
     * @param name preset name
     * @return preset configuration
     * @throws IllegalArgumentException if unknown
     */
    public static JSONFold preset(String name) {
        JSONFold cfg = PRESETS.get(name);
        if (cfg == null) {
            throw new IllegalArgumentException(
                    "Unknown JSONFold preset: " + name);
        }
        return cfg;
    }
}