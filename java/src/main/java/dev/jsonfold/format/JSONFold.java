package dev.jsonfold.format;

import java.util.Map;

/**
 * Immutable configuration for JSON folding, packing and joining.
 *
 * <p>A value of 0 disables the corresponding feature.
 *
 * <p>Instances are thread-safe and reusable.
 */
public final class JSONFold {

    public static final int MAX_ARRAY_ITEMS = 1000;
    public static final int MAX_OBJ_ITEMS = 1000;
    public static final int MAX_NESTING = 10;

    /** Target maximum output line width. */
    public final int width;

    /** Phase 1: scalar packing. */
    public final int packArrayItems;
    public final int packObjItems;
    public final int packNesting;

    /** Phase 2: container folding. */
    public final int foldArrayItems;
    public final int foldObjItems;
    public final int foldNesting;

    /** Phase 3: folded-line joining. */
    public final int joinArrayItems;
    public final int joinObjItems;
    public final int joinNesting;

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
     * Return a copy with a different width.
     */
    public JSONFold withWidth(int width) {
        return new JSONFold(
                width,
                packArrayItems,
                packObjItems,
                packNesting,
                foldArrayItems,
                foldObjItems,
                foldNesting,
                joinArrayItems,
                joinObjItems,
                joinNesting);
    }

    /**
     * Return a named preset.
     *
     * @throws IllegalArgumentException if the preset name is unknown
     */
    public static JSONFold preset(String name) {
        JSONFold cfg = PRESETS.get(name);
        if (cfg == null) {
            throw new IllegalArgumentException(
                    "Unknown JSONFold preset: " + name);
        }
        return cfg;
    }

    // --------------------------------------------------------------------
    // Presets
    // --------------------------------------------------------------------

    /**
     * Disable all packing, folding and joining.
     */
    public static final JSONFold NONE =
            new JSONFold(
                    80,
                    0, 0, 0,
                    0, 0, 0,
                    0, 0, 0);

    /**
     * Balanced default configuration.
     */
    public static final JSONFold DEFAULT =
            new JSONFold(
                    80,
                    8, 4, 1,
                    8, 4, 1,
                    8, 4, 1);

    /**
     * Same as DEFAULT, but disallow nested folding/joining.
     */
    public static final JSONFold LOW =
            new JSONFold(
                    80,
                    8, 4, 1,
                    8, 4, 0,
                    8, 4, 0);

    /**
     * Same as DEFAULT, but disallow nested joins.
     */
    public static final JSONFold MED =
            new JSONFold(
                    80,
                    8, 4, 1,
                    8, 4, 1,
                    8, 4, 0);

    /**
     * More aggressive packing and folding.
     */
    public static final JSONFold HIGH =
            new JSONFold(
                    80,
                    16, 8, 4,
                    16, 8, 4,
                    16, 8, 2);

    /**
     * Aggressive compaction, still width-limited.
     */
    public static final JSONFold MAX =
            new JSONFold(
                    255,
                    MAX_ARRAY_ITEMS,
                    MAX_OBJ_ITEMS,
                    MAX_NESTING,
                    MAX_ARRAY_ITEMS,
                    MAX_OBJ_ITEMS,
                    MAX_NESTING,
                    MAX_ARRAY_ITEMS,
                    MAX_OBJ_ITEMS,
                    MAX_NESTING);

    /**
     * Packing only.
     */
    public static final JSONFold PACK =
            new JSONFold(
                    80,
                    MAX_ARRAY_ITEMS,
                    MAX_OBJ_ITEMS,
                    MAX_NESTING,
                    0, 0, 0,
                    0, 0, 0);

    /**
     * Folding only.
     */
    public static final JSONFold FOLD =
            new JSONFold(
                    80,
                    0, 0, 0,
                    MAX_ARRAY_ITEMS,
                    MAX_OBJ_ITEMS,
                    MAX_NESTING,
                    0, 0, 0);

    /**
     * Folding and joining only.
     */
    public static final JSONFold JOIN =
            new JSONFold(
                    80,
                    0, 0, 0,
                    MAX_ARRAY_ITEMS,
                    MAX_OBJ_ITEMS,
                    MAX_NESTING,
                    MAX_ARRAY_ITEMS,
                    MAX_OBJ_ITEMS,
                    MAX_NESTING);

    /**
     * Named preset lookup table.
     */
    public static final Map<String, JSONFold> PRESETS =
            Map.ofEntries(
                    Map.entry("", DEFAULT),
                    Map.entry("default", DEFAULT),
                    Map.entry("none", NONE),
                    Map.entry("low", LOW),
                    Map.entry("med", MED),
                    Map.entry("high", HIGH),
                    Map.entry("max", MAX),
                    Map.entry("pack", PACK),
                    Map.entry("fold", FOLD),
                    Map.entry("join", JOIN)
            );
}