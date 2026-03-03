import java.util.Scanner;

public class MatrixMult {

    static void onMult(int mAr, int mBr) {
        double[] pha = new double[mAr * mAr];
        double[] phb = new double[mAr * mAr];
        double[] phc = new double[mAr * mAr];

        for (int i = 0; i < mAr; i++)
            for (int j = 0; j < mAr; j++)
                pha[i * mAr + j] = 1.0;

        for (int i = 0; i < mBr; i++)
            for (int j = 0; j < mBr; j++)
                phb[i * mBr + j] = (double)(i + 1);

        long time1 = System.nanoTime();

        for (int i = 0; i < mAr; i++) {
            for (int j = 0; j < mBr; j++) {
                double temp = 0;
                for (int k = 0; k < mAr; k++) {
                    temp += pha[i * mAr + k] * phb[k * mBr + j];
                }
                phc[i * mAr + j] = temp;
            }
        }

        long time2 = System.nanoTime();
        System.out.printf("Time: %.3f seconds%n", (time2 - time1) / 1e9);

        System.out.println("Result matrix:");
        for (int j = 0; j < Math.min(10, mBr); j++)
            System.out.print(phc[j] + " ");
        System.out.println();
    }

    static void onMultLine(int mAr, int mBr) {
        double[] pha = new double[mAr * mAr];
        double[] phb = new double[mAr * mBr];
        double[] phc = new double[mAr * mBr];

        for (int i = 0; i < mAr; i++)
            for (int j = 0; j < mAr; j++)
                pha[i * mAr + j] = 1.0;

        for (int i = 0; i < mBr; i++)
            for (int j = 0; j < mBr; j++)
                phb[i * mBr + j] = (double)(i + 1);

        long time1 = System.nanoTime();

       for (int i = 0; i < mAr; i++) {
            for (int k = 0; k < mAr; k++) {
                double temp = pha[i * mAr + k];
                for (int j = 0; j < mBr; j++) {
                    phc[i * mBr + j] += temp * phb[k * mBr + j];
                }
            }
        }

        long time2 = System.nanoTime();
        System.out.printf("Time: %.3f seconds%n", (time2 - time1) / 1e9);

        System.out.println("Result matrix:");
        for (int j = 0; j < Math.min(10, mBr); j++)
            System.out.print(phc[j] + " ");
        System.out.println();
    }

    static void onMultBlock(int mAr, int mBr, int bkSize) {
        // Block matrix multiplication
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int op;

        do {
            System.out.println("\n1. Multiplication");
            System.out.println("2. Line Multiplication");
            System.out.println("3. Block Multiplication");
            System.out.println("0. Exit");
            System.out.print("Selection?: ");
            op = scanner.nextInt();

            if (op == 0) break;

            System.out.print("Dimensions: lins=cols ? ");
            int lin = scanner.nextInt();
            int col = lin;

            switch (op) {
                case 1:
                    onMult(lin, col);
                    break;
                case 2:
                    onMultLine(lin, col);
                    break;
                case 3:
                    System.out.print("Block Size? ");
                    int blockSize = scanner.nextInt();
                    onMultBlock(lin, col, blockSize);
                    break;
            }

        } while (op != 0);

        scanner.close();
    }
}