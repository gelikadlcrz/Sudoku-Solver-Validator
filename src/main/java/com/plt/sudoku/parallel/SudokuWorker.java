package com.plt.sudoku.parallel;

import com.plt.sudoku.model.SolveResult;
import com.plt.sudoku.model.SolveResult.Status;
import com.plt.sudoku.model.SudokuPuzzle;
import com.plt.sudoku.solver.SudokuSolver;
import com.plt.sudoku.validator.SudokuValidator;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runnable worker that drains a shared ConcurrentLinkedQueue of puzzles.
 *
 * ============================================================
 * PLT-Parallel Bridge Matrix — Phase 3: Control Constructs
 * ============================================================
 *
 * PARADIGM: Shared-State (Java)
 *
 * Shared data structures used here:
 *
 *   puzzleQueue  — ConcurrentLinkedQueue<SudokuPuzzle>
 *       A non-blocking, lock-free queue based on Michael-Scott algorithm.
 *       poll() is an atomic CAS (compare-and-swap) operation, so no two
 *       workers can dequeue the same puzzle even without an explicit lock.
 *       → No synchronized block needed for reading from the queue.
 *
 *   results      — CopyOnWriteArrayList<SolveResult>
 *       A thread-safe list backed by a fresh array copy on every write.
 *       Writes are expensive but reads are free; appropriate here because
 *       each worker writes exactly once per puzzle while the main thread
 *       reads everything at the end.
 *       → No synchronized block needed for adding a result.
 *
 * MUTUAL EXCLUSION: deliberately avoided by choosing lock-free data
 * structures. This is the canonical Java idiom for high-throughput
 * concurrent work queues (Goetz et al., 2006).
 *
 * VARIABLE LIFETIME:
 *   - `puzzle` is fetched from the heap queue but its working copy
 *     (created inside SudokuSolver.solve) lives only on the solver's
 *     call stack, dying when solve() returns. No references escape.
 *   - `result` is handed to the shared list and its lifetime extends
 *     until the list itself is GC'd — controlled by the main thread.
 *
 * SCOPE:
 *   - All loop-local variables (puzzle, result, startNs, elapsedNs)
 *     are block-scoped; they are invisible to other threads.
 */
public final class SudokuWorker implements Runnable {

    // -----------------------------------------------------------------------
    // Shared state (passed in by the orchestrator — not owned by this class)
    // -----------------------------------------------------------------------
    private final ConcurrentLinkedQueue<SudokuPuzzle> puzzleQueue;
    private final CopyOnWriteArrayList<SolveResult>   results;

    // Stateless singletons — thread-safe by design (no mutable fields)
    private static final SudokuValidator VALIDATOR = SudokuValidator.INSTANCE;
    private static final SudokuSolver   SOLVER    = SudokuSolver.INSTANCE;

    public SudokuWorker(ConcurrentLinkedQueue<SudokuPuzzle> puzzleQueue,
                        CopyOnWriteArrayList<SolveResult>   results) {
        this.puzzleQueue = puzzleQueue;
        this.results     = results;
    }

    // -----------------------------------------------------------------------
    // Worker loop
    // -----------------------------------------------------------------------

    /**
     * Keeps polling the shared queue until it is empty.
     * Each iteration:
     *   1. poll() — atomic, lock-free dequeue.
     *   2. validate the initial grid state.
     *   3. if valid → solve via backtracking; record SOLVED or UNSOLVABLE.
     *   4. if invalid → record INVALID_PUZZLE immediately.
     *   5. add SolveResult to the shared output list.
     */
    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();

        SudokuPuzzle puzzle;
        // poll() returns null when the queue is empty — natural termination.
        while ((puzzle = puzzleQueue.poll()) != null) {

            long startNs = System.nanoTime();
            SolveResult result;

            if (!VALIDATOR.isValid(puzzle)) {
                // Validation failure — no need to attempt solving.
                long elapsedNs = System.nanoTime() - startNs;
                result = new SolveResult(
                        puzzle.getId(), Status.INVALID_PUZZLE,
                        null, elapsedNs, threadName);
            } else {
                // Attempt backtracking solve on this thread's stack.
                int[][] solved = SOLVER.solve(puzzle);
                long elapsedNs = System.nanoTime() - startNs;

                if (solved != null) {
                    result = new SolveResult(
                            puzzle.getId(), Status.SOLVED,
                            solved, elapsedNs, threadName);
                } else {
                    result = new SolveResult(
                            puzzle.getId(), Status.UNSOLVABLE,
                            null, elapsedNs, threadName);
                }
            }

            // CopyOnWriteArrayList.add() is thread-safe without a lock.
            results.add(result);
        }
        // Thread terminates naturally; the thread pool reclaims it.
    }
}
