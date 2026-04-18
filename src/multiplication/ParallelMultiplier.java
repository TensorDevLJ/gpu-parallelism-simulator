package multiplication;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * ParallelMultiplier - Simulates GPU-style SIMD parallelism using Java threads.
 *
 * Two task granularity modes:
 *  1. PER_CELL  — one Callable per output cell  → fine-grained (high overhead, max parallelism)
 *  2. PER_ROW   — one Callable per output row   → coarse-grained (lower overhead, preferred)
 *
 * Uses a fixed-size ExecutorService (analogous to a GPU's warp scheduler).
 * The thread pool size is the knob that simulates "number of CUDA cores".
 */
public class ParallelMultiplier {

    public enum GranularityMode {
        PER_CELL,
        PER_ROW
    }

    private final int threadCount;
    private final GranularityMode mode;

    /**
     * @param threadCount Number of worker threads (analogous to GPU core count)
     * @param mode        Task granularity: PER_CELL or PER_ROW
     */
    public ParallelMultiplier(int threadCount, GranularityMode mode) {
        if (threadCount <= 0) throw new IllegalArgumentException("Thread count must be > 0");
        this.threadCount = threadCount;
        this.mode = mode;
    }

    /**
     * Convenience constructor — defaults to PER_ROW (recommended for performance).
     */
    public ParallelMultiplier(int threadCount) {
        this(threadCount, GranularityMode.PER_ROW);
    }

    /**
     * Multiplies A × B using a thread pool.
     *
     * @param A Left matrix (NxN)
     * @param B Right matrix (NxN)
     * @return C = A × B
     */
    public double[][] multiply(double[][] A, double[][] B) throws InterruptedException, ExecutionException {
        int n = A.length;
        double[][] C = new double[n][n];

        // Create a fixed thread pool — analogous to fixed number of GPU cores
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        if (mode == GranularityMode.PER_ROW) {
            // ---- ONE TASK PER ROW (coarse-grained) ----
            // Each task computes an entire row of C.
            // Better cache locality: row A[i] stays in L1 cache for the full inner loop.
            for (int i = 0; i < n; i++) {
                final int row = i;
                futures.add(executor.submit(() -> {
                    for (int k = 0; k < n; k++) {
                        double aik = A[row][k];
                        for (int j = 0; j < n; j++) {
                            C[row][j] += aik * B[k][j];
                        }
                    }
                }));
            }
        } else {
            // ---- ONE TASK PER CELL (fine-grained) ----
            // Each task computes exactly one element C[i][j].
            // Maximum parallelism but heavy thread scheduling overhead.
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    final int row = i, col = j;
                    futures.add(executor.submit(() -> {
                        double sum = 0.0;
                        for (int k = 0; k < n; k++) {
                            sum += A[row][k] * B[k][col];
                        }
                        C[row][col] = sum; // thread-safe: unique (row,col) per task
                    }));
                }
            }
        }

        // Wait for all tasks to complete
        for (Future<?> f : futures) {
            f.get(); // propagates exceptions from worker threads
        }

        // Graceful shutdown — drain remaining work then stop
        executor.shutdown();
        boolean terminated = executor.awaitTermination(60, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
            System.err.println("[WARN] Executor did not terminate in time — forced shutdown.");
        }

        return C;
    }

    public int getThreadCount() { return threadCount; }
    public GranularityMode getMode() { return mode; }
}
