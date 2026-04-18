package utils;

import performance.Metrics;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CSVExporter — Writes benchmark results to a CSV file for external analysis
 * (Excel, Python/pandas, R, Gnuplot, etc.).
 *
 * Output file: results/benchmark_<timestamp>.csv
 */
public class CSVExporter {

    private static final String OUTPUT_DIR = "results";

    // CSV header (all times in milliseconds)
    private static final String HEADER =
        "MatrixSize,Threads,SeqTime_ms,ParTime_ms,ParCellTime_ms," +
        "BlockTime_ms,SpeedupPar,SpeedupBlock,EfficiencyPar,EfficiencyBlock";

    /**
     * Exports a list of Metrics to a timestamped CSV file.
     *
     * @param results List of benchmark results
     * @return Absolute path of the written file, or null on failure
     */
    public static String export(List<Metrics> results) {
        // Ensure output directory exists
        java.io.File dir = new java.io.File(OUTPUT_DIR);
        if (!dir.exists()) dir.mkdirs();

        // Timestamped filename so successive runs don't overwrite each other
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = OUTPUT_DIR + "/benchmark_" + timestamp + ".csv";

        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println(HEADER);
            for (Metrics m : results) {
                pw.println(m.toCsvRow());
            }
            System.out.println("\n  [CSV] Results exported to: " + filename);
            return filename;
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to write CSV: " + e.getMessage());
            return null;
        }
    }

    /**
     * Convenience method — exports a single Metrics result.
     */
    public static String export(Metrics result) {
        return export(List.of(result));
    }
}
