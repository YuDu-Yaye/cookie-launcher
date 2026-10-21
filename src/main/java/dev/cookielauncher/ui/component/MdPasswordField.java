package dev.cookielauncher.ui.component;

import dev.cookielauncher.ui.theme.ColorScheme;
import dev.cookielauncher.ui.theme.TypeScale;

import javax.swing.JPasswordField;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Material Design 3 filled password field: same container and underline as
 * {@link MdTextField}, but masked input and no placeholder echo once typing starts.
 */
public class MdPasswordField extends JPasswordField {

    private final ColorScheme colors;
    private String placeholder;
    private boolean focused;

    public MdPasswordField(ColorScheme colors, String placeholder) {
        this.colors = colors;
        this.placeholder = placeholder == null ? "" : placeholder;
        setOpaque(false);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 16, 10, 16));
        setFont(TypeScale.bodyLarge());
        setForeground(colors.onSurface);
        setCaretColor(colors.primary);
        setSelectionColor(colors.alpha(colors.primary, 0.35f));
        setSelectedTextColor(colors.onSurface);
        setEchoChar('\u2022');
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

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();

        g.setColor(colors.surfaceContainerHighest);
        g.fillRoundRect(0, 0, width, height + TypeScale.SHAPE_EXTRA_SMALL,
                TypeScale.SHAPE_EXTRA_SMALL, TypeScale.SHAPE_EXTRA_SMALL);

        int thickness = focused ? 2 : 1;
        g.setColor(focused ? colors.primary : colors.onSurfaceVariant);
        g.fillRect(0, height - thickness, width, thickness);

        if (getPassword().length == 0 && !placeholder.isEmpty()) {
            g.setColor(colors.onSurfaceVariant);
            g.setFont(TypeScale.bodyLarge());
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(placeholder, 16, (height + metrics.getAscent() - metrics.getDescent()) / 2);
        }
        g.dispose();
        super.paintComponent(graphics);
    }
}
