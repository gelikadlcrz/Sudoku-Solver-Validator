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
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.List;
import java.util.*;

/**
 * Swing UI for the Batch Sudoku Validator & Solver.
 */
public final class SudokuUI {

    // ── Palette (Adjust these to perfectly match your screenshot) ──────────
    private static final Color BG          = new Color(0xF0F2F5); // App Background
    private static final Color SURFACE     = Color.WHITE;         // Card Background
    private static final Color HEADER_BG   = new Color(0x1B4965); // Deep Blue/Teal Header
    private static final Color BORDER_CLR  = new Color(0xE1E4E8);
    private static final Color ACCENT      = new Color(0x127393); // Primary Action Color
    private static final Color ACCENT_HOVER= new Color(0x0E5C76);
    private static final Color ACCENT_LITE = new Color(0xE8F4F8);
    private static final Color TEXT_PRI    = new Color(0x102A43); // Dark text
    private static final Color TEXT_SEC    = new Color(0x627D98); // Muted text

    // Status Colors
    private static final Color GREEN       = new Color(0x059669);
    private static final Color GREEN_BG    = new Color(0xECFDF5);
    private static final Color RED_CLR     = new Color(0xDC2626);
    private static final Color RED_BG      = new Color(0xFEF2F2);
    private static final Color AMBER       = new Color(0xD97706);
    private static final Color AMBER_BG    = new Color(0xFFFBEB);
    private static final Color BLUE_CLR    = new Color(0x2563EB);
    private static final Color BLUE_BG     = new Color(0xEFF6FF);

    // ── State ─────────────────────────────────────────────────────────────
    private JFrame frame;
    private JSpinner countSpinner;
    private JSpinner threadSpinner;
    private JCheckBox seqCheckBox;
    private JLabel fileLabel;
    private String selectedFilePath = null;
    private JButton runButton;
    private JTextArea logArea;

    private JLabel totalLbl, solvedLbl, invalidLbl, unsolvableLbl;
    private JLabel seqTimeLbl, parTimeLbl, speedupLbl, efficiencyLbl;
    private JProgressBar speedupBar, efficiencyBar;

    private DefaultTableModel tableModel;
    private JTable resultsTable;
    private SudokuGridPanel previewPanel;

