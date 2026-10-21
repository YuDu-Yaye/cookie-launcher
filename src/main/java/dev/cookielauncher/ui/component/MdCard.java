package dev.cookielauncher.ui.component;

import dev.cookielauncher.ui.theme.ColorScheme;
import dev.cookielauncher.ui.theme.TypeScale;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/**
 * Material Design 3 card. Supports the three M3 card types:
 * elevated (shadow), filled (surface container), and outlined (1dp border).
 */
public class MdCard extends JPanel {

    public enum Variant {
        ELEVATED, FILLED, OUTLINED
    }

    private final ColorScheme colors;
    private final Variant variant;
    private int radius = TypeScale.SHAPE_MEDIUM;
    private Color overrideFill;

    public MdCard(ColorScheme colors) {
        this(colors, Variant.FILLED);
    }

    public MdCard(ColorScheme colors, Variant variant) {
        this.colors = colors;
        this.variant = variant;
        setOpaque(false);
    }

    public static MdCard filled(ColorScheme colors, LayoutManager layout) {
        MdCard card = new MdCard(colors, Variant.FILLED);
        card.setLayout(layout);
        return card;
    }

    public static MdCard elevated(ColorScheme colors, LayoutManager layout) {
        MdCard card = new MdCard(colors, Variant.ELEVATED);
        card.setLayout(layout);
        return card;
    }

    public static MdCard outlined(ColorScheme colors, LayoutManager layout) {
        MdCard card = new MdCard(colors, Variant.OUTLINED);
        card.setLayout(layout);
        return card;
    }

    public MdCard radius(int value) {
        this.radius = value;
        repaint();
        return this;
    }

    public MdCard fill(Color color) {
        this.overrideFill = color;
        repaint();
        return this;
    }

    public MdCard padded(int all) {
        setBorder(javax.swing.BorderFactory.createEmptyBorder(all, all, all, all));
        return this;
    }

    public MdCard padded(int vertical, int horizontal) {
        setBorder(javax.swing.BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal));
        return this;
    }

    public Color surfaceColor() {
        if (overrideFill != null) {
            return overrideFill;
        }
        return switch (variant) {
            case ELEVATED -> colors.surfaceContainerLow;
            case FILLED -> colors.surfaceContainerHigh;
            case OUTLINED -> colors.surface;
        };
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();

        if (variant == Variant.ELEVATED) {
            // M3 level-1 elevation shadow: a soft, low-opacity halo.
            for (int layer = 5; layer >= 1; layer--) {
                g.setColor(new Color(0, 0, 0, 5 + layer * 2));
                g.fillRoundRect(layer, layer + 1, width - layer * 2, height - layer * 2,
                        radius + layer, radius + layer);
            }
        }
        g.setColor(surfaceColor());
        g.fillRoundRect(0, 0, width, height, radius, radius);

        if (variant == Variant.OUTLINED) {
            g.setColor(colors.outlineVariant);
            g.drawRoundRect(0, 0, width - 1, height - 1, radius, radius);
        }
        g.dispose();
        super.paintComponent(graphics);
    }
}
