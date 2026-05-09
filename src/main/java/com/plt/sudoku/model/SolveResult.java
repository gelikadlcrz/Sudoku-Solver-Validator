package com.plt.sudoku.model;

/**
 * The outcome of processing one puzzle — carries the original id,
 * solved board (or null), and timing / status metadata.
 *
 * PLT NOTE — Type Binding:
 *   Java uses static type binding at compile time.  The SolveResult type
 *   guarantees at compile time that every consumer of this object sees a
 *   consistent, fully-populated record — no null-pointer surprises from
 *   forgotten fields.
 */
public final class SolveResult {

    public enum Status { SOLVED, INVALID_PUZZLE, UNSOLVABLE }

    private final int puzzleId;
    private final Status status;
    private final int[][] solvedBoard;   // null unless status == SOLVED
    private final long solveTimeNanos;
    private final String workerThread;

    public SolveResult(int puzzleId, Status status,
                       int[][] solvedBoard, long solveTimeNanos,
                       String workerThread) {
        this.puzzleId      = puzzleId;
        this.status        = status;
        this.solvedBoard   = solvedBoard;
        this.solveTimeNanos = solveTimeNanos;
        this.workerThread  = workerThread;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public int     getPuzzleId()       { return puzzleId; }
    public Status  getStatus()         { return status; }
    public int[][] getSolvedBoard()    { return solvedBoard; }
    public long    getSolveTimeNanos() { return solveTimeNanos; }
    public String  getWorkerThread()   { return workerThread; }

    public String boardToString() {
        if (solvedBoard == null) return "(no solution)";
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                sb.append(solvedBoard[r][c]);
                if (c == 2 || c == 5) sb.append('|');
            }
            sb.append('\n');
            if (r == 2 || r == 5) sb.append("---+---+---\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("Puzzle #%d [%s] solved in %.3f ms by %s",
                puzzleId, status,
                solveTimeNanos / 1_000_000.0,
                workerThread);
    }
}
