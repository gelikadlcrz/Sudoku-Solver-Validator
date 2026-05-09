package com.plt.sudoku.validator;

import com.plt.sudoku.model.SudokuPuzzle;

/**
 * Stateless validator — every method is pure (no side effects, no shared state).
 *
 * PLT NOTE — Scope & Shared State:
 *   Because this class holds no instance fields, its instances are inherently
 *   thread-safe.  Every local variable lives exclusively on the calling
 *   thread's stack frame, so there is no shared mutable state to protect.
 *   This is the simplest way to achieve thread safety: don't share mutable
 *   state at all.
 *
 * Validation Rules (standard Sudoku):
 *   1. Every filled cell (1–9) must appear at most once per row.
 *   2. Every filled cell (1–9) must appear at most once per column.
 *   3. Every filled cell (1–9) must appear at most once per 3×3 box.
 *   4. No cell may hold a value outside [0, 9].
 */
public final class SudokuValidator {

    // Singleton — stateless, so one instance for the whole JVM is fine.
    public static final SudokuValidator INSTANCE = new SudokuValidator();

    private SudokuValidator() {}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns true iff the puzzle's initial state is a legal (partial) grid.
     * A partially filled grid is valid as long as no constraint is currently
     * violated — empty cells (0) are ignored.
     */
    public boolean isValid(SudokuPuzzle puzzle) {
        return rowsValid(puzzle)
            && colsValid(puzzle)
            && boxesValid(puzzle)
            && valuesInRange(puzzle);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private boolean valuesInRange(SudokuPuzzle p) {
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++) {
                int v = p.getCell(r, c);
                if (v < 0 || v > 9) return false;
            }
        return true;
    }

    private boolean rowsValid(SudokuPuzzle p) {
        for (int r = 0; r < 9; r++) {
            boolean[] seen = new boolean[10]; // index 1–9
            for (int c = 0; c < 9; c++) {
                int v = p.getCell(r, c);
                if (v == 0) continue;
                if (seen[v]) return false;
                seen[v] = true;
            }
        }
        return true;
    }

    private boolean colsValid(SudokuPuzzle p) {
        for (int c = 0; c < 9; c++) {
            boolean[] seen = new boolean[10];
            for (int r = 0; r < 9; r++) {
                int v = p.getCell(r, c);
                if (v == 0) continue;
                if (seen[v]) return false;
                seen[v] = true;
            }
        }
        return true;
    }

    private boolean boxesValid(SudokuPuzzle p) {
        for (int boxRow = 0; boxRow < 3; boxRow++) {
            for (int boxCol = 0; boxCol < 3; boxCol++) {
                boolean[] seen = new boolean[10];
                for (int r = boxRow * 3; r < boxRow * 3 + 3; r++) {
                    for (int c = boxCol * 3; c < boxCol * 3 + 3; c++) {
                        int v = p.getCell(r, c);
                        if (v == 0) continue;
                        if (seen[v]) return false;
                        seen[v] = true;
                    }
                }
            }
        }
        return true;
    }
}
