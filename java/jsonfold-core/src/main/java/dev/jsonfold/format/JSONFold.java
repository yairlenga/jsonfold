package dev.jsonfold.format;

import java.util.Map;

/**
 * Mutable configuration object for JSONFold formatting.
 *
 * <p>Preset instances are kept internally, but {@link #preset(String)}
 * always returns a clone, so callers may safely modify the returned object
 * with setters.</p>
 */
public class JSONFold implements Cloneable {

    public static final int MAX_ARRAY_ITEMS = 1000;
    public static final int MAX_OBJ_ITEMS = 1000;
    public static final int MAX_NESTING = 10;
    public static final int DEFAULT_WIDTH = 100;

    int width = DEFAULT_WIDTH;

    int packArrayItems = 8;
    int packObjItems = 4;
    int packNesting = 1;

    int foldArrayItems = 8;
    int foldObjItems = 4;
    int foldNesting = 1;

    int joinArrayItems = 8;
    int joinObjItems = 4;
    int joinNesting = 1;

    public JSONFold() {
    }

    public JSONFold(int width)
    {
        super();
        this.width = width ;
    }

    @Override
    public JSONFold clone() {
        try {
            return (JSONFold) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    public JSONFold withWidth(int width) {
        this.width = width;
        return this;
    }

    private static final Map<String, JSONFold> PRESETS = Map.ofEntries(
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

    public static JSONFold preset(String name) {
        if ( name.equals("off")) return null ;

        JSONFold cfg = PRESETS.get(name == null ? "" : name);
        if (cfg == null) {
            throw new IllegalArgumentException("Unknown JSONFold preset: " + name);
        }
        return cfg.clone();
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getPackArrayItems() {
        return packArrayItems;
    }

    public void setPackArrayItems(int packArrayItems) {
        this.packArrayItems = packArrayItems;
    }

    public int getPackObjItems() {
        return packObjItems;
    }

    public void setPackObjItems(int packObjItems) {
        this.packObjItems = packObjItems;
    }

    public int getPackNesting() {
        return packNesting;
    }

    public void setPackNesting(int packNesting) {
        this.packNesting = packNesting;
    }

    public int getFoldArrayItems() {
        return foldArrayItems;
    }

    public void setFoldArrayItems(int foldArrayItems) {
        this.foldArrayItems = foldArrayItems;
    }

    public int getFoldObjItems() {
        return foldObjItems;
    }

    public void setFoldObjItems(int foldObjItems) {
        this.foldObjItems = foldObjItems;
    }

    public int getFoldNesting() {
        return foldNesting;
    }

    public void setFoldNesting(int foldNesting) {
        this.foldNesting = foldNesting;
    }

    public int getJoinArrayItems() {
        return joinArrayItems;
    }

    public void setJoinArrayItems(int joinArrayItems) {
        this.joinArrayItems = joinArrayItems;
    }

    public int getJoinObjItems() {
        return joinObjItems;
    }

    public void setJoinObjItems(int joinObjItems) {
        this.joinObjItems = joinObjItems;
    }

    public int getJoinNesting() {
        return joinNesting;
    }

    public void setJoinNesting(int joinNesting) {
        this.joinNesting = joinNesting;
    }

    public void setPackItems(int value) {
        this.packArrayItems = value;
        this.packObjItems = value;
    }

    public void setFoldItems(int value) {
        this.foldArrayItems = value;
        this.foldObjItems = value;
    }

    public void setJoinItems(int value) {
        this.joinArrayItems = value;
        this.joinObjItems = value;
    }

    private static JSONFold createDefault() {
        return new JSONFold();
    }

    private static JSONFold createNone() {
        JSONFold cfg = new JSONFold();

        cfg.packArrayItems = 0;
        cfg.packObjItems = 0;
        cfg.packNesting = 0;

        cfg.foldArrayItems = 0;
        cfg.foldObjItems = 0;
        cfg.foldNesting = 0;

        cfg.joinArrayItems = 0;
        cfg.joinObjItems = 0;
        cfg.joinNesting = 0;

        return cfg;
    }

    private static JSONFold createLow() {
        JSONFold cfg = createDefault();
        cfg.foldNesting = 0;
        cfg.joinNesting = 0;
        return cfg;
    }

    private static JSONFold createMed() {
        JSONFold cfg = createDefault();
        cfg.joinNesting = 0;
        return cfg;
    }

    private static JSONFold createHigh() {
        JSONFold cfg = createDefault();

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

    private static JSONFold createMax() {
        JSONFold cfg = createNone();

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

    private static JSONFold createPack() {
        JSONFold cfg = createNone();

        cfg.packArrayItems = MAX_ARRAY_ITEMS;
        cfg.packObjItems = MAX_OBJ_ITEMS;
        cfg.packNesting = MAX_NESTING;

        return cfg;
    }

    private static JSONFold createFold() {
        JSONFold cfg = createNone();

        cfg.foldArrayItems = MAX_ARRAY_ITEMS;
        cfg.foldObjItems = MAX_OBJ_ITEMS;
        cfg.foldNesting = MAX_NESTING;

        return cfg;
    }

    private static JSONFold createJoin() {
        JSONFold cfg = createNone();

        cfg.foldArrayItems = MAX_ARRAY_ITEMS;
        cfg.foldObjItems = MAX_OBJ_ITEMS;
        cfg.foldNesting = MAX_NESTING;

        cfg.joinArrayItems = MAX_ARRAY_ITEMS;
        cfg.joinObjItems = MAX_OBJ_ITEMS;
        cfg.joinNesting = MAX_NESTING;

        return cfg;
    }

    public static JSONFold none()
    {
        return createNone() ;
    }

    public static JSONFold low()
    {
        return createLow() ;
    }

    public static JSONFold med()
    {
        return createMed() ;
    }

    public static JSONFold high()
    {
        return createHigh();
    }

    public static JSONFold max()
    {
        return max() ;
    }

    public static JSONFold defaults()
    {
        return createDefault() ;
    }

    static JSONFold pack()
    {
        return createPack() ;
    }

    static JSONFold fold()
    {
        return createFold() ;
    }

    static JSONFold join()
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