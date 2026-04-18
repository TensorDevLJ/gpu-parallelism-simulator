package main;

import matrix.MatrixGenerator;
import multiplication.*;
import performance.*;
import utils.*;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * ═══════════════════════════════════════════════════════════════
 *  CPU vs GPU Simulation — Main Entry Point
 * ═══════════════════════════════════════════════════════════════
 *
 *  Execution flow:
 *  1.  Print system info
 *  2.  Accept user input (size, threads) or run in batch mode
 *  3.  Run all three multipliers with warm-up + multiple iterations
 *  4.  Print formatted results + bottleneck analysis
 *  5.  Thread-scaling sweep (1, 2, 4, 8 threads)
 *  6.  Granularity comparison (PER_ROW vs PER_CELL)
 *  7.  Export all results to CSV
 * ═══════════════════════════════════════════════════════════════
 */
public class Main {

    // ── Batch mode matrix sizes and thread counts ──────────────────────────
    private static final int[] BATCH_SIZES   = {100, 300, 500};
    private static final int[] BATCH_THREADS = {1, 2, 4, 8};

    public static void main(String[] args) {
        System.out.println("\n╔═══════════════════════════════════════════════════════╗");
        System.out.println("║   CPU vs GPU Simulation — Parallel Matrix Multiply    ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");

        // ── Step 1: System Info ────────────────────────────────────────────
        SystemInfo.printSystemInfo();

        Scanner scanner = new Scanner(System.in);
        List<Metrics> allResults = new ArrayList<>();

        // ── Step 2: Choose mode ───────────────────────────────────────────
        System.out.println("\nSelect mode:");
        System.out.println("  [1] Interactive  — custom matrix size & thread count");
        System.out.println("  [2] Batch        — auto-run " + Arrays.toString(BATCH_SIZES) +
                           " × " + Arrays.toString(BATCH_THREADS));
        System.out.print("Enter choice (1 or 2): ");

        int choice = readInt(scanner, 1, 2, 1);

        if (choice == 1) {
            // ── INTERACTIVE MODE ──────────────────────────────────────────
            System.out.print("\nMatrix size N (e.g. 300): ");
            int size = readInt(scanner, 10, 2000, 300);

            System.out.print("Number of threads (e.g. 4): ");
            int threads = readInt(scanner, 1, Runtime.getRuntime().availableProcessors() * 2, 4);

            Metrics m = runSingleBenchmark(size, threads);
            if (m != null) {
                allResults.add(m);
                System.out.println("\n" + m);
                PerformanceAnalyzer.printBottleneckAnalysis(m);

                // Thread-scaling for the chosen size
                System.out.println("\n\n  ── Thread Scaling Analysis (fixed N=" + size + ") ──────────");
                runThreadScaling(size, allResults);
            }
        } else {
            // ── BATCH MODE ────────────────────────────────────────────────
            System.out.println("\n[Batch] Running " + BATCH_SIZES.length + " sizes × " +
                               BATCH_THREADS.length + " thread counts …\n");
            for (int size : BATCH_SIZES) {
                for (int threads : BATCH_THREADS) {
                    Metrics m = runSingleBenchmark(size, threads);
                    if (m != null) {
                        allResults.add(m);
                        System.out.println(m);
                        PerformanceAnalyzer.printBottleneckAnalysis(m);
                    }
                }
            }
        }

        // ── Step 7: Granularity Comparison ───────────────────────────────
        runGranularityComparison(300, 4);

        // ── Step 8: Export ────────────────────────────────────────────────
        if (!allResults.isEmpty()) {
            CSVExporter.export(allResults);
        }

        System.out.println("\n[Done] Simulation complete.");
        scanner.close();
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs a full benchmark for a single (size, threads) configuration.
     */
    private static Metrics runSingleBenchmark(int size, int threads) {
        System.out.println("\n  ▶ Benchmarking N=" + size + ", threads=" + threads);
        long memBefore = SystemInfo.usedMemoryBytes();

        try {
            PerformanceAnalyzer analyzer = new PerformanceAnalyzer(size, threads);
            Metrics m = analyzer.analyze();
            SystemInfo.printMemoryDelta(memBefore, "N=" + size + " T=" + threads);
            return m;
        } catch (InterruptedException e) {
            System.err.println("[ERROR] Benchmark interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            System.err.println("[ERROR] Worker thread exception: " + e.getCause().getMessage());
            return null;
        }
    }

    /**
     * Thread-scaling sweep: tests 1, 2, 4, 8 threads for a fixed matrix size.
     * Demonstrates how speedup scales with thread count (Amdahl's Law in practice).
     */
    private static void runThreadScaling(int size, List<Metrics> collector) {
        System.out.printf("  %-8s %-12s %-14s %-14s %-12s%n",
                "Threads", "Seq(ms)", "Par-Row(ms)", "Block(ms)", "Speedup");
        System.out.println("  " + "─".repeat(60));

        for (int t : BATCH_THREADS) {
            try {
                PerformanceAnalyzer pa = new PerformanceAnalyzer(size, t);
                Metrics m = pa.analyze();
                collector.add(m);
                System.out.printf("  %-8d %-12.2f %-14.2f %-14.2f %-12.4f%n",
                        t,
                        Metrics.toMs(m.sequentialTimeNs),
                        Metrics.toMs(m.parallelTimeNs),
                        Metrics.toMs(m.blockTimeNs),
                        m.speedupParallel);
            } catch (Exception e) {
                System.err.println("[ERROR] Thread scaling at t=" + t + ": " + e.getMessage());
            }
        }
    }

    /**
     * Compares PER_CELL vs PER_ROW task granularity on a medium-sized matrix.
     * Illustrates the overhead of over-decomposition (fine-grained tasks).
     */
    private static void runGranularityComparison(int size, int threads) {
        System.out.println("\n\n  ── Granularity Comparison (N=" + size + ", T=" + threads + ") ──");
        MatrixGenerator gen = new MatrixGenerator(size);
        double[][] A = gen.generate();
        double[][] B = gen.generate();

        try {
            // PER_ROW
            ParallelMultiplier parRow =
                new ParallelMultiplier(threads, ParallelMultiplier.GranularityMode.PER_ROW);
            long startRow = System.nanoTime();
            parRow.multiply(A, B);
            long rowTime = System.nanoTime() - startRow;

            // PER_CELL
            ParallelMultiplier parCell =
                new ParallelMultiplier(threads, ParallelMultiplier.GranularityMode.PER_CELL);
            long startCell = System.nanoTime();
            parCell.multiply(A, B);
            long cellTime = System.nanoTime() - startCell;

            System.out.printf("  PER_ROW  (coarse): %10.3f ms  [tasks=%d]%n",
                    Metrics.toMs(rowTime), size);
            System.out.printf("  PER_CELL (fine)  : %10.3f ms  [tasks=%d]%n",
                    Metrics.toMs(cellTime), size * size);
            System.out.printf("  Overhead ratio   : %.2fx  (PER_CELL / PER_ROW)%n",
                    (double) cellTime / rowTime);
            System.out.println("  → Fine-grained tasks show scheduler overhead from " + (size * size) +
                               " Future submissions.");
        } catch (Exception e) {
            System.err.println("[ERROR] Granularity comparison failed: " + e.getMessage());
        }
    }

    /**
     * Reads an integer from stdin with bounds validation and a default fallback.
     */
    private static int readInt(Scanner sc, int min, int max, int defaultVal) {
        try {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) return defaultVal;
            int val = Integer.parseInt(line);
            if (val < min || val > max) {
                System.out.printf("  [Input] Out of range [%d, %d]. Using default=%d%n",
                        min, max, defaultVal);
                return defaultVal;
            }
            return val;
        } catch (NumberFormatException e) {
            System.out.println("  [Input] Invalid number. Using default=" + defaultVal);
            return defaultVal;
        }
    }
}
