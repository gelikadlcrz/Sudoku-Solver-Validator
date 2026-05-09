package com.plt.sudoku.ui;

import com.plt.sudoku.generator.PuzzleGenerator;
import com.plt.sudoku.model.SolveResult;
import com.plt.sudoku.model.SolveResult.Status;
import com.plt.sudoku.model.SudokuPuzzle;
import com.plt.sudoku.parallel.ParallelSolverOrchestrator;
import com.plt.sudoku.util.PuzzleFileParser;
import com.plt.sudoku.util.ResultWriter;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Swing UI for the Batch Sudoku Validator & Solver.
 *
 * Layout:
 *   ┌─────────────────────────────────────────────────────┐
 *   │  HEADER — title + subtitle                          │
 *   ├────────────────────┬────────────────────────────────┤
 *   │  LEFT PANEL        │  RIGHT PANEL                   │
 *   │  • Config card     │  • Metric cards (4)            │
 *   │  • Puzzle preview  │  • Speedup / efficiency bars   │
 *   │  • Run button      │  • Results table               │
 *   ├────────────────────┴────────────────────────────────┤
 *   │  LOG PANEL — scrollable console output              │
 *   └─────────────────────────────────────────────────────┘
 */
public final class SudokuUI {

    // ── Palette ──────────────────────────────────────────────────────────
    private static final Color BG          = new Color(0xF7F6F3);
    private static final Color SURFACE     = Color.WHITE;
    private static final Color BORDER_CLR  = new Color(0xE0DDD5);
    private static final Color ACCENT      = new Color(0x3C3489);   // purple-800
    private static final Color ACCENT_LITE = new Color(0xEEEDFE);   // purple-50
    private static final Color TEXT_PRI    = new Color(0x1C1B18);
    private static final Color TEXT_SEC    = new Color(0x5F5E5A);
    private static final Color GREEN       = new Color(0x3B6D11);
    private static final Color GREEN_BG    = new Color(0xEAF3DE);
    private static final Color RED_CLR     = new Color(0xA32D2D);
    private static final Color RED_BG      = new Color(0xFCEBEB);
    private static final Color AMBER       = new Color(0x854F0B);
    private static final Color AMBER_BG    = new Color(0xFAEEDA);
    private static final Color TEAL        = new Color(0x0F6E56);
    private static final Color TEAL_BG     = new Color(0xE1F5EE);

    // ── State ─────────────────────────────────────────────────────────────
    private JFrame frame;
    private JSpinner countSpinner;
    private JSpinner threadSpinner;
    private JCheckBox seqCheckBox;
    private JLabel fileLabel;
    private String selectedFilePath = null;
    private JButton runButton;
    private JTextArea logArea;

    // Metric labels
    private JLabel totalLbl, solvedLbl, invalidLbl, unsolvableLbl;
    private JLabel seqTimeLbl, parTimeLbl, speedupLbl, efficiencyLbl;
    private JProgressBar speedupBar, efficiencyBar;

    // Table
    private DefaultTableModel tableModel;
    private JTable resultsTable;

    // Puzzle preview
    private SudokuGridPanel previewPanel;

    // ── Entry ─────────────────────────────────────────────────────────────

