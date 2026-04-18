package utils;

/**
 * SystemInfo — Prints CPU and JVM memory statistics.
 *
 * Memory readings use Runtime.getRuntime() which reflects the JVM heap.
 * For native/off-heap memory, a JVM agent (e.g. jemalloc) would be needed.
 */
public class SystemInfo {

    private static final Runtime RT = Runtime.getRuntime();

    /** Prints a header banner with system details. */
    public static void printSystemInfo() {
        System.out.println("╔═══════════════════════════════════════════════════╗");
        System.out.println("║       CPU vs GPU Simulation — System Info         ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.printf( "║  OS            : %-33s ║%n", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.printf( "║  JVM           : %-33s ║%n", System.getProperty("java.version"));
        System.out.printf( "║  Available CPUs: %-33d ║%n", RT.availableProcessors());
        System.out.printf( "║  Max Heap      : %-33s ║%n", formatBytes(RT.maxMemory()));
        System.out.printf( "║  Total Heap    : %-33s ║%n", formatBytes(RT.totalMemory()));
        System.out.printf( "║  Free Heap     : %-33s ║%n", formatBytes(RT.freeMemory()));
        System.out.printf( "║  Used Heap     : %-33s ║%n", formatBytes(RT.totalMemory() - RT.freeMemory()));
        System.out.println("╚═══════════════════════════════════════════════════╝");
    }

    /**
     * Captures and returns current used-heap in bytes.
     * Call this before and after a computation to estimate memory delta.
     */
    public static long usedMemoryBytes() {
        return RT.totalMemory() - RT.freeMemory();
    }

    /**
     * Prints a delta-memory summary.
     *
     * @param before usedMemoryBytes() captured before the run
     * @param label  label for the run
     */
    public static void printMemoryDelta(long before, String label) {
        long after = usedMemoryBytes();
        long delta = after - before;
        System.out.printf("  [Memory] %-20s  before=%-10s after=%-10s delta=%s%n",
                label,
                formatBytes(before),
                formatBytes(after),
                (delta >= 0 ? "+" : "") + formatBytes(Math.abs(delta)));
    }

    /** Pretty-formats byte count to KB / MB / GB. */
    public static String formatBytes(long bytes) {
        if (bytes < 0) return "N/A";
        if (bytes < 1_024L)            return bytes + " B";
        if (bytes < 1_048_576L)        return String.format("%.1f KB", bytes / 1_024.0);
        if (bytes < 1_073_741_824L)    return String.format("%.1f MB", bytes / 1_048_576.0);
        return String.format("%.2f GB", bytes / 1_073_741_824.0);
    }
}
