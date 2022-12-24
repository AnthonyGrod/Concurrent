
import java.util.concurrent.*;
import java.util.function.IntBinaryOperator;

public class MatrixRowSums {
    private static final int ROWS = 10;
    private static final int COLUMNS = 100;
    private static final int[][] matrix2d = new int[ROWS][COLUMNS];

    private static class Matrix {

        private final int rows;
        private final int columns;
        private final IntBinaryOperator definition;


        public Matrix(int rows, int columns, IntBinaryOperator definition) {
            this.rows = rows;
            this.columns = columns;
            this.definition = definition;
        }

        private static class Counting implements Callable<Integer> {

            private final Matrix matrix;
            private final int row;
            private final int column;

            private Counting(Matrix matrix, int row, int column) {
                this.matrix = matrix;
                this.row = row;
                this.column = column;
            }

            @Override
            public Integer call() throws InterruptedException {
                return matrix.definition.applyAsInt(row, column);
            }
        }

        public void rowSumsConcurrent() throws InterruptedException {
            ExecutorService countingPool = Executors.newFixedThreadPool(4);
            try {
                for (int i = 0; i < ROWS; i++) {
                    for (int j = 0; j < COLUMNS; j++) {
                        Callable<Integer> task = new Counting(this, i, j);
                        Future<Integer> futureResult = countingPool.submit(task);
                        matrix2d[i][j] = futureResult.get();
                    }
                }
            } catch (ExecutionException e) {
                System.out.println("ERROR");
            } finally {
                countingPool.shutdown();
            }
        }

        public static void main(String[] args) {
            Matrix matrix = new Matrix(ROWS, COLUMNS, (row, column) -> {
                int a = 2 * column + 1;
                return (row + 1) * (a % 4 - 2) * a;
            });
            try {
                matrix.rowSumsConcurrent();
                for (int i = 0; i < ROWS; i++) {
                    int row = 0;
                    for (int j = 0; j < COLUMNS; j++) {
                        row += matrix2d[i][j];
                    }
                    System.out.println("Row " + i + "'th -->" + row);
                }
            } catch (InterruptedException e) {
                System.out.println("ERROR");
            }
        }
    }
}


