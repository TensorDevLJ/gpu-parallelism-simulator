package multiplication;

/**
 * SequentialMultiplier - Classic triple-loop matrix multiplication.
 *
 * This is the CPU baseline (single-threaded). Every parallel/block
 * implementation is compared against this to compute speedup.
 *
 * Time complexity: O(N^3)
 * Space complexity: O(N^2) for the result matrix
 */
public class SequentialMultiplier {

    /**
     * Multiplies matrix A (NxN) by matrix B (NxN) sequentially.
     *
     * @param A Left operand
     * @param B Right operand
     * @return C = A × B
     */
    public double[][] multiply(double[][] A, double[][] B) {
        int n = A.length;
        if (B.length != n || B[0].length != n) {
            throw new IllegalArgumentException(
                "Matrix dimensions mismatch: A is " + n + "x" + n +
                ", B is " + B.length + "x" + B[0].length
            );
        }

        double[][] C = new double[n][n];

        // Standard ijk triple loop
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < n; k++) {
                // Hoisting A[i][k] reduces repeated array indexing (minor JIT hint)
                double aik = A[i][k];
                for (int j = 0; j < n; j++) {
                    C[i][j] += aik * B[k][j];
                }
            }
        }

        return C;
    }
}
