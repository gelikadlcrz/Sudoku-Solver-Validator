# Batch Sudoku Validator & Solver
### PLT-Parallel Bridge Matrix — Java (Shared-State) Implementation

---

## Overview

This application ingests up to 10,000 Sudoku puzzles, validates their initial states, and solves them in parallel using Java's **Shared-State concurrency model**. It is the Java half of a two-language study that applies the **PLT-Parallel Bridge Matrix** framework to a concrete computational problem.

---

## PLT-Parallel Bridge Matrix Walkthrough

### Phase 1 — Problem & Paradigm Identification

| Dimension | Decision |
|---|---|
| **Problem domain** | Batch Sudoku solving |
| **Parallelism type** | **Task Parallelism** — each puzzle is a distinct, independent task with variable execution time |
| **Paradigm** | **Shared-State** (Java) |
| **Workload** | **CPU-Bound** → thread count = `availableProcessors()` |

Task Parallelism is the correct classification because individual puzzles are not uniform units of work: an "easy" puzzle (40+ givens) solves in microseconds, while an "extreme" puzzle (17 givens) may require millions of backtracking steps. This non-uniform workload makes **dynamic load-balancing via a shared work queue** the optimal architectural choice.

---

### Phase 2 — Variable Architecture

#### Shared (heap-allocated, accessed by multiple threads)

```
ConcurrentLinkedQueue<SudokuPuzzle>   puzzleQueue   // the work queue
CopyOnWriteArrayList<SolveResult>     results        // output accumulator
```

**Type binding:** Static (compile-time). Java's type system guarantees at compile time that `puzzleQueue` holds only `SudokuPuzzle` objects and `results` holds only `SolveResult` objects. No runtime type confusion is possible.

**Lifetime:** `puzzleQueue` objects are dequeued and become eligible for GC as soon as no reference holds them. `SolveResult` objects live until the main thread writes output files and the list is released.

#### Thread-local (stack-allocated, invisible to other threads)

```
int[][] board   // working copy inside SudokuSolver.solve()
int[]   cell    // MRV candidate cell
int     digit   // current backtracking candidate
```

The `board` array is a deep copy created per-puzzle inside `solve()`. Its lifetime is bounded to the solver call stack. No other thread can ever see this reference.

---

### Phase 3 — Control Constructs & Abstraction

**Key design choice: zero explicit locks.**

| Construct | Purpose | Thread-safety mechanism |
|---|---|---|
| `ConcurrentLinkedQueue.poll()` | Dequeue next puzzle | Michael-Scott non-blocking CAS |
| `CopyOnWriteArrayList.add()` | Write result | Copy-on-write array replacement |
| `ExecutorService.newFixedThreadPool(N)` | Thread lifecycle | JVM-managed pool |

No `synchronized` blocks, no `ReentrantLock`, no `Semaphore` — the lock-free data structures handle mutual exclusion internally. This is the canonical Java idiom for high-throughput concurrent worker pools (Goetz et al., 2006).

**Abstraction hierarchy:**
```
ExecutorService (thread lifecycle)
  └── SudokuWorker (task loop)
        ├── SudokuValidator (constraint checking)
        └── SudokuSolver    (backtracking + MRV)
```

**Value Binding in the backtracking algorithm** (PLT concept made visible):
```java
board[row][col] = digit;          // bind   — tentative assignment
if (backtrack(board)) return true; // recurse
board[row][col] = 0;               // unbind — backtrack on failure
```
This is Java's mutable value binding in action. Compare to Erlang's single-assignment invariant, where re-binding is illegal at the language level.

---

### Phase 4 — Syntactic Formalization (BNF sketch)

```bnf
<batch-solve>    ::= <load-puzzles> <run-sequential> <run-parallel> <write-output>

<run-parallel>   ::= new ExecutorService(N) -> submit(<worker>)* -> shutdown -> awaitTermination

<worker-loop>    ::= while (queue.poll() != null) { <validate> ; <solve> ; results.add() ; }

<validate>       ::= SudokuValidator.isValid(<puzzle>) -> boolean

<solve>          ::= SudokuSolver.solve(<puzzle>) -> int[][] | null

<backtrack>      ::= <find-mrv-cell> ( <bind> <backtrack> <unbind> )* -> boolean

<shared-queue>   ::= new ConcurrentLinkedQueue<>( <puzzle-list> )

<shared-list>    ::= new CopyOnWriteArrayList<>()

<result>         ::= new SolveResult( id, status, board?, nanos, threadName )
```

---

## Project Structure

