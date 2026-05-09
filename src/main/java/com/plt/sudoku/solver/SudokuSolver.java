package com.plt.sudoku.solver;

import com.plt.sudoku.model.SudokuPuzzle;

/**
 * Classic recursive backtracking solver with Minimum Remaining Values (MRV)
 * heuristic to improve average-case performance.
 *
 * PLT NOTE — Value Binding & Lifetime:
 *   solve() receives a board copy whose lifetime is bounded to the call.
 *   The backtracking algorithm temporarily binds a value to a cell
 *   (board[row][col] = digit), recurses, and then UNbinds it
 *   (board[row][col] = 0) if the branch fails.  This pattern makes the
 *   "value binding" concept from PLT theory directly visible: a variable
 *   can be re-bound to a new value within the same scope as long as the
 *   language (Java) permits mutation — contrasted with Erlang's
 *   single-assignment invariant where re-binding is illegal.
 *
 * Algorithm:
 *   1. Find the unfilled cell with the fewest legal candidates (MRV).
 *   2. For each candidate, tentatively bind it, recurse.
 *   3. If recursion succeeds → propagate success upward.
 *   4. If recursion fails  → undo binding, try next candidate.
 *   5. If no candidates    → this branch is dead; return false.
 *   6. If no empty cells   → puzzle is solved; return true.
 */
public final class SudokuSolver {

    public static final SudokuSolver INSTANCE = new SudokuSolver();

    private SudokuSolver() {}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Attempts to solve the puzzle.
     *
     * @param puzzle the puzzle to solve (read-only; a working copy is made internally)
     * @return a fully solved 9×9 board, or null if the puzzle has no solution
     */
    public int[][] solve(SudokuPuzzle puzzle) {
        int[][] board = puzzle.getBoardCopy(); // mutable working copy
        if (backtrack(board)) return board;
        return null;
    }

    // -----------------------------------------------------------------------
    // Core backtracking engine
    // -----------------------------------------------------------------------

    /**
     * Mutates {@code board} in-place.  Returns true iff a solution was found.
     *
     * PLT TIE-IN: each recursive call gets its own stack frame, so local
     * variables (row, col, candidates) are scoped to that invocation.  The
     * board array itself lives on the heap and is shared across all recursive
     * calls — this is the one shared mutable state, intentionally managed by
     * the backtracking protocol (assign → recurse → undo).
     */
    private boolean backtrack(int[][] board) {
        // Step 1 — pick the best empty cell (MRV heuristic)
        int[] cell = findMRVCell(board);
        if (cell == null) return true; // no empty cells → solved!

        int row = cell[0];
        int col = cell[1];

        // Step 2 — try each legal digit
        for (int digit = 1; digit <= 9; digit++) {
            if (isLegal(board, row, col, digit)) {
                board[row][col] = digit;          // bind
                if (backtrack(board)) return true; // recurse
                board[row][col] = 0;               // unbind (backtrack)
            }
        }
        return false; // no digit worked → dead end
    }

    // -----------------------------------------------------------------------
    // MRV heuristic — pick the cell with the fewest remaining legal values
    // -----------------------------------------------------------------------

    /**
     * Returns {row, col} of the empty cell with the minimum number of legal
     * candidates, or null if no empty cell exists.
     *
     * MRV reduces the effective branching factor and dramatically cuts the
     * number of backtracks on harder puzzles.
     */
    private int[] findMRVCell(int[][] board) {
        int bestCount = Integer.MAX_VALUE;
        int[] best = null;

        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (board[r][c] != 0) continue;
                int count = countLegal(board, r, c);
                if (count == 0) return new int[]{r, c}; // immediate dead end
                if (count < bestCount) {
                    bestCount = count;
                    best = new int[]{r, c};
                }
            }
        }
        return best;
    }

    private int countLegal(int[][] board, int row, int col) {
        int count = 0;
        for (int d = 1; d <= 9; d++)
            if (isLegal(board, row, col, d)) count++;
        return count;
    }

    // -----------------------------------------------------------------------
    // Constraint checking
    // -----------------------------------------------------------------------

    /**
     * Returns true iff placing {@code digit} at (row, col) violates no
     * row, column, or 3×3-box constraint.
     */
    private boolean isLegal(int[][] board, int row, int col, int digit) {
        // Row check
        for (int c = 0; c < 9; c++)
            if (board[row][c] == digit) return false;

        // Column check
        for (int r = 0; r < 9; r++)
            if (board[r][col] == digit) return false;

        // 3×3 box check
        int boxStartRow = (row / 3) * 3;
        int boxStartCol = (col / 3) * 3;
        for (int r = boxStartRow; r < boxStartRow + 3; r++)
            for (int c = boxStartCol; c < boxStartCol + 3; c++)
                if (board[r][c] == digit) return false;

        return true;
    }
}
