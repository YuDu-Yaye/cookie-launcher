package dev.cookielauncher.ui.component;

import dev.cookielauncher.ui.theme.ColorScheme;
import dev.cookielauncher.ui.theme.TypeScale;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Material Design 3 assist / status chip. */
public class MdChip extends JComponent {

    private final ColorScheme colors;
    private String text;
    private Color tone;
    private boolean outlined;
    private boolean compact;

    public MdChip(ColorScheme colors, String text) {
        this(colors, text, colors.secondary);
    }

    public MdChip(ColorScheme colors, String text, Color tone) {
        this.colors = colors;
        this.text = text == null ? "" : text;
        this.tone = tone;
        setOpaque(false);
        setFont(TypeScale.labelMedium());
    }

    public static MdChip success(ColorScheme colors, String text) {
        return new MdChip(colors, text, colors.tertiary);
    }

    public static MdChip warning(ColorScheme colors, String text) {
        return new MdChip(colors, text, colors.error);
    }

    public static MdChip neutral(ColorScheme colors, String text) {
        return new MdChip(colors, text, colors.onSurfaceVariant);
    }

    public MdChip outlined() {
        this.outlined = true;
        repaint();
        return this;
    }

    public MdChip compact() {
        this.compact = true;
        revalidate();
        repaint();
        return this;
    }

    public MdChip text(String value) {
        this.text = value == null ? "" : value;
        revalidate();
        repaint();
        return this;
    }

    public MdChip tone(Color value) {
        this.tone = value;
        repaint();
        return this;
    }

    public String text() {
        return text;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        int padding = compact ? 16 : 22;
        int height = compact ? 22 : 28;
        return new Dimension(metrics.stringWidth(text) + padding, height);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        int radius = height;

        if (outlined) {
            g.setColor(colors.outline);
            g.drawRoundRect(0, 0, width - 1, height - 1, radius, radius);
        } else {
            g.setColor(colors.alpha(tone, 0.16f));
            g.fillRoundRect(0, 0, width, height, radius, radius);
        }
        g.setColor(outlined ? colors.onSurfaceVariant : tone);
        g.setFont(getFont());
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, (width - metrics.stringWidth(text)) / 2,
                (height + metrics.getAscent() - metrics.getDescent()) / 2);
        g.dispose();
    }
}
