import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.function.IntBinaryOperator;

public class Matrix2 {

    private static final int ROWS = 10;
    private static final int COLUMNS = 100;

    private static class Matrix {
        private final int rows;
        private final int columns;
        private final IntBinaryOperator definition;
        private int[][] matrixSum; // is volatile neccessary?
        private CyclicBarrier barrier;

        public Matrix(int rows, int columns, IntBinaryOperator definition) {
            this.rows = rows;
            this.columns = columns;
            this.definition = definition;
            this.matrixSum = new int[ROWS][COLUMNS];
        }

        public void setMatrixSum(int[][] matrixSum, int row, int column, int value) {
            this.matrixSum[row][column] = value;
        }

        public int[][] getMatrixSum() {
            return matrixSum;
        }

        public IntBinaryOperator getDefinition() {
            return definition;
        }

        public static int countRow(int[][] matrixSum, int row) {
            int result = 0;
            for (int i = 0; i < matrixSum[row].length; i++) {
                result += matrixSum[row][i];
            }
            return result;
        }

        private class Helper implements Runnable {
            private int column;

            public Helper(int column) {
                this.column = column;
            }

            @Override
            public void run() {
                for (int i = 0; i < ROWS; i ++) {
                    try {
                        setMatrixSum(matrixSum, i, this.column, getDefinition().applyAsInt(i, this.column));
                        barrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        System.out.println("ERROR");
                    }
                }
            }
        }

        public int[] rowSums() {
            int[] rowSums = new int[rows];
            for (int row = 0; row < rows; ++row) {
                int sum = 0;
                for (int column = 0; column < columns; ++column) {
                    sum += definition.applyAsInt(row, column);
                }
                rowSums[row] = sum;
            }
            return rowSums;
        }

        public int[] rowSumsConcurrent() throws InterruptedException {
            List<Thread> threads =  new ArrayList<Thread>();
            Thread tmpThread;
            int[] rowsSums = new int[this.rows];
            int[] row = {0};
            this.barrier = new CyclicBarrier(COLUMNS, () -> {
                rowsSums[row[0]] = countRow(this.getMatrixSum(), row[0]);
                row[0]++;
            });
            for (int i = 0; i < this.columns; i++) {
                tmpThread = new Thread(new Helper(i));
                threads.add(tmpThread);
                tmpThread.start();
            }
            for (Thread thread: threads) {
                thread.join();
            }
            return rowsSums;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Matrix matrix = new Matrix(ROWS, COLUMNS, (row, column) -> {
            int a = 2 * column + 1;
            return (row + 1) * (a % 4 - 2) * a;
        });

        int[] rowSums = matrix.rowSums();
        int[] concurrentRowSums = matrix.rowSumsConcurrent();

        System.out.println("PRINTING FOR SEQUENTIAL:");

        for (int i = 0; i < rowSums.length; i++) {
            System.out.println(i + " -> " + rowSums[i]);
        }

        System.out.println("\n\n");

        System.out.println("PRINTING FOR CONCURRENT");

        for (int i = 0; i < concurrentRowSums.length; i++) {
            System.out.println(i + " -> " + concurrentRowSums[i]);
        }
    }

}