package matrix;

import java.util.Random;

/**
 * MatrixGenerator - Generates NxN matrices filled with random double values.
 * Supports configurable size for benchmark flexibility.
 */
public class MatrixGenerator {

    private final int size;
    private final Random random;

    /**
     * @param size The dimension N for an NxN matrix
     */
    public MatrixGenerator(int size) {
        if (size <= 0) throw new IllegalArgumentException("Matrix size must be positive. Got: " + size);
        this.size = size;
        this.random = new Random(42); // Fixed seed for reproducibility
    }

    /**
     * Generates a new NxN matrix with random double values in [0.0, 10.0).
     */
    public double[][] generate() {
        double[][] matrix = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrix[i][j] = random.nextDouble() * 10.0;
            }
        }
        return matrix;
    }

    /**
     * Generates an identity matrix of the given size (useful for validation).
     */
    public double[][] generateIdentity() {
        double[][] matrix = new double[size][size];
        for (int i = 0; i < size; i++) {
            matrix[i][i] = 1.0;
        }
        return matrix;
    }

    /**
     * Generates a zero matrix.
     */
    public double[][] generateZero() {
        return new double[size][size];
    }

    public int getSize() {
        return size;
    }

    /**
     * Pretty-prints a small matrix (only for debug; skip for large matrices).
     */
    public static void print(double[][] matrix) {
        int n = matrix.length;
        if (n > 8) {
            System.out.println("[Matrix too large to display — " + n + "x" + n + "]");
            return;
        }
        for (double[] row : matrix) {
            StringBuilder sb = new StringBuilder("[ ");
            for (double val : row) {
                sb.append(String.format("%7.2f ", val));
            }
            sb.append("]");
            System.out.println(sb);
        }
    }
}
