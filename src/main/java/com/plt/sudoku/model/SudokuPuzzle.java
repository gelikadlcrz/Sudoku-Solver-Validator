package com.plt.sudoku.model;

/**
 * Immutable snapshot of a Sudoku puzzle's initial state.
 * The board is a 9×9 int array where 0 represents an empty cell.
 *
 * PLT NOTE — Value Binding & Lifetime:
 *   The board array is copied on construction so that worker threads
 *   never share a reference to the same grid (a data race would corrupt
 *   the backtracking state).  Each SudokuPuzzle lives on the shared heap
 *   but its mutable "working copy" is created inside the solver's stack
 *   frame, keeping lifetime scoped to the task.
 */
public final class SudokuPuzzle {

    private final int id;
    private final int[][] board; // 0 = empty cell

    public SudokuPuzzle(int id, int[][] board) {
        this.id = id;
        // Defensive copy — never hand out a reference we don't own.
        this.board = deepCopy(board);
    }

    public int getId() { return id; }

    /** Returns a fresh deep copy so the solver can mutate freely. */
    public int[][] getBoardCopy() { return deepCopy(board); }

    /** Read-only peek used by the validator (no copy needed — we only read). */
    public int getCell(int row, int col) { return board[row][col]; }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static int[][] deepCopy(int[][] src) {
        int[][] copy = new int[9][9];
        for (int r = 0; r < 9; r++)
            System.arraycopy(src[r], 0, copy[r], 0, 9);
        return copy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Puzzle #").append(id).append('\n');
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                sb.append(board[r][c] == 0 ? '.' : (char)('0' + board[r][c]));
                if (c == 2 || c == 5) sb.append('|');
            }
            sb.append('\n');
            if (r == 2 || r == 5) sb.append("---+---+---\n");
        }
        return sb.toString();
    }
}
