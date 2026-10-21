package dev.cookielauncher.ui.component;

import dev.cookielauncher.ui.theme.ColorScheme;
import dev.cookielauncher.ui.theme.TypeScale;

import javax.swing.JTextField;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Material Design 3 filled text field.
 *
 * <p>M3 filled style: a surface-container-highest container with only the bottom
 * corners rounded and a 1dp underline that grows to 2dp in the primary colour on
 * focus. Floating label and supporting text are drawn by the owning form.</p>
 */
public class MdTextField extends JTextField {

    private final ColorScheme colors;
    private String placeholder;
    private boolean focused;
    private Color accent;

    public MdTextField(ColorScheme colors, String placeholder) {
        this.colors = colors;
        this.placeholder = placeholder == null ? "" : placeholder;
        setOpaque(false);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 16, 10, 16));
        setFont(TypeScale.bodyLarge());
        setForeground(colors.onSurface);
        setCaretColor(colors.primary);
        setSelectionColor(colors.alpha(colors.primary, 0.35f));
        setSelectedTextColor(colors.onSurface);
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                focused = true;
                repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                focused = false;
                repaint();
            }
        });
    }

    public MdTextField placeholder(String value) {
        this.placeholder = value == null ? "" : value;
        repaint();
        return this;
    }

    public MdTextField accent(Color value) {
        this.accent = value;
        repaint();
        return this;
    }

    public boolean isInvalid() {
        return false;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        Color line = accent != null ? accent : colors.primary;
        int radius = TypeScale.SHAPE_EXTRA_SMALL;

        // M3 filled field: square top corners, rounded bottom corners. Painted as
        // a body rectangle clipped by two bottom-corner arcs so nothing spills
        // outside the component bounds (an earlier version over-filled by 12px).
        g.setColor(colors.surfaceContainerHighest);
        g.fillRect(0, 0, width, Math.max(0, height - radius));
        g.fillRoundRect(0, Math.max(0, height - radius * 2), width, radius * 2, radius, radius);

        // Active indicator: 1dp at rest, 2dp while focused.
        int thickness = focused ? 2 : 1;
        g.setColor(focused ? line : colors.onSurfaceVariant);
        g.fillRect(0, height - thickness, width, thickness);

        if (getText().isEmpty() && !placeholder.isEmpty()) {
            g.setColor(colors.onSurfaceVariant);
            g.setFont(TypeScale.bodyLarge());
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(placeholder, 16, (height + metrics.getAscent() - metrics.getDescent()) / 2);
        }
        g.dispose();
        super.paintComponent(graphics);
    }
}
