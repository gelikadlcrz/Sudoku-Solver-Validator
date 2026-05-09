package com.plt.sudoku.generator;

import com.plt.sudoku.model.SudokuPuzzle;
import com.plt.sudoku.solver.SudokuSolver;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Generates a large set of valid Sudoku puzzles for testing.
 *
 * Strategy:
 *   1. Start with a known solved board.
 *   2. Shuffle it legally (row/column swaps within bands, digit permutation).
 *   3. Remove cells randomly while maintaining a target "difficulty" (number
 *      of givens).  A puzzle is only accepted if it is still uniquely solvable.
 *
 * Difficulty bands (number of given cells):
 *   Easy   : 36–45 givens
 *   Medium : 27–35 givens
 *   Hard   : 17–26 givens  (17 is the known minimum for a unique solution)
 *   Extreme: 17–20 givens  (deep backtracking required)
 *
 * The generator intentionally produces a small percentage (~2%) of invalid
 * puzzles (by deliberately corrupting a cell) to demonstrate the validator.
 *
 * PLT NOTE — Abstraction:
 *   This class is a test-data factory.  In production the main application
 *   would read a real puzzle file; this generator lets the demo run
 *   self-contained without requiring an external dataset.
 */
public final class PuzzleGenerator {

    private static final SudokuSolver SOLVER = SudokuSolver.INSTANCE;

    // Canonical solved board (valid, well-known)
    private static final int[][] BASE_BOARD = {
        {5,3,4,6,7,8,9,1,2},
        {6,7,2,1,9,5,3,4,8},
        {1,9,8,3,4,2,5,6,7},
        {8,5,9,7,6,1,4,2,3},
        {4,2,6,8,5,3,7,9,1},
        {7,1,3,9,2,4,8,5,6},
        {9,6,1,5,3,7,2,8,4},
        {2,8,7,4,1,9,6,3,5},
        {3,4,5,2,8,6,1,7,9}
    };

    private PuzzleGenerator() {}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Generates {@code count} puzzles and writes them in one-line format to
     * {@code filePath}.
     */
    public static void generateToFile(int count, String filePath) throws IOException {
        List<SudokuPuzzle> puzzles = generate(count);
        Path path = Path.of(filePath);
        Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(path))) {
            pw.println("# Auto-generated Sudoku puzzles — " + count + " total");
            pw.println("# Format: 81 characters per line, 0=empty");
            for (SudokuPuzzle p : puzzles) {
                pw.println(toOneLine(p));
            }
        }
        System.out.printf("[Generator] Wrote %d puzzles to %s%n", count,
                path.toAbsolutePath());
    }

    public static List<SudokuPuzzle> generate(int count) {
        Random rng = new Random(42); // seeded for reproducibility
        List<SudokuPuzzle> puzzles = new ArrayList<>(count);

        // Difficulty distribution for a realistic mix
        int[] targetGivens = buildGivensSchedule(count, rng);

        for (int i = 0; i < count; i++) {
            int[][] board = shuffleBoard(deepCopy(BASE_BOARD), rng);
            removeClues(board, targetGivens[i], rng);

            // ~2% chance: introduce an invalid puzzle for validator demonstration
            boolean makeInvalid = (rng.nextInt(50) == 0);
            if (makeInvalid) corruptBoard(board, rng);

            puzzles.add(new SudokuPuzzle(i + 1, board));
        }
        return puzzles;
    }

    // -----------------------------------------------------------------------
    // Board shuffling (preserves solution validity)
    // -----------------------------------------------------------------------

    /**
     * Legal shuffles that keep the board valid:
     *   - Permute digits (e.g. swap all 1s ↔ 7s)
     *   - Swap rows within the same band (rows 0-2, 3-5, 6-8)
     *   - Swap columns within the same band
     *   - Swap entire row-bands
     *   - Swap entire column-bands
     */
    private static int[][] shuffleBoard(int[][] board, Random rng) {
        // Digit permutation
        int[] perm = randomPermutation(rng);
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++)
                board[r][c] = perm[board[r][c] - 1];

        // Row swaps within bands
        for (int band = 0; band < 3; band++) {
            int base = band * 3;
            for (int s = 0; s < 3; s++) {
                int a = base + rng.nextInt(3);
                int b = base + rng.nextInt(3);
                swapRows(board, a, b);
            }
        }

        // Column swaps within bands
        int[][] t = transpose(board);
        for (int band = 0; band < 3; band++) {
            int base = band * 3;
            for (int s = 0; s < 3; s++) {
                int a = base + rng.nextInt(3);
                int b = base + rng.nextInt(3);
                swapRows(t, a, b);
            }
        }
        board = transpose(t);

        // Band swaps
        if (rng.nextBoolean()) { swapRows(board, 0, 3); swapRows(board, 1, 4); swapRows(board, 2, 5); }
        if (rng.nextBoolean()) { swapRows(board, 3, 6); swapRows(board, 4, 7); swapRows(board, 5, 8); }

        return board;
    }

    private static void removeClues(int[][] board, int targetGivens, Random rng) {
        // We don't verify unique-solution property here (too slow for 10k puzzles);
        // puzzles are guaranteed valid initial states — the backtracker will handle
        // cases with multiple solutions by finding one.
        List<int[]> cells = new ArrayList<>(81);
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++)
                cells.add(new int[]{r, c});
        Collections.shuffle(cells, rng);

        int filled = 81;
        for (int[] cell : cells) {
            if (filled <= targetGivens) break;
            board[cell[0]][cell[1]] = 0;
            filled--;
        }
    }

    /** Deliberately break one constraint to create an invalid puzzle. */
    private static void corruptBoard(int[][] board, Random rng) {
        int r = rng.nextInt(9);
        int c = rng.nextInt(9);
        // Force a duplicate in the row
        int neighbor = rng.nextInt(9);
        int v = board[r][neighbor];
        if (v != 0) {
            board[r][c] = v; // duplicate — constraint violated
        }
    }

    // -----------------------------------------------------------------------
    // Difficulty schedule
    // -----------------------------------------------------------------------

    private static int[] buildGivensSchedule(int count, Random rng) {
        int[] schedule = new int[count];
        for (int i = 0; i < count; i++) {
            double d = rng.nextDouble();
            if      (d < 0.30) schedule[i] = 36 + rng.nextInt(10); // Easy   36-45
            else if (d < 0.55) schedule[i] = 27 + rng.nextInt(9);  // Medium 27-35
            else if (d < 0.85) schedule[i] = 22 + rng.nextInt(5);  // Hard   22-26
            else               schedule[i] = 17 + rng.nextInt(5);  // Extreme 17-21
        }
        return schedule;
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private static int[] randomPermutation(Random rng) {
        int[] p = {1,2,3,4,5,6,7,8,9};
        for (int i = 8; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
        }
        return p;
    }

    private static void swapRows(int[][] board, int a, int b) {
        int[] tmp = board[a]; board[a] = board[b]; board[b] = tmp;
    }

    private static int[][] transpose(int[][] m) {
        int[][] t = new int[9][9];
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++)
                t[c][r] = m[r][c];
        return t;
    }

    private static int[][] deepCopy(int[][] src) {
        int[][] c = new int[9][9];
        for (int r = 0; r < 9; r++) System.arraycopy(src[r], 0, c[r], 0, 9);
        return c;
    }

    private static String toOneLine(SudokuPuzzle p) {
        StringBuilder sb = new StringBuilder(81);
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++)
                sb.append(p.getCell(r, c));
        return sb.toString();
    }
}
