package dev.jsonfold.format;

import java.util.Map;

/**
 * Mutable configuration object for JSONFold formatting.
 *
 * <p>Preset instances are kept internally, but {@link #preset(String)}
 * always returns a clone, so callers may safely modify the returned object
 * with setters.</p>
 */
public class Config {

    public static final int MAX_ARRAY_ITEMS = 1000;
    public static final int MAX_OBJ_ITEMS = 1000;
    public static final int MAX_NESTING = 10;
    public static final int DEFAULT_WIDTH = 100;

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

        public Config build() {
            return new Config(this);
        }
    }

    private static final Map<String, Config> PRESETS = Map.ofEntries(
        Map.entry("", createDefault()),
        Map.entry("default", createDefault()),
        Map.entry("none", createNone()),
        Map.entry("low", createLow()),
        Map.entry("med", createMed()),
        Map.entry("high", createHigh()),
        Map.entry("max", createMax()),
        Map.entry("pack", createPack()),
        Map.entry("fold", createFold()),
        Map.entry("join", createJoin())
    );

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

    public static Config none()
    {
        return createNone() ;
    }

    public static Config low()
    {
        return createLow() ;
    }

    public static Config med()
    {
        return createMed() ;
    }

    public static Config high()
    {
        return createHigh();
    }

    public static Config max()
    {
        return createMax() ;
    }

    public static Config defaultConfig()
    {
        return createDefault() ;
    }

    static Config pack()
    {
        return createPack() ;
    }

    static Config fold()
    {
        return createFold() ;
    }

    static Config join()
    {
        return createJoin() ;
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