    public static void launch() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new SudokuUI().show());
    }

    private void show() {
        frame = new JFrame("Batch Sudoku Validator & Solver — PLT-Parallel Bridge Matrix");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1100, 780));
        frame.getContentPane().setBackground(BG);
        frame.setLayout(new BorderLayout(0, 0));

        frame.add(buildHeader(),     BorderLayout.NORTH);
        frame.add(buildCenter(),     BorderLayout.CENTER);
        frame.add(buildLogPanel(),   BorderLayout.SOUTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // Show a random preview puzzle immediately
        refreshPreview();
        log("Ready. Configure options and click  Run Batch Solve.");
    }

    // ── Header ────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(ACCENT);
        p.setBorder(new EmptyBorder(18, 24, 18, 24));

        JLabel title = new JLabel("Batch Sudoku Validator & Solver");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("PLT-Parallel Bridge Matrix  ·  Java Shared-State Implementation  ·  Task Parallelism");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(new Color(0xCECBF6)); // purple-100

        JPanel text = new JPanel(new GridLayout(2, 1, 0, 3));
        text.setOpaque(false);
        text.add(title);
        text.add(sub);
        p.add(text, BorderLayout.CENTER);

        // Phase badges
        JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        badges.setOpaque(false);
        for (String badge : new String[]{"Phase 1: Task Parallelism", "Phase 2: Shared Heap", "Phase 3: Lock-Free", "Phase 4: BNF"}) {
            JLabel b = new JLabel(badge);
            b.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            b.setForeground(new Color(0xCECBF6));
            b.setBorder(new CompoundBorder(
                    new LineBorder(new Color(0x534AB7), 1, true),
                    new EmptyBorder(3, 8, 3, 8)));
            badges.add(b);
        }
        p.add(badges, BorderLayout.EAST);
        return p;
    }

    // ── Center split ──────────────────────────────────────────────────────

    private JSplitPane buildCenter() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildLeftPanel(), buildRightPanel());
        split.setDividerLocation(320);
        split.setDividerSize(1);
        split.setBorder(null);
        split.setBackground(BORDER_CLR);
        return split;
    }

    // ── LEFT PANEL ────────────────────────────────────────────────────────

    private JPanel buildLeftPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(16, 16, 16, 8));

        p.add(buildConfigCard(),   BorderLayout.NORTH);
        p.add(buildPreviewCard(),  BorderLayout.CENTER);
        p.add(buildRunButton(),    BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildConfigCard() {
        JPanel card = card("Configuration");

        // Puzzle count
        card.add(label("Puzzle count"));
        countSpinner = new JSpinner(new SpinnerNumberModel(1000, 10, 10000, 100));
        styleSpinner(countSpinner);
        card.add(countSpinner);

        // Thread count
        card.add(label("Worker threads"));
        int cores = Runtime.getRuntime().availableProcessors();
        threadSpinner = new JSpinner(new SpinnerNumberModel(cores, 1, cores * 4, 1));
        styleSpinner(threadSpinner);
        card.add(threadSpinner);

        // Sequential baseline
        seqCheckBox = new JCheckBox("Run sequential baseline (for speedup S = Ts/Tp)", true);
        seqCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        seqCheckBox.setBackground(SURFACE);
        seqCheckBox.setForeground(TEXT_PRI);
        card.add(seqCheckBox);
        card.add(new JLabel(" ")); // spacer

        // File chooser
        card.add(label("Puzzle file (optional)"));
        JPanel fileRow = new JPanel(new BorderLayout(6, 0));
        fileRow.setOpaque(false);
        fileLabel = new JLabel("Using generated puzzles");
        fileLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        fileLabel.setForeground(TEXT_SEC);
        JButton browseBtn = accentButton("Browse...");
        browseBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        browseBtn.addActionListener(e -> chooseFile());
        fileRow.add(fileLabel,  BorderLayout.CENTER);
        fileRow.add(browseBtn,  BorderLayout.EAST);
        card.add(fileRow);

        JButton clearFile = new JButton("Clear file");
        clearFile.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        clearFile.setForeground(TEXT_SEC);
        clearFile.setBorderPainted(false);
        clearFile.setContentAreaFilled(false);
        clearFile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearFile.addActionListener(e -> { selectedFilePath = null; fileLabel.setText("Using generated puzzles"); refreshPreview(); });
        card.add(clearFile);

        return card;
    }

    private JPanel buildPreviewCard() {
        JPanel card = card("Puzzle preview (sample)");
        previewPanel = new SudokuGridPanel();
        card.add(previewPanel);
        JButton refresh = new JButton("Refresh sample");
        refresh.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        refresh.setForeground(ACCENT);
        refresh.setBorderPainted(false);
        refresh.setContentAreaFilled(false);
        refresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refresh.addActionListener(e -> refreshPreview());
        card.add(refresh);
        return card;
    }

    private JButton buildRunButton() {
        runButton = new JButton("▶  Run Batch Solve");
        runButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        runButton.setBackground(ACCENT);
        runButton.setForeground(Color.WHITE);
        runButton.setFocusPainted(false);
        runButton.setBorderPainted(false);
        runButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        runButton.setPreferredSize(new Dimension(0, 44));
        runButton.addActionListener(e -> startSolve());
        return runButton;
    }

    // ── RIGHT PANEL ───────────────────────────────────────────────────────

    private JPanel buildRightPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(16, 8, 16, 16));

        p.add(buildMetrics(), BorderLayout.NORTH);
        p.add(buildTablePanel(), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildMetrics() {
        JPanel outer = new JPanel(new BorderLayout(0, 10));
        outer.setOpaque(false);

        // 4 count cards
        JPanel counts = new JPanel(new GridLayout(1, 4, 8, 0));
        counts.setOpaque(false);

        totalLbl      = metricCard(counts, "Total",       "—", TEXT_PRI,  BG);
        solvedLbl     = metricCard(counts, "Solved ✓",    "—", GREEN,     GREEN_BG);
        invalidLbl    = metricCard(counts, "Invalid ✗",   "—", RED_CLR,   RED_BG);
        unsolvableLbl = metricCard(counts, "Unsolvable",  "—", AMBER,     AMBER_BG);

        // Timing + speedup row
        JPanel timing = new JPanel(new GridLayout(1, 4, 8, 0));
        timing.setOpaque(false);

        seqTimeLbl   = metricCard(timing, "Sequential Ts", "— ms",  TEXT_SEC, BG);
        parTimeLbl   = metricCard(timing, "Parallel Tp",   "— ms",  TEAL,    TEAL_BG);
        speedupLbl   = metricCard(timing, "Speedup S=Ts/Tp","—×",   ACCENT,  ACCENT_LITE);
        efficiencyLbl= metricCard(timing, "Efficiency E=S/N","—%",  ACCENT,  ACCENT_LITE);

        // Progress bars
        JPanel bars = new JPanel(new GridLayout(2, 1, 0, 4));
        bars.setOpaque(false);
        bars.setBorder(new EmptyBorder(0, 0, 0, 0));

        speedupBar   = progressBar(ACCENT);
        efficiencyBar= progressBar(TEAL);

        JPanel sRow = labeledBar("Speedup", speedupBar);
        JPanel eRow = labeledBar("Efficiency", efficiencyBar);
        bars.add(sRow);
        bars.add(eRow);

        JPanel top = new JPanel(new GridLayout(3, 1, 0, 8));
        top.setOpaque(false);
        top.add(counts);
        top.add(timing);
        top.add(bars);

        outer.add(top, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildTablePanel() {
        String[] cols = {"#", "Status", "Thread", "Time (ms)", "Givens"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        resultsTable = new JTable(tableModel);
        resultsTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        resultsTable.setRowHeight(22);
        resultsTable.setShowGrid(false);
        resultsTable.setIntercellSpacing(new Dimension(0, 0));
        resultsTable.setBackground(SURFACE);
        resultsTable.setSelectionBackground(ACCENT_LITE);
        resultsTable.setSelectionForeground(TEXT_PRI);
        resultsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        resultsTable.getTableHeader().setBackground(BG);
        resultsTable.getTableHeader().setForeground(TEXT_SEC);
        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        resultsTable.getColumnModel().getColumn(3).setPreferredWidth(90);
        resultsTable.getColumnModel().getColumn(4).setPreferredWidth(60);

        // Color rows by status
        resultsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                           boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                if (!sel) {
                    String status = (String) tableModel.getValueAt(row, 1);
                    if ("SOLVED".equals(status))         { c.setBackground(SURFACE); c.setForeground(TEXT_PRI); }
                    else if ("INVALID_PUZZLE".equals(status)) { c.setBackground(RED_BG); c.setForeground(RED_CLR); }
                    else                                 { c.setBackground(AMBER_BG); c.setForeground(AMBER); }
                }
                return c;
            }
        });

        JScrollPane scroll = new JScrollPane(resultsTable);
        scroll.setBorder(new LineBorder(BORDER_CLR));
        scroll.setBackground(SURFACE);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        JLabel tableTitle = new JLabel("Results  (most recent 500 rows shown)");
        tableTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tableTitle.setForeground(TEXT_PRI);
        tableTitle.setBorder(new EmptyBorder(0, 0, 6, 0));
        wrapper.add(tableTitle, BorderLayout.NORTH);
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    // ── Log panel ─────────────────────────────────────────────────────────

    private JPanel buildLogPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(0x1C1B18));
        p.setBorder(new EmptyBorder(0, 0, 0, 0));
        p.setPreferredSize(new Dimension(0, 140));

        JLabel lbl = new JLabel(" Console log");
        lbl.setFont(new Font("Segoe UI Mono", Font.PLAIN, 11));
        lbl.setForeground(new Color(0x888780));
        lbl.setBorder(new EmptyBorder(4, 8, 4, 0));
        lbl.setBackground(new Color(0x1C1B18));
        lbl.setOpaque(true);
        p.add(lbl, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setFont(new Font("Segoe UI Mono", Font.PLAIN, 11));
        logArea.setBackground(new Color(0x1C1B18));
        logArea.setForeground(new Color(0xC0BDB6));
        logArea.setCaretColor(new Color(0xC0BDB6));
        logArea.setEditable(false);
        logArea.setBorder(new EmptyBorder(0, 12, 8, 12));

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(null);
        scroll.setBackground(new Color(0x1C1B18));
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    // ── Solve logic ───────────────────────────────────────────────────────

    private void startSolve() {
        runButton.setEnabled(false);
        runButton.setText("Running...");
        tableModel.setRowCount(0);
        resetMetrics();

        int count   = (int) countSpinner.getValue();
        int threads = (int) threadSpinner.getValue();
        boolean doSeq = seqCheckBox.isSelected();

        new Thread(() -> {
            try {
                // Load puzzles
                List<SudokuPuzzle> puzzles;
                if (selectedFilePath != null) {
                    log("Loading puzzles from file: " + selectedFilePath);
                    puzzles = PuzzleFileParser.parseFile(selectedFilePath);
                } else {
                    log("Generating " + count + " puzzles...");
                    puzzles = PuzzleGenerator.generate(count);
                }
                log("Loaded " + puzzles.size() + " puzzles.");

                // Update preview with first puzzle
                if (!puzzles.isEmpty()) SwingUtilities.invokeLater(() -> previewPanel.setPuzzle(puzzles.get(0)));

                ParallelSolverOrchestrator orch = new ParallelSolverOrchestrator(threads);

                // Sequential baseline
                long seqMs = 0;
                if (doSeq) {
                    log("Running sequential baseline (single thread)...");
                    long t = System.currentTimeMillis();
                    orch.solveSequential(puzzles);
                    seqMs = System.currentTimeMillis() - t;
                    log("Sequential finished in " + seqMs + " ms.");
                }

                // Parallel run
                log("Starting parallel solve on " + threads + " thread(s)...");
                long parStart = System.currentTimeMillis();
                List<SolveResult> results = orch.solve(puzzles);
                long parMs = System.currentTimeMillis() - parStart;
                log("Parallel finished in " + parMs + " ms.");

                // Save output
                ResultWriter.writeResults(results, "output", parMs, seqMs, threads);
                log("Results written to ./output/");

                // Update UI on EDT
                final long finalSeqMs = seqMs;
                final long finalParMs = parMs;
                SwingUtilities.invokeLater(() -> {
                    updateMetrics(results, finalSeqMs, finalParMs, threads);
                    populateTable(results);
                    runButton.setEnabled(true);
                    runButton.setText("▶  Run Batch Solve");
                    log("Done. " + results.size() + " puzzles processed.");
                });

            } catch (Exception ex) {
                log("ERROR: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    runButton.setEnabled(true);
                    runButton.setText("▶  Run Batch Solve");
                    JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "ui-solve-thread").start();
    }

    private void updateMetrics(List<SolveResult> results, long seqMs, long parMs, int threads) {
        long solved      = results.stream().filter(r -> r.getStatus() == Status.SOLVED).count();
        long invalid     = results.stream().filter(r -> r.getStatus() == Status.INVALID_PUZZLE).count();
        long unsolvable  = results.stream().filter(r -> r.getStatus() == Status.UNSOLVABLE).count();

        totalLbl.setText(String.valueOf(results.size()));
        solvedLbl.setText(String.valueOf(solved));
        invalidLbl.setText(String.valueOf(invalid));
        unsolvableLbl.setText(String.valueOf(unsolvable));

        seqTimeLbl.setText(seqMs > 0 ? seqMs + " ms" : "skipped");
        parTimeLbl.setText(parMs + " ms");

        if (seqMs > 0 && parMs > 0) {
            double s = (double) seqMs / parMs;
            double e = s / threads * 100.0;
            speedupLbl.setText(String.format("%.2f×", s));
            efficiencyLbl.setText(String.format("%.1f%%", e));
            speedupBar.setValue(Math.min(100, (int)(s / threads * 100)));
            efficiencyBar.setValue((int) Math.min(100, e));
        }
    }

    private void populateTable(List<SolveResult> results) {
        tableModel.setRowCount(0);
        // Show latest 500 to keep UI responsive
        int start = Math.max(0, results.size() - 500);
        List<SolveResult> sorted = new ArrayList<>(results.subList(start, results.size()));
        sorted.sort(Comparator.comparingInt(SolveResult::getPuzzleId));
        for (SolveResult r : sorted) {
            int givens = 0;
            if (r.getSolvedBoard() != null)
                for (int[] row : r.getSolvedBoard()) for (int c : row) if (c != 0) givens++;
            tableModel.addRow(new Object[]{
                r.getPuzzleId(),
                r.getStatus().name(),
                r.getWorkerThread(),
                String.format("%.3f", r.getSolveTimeNanos() / 1_000_000.0),
                givens > 0 ? givens : "—"
            });
        }
    }

    private void resetMetrics() {
        for (JLabel l : new JLabel[]{totalLbl, solvedLbl, invalidLbl, unsolvableLbl,
                seqTimeLbl, parTimeLbl, speedupLbl, efficiencyLbl}) {
            if (l != null) l.setText("—");
        }
        if (speedupBar != null)    speedupBar.setValue(0);
        if (efficiencyBar != null) efficiencyBar.setValue(0);
    }

    // ── Preview ───────────────────────────────────────────────────────────

    private void refreshPreview() {
        List<SudokuPuzzle> sample = PuzzleGenerator.generate(1);
        if (!sample.isEmpty()) previewPanel.setPuzzle(sample.get(0));
    }

    // ── File chooser ──────────────────────────────────────────────────────

    private void chooseFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select puzzle file");
        fc.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            selectedFilePath = f.getAbsolutePath();
            fileLabel.setText(f.getName());
            log("File selected: " + selectedFilePath);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private JPanel card(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(SURFACE);
        p.setBorder(new CompoundBorder(
                new LineBorder(BORDER_CLR, 1, true),
                new EmptyBorder(12, 14, 12, 14)));

        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(TEXT_PRI);
        lbl.setBorder(new EmptyBorder(0, 0, 10, 0));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(lbl);
        return p;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        l.setForeground(TEXT_SEC);
        l.setBorder(new EmptyBorder(6, 0, 2, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private void styleSpinner(JSpinner s) {
        s.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
    }

    private JButton accentButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        b.setForeground(ACCENT);
        b.setBackground(ACCENT_LITE);
        b.setBorder(new CompoundBorder(new LineBorder(ACCENT, 1, true), new EmptyBorder(4, 10, 4, 10)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /**
     * Creates a metric card and adds it to `parent`, returning the value label.
     */
    private JLabel metricCard(JPanel parent, String title, String initial, Color fg, Color bg) {
        JPanel card = new JPanel(new GridLayout(2, 1, 0, 2));
        card.setBackground(bg);
        card.setBorder(new CompoundBorder(
                new LineBorder(BORDER_CLR, 1, true),
                new EmptyBorder(10, 12, 10, 12)));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        titleLbl.setForeground(TEXT_SEC);

        JLabel valueLbl = new JLabel(initial);
        valueLbl.setFont(new Font("Segoe UI", Font.BOLD, 20));
        valueLbl.setForeground(fg);

        card.add(titleLbl);
        card.add(valueLbl);
        parent.add(card);
        return valueLbl;
    }

    private JProgressBar progressBar(Color color) {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setForeground(color);
        bar.setBackground(BORDER_CLR);
        bar.setBorderPainted(false);
        bar.setPreferredSize(new Dimension(0, 8));
        return bar;
    }

    private JPanel labeledBar(String label, JProgressBar bar) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbl.setForeground(TEXT_SEC);
        lbl.setPreferredSize(new Dimension(70, 0));
        row.add(lbl, BorderLayout.WEST);
        row.add(bar, BorderLayout.CENTER);
        return row;
    }
}
