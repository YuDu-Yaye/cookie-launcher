package dev.cookielauncher.ui.component;

import dev.cookielauncher.ui.theme.ColorScheme;
import dev.cookielauncher.ui.theme.TypeScale;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Material Design 3 linear progress indicator (determinate and indeterminate). */
public class MdProgress extends JComponent {

    private final ColorScheme colors;
    private double fraction;
    private boolean indeterminate;
    private final Timer timer;
    private long phase;

    public MdProgress(ColorScheme colors) {
        this.colors = colors;
        setOpaque(false);
        setPreferredSize(new Dimension(200, 4));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
        setMinimumSize(new Dimension(40, 4));
        timer = new Timer(16, event -> {
            phase += 16;
            repaint();
        });
    }

    public void setFraction(double value) {
        this.fraction = Math.max(0, Math.min(1, value));
        this.indeterminate = false;
        stopTimer();
        repaint();
    }

    public void setIndeterminate(boolean value) {
        this.indeterminate = value;
        if (value) {
            startTimer();
        } else {
            stopTimer();
        }
        repaint();
    }

    public double fraction() {
        return fraction;
    }

    private void startTimer() {
        if (!timer.isRunning()) {
            phase = 0;
            timer.start();
        }
    }

    private void stopTimer() {
        if (timer.isRunning()) {
            timer.stop();
        }
    }

    @Override
    public void removeNotify() {
        stopTimer();
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        // An idle, determinate bar at zero shows nothing: M3 only paints a track
        // while work is actually in progress.
        if (!indeterminate && fraction <= 0) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        // M3 fixes the linear indicator at 4dp. A layout manager may hand us a
        // taller box, so clamp the painted thickness and centre it rather than
        // stretching the track into a slab.
        int thickness = Math.min(TRACK_THICKNESS, Math.max(2, getHeight()));
        int offsetY = Math.max(0, (getHeight() - thickness) / 2);
        int radius = thickness;

        g.setColor(colors.alpha(colors.primary, 0.24f));
        g.fillRoundRect(0, offsetY, width, thickness, radius, radius);

        g.setColor(colors.primary);
        if (indeterminate) {
            double cycle = 1800.0;
            double position = (phase % cycle) / cycle;
            int barWidth = Math.max(80, (int) (width * 0.35));
            int travel = width + barWidth;
            int x = (int) (travel * easeInOut(position)) - barWidth;
            int visibleX = Math.max(0, x);
            int visibleWidth = Math.min(barWidth - (visibleX - x), width - visibleX);
            if (visibleWidth > 0) {
                g.fillRoundRect(visibleX, offsetY, visibleWidth, thickness, radius, radius);
            }
        } else if (fraction > 0) {
            int filled = (int) Math.round(width * fraction);
            g.fillRoundRect(0, offsetY, Math.max(radius, filled), thickness, radius, radius);
        }
        g.dispose();
    }

    /** M3 linear progress thickness in dp. */
    public static final int TRACK_THICKNESS = 4;

    private static double easeInOut(double t) {
        return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    }

    /** A labelled progress block with a caption and a percentage on the right. */
    public static final class Labeled extends javax.swing.JPanel {

        private final MdProgress bar;
        private final javax.swing.JLabel caption;
        private final javax.swing.JLabel detail;

        public Labeled(ColorScheme colors) {
            setOpaque(false);
            setLayout(new java.awt.BorderLayout(0, 6));
            javax.swing.JPanel top = new javax.swing.JPanel(new java.awt.BorderLayout());
            top.setOpaque(false);
            caption = new javax.swing.JLabel(" ");
            caption.setFont(TypeScale.bodySmall());
            caption.setForeground(colors.onSurfaceVariant);
            detail = new javax.swing.JLabel(" ");
            detail.setFont(TypeScale.labelSmall());
            detail.setForeground(colors.onSurfaceVariant);
            detail.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
            top.add(caption, java.awt.BorderLayout.WEST);
            top.add(detail, java.awt.BorderLayout.EAST);
            bar = new MdProgress(colors);
            add(top, java.awt.BorderLayout.NORTH);
            add(bar, java.awt.BorderLayout.CENTER);
        }

        public void setStatus(String text) {
            caption.setText(text == null || text.isBlank() ? " " : text);
        }

        public void setDetail(String text) {
            detail.setText(text == null || text.isBlank() ? " " : text);
        }

        public void setFraction(double value) {
            bar.setFraction(value);
        }

        public void setIndeterminate(boolean value) {
            bar.setIndeterminate(value);
        }

        public MdProgress bar() {
            return bar;
        }
    }
}
