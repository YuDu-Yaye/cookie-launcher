package dev.cookielauncher.ui.component;

import dev.cookielauncher.ui.theme.ColorScheme;
import dev.cookielauncher.ui.theme.TypeScale;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Material Design 3 button with five variants and correct state layers.
 *
 * <p>Spec followed: 40dp height, 20dp corner radius (fully rounded), 24dp
 * horizontal padding, label-large type, ripple + hover + focus states, and the
 * M3 rule that disabled containers drop to 12% on-surface while labels drop to
 * 38%.</p>
 */
public class MdButton extends JButton {

    public enum Variant {
        /** High-emphasis: filled with primary. */
        FILLED,
        /** Medium-emphasis: secondary container. */
        TONAL,
        /** Medium-emphasis contour. */
        OUTLINED,
        /** Low-emphasis, text only. */
        TEXT,
        /** Destructive. */
        DANGER
    }

    private final ColorScheme colors;
    private final Variant variant;
    private final Ripple ripple;
    private int height = TypeScale.BUTTON_HEIGHT;

    public MdButton(ColorScheme colors, String text, Variant variant) {
        super(text);
        this.colors = colors;
        this.variant = variant;
        this.ripple = new Ripple(this, colors);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setFont(TypeScale.labelLarge());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFocusable(true);
        setRolloverEnabled(false);
        setForeground(foreground());
        int width = Math.max(88, getFontMetrics(getFont()).stringWidth(text) + 48);
        setPreferredSize(new Dimension(width, height));
        setMinimumSize(new Dimension(64, height));
    }

    public MdButton compact() {
        this.height = TypeScale.SMALL_BUTTON_HEIGHT;
        setPreferredSize(new Dimension(getPreferredSize().width - 8, height));
        return this;
    }

    /** Expands the button to fill its container width (M3 full-width button). */
    public MdButton fullWidth() {
        setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return this;
    }

    private Color container() {
        if (!isEnabled()) {
            return colors.alpha(colors.onSurface, 0.12f);
        }
        return switch (variant) {
            case FILLED -> colors.primary;
            case TONAL -> colors.secondaryContainer;
            case OUTLINED, TEXT -> new Color(0, 0, 0, 0);
            case DANGER -> colors.error;
        };
    }

    private Color foreground() {
        if (!isEnabled()) {
            return colors.alpha(colors.onSurface, 0.38f);
        }
        return switch (variant) {
            case FILLED -> colors.onPrimary;
            case TONAL -> colors.onSecondaryContainer;
            case OUTLINED, TEXT -> colors.primary;
            case DANGER -> colors.onError;
        };
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setForeground(foreground());
        repaint();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int width = getWidth();
        int paintHeight = getHeight();
        int radius = paintHeight / 2;

        Color container = container();
        if (container.getAlpha() > 0) {
            g.setColor(container);
            g.fillRoundRect(0, 0, width, paintHeight, radius * 2, radius * 2);
        }
        if (variant == Variant.OUTLINED) {
            g.setColor(isEnabled() ? colors.outline : colors.alpha(colors.onSurface, 0.12f));
            g.drawRoundRect(0, 0, width - 1, paintHeight - 1, radius * 2, radius * 2);
        }
        ripple.paint(g, width, paintHeight, radius, foreground());

        String label = getText();
        FontMetrics metrics = g.getFontMetrics(getFont());
        g.setFont(getFont());
        g.setColor(foreground());
        int textX = (width - metrics.stringWidth(label)) / 2;
        int textY = (paintHeight + metrics.getAscent() - metrics.getDescent()) / 2;
        g.drawString(label, textX, textY);

        if (isFocusOwner() && isEnabled()) {
            g.setColor(colors.alpha(colors.onSurface, 0.7f));
            g.setStroke(new java.awt.BasicStroke(1.4f, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND, 1f, new float[]{3f, 3f}, 0f));
            g.drawRoundRect(2, 2, width - 5, paintHeight - 5, radius * 2, radius * 2);
        }
        g.dispose();
    }
}
