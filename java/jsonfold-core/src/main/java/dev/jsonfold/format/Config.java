package dev.jsonfold.format;

import java.util.Map;

/**
 * Configuration controlling JSONFold packing, folding, and joining behavior.
 *
 * <p>Most applications should use one of the predefined presets such as
 * {@link #defaultConfig()}, {@link #high()}, or {@link #max()}.
 *
 * <p>Preset configurations are stored internally and copied before being
 * returned, allowing callers to safely customize them through a
 * {@link Builder}.
 */
public class Config {

    public static final int MAX_ARRAY_ITEMS = 1000;
    public static final int MAX_OBJ_ITEMS = 1000;
    public static final int MAX_NESTING = 10;
    public static final int DEFAULT_WIDTH = 100;

    public static final String PRESET_DEFAULT = "default" ;
    public static final String PRESET_NONE = "none" ;
    public static final String PRESET_HIGH = "high" ;
    public static final String PRESET_MED = "med" ;
    public static final String PRESET_LOW = "low" ;
    public static final String PRESET_MAX = "max" ;
    static final String PRESET_FOLD = "fold" ;
    static final String PRESET_PACK = "pack" ;
    static final String PRESET_JOIN = "join" ;

    int width = DEFAULT_WIDTH;

    int packArrayItems ;
    int packObjItems ;
    int packNesting ;

    int foldArrayItems ;
    int foldObjItems ;
    int foldNesting ;

    int joinArrayItems ;
    int joinObjItems ;
    int joinNesting ;

    private Config(boolean isDefault) {
        if ( isDefault ) {
            width = DEFAULT_WIDTH;

            packArrayItems = 8;
            packObjItems = 4;
            packNesting = 1;

            foldArrayItems = 8;
            foldObjItems = 4;
            foldNesting = 1;

            joinArrayItems = 8;
            joinObjItems = 4;
            joinNesting = 1;
        }
    }

    public Config() {
        this(true) ;
    }

    public Config(Config other) {
        this.width = other.width;
        this.packArrayItems = other.packArrayItems;
        this.packObjItems = other.packObjItems;
        this.packNesting = other.packNesting;
        this.foldArrayItems = other.foldArrayItems;
        this.foldObjItems = other.foldObjItems;
        this.foldNesting = other.foldNesting;
        this.joinArrayItems = other.joinArrayItems;
        this.joinObjItems = other.joinObjItems;
        this.joinNesting = other.joinNesting;
    }

    public Config(Config other, int width)
    {
        this(other) ;
        this.width = width ;
    }

    Builder builder()
    {
        return new Builder(this) ;
    }

    /**
     * Fluent builder for {@link Config}.
     *
     * <p>
     * Typically obtained from an existing configuration:
     *
     * <pre>
     * Config cfg = Config.defaultConfig()
     *         .builder()
     *         .joinNesting(0)
     *         .build();
     * </pre>
     */
    public static class Builder extends Config {

        public Builder() {
        }

        public Builder(Config cfg) {
            super(cfg);
        }

        public Builder width(int value) {
            width = value;
            return this;
        }

        public Builder packArrayItems(int value) {
            packArrayItems = value;
            return this;
        }

        public Builder packObjItems(int value) {
            packObjItems = value;
            return this;
        }

        public Builder packNesting(int value) {
            packNesting = value;
            return this;
        }

        public Builder foldArrayItems(int value) {
            foldArrayItems = value;
            return this;
        }

        public Builder foldObjItems(int value) {
            foldObjItems = value;
            return this;
        }

        public Builder foldNesting(int value) {
            foldNesting = value;
            return this;
        }

        public Builder joinArrayItems(int value) {
            joinArrayItems = value;
            return this;
        }

        public Builder joinObjItems(int value) {
            joinObjItems = value;
            return this;
        }

        public Builder joinNesting(int value) {
            joinNesting = value;
            return this;
        }

    /**
     * Build a configuration from the current builder settings.
     *
     * @return configuration instance
     */
        public Config build() {
            return new Config(this);
        }
    }

    private static final Config DEFAULT_CONFIG = new Config(true) ;
    private static final Config NONE_CONFIG = new Config(false) ;
    private static final Config LOW_CONFIG = createLow() ;
    private static final Config MED_CONFIG = createMed() ;
    private static final Config HIGH_CONFIG = createHigh() ;
    private static final Config MAX_CONFIG = createMax() ;

    private static final Config PACK_CONFIG = createPack() ;
    private static final Config FOLD_CONFIG = createFold() ;
    private static final Config JOIN_CONFIG = createJoin() ;

