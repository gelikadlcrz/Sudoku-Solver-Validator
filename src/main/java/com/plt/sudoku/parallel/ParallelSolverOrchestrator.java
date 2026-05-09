package com.plt.sudoku.parallel;

import com.plt.sudoku.model.SolveResult;
import com.plt.sudoku.model.SudokuPuzzle;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

/**
 * Orchestrates the parallel solving session.
 *
 * ============================================================
 * PLT-Parallel Bridge Matrix — Phase 1 & 3 combined
 * ============================================================
 *
 * PARADIGM: Task Parallelism via Shared-State (Java)
 *
 * Task Parallelism: each puzzle is an independent task with its own
 * execution path and timeline.  Some puzzles finish in microseconds
 * (simple givens) while others require deep backtracking (hard puzzles),
 * producing natural load imbalance — exactly why this problem is
 * classified as Task Parallelism rather than Data Parallelism.
 *
 * CONTROL CONSTRUCT — ExecutorService (Fixed Thread Pool):
 *   Creates exactly `threadCount` OS-level threads that remain alive for
 *   the entire processing window.  Thread creation overhead is paid once
 *   at startup, not per-puzzle.  The pool distributes runnable tasks
 *   across threads via its internal BlockingQueue<Runnable>.
 *
 * WORKLOAD: CPU-Bound.
 *   Backtracking is pure computation; no disk or network I/O.  The
 *   optimal thread count is therefore N_cores (or N_cores + 1 at most),
 *   not hundreds of threads as one would use for I/O-bound work.
 *   We default to Runtime.getRuntime().availableProcessors().
 *
 * SHARED STATE SUMMARY:
 *   puzzleQueue  — ConcurrentLinkedQueue (lock-free, many producers / many
 *                  consumers; here populated before threads start, then
 *                  drained by workers)
 *   results      — CopyOnWriteArrayList (safe concurrent appends)
 *
 * MUTUAL EXCLUSION: none explicit.  Both collections provide their own
 *   thread-safety guarantees internally.
 */
public final class ParallelSolverOrchestrator {

    private final int threadCount;

    public ParallelSolverOrchestrator() {
        this(Runtime.getRuntime().availableProcessors());
    }

    public ParallelSolverOrchestrator(int threadCount) {
        if (threadCount < 1) throw new IllegalArgumentException("threadCount must be >= 1");
        this.threadCount = threadCount;
    }

    // -----------------------------------------------------------------------
    // Main entry point
    // -----------------------------------------------------------------------

    /**
     * Solves all puzzles in parallel and returns an unmodifiable result list.
     *
     * Steps:
     *   1. Load all puzzles into the shared ConcurrentLinkedQueue.
     *   2. Create a fixed thread pool.
     *   3. Submit one SudokuWorker per thread (each worker self-terminates
     *      when the queue is empty).
     *   4. Shut down the pool and await termination.
     *   5. Return collected results.
     *
     * @param puzzles list of puzzles to process
     * @return unmodifiable list of SolveResult in completion order
     */
    public List<SolveResult> solve(List<SudokuPuzzle> puzzles) throws InterruptedException {

        // ----- Shared data structures ---------------------------------------
        ConcurrentLinkedQueue<SudokuPuzzle>  puzzleQueue = new ConcurrentLinkedQueue<>(puzzles);
        CopyOnWriteArrayList<SolveResult>    results     = new CopyOnWriteArrayList<>();

        // ----- Thread pool --------------------------------------------------
        ExecutorService pool = Executors.newFixedThreadPool(
                threadCount,
                r -> {
                    Thread t = new Thread(r);
                    t.setName("sudoku-worker-" + t.getId());
                    t.setDaemon(true); // don't block JVM shutdown
                    return t;
                });

        System.out.printf("[Orchestrator] Starting %d worker threads for %d puzzles%n",
                threadCount, puzzles.size());

        // Submit one worker per thread.  Each worker loops until the queue is
        // empty, so we don't need one Future per puzzle — just one per thread.
        for (int i = 0; i < threadCount; i++) {
            pool.submit(new SudokuWorker(puzzleQueue, results));
        }

        // ----- Await completion ---------------------------------------------
        pool.shutdown(); // no new tasks accepted
        boolean finished = pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        if (!finished) {
            System.err.println("[Orchestrator] WARNING: pool did not terminate cleanly.");
        }

        return Collections.unmodifiableList(results);
    }

    // -----------------------------------------------------------------------
    // Sequential baseline (for Speedup calculation: S = Ts / Tp)
    // -----------------------------------------------------------------------

    /**
     * Runs the exact same validation + solving logic on a single thread,
     * producing results in puzzle-id order.  Used to compute the sequential
     * baseline time Ts.
     */
    public List<SolveResult> solveSequential(List<SudokuPuzzle> puzzles) {
        ConcurrentLinkedQueue<SudokuPuzzle> queue   = new ConcurrentLinkedQueue<>(puzzles);
        CopyOnWriteArrayList<SolveResult>   results = new CopyOnWriteArrayList<>();
        // Re-use the same worker code path on the calling thread.
        new SudokuWorker(queue, results).run();
        return Collections.unmodifiableList(results);
    }

    public int getThreadCount() { return threadCount; }
}
