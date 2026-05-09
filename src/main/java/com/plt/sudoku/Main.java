package com.plt.sudoku;

import com.plt.sudoku.generator.PuzzleGenerator;
import com.plt.sudoku.model.SolveResult;
import com.plt.sudoku.model.SudokuPuzzle;
import com.plt.sudoku.parallel.ParallelSolverOrchestrator;
import com.plt.sudoku.ui.SudokuUI;
import com.plt.sudoku.util.PuzzleFileParser;
import com.plt.sudoku.util.ResultWriter;

import java.util.List;

public final class Main {

    public static void main(String[] args) throws Exception {
        boolean headless = false;
        String  file     = null;
        int     count    = 10_000;
        int     threads  = Runtime.getRuntime().availableProcessors();
        String  output   = "output";
        boolean noSeq    = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--headless" -> headless = true;
                case "--file"     -> file    = args[++i];
                case "--count"    -> count   = Integer.parseInt(args[++i]);
                case "--threads"  -> threads = Integer.parseInt(args[++i]);
                case "--output"   -> output  = args[++i];
                case "--no-seq"   -> noSeq   = true;
            }
        }

        if (!headless) {
            SudokuUI.launch();
            return;
        }

        List<SudokuPuzzle> puzzles = (file != null)
                ? PuzzleFileParser.parseFile(file)
                : PuzzleGenerator.generate(count);
        System.out.printf("[Main] Loaded %d puzzles%n", puzzles.size());

        ParallelSolverOrchestrator orch = new ParallelSolverOrchestrator(threads);

        long seqMs = 0;
        if (!noSeq) {
            long t = System.currentTimeMillis();
            orch.solveSequential(puzzles);
            seqMs = System.currentTimeMillis() - t;
        }

        long t2 = System.currentTimeMillis();
        List<SolveResult> results = orch.solve(puzzles);
        long parMs = System.currentTimeMillis() - t2;

        ResultWriter.printSummary(results, parMs, seqMs, threads);
        ResultWriter.writeResults(results, output, parMs, seqMs, threads);
    }
}
