package dev.cookielauncher.ui.component;

import dev.cookielauncher.ui.theme.ColorScheme;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Material Design 3 ripple: a state-layer circle that expands from the press
 * point and fades out, painted above the component's own content.
 *
 * <p>This is the single detail that makes a hand-painted Swing UI feel like a
 * modern toolkit, so it is implemented for real rather than faked with a colour
 * swap on hover.</p>
 */
public final class Ripple {

    private static final int DURATION_MS = 460;
    private static final int FRAME_MS = 16;

    private final List<Wave> waves = new ArrayList<>();
    private final Timer timer;
    private final JComponent target;
    private final ColorScheme colors;

    private boolean hovered;
    private boolean pressed;
    private boolean focused;

    public Ripple(JComponent target, ColorScheme colors) {
        this.target = target;
        this.colors = colors;
        this.timer = new Timer(FRAME_MS, event -> tick());
        this.timer.setCoalesce(true);
        install();
    }

    private void install() {
        target.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                pressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                pressed = true;
                if (target.isEnabled()) {
                    addWave(e.getX(), e.getY(), 0.12f);
                }
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressed = false;
                repaint();
            }
        });
        target.addFocusListener(new java.awt.event.FocusAdapter() {
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

    private void addWave(int x, int y, float peakAlpha) {
        int width = Math.max(1, target.getWidth());
        int height = Math.max(1, target.getHeight());
        double maxDistance = 0;
        for (int cornerX : new int[]{0, width}) {
            for (int cornerY : new int[]{0, height}) {
                double dx = cornerX - x;
                double dy = cornerY - y;
                maxDistance = Math.max(maxDistance, Math.hypot(dx, dy));
            }
        }
        waves.add(new Wave(x, y, maxDistance, peakAlpha, System.currentTimeMillis()));
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Wave> iterator = waves.iterator(); iterator.hasNext(); ) {
            Wave wave = iterator.next();
            if (now - wave.start >= DURATION_MS) {
                iterator.remove();
            }
        }
        if (waves.isEmpty()) {
            timer.stop();
        }
        repaint();
    }

    private void repaint() {
        if (target != null) {
            target.repaint();
        }
    }

    /** Paints hover/focus/press state layers, then any active ripples. */
    public void paint(Graphics2D g, int width, int height, int radius, Color contentColor) {
        if (hovered && target.isEnabled()) {
            g.setColor(colors.alpha(contentColor, ColorScheme.HOVER_OPACITY));
            g.fillRoundRect(0, 0, width, height, radius * 2, radius * 2);
        }
        if (focused && target.isFocusOwner()) {
            g.setColor(colors.alpha(contentColor, ColorScheme.FOCUS_OPACITY));
            g.fillRoundRect(0, 0, width, height, radius * 2, radius * 2);
        }
        if (pressed && target.isEnabled()) {
            g.setColor(colors.alpha(contentColor, ColorScheme.PRESSED_OPACITY));
            g.fillRoundRect(0, 0, width, height, radius * 2, radius * 2);
        }
        long now = System.currentTimeMillis();
        for (Wave wave : waves) {
            double progress = (now - wave.start) / (double) DURATION_MS;
            if (progress >= 1.0) {
                continue;
            }
            double eased = 1 - Math.pow(1 - Math.min(1, progress), 2.2);
            int alpha = Math.round(255 * wave.peakAlpha * (1 - (float) progress));
            if (alpha <= 0) {
                continue;
            }
            int diameter = (int) (wave.maxRadius * 2 * eased);
            g.setColor(colors.alpha(contentColor, alpha / 255f));
            g.fillOval((int) (wave.x - diameter / 2.0), (int) (wave.y - diameter / 2.0), diameter, diameter);
        }
    }

    public boolean isHovered() {
        return hovered;
    }

    public void setHovered(boolean value) {
        this.hovered = value;
        repaint();
    }

    /** One expanding circle. */
    private static final class Wave {
        final int x;
        final int y;
        final double maxRadius;
        final float peakAlpha;
        final long start;

        Wave(int x, int y, double maxRadius, float peakAlpha, long start) {
            this.x = x;
            this.y = y;
            this.maxRadius = maxRadius;
            this.peakAlpha = peakAlpha;
            this.start = start;
        }
    }
}