    private static final Map<String, Config> PRESETS = Map.ofEntries(
        Map.entry("", DEFAULT_CONFIG),
        Map.entry(PRESET_DEFAULT, DEFAULT_CONFIG),
        Map.entry(PRESET_NONE, NONE_CONFIG),
        Map.entry(PRESET_LOW, LOW_CONFIG),
        Map.entry(PRESET_MED, MED_CONFIG),
        Map.entry(PRESET_HIGH, HIGH_CONFIG),
        Map.entry(PRESET_MAX, MAX_CONFIG),
        Map.entry(PRESET_PACK, PACK_CONFIG),
        Map.entry(PRESET_FOLD, FOLD_CONFIG),
        Map.entry(PRESET_JOIN, JOIN_CONFIG)
    );

/**
 * Return a copy of a named preset configuration.
 *
 * <p>Supported presets:
 * <ul>
 *   <li>{@code default} - recommended balance of readability and compactness</li>
 *   <li>{@code none} - disable all JSONFold transformations</li>
 *   <li>{@code low} - conservative folding</li>
 *   <li>{@code med} - moderate folding</li>
 *   <li>{@code high} - aggressive folding and joining</li>
 *   <li>{@code max} - maximum compaction</li>
 * </ul>
 *
 * <p>The special preset name {@code off} returns {@code null}, indicating
 * that JSONFold processing should be disabled entirely.
 *
 * @param name preset name
 * @return configuration copy, or {@code null} for {@code off}
 * @throws IllegalArgumentException if the preset name is unknown
 */
    public static Config preset(String name) {
        if ( "off".equals(name)) return null ;

        Config cfg = PRESETS.get(name == null ? "" : name);
        if (cfg == null) {
            throw new IllegalArgumentException("Unknown JSONFold preset: " + name);
        }
        return new Config(cfg) ;
    }

    int getWidth() {
        return width;
    }

    public int getPackArrayItems() {
        return packArrayItems;
    }

    public int getPackObjItems() {
        return packObjItems;
    }

    public int getPackNesting() {
        return packNesting;
    }

    public int getFoldArrayItems() {
        return foldArrayItems;
    }

    public int getFoldObjItems() {
        return foldObjItems;
    }

    public int getFoldNesting() {
        return foldNesting;
    }

    public int getJoinArrayItems() {
        return joinArrayItems;
    }

    public int getJoinObjItems() {
        return joinObjItems;
    }

    public int getJoinNesting() {
        return joinNesting;
    }

    private static Config createDefault() {
        return new Config();
    }

    private static Config createNone() {
        return new Config(false) ;
    }

    private static Config createLow() {
        Config cfg = createDefault() ;
        cfg.foldNesting = 0 ;
        cfg.joinNesting = 0 ;
        return cfg ;
    }

    private static Config createMed() {
        Config cfg = createDefault();
        cfg.joinNesting = 0;
        return cfg;
    }

    private static Config createHigh() {
        Config cfg = createDefault();

        cfg.packArrayItems = 16;
        cfg.packObjItems = 8;
        cfg.packNesting = 4;

        cfg.foldArrayItems = 16;
        cfg.foldObjItems = 8;
        cfg.foldNesting = 4;

        cfg.joinArrayItems = 16;
        cfg.joinObjItems = 8;
        cfg.joinNesting = 2;

        return cfg;
    }

    private static Config createMax() {
        Config cfg = createNone();

        cfg.width = 255 ;
        cfg.packArrayItems = MAX_ARRAY_ITEMS;
        cfg.packObjItems = MAX_OBJ_ITEMS;
        cfg.packNesting = MAX_NESTING;

        cfg.foldArrayItems = MAX_ARRAY_ITEMS;
        cfg.foldObjItems = MAX_OBJ_ITEMS;
        cfg.foldNesting = MAX_NESTING;

        cfg.joinArrayItems = MAX_ARRAY_ITEMS;
        cfg.joinObjItems = MAX_OBJ_ITEMS;
        cfg.joinNesting = MAX_NESTING;

        return cfg;
    }

    private static Config createPack() {
        Config cfg = createNone();

        cfg.packArrayItems = MAX_ARRAY_ITEMS;
        cfg.packObjItems = MAX_OBJ_ITEMS;
        cfg.packNesting = MAX_NESTING;

        return cfg;
    }

    private static Config createFold() {
        Config cfg = createNone();

        cfg.foldArrayItems = MAX_ARRAY_ITEMS;
        cfg.foldObjItems = MAX_OBJ_ITEMS;
        cfg.foldNesting = MAX_NESTING;

        return cfg;
    }

    private static Config createJoin() {
        Config cfg = createNone();

        cfg.foldArrayItems = MAX_ARRAY_ITEMS;
        cfg.foldObjItems = MAX_OBJ_ITEMS;
        cfg.foldNesting = MAX_NESTING;

        cfg.joinArrayItems = MAX_ARRAY_ITEMS;
        cfg.joinObjItems = MAX_OBJ_ITEMS;
        cfg.joinNesting = MAX_NESTING;

        return cfg;
    }

/**
 * Disable all JSONFold packing, folding, and joining.
 *
 * @return configuration copy
 */
    public static Config noneConfig()
    {
        return NONE_CONFIG ;
    }

/**
 * Return the recommended default JSONFold configuration.
 *
 * @return configuration copy
 */
    public static Config defaultConfig()
    {
        return DEFAULT_CONFIG ;
    }
    
    @Override
    public String toString() {
        return "JSONFold{" +
            "width=" + width +
            ", packArrayItems=" + packArrayItems +
            ", packObjItems=" + packObjItems +
            ", packNesting=" + packNesting +
            ", foldArrayItems=" + foldArrayItems +
            ", foldObjItems=" + foldObjItems +
            ", foldNesting=" + foldNesting +
            ", joinArrayItems=" + joinArrayItems +
            ", joinObjItems=" + joinObjItems +
            ", joinNesting=" + joinNesting +
            '}';
    }
}