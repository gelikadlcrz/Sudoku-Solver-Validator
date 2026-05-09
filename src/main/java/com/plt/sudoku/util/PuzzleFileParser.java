package com.plt.sudoku.util;

import com.plt.sudoku.model.SudokuPuzzle;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a flat text file of Sudoku puzzles.
 *
 * Supported formats
 * -----------------
 *  1. ONE-LINE format (most common — used by Project Euler, Kaggle datasets):
 *       Each puzzle is exactly 81 characters on one line.
 *       '0' or '.' denotes an empty cell.
 *       Lines starting with '#' are treated as comments and skipped.
 *       Example:
 *         003020600900305001001806400008102900700000008006708200002609500800203009005010300
 *
 *  2. GRID format (human-readable, 9 lines per puzzle):
 *       Groups of 9 non-empty, non-comment lines each containing exactly 9
 *       digit characters (0 or . for empty).  Separator lines (e.g. "---+---+---")
 *       and pipe characters are stripped automatically.
 *       Example:
 *         003020600
 *         900305001
 *         ...
 *
 * The parser auto-detects the format from the first data line encountered.
 *
 * PLT NOTE — Abstraction:
 *   PuzzleFileParser is a pure I/O abstraction layer.  It produces typed
 *   SudokuPuzzle objects and shields the rest of the application from raw
 *   file-format details — a direct application of the Abstraction dimension
 *   of the PLT-Parallel Bridge Matrix.
 */
public final class PuzzleFileParser {

    private PuzzleFileParser() {}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public static List<SudokuPuzzle> parseFile(String filePath) throws IOException {
        List<String> rawLines = Files.readAllLines(Path.of(filePath));
        return parse(rawLines);
    }

    public static List<SudokuPuzzle> parseLines(List<String> lines) {
        return parse(lines);
    }

    // -----------------------------------------------------------------------
    // Core parsing logic
    // -----------------------------------------------------------------------

    private static List<SudokuPuzzle> parse(List<String> rawLines) {
        List<String> dataLines = new ArrayList<>();
        for (String line : rawLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            dataLines.add(trimmed);
        }

        if (dataLines.isEmpty()) return List.of();

        // Auto-detect format
        String firstLine = dataLines.get(0).replaceAll("[^0-9.]", "");
        if (firstLine.length() == 81) {
            return parseOneLine(dataLines);
        } else {
            return parseGridFormat(dataLines);
        }
    }

    /** Each data line is one 81-char puzzle string. */
    private static List<SudokuPuzzle> parseOneLine(List<String> dataLines) {
        List<SudokuPuzzle> puzzles = new ArrayList<>(dataLines.size());
        int id = 1;
        for (String line : dataLines) {
            String digits = line.replaceAll("[^0-9.]", "");
            if (digits.length() != 81) {
                System.err.printf("[Parser] Skipping malformed line %d (length=%d)%n",
                        id, digits.length());
                id++;
                continue;
            }
            puzzles.add(new SudokuPuzzle(id++, toBoard(digits)));
        }
        return puzzles;
    }

    /** Every 9 data lines form one puzzle. */
    private static List<SudokuPuzzle> parseGridFormat(List<String> dataLines) {
        List<SudokuPuzzle> puzzles = new ArrayList<>();
        int id = 1;
        int lineIdx = 0;

        while (lineIdx + 9 <= dataLines.size()) {
            StringBuilder sb = new StringBuilder(81);
            for (int r = 0; r < 9; r++) {
                String row = dataLines.get(lineIdx + r).replaceAll("[^0-9.]", "");
                sb.append(row);
            }
            lineIdx += 9;
            // skip optional separator line after each grid block
            if (lineIdx < dataLines.size()) {
                String sep = dataLines.get(lineIdx).replaceAll("[^0-9.]", "");
                if (sep.isEmpty()) lineIdx++;
            }

            String digits = sb.toString();
            if (digits.length() != 81) {
                System.err.printf("[Parser] Grid puzzle #%d has %d digits — skipping%n",
                        id, digits.length());
                id++;
                continue;
            }
            puzzles.add(new SudokuPuzzle(id++, toBoard(digits)));
        }
        return puzzles;
    }

    /** Converts an 81-character string into a 9×9 int array. */
    private static int[][] toBoard(String digits) {
        int[][] board = new int[9][9];
        for (int i = 0; i < 81; i++) {
            char ch = digits.charAt(i);
            board[i / 9][i % 9] = (ch == '.' || ch == '0') ? 0 : (ch - '0');
        }
        return board;
    }
}
