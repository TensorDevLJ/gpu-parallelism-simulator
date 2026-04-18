package performance;

import matrix.MatrixGenerator;
import multiplication.*;

import java.util.concurrent.ExecutionException;

/**
 * PerformanceAnalyzer — Orchestrates benchmark runs for a given (size, threads) pair.
 *
 * Methodology (mirrors JMH best-practices without the JMH dependency):
 *  1. WARM-UP  — runs each multiplier once to let the JIT compile hot paths.
 *  2. TIMED    — runs ITERATIONS times, records wall-clock via System.nanoTime().
 *  3. AVERAGE  — takes the arithmetic mean of timed runs.
 *
 * This approach controls for JIT warm-up bias and short-lived OS scheduling noise.
 */
public class PerformanceAnalyzer {

    private static final int WARM_UP_RUNS = 2;
    private static final int ITERATIONS   = 5;

    private final int matrixSize;
    private final int threadCount;

    public PerformanceAnalyzer(int matrixSize, int threadCount) {
        this.matrixSize  = matrixSize;
        this.threadCount = threadCount;
    }

    /**
     * Runs all four multipliers and returns a fully-populated Metrics object.
     */
    public Metrics analyze() throws InterruptedException, ExecutionException {
        MatrixGenerator gen = new MatrixGenerator(matrixSize);
        double[][] A = gen.generate();
        double[][] B = gen.generate();

        System.out.printf("%n  [Analyzer] Matrix=%dx%d, Threads=%d%n",
                matrixSize, matrixSize, threadCount);

        // ── 1. SEQUENTIAL ────────────────────────────────────────────────────
        SequentialMultiplier seq = new SequentialMultiplier();
        warmUp(() -> seq.multiply(A, B), "Sequential");
        long seqTime = averageTime(() -> seq.multiply(A, B), "Sequential");

        // ── 2. PARALLEL (PER_ROW) ────────────────────────────────────────────
        ParallelMultiplier parRow = new ParallelMultiplier(threadCount, ParallelMultiplier.GranularityMode.PER_ROW);
        warmUp(() -> {
            try { parRow.multiply(A, B); } catch (Exception e) { throw new RuntimeException(e); }
        }, "Parallel-Row");
        long parTime = averageTime(() -> {
            try { parRow.multiply(A, B); } catch (Exception e) { throw new RuntimeException(e); }
        }, "Parallel-Row");

        // ── 3. PARALLEL (PER_CELL) — only for smaller matrices (avoids OOM) ─
        long parCellTime;
        if (matrixSize <= 300) {
            ParallelMultiplier parCell = new ParallelMultiplier(threadCount, ParallelMultiplier.GranularityMode.PER_CELL);
            warmUp(() -> {
                try { parCell.multiply(A, B); } catch (Exception e) { throw new RuntimeException(e); }
            }, "Parallel-Cell");
            parCellTime = averageTime(() -> {
                try { parCell.multiply(A, B); } catch (Exception e) { throw new RuntimeException(e); }
            }, "Parallel-Cell");
        } else {
            // Skip per-cell for large matrices (task queue would have N² entries)
            System.out.println("  [Analyzer] Skipping PER_CELL for N>" + 300 + " (too many tasks)");
            parCellTime = -1L;
        }

        // ── 4. BLOCK / TILED ─────────────────────────────────────────────────
        BlockMultiplier block = new BlockMultiplier(threadCount);
        warmUp(() -> {
            try { block.multiply(A, B); } catch (Exception e) { throw new RuntimeException(e); }
        }, "Block");
        long blockTime = averageTime(() -> {
            try { block.multiply(A, B); } catch (Exception e) { throw new RuntimeException(e); }
        }, "Block");

        return new Metrics(matrixSize, threadCount, seqTime, parTime, parCellTime, blockTime);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Executes the task WARM_UP_RUNS times (results discarded) to trigger JIT compilation.
     */
    private void warmUp(Runnable task, String label) {
        System.out.printf("  [Warmup]   %-16s ...", label);
        for (int i = 0; i < WARM_UP_RUNS; i++) {
            task.run();
        }
        System.out.println(" done");
    }

    /**
     * Runs the task ITERATIONS times and returns the average wall-clock time in nanoseconds.
     */
    private long averageTime(Runnable task, String label) {
        long total = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            task.run();
            total += System.nanoTime() - start;
        }
        long avg = total / ITERATIONS;
        System.out.printf("  [Timed]    %-16s avg = %.3f ms%n", label, Metrics.toMs(avg));
        return avg;
    }

    // ── Bottleneck Analysis ───────────────────────────────────────────────────

    /**
     * Prints qualitative bottleneck observations based on the measured metrics.
     * This simulates what a profiler/perf-analyst would report.
     */
    public static void printBottleneckAnalysis(Metrics m) {
        System.out.println("\n  ── Bottleneck Analysis ─────────────────────────────");

        // Thread overhead
        if (m.parallelCellTimeNs > 0 && m.parallelCellTimeNs > m.parallelTimeNs * 2) {
            System.out.println("  ⚠  HIGH THREAD OVERHEAD: PER_CELL is >2x slower than PER_ROW.");
            System.out.println("     Cause: Task-queue pressure and Future.get() latency dominate.");
            System.out.println("     Fix : Use PER_ROW or WorkStealingPool for finer tasks.");
        } else {
            System.out.println("  ✓  Thread overhead is acceptable (PER_CELL ≈ PER_ROW).");
        }

        // Memory contention
        if (m.efficiencyParallel < 0.5) {
            System.out.println("  ⚠  LOW PARALLEL EFFICIENCY (" +
                String.format("%.2f", m.efficiencyParallel) + "):");
            System.out.println("     Cause: Cache-line false sharing or DRAM bandwidth saturation.");
            System.out.println("     Fix : Tiling (see BlockMultiplier) or NUMA-aware allocation.");
        } else {
            System.out.printf("  ✓  Parallel efficiency = %.2f (above 0.5 is good for pure Java).%n",
                m.efficiencyParallel);
        }

        // Block vs parallel
        if (m.blockTimeNs < m.parallelTimeNs) {
            System.out.printf("  ✓  Tiling improved performance by %.1f%% over naive parallel.%n",
                100.0 * (m.parallelTimeNs - m.blockTimeNs) / m.parallelTimeNs);
        } else {
            System.out.println("  ℹ  Tiling did not outperform naive parallel at this size.");
            System.out.println("     For small N, tile-overhead > cache-miss savings.");
        }

        // Amdahl ceiling
        double theoreticalMax = m.threadCount; // perfect linear scaling
        System.out.printf("  ℹ  Amdahl ceiling for %d threads = %.1fx speedup.%n",
                m.threadCount, theoreticalMax);
        System.out.printf("     Achieved: %.4fx (%.1f%% of theoretical max)%n",
                m.speedupParallel,
                100.0 * m.speedupParallel / theoreticalMax);

        System.out.println("  ────────────────────────────────────────────────────");
    }
}
