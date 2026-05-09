package com.plt.sudoku.ui;

import com.plt.sudoku.model.SudokuPuzzle;

import javax.swing.*;
import java.awt.*;

/**
 * Renders a 9×9 Sudoku grid with:
 *  - Perfect square proportions at ANY panel size (auto-centering)
 *  - Alternate 3x3 block background shading
 *  - Thin lines between cells
 *  - Given digits in dark ink, empty cells as light dots
 */
public final class SudokuGridPanel extends JPanel {

    private SudokuPuzzle puzzle = null;

    // ── Palette ──────────────────────────────────────────────────────────
    private static final Color GRID_BG        = Color.WHITE;
    private static final Color SHADED_BLOCK   = new Color(0xF0F4F8); // Very light slate
    private static final Color THIN_LINE      = new Color(0xD9E2EC);
    private static final Color THICK_LINE     = new Color(0x102A43); // Deep navy/slate
    private static final Color DIGIT_CLR      = new Color(0x102A43);
    private static final Color EMPTY_DOT      = new Color(0xBCCCDC);

    public SudokuGridPanel() {
        setPreferredSize(new Dimension(220, 220));
        setBackground(new Color(0xF4F5F7)); // Matches parent card background transparently
        setOpaque(false);
    }

    public void setPuzzle(SudokuPuzzle p) {
        this.puzzle = p;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Calculate perfect square dimensions
        int padding = 4;
        int size = Math.min(getWidth(), getHeight()) - (padding * 2);
        float cellSize = size / 9f;

        // Calculate offsets to center the perfect square inside the rectangular panel
        int ox = (getWidth() - size) / 2;
        int oy = (getHeight() - size) / 2;

        // 1. Fill Base Background
        g2.setColor(GRID_BG);
        g2.fillRoundRect(ox, oy, size, size, 8, 8);

        // 2. Draw alternating 3x3 block shading
        g2.setColor(SHADED_BLOCK);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if ((r + c) % 2 != 0) {
                    int blockX = ox + (int)(c * 3 * cellSize);
                    int blockY = oy + (int)(r * 3 * cellSize);
                    int blockW = (int)(3 * cellSize);

                    // Clip the edges so the shading doesn't overflow the rounded corners
                    Shape originalClip = g2.getClip();
                    g2.clip(new java.awt.geom.RoundRectangle2D.Float(ox, oy, size, size, 8, 8));
                    g2.fillRect(blockX, blockY, blockW, blockW);
                    g2.setClip(originalClip);
                }
            }
        }

        // 3. Draw cells (digits and dots)
        if (puzzle != null) {
            Font digitFont = new Font("Segoe UI", Font.BOLD, Math.max(10, (int)(cellSize * 0.55)));
            g2.setFont(digitFont);
            FontMetrics fm = g2.getFontMetrics();

            for (int r = 0; r < 9; r++) {
                for (int c = 0; c < 9; c++) {
                    int val = puzzle.getCell(r, c);
                    int x   = ox + (int)(c * cellSize);
                    int y   = oy + (int)(r * cellSize);
                    int cw  = (int)((c + 1) * cellSize) - (int)(c * cellSize);
                    int ch  = (int)((r + 1) * cellSize) - (int)(r * cellSize);

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

        // 4. Thin grid lines
        g2.setColor(THIN_LINE);
        g2.setStroke(new BasicStroke(0.5f));
        for (int i = 1; i < 9; i++) {
            int linePos = (int)(i * cellSize);
            g2.drawLine(ox + linePos, oy, ox + linePos, oy + size); // Vertical
            g2.drawLine(ox, oy + linePos, ox + size, oy + linePos); // Horizontal
        }

        // 5. Thick box lines (Internal)
        g2.setColor(THICK_LINE);
        g2.setStroke(new BasicStroke(1.5f));
        for (int i = 3; i < 9; i += 3) {
            int linePos = (int)(i * cellSize);
            g2.drawLine(ox + linePos, oy, ox + linePos, oy + size);
            g2.drawLine(ox, oy + linePos, ox + size, oy + linePos);
        }

        // 6. Outer Border
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawRoundRect(ox, oy, size, size, 8, 8);

        g2.dispose();
    }
}