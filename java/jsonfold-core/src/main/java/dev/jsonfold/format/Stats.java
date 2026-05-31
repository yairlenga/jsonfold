package dev.jsonfold.format;

/**
 * Formatting statistics collected during a JSON folding run.
 *
 * <p>The formatter updates these counters while processing input
 * and producing output. The resulting instance is returned by
 * methods such as {@code dumpi()} for diagnostic and benchmarking
 * purposes.
 *
 * <p>Instances are mutable internally but effectively read-only
 * to library users.
 */
public final class Stats {

    /** Total input bytes received by the formatter. */
    private long bytesIn;

    /** Total output bytes written by the formatter. */
    private long bytesOut;

    /** Total input lines processed. */
    private long linesIn;

    /** Total output lines generated. */
    private long linesOut;

    /**
     * Return the total number of input bytes processed.
     */
    public long getBytesIn() {
        return bytesIn;
    }

    /**
     * Return the total number of output bytes generated.
     */
    public long getBytesOut() {
        return bytesOut;
    }

    /**
     * Return the number of input lines processed.
     */
    public long getLinesIn() {
        return linesIn;
    }

    /**
     * Return the number of output lines generated.
     */
    public long getLinesOut() {
        return linesOut;
    }

    /**
     * Increment the input byte count.
     *
     * <p>Package-private: intended for formatter internals only.
     *
     * @param n number of bytes to add
     */
    void addBytesIn(long n) {
        bytesIn += n;
    }

    /**
     * Increment the output byte count.
     *
     * <p>Package-private: intended for formatter internals only.
     *
     * @param n number of bytes to add
     */
    void addBytesOut(long n) {
        bytesOut += n;
    }

    /**
     * Increment the input line count.
     *
     * <p>Package-private: intended for formatter internals only.
     *
     * @param n number of lines to add
     */
    void addLinesIn(long n) {
        linesIn += n;
    }

    /**
     * Increment the output line count.
     *
     * <p>Package-private: intended for formatter internals only.
     *
     * @param n number of lines to add
     */
    void addLinesOut(long n) {
        linesOut += n;
    }

    /**
     * Reset all counters to zero.
     *
     * <p>Package-private: intended for reuse by formatter internals.
     */
    void clear() {
        bytesIn = 0;
        bytesOut = 0;
        linesIn = 0;
        linesOut = 0;
    }

    /**
     * Return a human-readable representation of the statistics.
     */
    @Override
    public String toString() {
        return "Stats{" +
                "bytesIn=" + bytesIn +
                ", bytesOut=" + bytesOut +
                ", linesIn=" + linesIn +
                ", linesOut=" + linesOut +
                '}';
    }
}