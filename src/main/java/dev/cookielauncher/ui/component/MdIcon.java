package dev.cookielauncher.ui.component;

import dev.cookielauncher.ui.theme.ColorScheme;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * Material Design 3 icon set drawn as vectors.
 *
 * <p>M3 icons are geometrically simple: mostly strokes with rounded caps at a
 * 24dp grid, occasionally filled shapes. Drawing them keeps the build
 * dependency-free and crisp at every DPI, which is the whole point of a
 * self-painted UI.</p>
 */
public final class MdIcon {

    public enum Glyph {
        HOME, LIBRARY, EXTENSION, PERSON, ARTICLE, SETTINGS,
        PLAY, STOP, ADD, REFRESH, FOLDER, CHEVRON_RIGHT, CHECK,
        WARNING, DELETE, SEARCH, LANGUAGE, ARROW_DOWN, TUNE, CLOSE
    }

    private MdIcon() {
    }

    public static JComponent of(ColorScheme colors, Glyph glyph, int size) {
        return of(colors, glyph, size, colors.onSurfaceVariant);
    }

    public static JComponent of(ColorScheme colors, Glyph glyph, int size, Color color) {
        return new IconView(glyph, size, color);
    }

    /** Paints a glyph into a 24dp coordinate space scaled to {@code size}. */
    public static void draw(Graphics2D g, Glyph glyph, Color color, int size) {
        Graphics2D copy = (Graphics2D) g.create();
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        copy.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        float scale = size / 24f;
        copy.scale(scale, scale);
        copy.setColor(color);
        copy.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (glyph) {
            case HOME -> {
                Path2D roof = new Path2D.Float();
                roof.moveTo(3, 11.5);
                roof.lineTo(12, 4);
                roof.lineTo(21, 11.5);
                copy.draw(roof);
                copy.drawRoundRect(5, 11, 14, 9, 2, 2);
                copy.drawLine(10, 20, 10, 15);
                copy.drawLine(14, 15, 14, 20);
            }
            case LIBRARY -> {
                copy.drawRoundRect(3, 4, 5, 16, 1, 1);
                copy.drawRoundRect(10, 4, 5, 16, 1, 1);
                Path2D tilted = new Path2D.Float();
                tilted.moveTo(17.5f, 5.2f);
                tilted.lineTo(20.8f, 5.9f);
                tilted.lineTo(18.6f, 19.4f);
                tilted.lineTo(15.3f, 18.7f);
                tilted.closePath();
                copy.draw(tilted);
            }
            case EXTENSION -> {
                Path2D path = new Path2D.Float();
                path.moveTo(5, 8);
                path.curveTo(5, 5.5, 7, 4, 12, 4);
                path.curveTo(17, 4, 19, 5.5, 19, 8);
                path.lineTo(19, 11);
                path.curveTo(21, 11, 21, 15, 19, 15);
                path.lineTo(19, 19);
                path.lineTo(5, 19);
                path.lineTo(5, 15);
                path.curveTo(3, 15, 3, 11, 5, 11);
                path.closePath();
                copy.draw(path);
            }
            case PERSON -> {
                copy.drawOval(8, 4, 8, 8);
                Path2D shoulders = new Path2D.Float();
                shoulders.moveTo(4, 20);
                shoulders.curveTo(4, 15, 20, 15, 20, 20);
                copy.draw(shoulders);
            }
            case ARTICLE -> {
                copy.drawRoundRect(4, 3, 16, 18, 2, 2);
                copy.drawLine(8, 8, 16, 8);
                copy.drawLine(8, 12, 16, 12);
                copy.drawLine(8, 16, 13, 16);
            }
            case SETTINGS -> {
                copy.drawOval(9, 9, 6, 6);
                for (int i = 0; i < 8; i++) {
                    double angle = Math.PI * i / 4 + Math.PI / 8;
                    int x1 = (int) Math.round(12 + Math.cos(angle) * 7);
                    int y1 = (int) Math.round(12 + Math.sin(angle) * 7);
                    int x2 = (int) Math.round(12 + Math.cos(angle) * 9.4);
                    int y2 = (int) Math.round(12 + Math.sin(angle) * 9.4);
                    copy.drawLine(x1, y1, x2, y2);
                }
            }
            case PLAY -> {
                Path2D triangle = new Path2D.Float();
                triangle.moveTo(8, 5);
                triangle.lineTo(19.5f, 12);
                triangle.lineTo(8, 19);
                triangle.closePath();
                copy.fill(triangle);
            }
            case STOP -> copy.fillRoundRect(7, 7, 10, 10, 2, 2);
            case ADD -> {
                copy.drawLine(12, 5, 12, 19);
                copy.drawLine(5, 12, 19, 12);
            }
            case REFRESH -> {
                copy.drawArc(4, 4, 16, 16, 60, 260);
                Path2D arrow = new Path2D.Float();
                arrow.moveTo(16.5f, 2.5f);
                arrow.lineTo(20.5f, 8.5f);
                arrow.lineTo(14, 8.5f);
                copy.draw(arrow);
            }
            case FOLDER -> {
                Path2D folder = new Path2D.Float();
                folder.moveTo(3, 7);
                folder.lineTo(9.5f, 7);
                folder.lineTo(11.5f, 10);
                folder.lineTo(21, 10);
                folder.lineTo(21, 19);
                folder.lineTo(3, 19);
                folder.closePath();
                copy.draw(folder);
            }
            case CHEVRON_RIGHT -> {
                Path2D chevron = new Path2D.Float();
                chevron.moveTo(9.5f, 6);
                chevron.lineTo(15.5f, 12);
                chevron.lineTo(9.5f, 18);
                copy.draw(chevron);
            }
            case CHECK -> {
                Path2D check = new Path2D.Float();
                check.moveTo(5, 12.5f);
                check.lineTo(10, 17.5f);
                check.lineTo(19.5f, 6.5f);
                copy.draw(check);
            }
            case WARNING -> {
                Path2D triangle = new Path2D.Float();
                triangle.moveTo(12, 3.5f);
                triangle.lineTo(21.5f, 20);
                triangle.lineTo(2.5f, 20);
                triangle.closePath();
                copy.draw(triangle);
                copy.drawLine(12, 9, 12, 14);
                copy.fillOval(11, 16, 2, 2);
            }
            case DELETE -> {
                copy.drawRoundRect(6, 7, 12, 14, 2, 2);
                copy.drawLine(4, 7, 20, 7);
                copy.drawLine(9, 4, 15, 4);
                copy.drawLine(10, 11, 10, 17);
                copy.drawLine(14, 11, 14, 17);
            }
            case SEARCH -> {
                copy.drawOval(4, 4, 12, 12);
                copy.drawLine(15, 15, 21, 21);
            }
            case LANGUAGE -> {
                copy.drawOval(3, 3, 18, 18);
                copy.drawLine(3, 12, 21, 12);
                copy.drawOval(9, 3, 6, 18);
            }
            case ARROW_DOWN -> {
                copy.drawLine(12, 4, 12, 16);
                Path2D stem = new Path2D.Float();
                stem.moveTo(7, 11);
                stem.lineTo(12, 16.5f);
                stem.lineTo(17, 11);
                copy.draw(stem);
                copy.drawLine(5, 20, 19, 20);
            }
            case TUNE -> {
                copy.drawLine(3, 8, 21, 8);
                copy.drawLine(3, 16, 21, 16);
                copy.fillOval(7, 6, 5, 5);
                copy.fillOval(13, 14, 5, 5);
            }
            case CLOSE -> {
                copy.drawLine(6, 6, 18, 18);
                copy.drawLine(18, 6, 6, 18);
            }
        }
        copy.dispose();
    }

