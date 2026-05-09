package com.plt.sudoku.util;

import com.plt.sudoku.model.SolveResult;
import com.plt.sudoku.model.SolveResult.Status;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Writes solve results to disk and prints a summary report to stdout.
 *
 * Output files produced:
 *   results_solved.txt      — one line per solved puzzle  (id + flat 81-char solution)
 *   results_invalid.txt     — ids of puzzles with illegal initial states
 *   results_unsolvable.txt  — ids of valid but unsolvable puzzles
 *   benchmark_report.txt    — full timing / speedup table (if both seq and par times given)
 */
public final class ResultWriter {

    private ResultWriter() {}

    // -----------------------------------------------------------------------
    // Console summary
    // -----------------------------------------------------------------------

    public static void printSummary(List<SolveResult> results,
                                    long parallelMs,
                                    long sequentialMs,
                                    int  threadCount) {
        long solved      = results.stream().filter(r -> r.getStatus() == Status.SOLVED).count();
        long invalid     = results.stream().filter(r -> r.getStatus() == Status.INVALID_PUZZLE).count();
        long unsolvable  = results.stream().filter(r -> r.getStatus() == Status.UNSOLVABLE).count();

        long totalNanos  = results.stream().mapToLong(SolveResult::getSolveTimeNanos).sum();
        OptionalDouble avgMs = results.stream()
                .mapToDouble(r -> r.getSolveTimeNanos() / 1_000_000.0)
                .average();

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║           BATCH SUDOKU VALIDATOR & SOLVER                ║");
        System.out.println("║           PLT-Parallel Bridge Matrix — Java              ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Total puzzles processed : %-30d║%n", results.size());
        System.out.printf( "║  ✓ Solved                : %-30d║%n", solved);
        System.out.printf( "║  ✗ Invalid initial state : %-30d║%n", invalid);
        System.out.printf( "║  ✗ No solution exists    : %-30d║%n", unsolvable);
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Worker threads          : %-30d║%n", threadCount);
        System.out.printf( "║  Sequential time (Ts)    : %-27s ms ║%n", sequentialMs);
        System.out.printf( "║  Parallel time   (Tp)    : %-27s ms ║%n", parallelMs);

        if (parallelMs > 0 && sequentialMs > 0) {
            double speedup    = (double) sequentialMs / parallelMs;
            double efficiency = speedup / threadCount * 100.0;
            System.out.printf("║  Speedup  S = Ts/Tp      : %-27.2f   ║%n", speedup);
            System.out.printf("║  Efficiency E = S/N      : %-26.1f%%  ║%n", efficiency);
        }

        System.out.printf( "║  Avg solve time per puzzle: %-25s ms ║%n",
                avgMs.isPresent() ? String.format("%.4f", avgMs.getAsDouble()) : "N/A");
        System.out.printf( "║  Total CPU work (sum)    : %-27s ms ║%n",
                String.format("%.2f", totalNanos / 1_000_000.0));
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        // Per-thread workload breakdown
        System.out.println("\n── Per-thread workload ──────────────────────────────────────");
        results.stream()
               .collect(Collectors.groupingBy(SolveResult::getWorkerThread,
                        Collectors.counting()))
               .entrySet().stream()
               .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
               .forEach(e -> System.out.printf("  %-30s → %d puzzles%n", e.getKey(), e.getValue()));
        System.out.println();
    }

    // -----------------------------------------------------------------------
    // File output
    // -----------------------------------------------------------------------

    public static void writeResults(List<SolveResult> results,
                                    String outputDir,
                                    long parallelMs,
                                    long sequentialMs,
                                    int  threadCount) throws IOException {
        Files.createDirectories(Path.of(outputDir));

        writeSolved    (results, outputDir);
        writeInvalid   (results, outputDir);
        writeUnsolvable(results, outputDir);
        writeBenchmark (results, outputDir, parallelMs, sequentialMs, threadCount);

        System.out.println("[Writer] Output written to: " + Path.of(outputDir).toAbsolutePath());
    }

    private static void writeSolved(List<SolveResult> results, String dir) throws IOException {
        Path path = Path.of(dir, "results_solved.txt");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(path))) {
            pw.println("# id,solution_81chars");
            results.stream()
                   .filter(r -> r.getStatus() == Status.SOLVED)
                   .sorted(Comparator.comparingInt(SolveResult::getPuzzleId))
                   .forEach(r -> {
                       pw.print(r.getPuzzleId());
                       pw.print(',');
                       for (int[] row : r.getSolvedBoard())
                           for (int cell : row) pw.print(cell);
                       pw.println();
                   });
        }
    }

    private static void writeInvalid(List<SolveResult> results, String dir) throws IOException {
        Path path = Path.of(dir, "results_invalid.txt");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(path))) {
            pw.println("# Puzzles with illegal initial states");
            results.stream()
                   .filter(r -> r.getStatus() == Status.INVALID_PUZZLE)
                   .sorted(Comparator.comparingInt(SolveResult::getPuzzleId))
                   .forEach(r -> pw.println(r.getPuzzleId()));
        }
    }

    private static void writeUnsolvable(List<SolveResult> results, String dir) throws IOException {
        Path path = Path.of(dir, "results_unsolvable.txt");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(path))) {
            pw.println("# Valid puzzles for which no solution exists");
            results.stream()
                   .filter(r -> r.getStatus() == Status.UNSOLVABLE)
                   .sorted(Comparator.comparingInt(SolveResult::getPuzzleId))
                   .forEach(r -> pw.println(r.getPuzzleId()));
        }
    }

    private static void writeBenchmark(List<SolveResult> results,
                                       String dir,
                                       long parallelMs,
                                       long sequentialMs,
                                       int  threadCount) throws IOException {
        Path path = Path.of(dir, "benchmark_report.txt");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(path))) {
            pw.println("PLT-Parallel Bridge Matrix — Benchmark Report");
            pw.println("==============================================");
            pw.println("Paradigm  : Shared-State (Java)");
            pw.println("Construct : ConcurrentLinkedQueue + CopyOnWriteArrayList + FixedThreadPool");
            pw.printf ("Threads N : %d%n", threadCount);
            pw.printf ("Ts (seq)  : %d ms%n", sequentialMs);
            pw.printf ("Tp (par)  : %d ms%n", parallelMs);
            if (parallelMs > 0 && sequentialMs > 0) {
                double s = (double) sequentialMs / parallelMs;
                pw.printf("S = Ts/Tp : %.2f%n", s);
                pw.printf("E = S/N   : %.1f%%%n", s / threadCount * 100.0);
            }
            pw.println();
            pw.println("# Per-puzzle timing (id, status, nanos, thread)");
            results.stream()
                   .sorted(Comparator.comparingInt(SolveResult::getPuzzleId))
                   .forEach(r -> pw.printf("%d,%s,%d,%s%n",
                           r.getPuzzleId(), r.getStatus(),
                           r.getSolveTimeNanos(), r.getWorkerThread()));
        }
    }
}
