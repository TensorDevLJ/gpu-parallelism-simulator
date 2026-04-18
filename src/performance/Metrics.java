package performance;

/**
 * Metrics — Plain data container for one benchmark run's results.
 *
 * Stores all timing and derived statistics so they can be printed,
 * compared, and exported to CSV without coupling to the analysis logic.
 */
public class Metrics {

    // --- Configuration -------------------------------------------------------
    public final int matrixSize;
    public final int threadCount;

    // --- Raw timing (nanoseconds) --------------------------------------------
    public final long sequentialTimeNs;
    public final long parallelTimeNs;      // PER_ROW granularity
    public final long parallelCellTimeNs;  // PER_CELL granularity
    public final long blockTimeNs;

    // --- Derived statistics ---------------------------------------------------
    public final double speedupParallel;   // seqTime / parallelTime
    public final double speedupBlock;      // seqTime / blockTime
    public final double efficiencyParallel;// speedupParallel / threadCount
    public final double efficiencyBlock;   // speedupBlock    / threadCount

    /**
     * Full constructor — computed stats are derived automatically.
     */
    public Metrics(int matrixSize, int threadCount,
                   long sequentialTimeNs,
                   long parallelTimeNs,
                   long parallelCellTimeNs,
                   long blockTimeNs) {

        this.matrixSize        = matrixSize;
        this.threadCount       = threadCount;
        this.sequentialTimeNs  = sequentialTimeNs;
        this.parallelTimeNs    = parallelTimeNs;
        this.parallelCellTimeNs= parallelCellTimeNs;
        this.blockTimeNs       = blockTimeNs;

        // Speedup (Amdahl's Law numerator)
        this.speedupParallel  = (parallelTimeNs > 0)
                ? (double) sequentialTimeNs / parallelTimeNs : 0;
        this.speedupBlock     = (blockTimeNs > 0)
                ? (double) sequentialTimeNs / blockTimeNs    : 0;

        // Efficiency = speedup / threads (1.0 = perfect linear scaling)
        this.efficiencyParallel = (threadCount > 0) ? speedupParallel / threadCount : 0;
        this.efficiencyBlock    = (threadCount > 0) ? speedupBlock    / threadCount : 0;
    }

    /** Converts nanoseconds to milliseconds for display. */
    public static double toMs(long ns) {
        return ns / 1_000_000.0;
    }

    /**
     * CSV row matching CSVExporter header:
     * MatrixSize,Threads,SeqTime_ms,ParTime_ms,ParCellTime_ms,BlockTime_ms,
     * SpeedupPar,SpeedupBlock,EffPar,EffBlock
     */
    public String toCsvRow() {
        return String.format("%d,%d,%.3f,%.3f,%.3f,%.3f,%.4f,%.4f,%.4f,%.4f",
                matrixSize, threadCount,
                toMs(sequentialTimeNs),
                toMs(parallelTimeNs),
                toMs(parallelCellTimeNs),
                toMs(blockTimeNs),
                speedupParallel, speedupBlock,
                efficiencyParallel, efficiencyBlock);
    }

    @Override
    public String toString() {
        return String.format(
            "┌─────────────────────────────────────────────────┐\n" +
            "│  Matrix Size : %-5d   Threads : %-5d          │\n" +
            "├─────────────────────────────────────────────────┤\n" +
            "│  Sequential      : %12.3f ms                │\n" +
            "│  Parallel (row)  : %12.3f ms                │\n" +
            "│  Parallel (cell) : %12.3f ms                │\n" +
            "│  Block/Tiled     : %12.3f ms                │\n" +
            "├─────────────────────────────────────────────────┤\n" +
            "│  Speedup  (row)  : %8.4fx                    │\n" +
            "│  Speedup  (block): %8.4fx                    │\n" +
            "│  Efficiency(row) : %8.4f  (ideal=1.0)       │\n" +
            "│  Efficiency(blk) : %8.4f  (ideal=1.0)       │\n" +
            "└─────────────────────────────────────────────────┘",
            matrixSize, threadCount,
            toMs(sequentialTimeNs),
            toMs(parallelTimeNs),
            toMs(parallelCellTimeNs),
            toMs(blockTimeNs),
            speedupParallel,  speedupBlock,
            efficiencyParallel, efficiencyBlock
        );
    }
}