```
sudoku-solver/
├── src/main/java/com/plt/sudoku/
│   ├── Main.java                          ← Entry point, CLI, benchmark harness
│   ├── model/
│   │   ├── SudokuPuzzle.java              ← Immutable puzzle (deep copy on construct)
│   │   └── SolveResult.java               ← Outcome record (id, status, board, timing)
│   ├── validator/
│   │   └── SudokuValidator.java           ← Stateless row/col/box constraint checker
│   ├── solver/
│   │   └── SudokuSolver.java              ← Backtracking + MRV heuristic
│   ├── parallel/
│   │   ├── SudokuWorker.java              ← Runnable — drains shared queue
│   │   └── ParallelSolverOrchestrator.java← Thread pool setup + sequential baseline
│   ├── generator/
│   │   └── PuzzleGenerator.java           ← Self-contained test data factory
│   └── util/
│       ├── PuzzleFileParser.java           ← Reads one-line or grid format files
│       └── ResultWriter.java              ← Console report + file output
├── run.sh                                 ← Build & run script
└── README.md
```

---

## Puzzle File Format

**One-line format** (recommended, compatible with Project Euler / Kaggle datasets):
```
# Lines starting with # are comments
003020600900305001001806400008102900700000008006708200002609500800203009005010300
...
```
Each line is exactly 81 characters. `0` or `.` = empty cell.

**Grid format** (human-readable, 9 lines per puzzle):
```
003020600
900305001
001806400
...
```

The parser auto-detects the format.

---

## Build & Run

### Prerequisites
- Java 17 or later (`java`, `javac`)

### Compile & run (all-in-one)
```bash
chmod +x run.sh
./run.sh
```

### Manual steps
```bash
# Compile
mkdir -p out
find src -name "*.java" | xargs javac -d out

# Run with generated puzzles (default 10,000)
java -cp out com.plt.sudoku.Main

# Run with a puzzle file
java -cp out com.plt.sudoku.Main --file puzzles.txt

# Control thread count and output directory
java -cp out com.plt.sudoku.Main --count 10000 --threads 8 --output results/

# Generate a puzzle file for later use
java -cp out com.plt.sudoku.Main --generate puzzles/my_10k.txt --count 10000

# Skip sequential baseline (faster for huge batches)
java -cp out com.plt.sudoku.Main --no-seq

# Run JAR directly (after build)
java -jar sudoku-solver.jar --count 10000
```

---

## Output

| File | Contents |
|---|---|
| `output/results_solved.txt` | `id,81-char-solution` per solved puzzle |
| `output/results_invalid.txt` | IDs of puzzles with illegal initial states |
| `output/results_unsolvable.txt` | IDs of valid but unsolvable puzzles |
| `output/benchmark_report.txt` | Full per-puzzle timing + speedup table |

---

## Performance Metrics

The application computes the speedup formulas from the PLT-Parallel Bridge Matrix:

```
S = Ts / Tp          (Speedup)
E = S / N  × 100%    (Efficiency)

where Ts = sequential time, Tp = parallel time, N = thread count
```

Expected results on a modern multi-core machine with 10,000 puzzles and N=8 threads:
- S ≈ 4–6× (sub-linear due to queue contention and GC pressure on hard puzzles)
- E ≈ 50–75% (acceptable for CPU-bound Task Parallelism with variable task duration)

---

## Key Implementation Notes

### Why `ConcurrentLinkedQueue` instead of `BlockingQueue`?
`poll()` is non-blocking and returns `null` when empty, which is perfect for the "drain until empty" pattern. `BlockingQueue.take()` would require an explicit poison-pill sentinel to signal termination, adding complexity.

### Why `CopyOnWriteArrayList` for results?
Each worker thread writes exactly once per puzzle, and all reads happen after all writes. The copy-on-write cost is paid per write, not per read, which is fine for our access pattern.

### Why MRV (Minimum Remaining Values) in the solver?
MRV selects the empty cell with the fewest legal candidates first. This dramatically reduces the effective branching factor: starting with a cell that has only 1 legal value is deterministic; starting with a cell that has 9 legal values wastes time on guaranteed dead-ends. In practice, MRV reduces backtracking by 80–95% on hard puzzles.

---

## References

- Goetz, B. et al. (2006). *Java Concurrency in Practice*. Addison-Wesley.
- Herlihy, M. & Shavit, N. (2012). *The Art of Multiprocessor Programming*. Elsevier.
- Mattson, T. et al. (2004). *Patterns for Parallel Programming*. Addison-Wesley.
- McConnell, S. (2004). *Code Complete* (2nd ed.). Microsoft Press.
- Scott, M. L. (2016). *Programming Language Pragmatics* (4th ed.). Morgan Kaufmann.
