package dev.cookielauncher.ui.component;

import dev.cookielauncher.ui.theme.ColorScheme;
import dev.cookielauncher.ui.theme.TypeScale;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Material Design 3 navigation drawer item: a pill container with an icon and a
 * label, an active indicator and a proper state layer.
 */
public class NavItem extends JPanel {

    private final ColorScheme colors;
    private final MdIcon.Glyph glyph;
    private final String label;
    private final Runnable onActivate;
    private boolean active;
    private boolean hovered;
    private final Ripple ripple;

    public NavItem(ColorScheme colors, MdIcon.Glyph glyph, String label, Runnable onActivate) {
        this.colors = colors;
        this.glyph = glyph;
        this.label = label;
        this.onActivate = onActivate;
        this.ripple = new Ripple(this, colors);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(200, 48));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (onActivate != null) {
                    onActivate.run();
                }
            }
        });
    }

    public void setActive(boolean value) {
        if (active != value) {
            active = value;
            repaint();
        }
    }

    public String label() {
        return label;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        int pillHeight = 40;
        int y = (height - pillHeight) / 2;

        if (active) {
            g.setColor(colors.secondaryContainer);
            g.fillRoundRect(4, y, width - 8, pillHeight, pillHeight, pillHeight);
        }
        ripple.paint(g, width, height, Math.max(0, (height - 8) / 2),
                active ? colors.onSecondaryContainer : colors.onSurface);

        Color content = active ? colors.onSecondaryContainer : colors.onSurfaceVariant;
        g.dispose();

        Graphics2D iconGraphics = (Graphics2D) graphics.create();
        iconGraphics.translate(18, (height - 20) / 2);
        MdIcon.draw(iconGraphics, glyph, content, 20);
        iconGraphics.dispose();

        Graphics2D textGraphics = (Graphics2D) graphics.create();
        textGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        textGraphics.setFont(active ? TypeScale.labelLarge() : TypeScale.bodyLarge());
        textGraphics.setColor(active ? colors.onSecondaryContainer : colors.onSurfaceVariant);
        FontMetrics metrics = textGraphics.getFontMetrics();
        textGraphics.drawString(label, 52,
                (height + metrics.getAscent() - metrics.getDescent()) / 2);
        textGraphics.dispose();
    }

    /** Kept for symmetry with other components; NavItem has no outline. */
    public void setHovered(boolean value) {
        this.hovered = value;
        ripple.setHovered(value);
        repaint();
    }

    public boolean isHovered() {
        return hovered;
    }
}
