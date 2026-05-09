package com.plt.sudoku.ui;

import com.plt.sudoku.model.SudokuPuzzle;

import javax.swing.*;
import java.awt.*;

/**
 * Renders a 9×9 Sudoku grid with:
 *  - Thin lines between cells
 *  - Bold lines between 3×3 boxes
 *  - Given digits in dark ink, empty cells as light dots
 *  - Correct proportions at any size
 */
public final class SudokuGridPanel extends JPanel {

    private SudokuPuzzle puzzle = null;

    private static final Color GRID_BG      = Color.WHITE;
    private static final Color THIN_LINE    = new Color(0xD3D1C7);
    private static final Color THICK_LINE   = new Color(0x3C3489);
    private static final Color DIGIT_CLR    = new Color(0x1C1B18);
    private static final Color EMPTY_DOT    = new Color(0xD3D1C7);

    public SudokuGridPanel() {
        setPreferredSize(new Dimension(198, 198));
        setBackground(GRID_BG);
        setBorder(javax.swing.BorderFactory.createLineBorder(THICK_LINE, 2));
    }

    public void setPuzzle(SudokuPuzzle p) {
        this.puzzle = p;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        float cellW = w / 9f;
        float cellH = h / 9f;

        g2.setColor(GRID_BG);
        g2.fillRect(0, 0, w, h);

        // Draw cells
        if (puzzle != null) {
            Font digitFont = new Font("Segoe UI", Font.BOLD, Math.max(8, (int)(cellH * 0.55)));
            g2.setFont(digitFont);
            FontMetrics fm = g2.getFontMetrics();

            for (int r = 0; r < 9; r++) {
                for (int c = 0; c < 9; c++) {
                    int val = puzzle.getCell(r, c);
                    int x   = (int)(c * cellW);
                    int y   = (int)(r * cellH);
                    int cw  = (int)((c + 1) * cellW) - x;
                    int ch  = (int)((r + 1) * cellH) - y;

                    if (val != 0) {
                        String s = String.valueOf(val);
                        int tx = x + (cw - fm.stringWidth(s)) / 2;
                        int ty = y + (ch + fm.getAscent() - fm.getDescent()) / 2 - 1;
                        g2.setColor(DIGIT_CLR);
                        g2.drawString(s, tx, ty);
                    } else {
                        // Empty cell dot
                        int cx = x + cw / 2;
                        int cy = y + ch / 2;
                        g2.setColor(EMPTY_DOT);
                        g2.fillOval(cx - 2, cy - 2, 4, 4);
                    }
                }
            }
        }

        // Thin grid lines
        g2.setColor(THIN_LINE);
        g2.setStroke(new BasicStroke(0.5f));
        for (int i = 1; i < 9; i++) {
            if (i % 3 != 0) {
                g2.drawLine((int)(i * cellW), 0, (int)(i * cellW), h);
                g2.drawLine(0, (int)(i * cellH), w, (int)(i * cellH));
            }
        }

        // Thick box lines
        g2.setColor(THICK_LINE);
        g2.setStroke(new BasicStroke(1.8f));
        for (int i = 3; i < 9; i += 3) {
            g2.drawLine((int)(i * cellW), 0, (int)(i * cellW), h);
            g2.drawLine(0, (int)(i * cellH), w, (int)(i * cellH));
        }
    }
}
