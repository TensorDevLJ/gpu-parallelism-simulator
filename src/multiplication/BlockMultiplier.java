package multiplication;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * BlockMultiplier — Tiled (blocked) parallel matrix multiplication.
 *
 * ── WHY BLOCKING / TILING? ──────────────────────────────────────────────────
 * GPU shared memory is organized in tiles. When a thread block loads a sub-tile
 * of A and B into fast shared memory, all threads in the block reuse that data
 * for many MACs (multiply-accumulate). This is called "register blocking" or
 * "shared memory tiling" in CUDA.
 *
 * On CPU, the equivalent is cache blocking / loop tiling:
 *   - Work on a BLOCK_SIZE × BLOCK_SIZE sub-matrix that fits in L1/L2 cache.
 *   - Each element of A and B is read once from RAM and reused BLOCK_SIZE times
 *     from cache → drastically reduces memory-bandwidth pressure.
 *
 * Formula: cache-miss reduction ≈ BLOCK_SIZE × compared to naive ijk.
 *
 * ── PARALLELISM ─────────────────────────────────────────────────────────────
 * Each tile-row (a horizontal strip of tiles) is dispatched as one task to
 * the thread pool. This achieves coarse-grained parallelism with low overhead
 * and perfect load balance (all strips have equal width = BLOCK_SIZE rows).
 */
public class BlockMultiplier {

    // Optimal block size: typically sqrt(L1_cache / element_size).
    // L1 ≈ 32 KB, double = 8 B → sqrt(32768/8) ≈ 64. We use 64 as default.
    private static final int DEFAULT_BLOCK_SIZE = 64;

    private final int threadCount;
    private final int blockSize;

    /**
     * @param threadCount Number of parallel worker threads
     * @param blockSize   Tile dimension (rows/cols per tile)
     */
    public BlockMultiplier(int threadCount, int blockSize) {
        if (threadCount <= 0) throw new IllegalArgumentException("Thread count must be > 0");
        if (blockSize <= 0)   throw new IllegalArgumentException("Block size must be > 0");
        this.threadCount = threadCount;
        this.blockSize   = blockSize;
    }

    /** Uses DEFAULT_BLOCK_SIZE = 64 */
    public BlockMultiplier(int threadCount) {
        this(threadCount, DEFAULT_BLOCK_SIZE);
    }

    /**
     * Computes C = A × B using parallel tiled multiplication.
     *
     * @param A NxN left matrix
     * @param B NxN right matrix
     * @return  NxN result matrix C
     */
    public double[][] multiply(double[][] A, double[][] B) throws InterruptedException, ExecutionException {
        int n = A.length;
        double[][] C = new double[n][n];

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        // Iterate over tile-rows (strips of BLOCK_SIZE rows)
        for (int i = 0; i < n; i += blockSize) {
            final int iStart = i;
            final int iEnd   = Math.min(i + blockSize, n); // handle non-divisible sizes

            // One task per tile-row strip
            futures.add(executor.submit(() -> {
                // Iterate over tile-columns
                for (int j = 0; j < n; j += blockSize) {
                    int jEnd = Math.min(j + blockSize, n);

                    // Iterate over k-tiles (the shared dimension)
                    for (int k = 0; k < n; k += blockSize) {
                        int kEnd = Math.min(k + blockSize, n);

                        // ── INNER MICRO-KERNEL (fits in L1 cache) ──────────
                        // Accumulate C[iStart:iEnd][j:jEnd] using
                        // A[iStart:iEnd][k:kEnd] × B[k:kEnd][j:jEnd]
                        for (int ii = iStart; ii < iEnd; ii++) {
                            for (int kk = k; kk < kEnd; kk++) {
                                double aik = A[ii][kk]; // hoisted for cache reuse
                                for (int jj = j; jj < jEnd; jj++) {
                                    C[ii][jj] += aik * B[kk][jj];
                                }
                            }
                        }
                        // ───────────────────────────────────────────────────
                    }
                }
            }));
        }

        // Barrier: wait for all tile-row strips to finish
        for (Future<?> f : futures) {
            f.get();
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(60, TimeUnit.SECONDS);
        if (!terminated) {
            executor.shutdownNow();
            System.err.println("[WARN] BlockMultiplier executor forced shutdown.");
        }

        return C;
    }

    public int getThreadCount() { return threadCount; }
    public int getBlockSize()   { return blockSize; }
}