    // ── Entry ─────────────────────────────────────────────────────────────
    public static void launch() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new SudokuUI().show());
    }

    private void show() {
        frame = new JFrame("Batch Sudoku Validator & Solver");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1200, 800));
        frame.getContentPane().setBackground(BG);
        frame.setLayout(new BorderLayout(0, 0));

        frame.add(buildHeader(),     BorderLayout.NORTH);
        frame.add(buildCenter(),     BorderLayout.CENTER);
        frame.add(buildLogPanel(),   BorderLayout.SOUTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        refreshPreview();
        log("Ready. Configure options and click Run Batch Solve.");
    }

    // ── Header ────────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(HEADER_BG);
        p.setBorder(new EmptyBorder(24, 32, 24, 32));

        JLabel title = new JLabel("Batch Sudoku Validator & Solver");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("PLT-Parallel Bridge Matrix  ·  Java Shared-State Implementation  ·  Task Parallelism");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sub.setForeground(new Color(255, 255, 255, 200));

        JPanel text = new JPanel(new GridLayout(2, 1, 0, 4));
        text.setOpaque(false);
        text.add(title);
        text.add(sub);
        p.add(text, BorderLayout.CENTER);

        JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        badges.setOpaque(false);
        for (String badge : new String[]{"Phase 1: Task", "Phase 2: Heap", "Phase 3: Lock-Free", "Phase 4: BNF"}) {
            JLabel b = new JLabel(badge);
            b.setFont(new Font("Segoe UI", Font.BOLD, 11));
            b.setForeground(Color.WHITE);
            b.setBorder(new CompoundBorder(
                    new LineBorder(new Color(255, 255, 255, 60), 1, true),
                    new EmptyBorder(4, 10, 4, 10)));
            badges.add(b);
        }
        p.add(badges, BorderLayout.EAST);
        return p;
    }

    // ── Center split ──────────────────────────────────────────────────────
    private JSplitPane buildCenter() {
        JPanel left = buildLeftPanel();
        JPanel right = buildRightPanel();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerLocation(340);
        split.setDividerSize(0);
        split.setBorder(null);
        split.setOpaque(false);
        return split;
    }

    // ── LEFT PANEL ────────────────────────────────────────────────────────
    private JPanel buildLeftPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 16));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(24, 24, 24, 12));

        p.add(buildConfigCard(),   BorderLayout.NORTH);
        p.add(buildPreviewCard(),  BorderLayout.CENTER);
        p.add(buildRunButton(),    BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildConfigCard() {
        RoundedPanel card = new RoundedPanel(16, SURFACE);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel header = new JLabel("Configuration");
        header.setFont(new Font("Segoe UI", Font.BOLD, 15));
        header.setForeground(TEXT_PRI);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(header);
        card.add(Box.createVerticalStrut(16));

        card.add(label("Puzzle count"));
        countSpinner = new JSpinner(new SpinnerNumberModel(1000, 10, 10000, 100));
        styleSpinner(countSpinner);
        card.add(countSpinner);
        card.add(Box.createVerticalStrut(12));

        card.add(label("Worker threads"));
        int cores = Runtime.getRuntime().availableProcessors();
        threadSpinner = new JSpinner(new SpinnerNumberModel(cores, 1, cores * 4, 1));
        styleSpinner(threadSpinner);
        card.add(threadSpinner);
        card.add(Box.createVerticalStrut(16));

        seqCheckBox = new JCheckBox("Run sequential baseline", true);
        seqCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        seqCheckBox.setOpaque(false);
        seqCheckBox.setForeground(TEXT_PRI);
        seqCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(seqCheckBox);
        card.add(Box.createVerticalStrut(16));

        card.add(label("Custom Puzzle File"));
        JPanel fileRow = new JPanel(new BorderLayout(8, 0));
        fileRow.setOpaque(false);
        fileRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        fileLabel = new JLabel("Using generated puzzles");
        fileLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        fileLabel.setForeground(TEXT_SEC);

        JButton browseBtn = new JButton("Browse");
        styleSecondaryButton(browseBtn);
        browseBtn.addActionListener(e -> chooseFile());

        fileRow.add(fileLabel,  BorderLayout.CENTER);
        fileRow.add(browseBtn,  BorderLayout.EAST);
        card.add(fileRow);

        return card;
    }

    private JPanel buildPreviewCard() {
        RoundedPanel card = new RoundedPanel(16, SURFACE);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("Preview (Sample)");
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(TEXT_PRI);

        previewPanel = new SudokuGridPanel();

        JButton refresh = new JButton("Refresh");
        styleSecondaryButton(refresh);
        refresh.addActionListener(e -> refreshPreview());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        header.add(refresh, BorderLayout.EAST);

        card.add(header, BorderLayout.NORTH);
        card.add(previewPanel, BorderLayout.CENTER);
        return card;
    }

    private JButton buildRunButton() {
        // We override paintComponent to force the background color to render
        // regardless of the operating system's native UI theme.
        runButton = new JButton("▶ Run Batch Solve") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Change color if hovered or pressed
                if (!isEnabled()) {
                    g2.setColor(new Color(0xBCCCDC)); // Disabled grey
                } else if (getModel().isPressed() || getModel().isRollover()) {
                    g2.setColor(ACCENT_HOVER);
                } else {
                    g2.setColor(ACCENT);
                }

                // Draw a rounded rectangle for the button background
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();

                super.paintComponent(g);
            }
        };

        runButton.setFont(new Font("Segoe UI", Font.BOLD, 15));
        runButton.setForeground(Color.WHITE); // Text color
        runButton.setFocusPainted(false);
        runButton.setBorderPainted(false);
        runButton.setContentAreaFilled(false); // CRITICAL: Stops the OS from painting over our color
        runButton.setOpaque(false);
        runButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        runButton.setPreferredSize(new Dimension(0, 50));

        runButton.addActionListener(e -> startSolve());
        return runButton;
    }

    // ── RIGHT PANEL ───────────────────────────────────────────────────────
    private JPanel buildRightPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 16));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(24, 12, 24, 24));

        p.add(buildMetrics(), BorderLayout.NORTH);
        p.add(buildTablePanel(), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildMetrics() {
        JPanel outer = new JPanel(new BorderLayout(0, 16));
        outer.setOpaque(false);

        JPanel counts = new JPanel(new GridLayout(1, 4, 12, 0));
        counts.setOpaque(false);
        totalLbl      = metricCard(counts, "Total",       "—", TEXT_PRI,  SURFACE);
        solvedLbl     = metricCard(counts, "Solved ✓",    "—", GREEN,     GREEN_BG);
        invalidLbl    = metricCard(counts, "Invalid ✗",   "—", RED_CLR,   RED_BG);
        unsolvableLbl = metricCard(counts, "Unsolvable",  "—", AMBER,     AMBER_BG);

        JPanel timing = new JPanel(new GridLayout(1, 4, 12, 0));
        timing.setOpaque(false);
        seqTimeLbl   = metricCard(timing, "Sequential Ts", "—",  TEXT_SEC, SURFACE);
        parTimeLbl   = metricCard(timing, "Parallel Tp",   "—",  GREEN,    GREEN_BG);
        speedupLbl   = metricCard(timing, "Speedup (S)",   "—",  BLUE_CLR, BLUE_BG);
        efficiencyLbl= metricCard(timing, "Efficiency (E)","—",  ACCENT,   ACCENT_LITE);

        RoundedPanel bars = new RoundedPanel(16, SURFACE);
        bars.setLayout(new GridLayout(2, 1, 0, 12));
        bars.setBorder(new EmptyBorder(16, 20, 16, 20));

        speedupBar   = progressBar(BLUE_CLR);
        efficiencyBar= progressBar(ACCENT);

        bars.add(labeledBar("Speedup Rating", speedupBar));
        bars.add(labeledBar("Core Efficiency", efficiencyBar));

        JPanel top = new JPanel(new GridLayout(3, 1, 0, 12));
        top.setOpaque(false);
        top.add(counts);
        top.add(timing);
        top.add(bars);

        outer.add(top, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildTablePanel() {
        RoundedPanel wrapper = new RoundedPanel(16, SURFACE);
        wrapper.setLayout(new BorderLayout());
        wrapper.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel tableTitle = new JLabel("Recent Results");
        tableTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        tableTitle.setForeground(TEXT_PRI);
        tableTitle.setBorder(new EmptyBorder(0, 4, 12, 0));
        wrapper.add(tableTitle, BorderLayout.NORTH);

        String[] cols = {"Puzzle #", "Status", "Worker Thread", "Time (ms)", "Givens"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        resultsTable = new JTable(tableModel);
        resultsTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        resultsTable.setRowHeight(28);
        resultsTable.setShowGrid(false);
        resultsTable.setIntercellSpacing(new Dimension(0, 0));
        resultsTable.setBackground(SURFACE);
        resultsTable.setSelectionBackground(ACCENT_LITE);
        resultsTable.setSelectionForeground(TEXT_PRI);

        JTableHeader header = resultsTable.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.setBackground(SURFACE);
        header.setForeground(TEXT_SEC);
        header.setBorder(new MatteBorder(0, 0, 1, 0, BORDER_CLR));
        header.setPreferredSize(new Dimension(0, 32));

        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        resultsTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        resultsTable.getColumnModel().getColumn(4).setPreferredWidth(60);

        resultsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                setBorder(new EmptyBorder(0, 8, 0, 8));
                if (!sel) {
                    String status = (String) tableModel.getValueAt(row, 1);
                    if ("SOLVED".equals(status)) { c.setBackground(SURFACE); c.setForeground(TEXT_PRI); }
                    else if ("INVALID_PUZZLE".equals(status)) { c.setBackground(RED_BG); c.setForeground(RED_CLR); }
                    else { c.setBackground(AMBER_BG); c.setForeground(AMBER); }
                }
                return c;
            }
        });

        JScrollPane scroll = new JScrollPane(resultsTable);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SURFACE);

        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    // ── Log panel ─────────────────────────────────────────────────────────
    private JPanel buildLogPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(0x15202B)); // Deep dark background for console
        p.setPreferredSize(new Dimension(0, 160));
        p.setBorder(new MatteBorder(4, 0, 0, 0, ACCENT));

        JLabel lbl = new JLabel(" System Console");
        lbl.setFont(new Font("Consolas", Font.BOLD, 12));
        lbl.setForeground(new Color(0x8899A6));
        lbl.setBorder(new EmptyBorder(8, 16, 4, 16));
        p.add(lbl, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBackground(new Color(0x15202B));
        logArea.setForeground(new Color(0xE1E8ED));
        logArea.setCaretColor(Color.WHITE);
        logArea.setEditable(false);
        logArea.setMargin(new Insets(4, 16, 16, 16));

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(null);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    // ── Solve logic ───────────────────────────────────────────────────────
    private void startSolve() {
        runButton.setEnabled(false);
        runButton.setText("Processing...");
        tableModel.setRowCount(0);
        resetMetrics();

        int count   = (int) countSpinner.getValue();
        int threads = (int) threadSpinner.getValue();
        boolean doSeq = seqCheckBox.isSelected();

        new Thread(() -> {
            try {
                List<SudokuPuzzle> puzzles;
                if (selectedFilePath != null) {
                    log("> Loading puzzles from: " + selectedFilePath);
                    puzzles = PuzzleFileParser.parseFile(selectedFilePath);
                } else {
                    log("> Generating " + count + " puzzles...");
                    puzzles = PuzzleGenerator.generate(count);
                }
                log("✓ Loaded " + puzzles.size() + " puzzles.");

                if (!puzzles.isEmpty()) SwingUtilities.invokeLater(() -> previewPanel.setPuzzle(puzzles.get(0)));

                ParallelSolverOrchestrator orch = new ParallelSolverOrchestrator(threads);

                long seqMs = 0;
                if (doSeq) {
                    log("> Running sequential baseline...");
                    long t = System.currentTimeMillis();
                    orch.solveSequential(puzzles);
                    seqMs = System.currentTimeMillis() - t;
                    log("✓ Sequential finished in " + seqMs + " ms.");
                }

                log("> Starting parallel execution (" + threads + " threads)...");
                long parStart = System.currentTimeMillis();
                List<SolveResult> results = orch.solve(puzzles);
                long parMs = System.currentTimeMillis() - parStart;
                log("✓ Parallel finished in " + parMs + " ms.");

                ResultWriter.writeResults(results, "output", parMs, seqMs, threads);

                final long fSeq = seqMs, fPar = parMs;
                SwingUtilities.invokeLater(() -> {
                    updateMetrics(results, fSeq, fPar, threads);
                    populateTable(results);
                    runButton.setEnabled(true);
                    runButton.setText("▶ Run Batch Solve");
                    log("★ Done processing " + results.size() + " records.");
                });

            } catch (Exception ex) {
                log("! ERROR: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    runButton.setEnabled(true);
                    runButton.setText("▶ Run Batch Solve");
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

        seqTimeLbl.setText(seqMs > 0 ? seqMs + " ms" : "Skipped");
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
        int start = Math.max(0, results.size() - 500);
        List<SolveResult> sorted = new ArrayList<>(results.subList(start, results.size()));
        sorted.sort(Comparator.comparingInt(SolveResult::getPuzzleId));
        for (SolveResult r : sorted) {
            int givens = 0;
            if (r.getSolvedBoard() != null)
                for (int[] row : r.getSolvedBoard()) for (int c : row) if (c != 0) givens++;
            tableModel.addRow(new Object[]{
                    r.getPuzzleId(), r.getStatus().name(), r.getWorkerThread(),
                    String.format("%.3f", r.getSolveTimeNanos() / 1_000_000.0), givens > 0 ? givens : "—"
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

    private void refreshPreview() {
        List<SudokuPuzzle> sample = PuzzleGenerator.generate(1);
        if (!sample.isEmpty()) previewPanel.setPuzzle(sample.get(0));
    }

    private void chooseFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select puzzle file");
        fc.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            selectedFilePath = f.getAbsolutePath();
            fileLabel.setText(f.getName());
            log("> File targeted: " + selectedFilePath);
        }
    }

    // ── UI Design Helpers ─────────────────────────────────────────────────
    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(TEXT_SEC);
        l.setBorder(new EmptyBorder(0, 0, 4, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private void styleSpinner(JSpinner s) {
        s.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JComponent editor = s.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            ((JSpinner.DefaultEditor)editor).getTextField().setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        }
        s.setBorder(new LineBorder(BORDER_CLR, 1, true));
    }

    private void styleSecondaryButton(JButton b) {
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setForeground(ACCENT);
        b.setBackground(ACCENT_LITE);
        b.setBorder(new EmptyBorder(6, 12, 6, 12));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private JLabel metricCard(JPanel parent, String title, String initial, Color fg, Color bg) {
        RoundedPanel card = new RoundedPanel(12, bg);
        card.setLayout(new GridLayout(2, 1, 0, 4));
        card.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLbl.setForeground(TEXT_SEC);

        JLabel valueLbl = new JLabel(initial);
        valueLbl.setFont(new Font("Segoe UI", Font.BOLD, 22));
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
        bar.setPreferredSize(new Dimension(0, 12));
        return bar;
    }

    private JPanel labeledBar(String label, JProgressBar bar) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);

        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(TEXT_PRI);
        lbl.setPreferredSize(new Dimension(100, 0));

        row.add(lbl, BorderLayout.WEST);
        row.add(bar, BorderLayout.CENTER);
        return row;
    }

    /**
     * Helper panel that draws an anti-aliased rounded rectangle background.
     */
    static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color bgColor;

        public RoundedPanel(int radius, Color bgColor) {
            this.radius = radius;
            this.bgColor = bgColor;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bgColor);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), radius, radius));
            g2.dispose();
            super.paintComponent(g);
        }
    }
}