    /** Draws the cookie mark used as the application logo. */
    public static void drawLogo(Graphics2D g, ColorScheme colors, int size) {
        Graphics2D copy = (Graphics2D) g.create();
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int inset = Math.max(1, size / 10);
        copy.setColor(colors.primary);
        copy.fillOval(inset, inset, size - inset * 2, size - inset * 2);
        copy.setColor(colors.onPrimary);
        int chip = Math.max(2, size / 9);
        copy.fillOval(size / 3, size / 4, chip, chip);
        copy.fillOval(size / 2, size / 2, chip, chip);
        copy.fillOval(size / 4, (int) (size * 0.55), chip - 1, chip - 1);
        copy.fillOval((int) (size * 0.58), (int) (size * 0.3), chip - 1, chip - 1);
        copy.dispose();
    }

    private static final class IconView extends JComponent {

        private final Glyph glyph;
        private final Color color;

        IconView(Glyph glyph, int size, Color color) {
            this.glyph = glyph;
            this.color = color;
            setOpaque(false);
            setPreferredSize(new java.awt.Dimension(size, size));
            setMinimumSize(new java.awt.Dimension(size, size));
            setMaximumSize(new java.awt.Dimension(size, size));
        }

        @Override
        protected void paintComponent(java.awt.Graphics graphics) {
            draw((Graphics2D) graphics, glyph, color, Math.min(getWidth(), getHeight()));
        }
    }
}
