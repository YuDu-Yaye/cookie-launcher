package dev.cookielauncher.ui;

import dev.cookielauncher.core.AppInfo;
import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.core.net.Mirror;
import dev.cookielauncher.core.util.Os;
import dev.cookielauncher.ui.component.MdButton;
import dev.cookielauncher.ui.component.MdIcon;
import dev.cookielauncher.ui.component.NavItem;
import dev.cookielauncher.ui.page.AccountsPage;
import dev.cookielauncher.ui.page.HomePage;
import dev.cookielauncher.ui.page.LogPage;
import dev.cookielauncher.ui.page.ModsPage;
import dev.cookielauncher.ui.page.Page;
import dev.cookielauncher.ui.page.SettingsPage;
import dev.cookielauncher.ui.page.VersionsPage;
import dev.cookielauncher.ui.theme.Theme;
import dev.cookielauncher.ui.theme.TypeScale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/** Main window: navigation drawer, page host, status strip and toast. */
public final class LauncherWindow extends JFrame {

    private static LauncherWindow instance;

    private final AppContext context;
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, NavItem> navItems = new LinkedHashMap<>();
    private final JPanel host = new JPanel(new BorderLayout());
    private final StatusStrip statusStrip;
    private final Toast toast;
    private Theme theme;

    public LauncherWindow(AppContext context) {
        this.context = context;
        this.theme = context.theme();
        setTitle(AppInfo.NAME + " " + AppInfo.VERSION);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(980, 640));
        setSize(1240, 800);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(theme.colors.surface);
        root.add(buildDrawer(), BorderLayout.WEST);

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        host.setOpaque(false);
        content.add(host, BorderLayout.CENTER);
        statusStrip = new StatusStrip(theme);
        content.add(statusStrip, BorderLayout.SOUTH);
        root.add(content, BorderLayout.CENTER);

        toast = new Toast(theme);
        root.add(toast, BorderLayout.NORTH);
        setContentPane(root);

        registerPages();
        context.setStatusSink(statusStrip::setMessage);
        context.onThemeChanged(this::applyTheme);
        showPage("home");

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
        Log.info(AppInfo.NAME + " " + AppInfo.VERSION + " started on " + Os.displayName());
        instance = this;
    }

    private JPanel buildDrawer() {
        JPanel drawer = new JPanel();
        drawer.setBackground(theme.colors.surfaceContainer);
        drawer.setLayout(new BoxLayout(drawer, BoxLayout.Y_AXIS));
        drawer.setPreferredSize(new Dimension(232, 0));
        drawer.setBorder(BorderFactory.createEmptyBorder(20, 12, 16, 12));

        JPanel brand = new JPanel(new BorderLayout(10, 0));
        brand.setOpaque(false);
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        brand.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        JPanel mark = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                MdIcon.drawLogo(g, theme.colors, Math.min(getWidth(), getHeight()));
                g.dispose();
            }
        };
        mark.setOpaque(false);
        mark.setPreferredSize(new Dimension(30, 30));
        mark.setMaximumSize(new Dimension(30, 30));
        brand.add(mark, BorderLayout.WEST);
        javax.swing.JLabel name = new javax.swing.JLabel("Cookie Launcher");
        name.setFont(TypeScale.titleLarge());
        name.setForeground(theme.colors.onSurface);
        brand.add(name, BorderLayout.CENTER);
        drawer.add(brand);
        drawer.add(Box.createVerticalStrut(24));

        addNav(drawer, "home", MdIcon.Glyph.HOME, "Play");
        addNav(drawer, "versions", MdIcon.Glyph.LIBRARY, "Versions");
        addNav(drawer, "mods", MdIcon.Glyph.EXTENSION, "Mods");
        addNav(drawer, "accounts", MdIcon.Glyph.PERSON, "Accounts");
        addNav(drawer, "logs", MdIcon.Glyph.ARTICLE, "Logs");
        addNav(drawer, "settings", MdIcon.Glyph.SETTINGS, "Settings");

        drawer.add(Box.createVerticalGlue());
        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setAlignmentX(LEFT_ALIGNMENT);
        javax.swing.JLabel version = new javax.swing.JLabel("v" + AppInfo.VERSION);
        version.setFont(TypeScale.labelSmall());
        version.setForeground(theme.colors.onSurfaceVariant);
        version.setAlignmentX(LEFT_ALIGNMENT);
        footer.add(version);
        javax.swing.JLabel licence = new javax.swing.JLabel("GPL-3.0  ·  open source");
        licence.setFont(TypeScale.labelSmall());
        licence.setForeground(theme.colors.onSurfaceVariant);
        licence.setAlignmentX(LEFT_ALIGNMENT);
        footer.add(Box.createVerticalStrut(2));
        footer.add(licence);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        drawer.add(footer);
        return drawer;
    }

    private void addNav(JPanel drawer, String id, MdIcon.Glyph glyph, String label) {
        NavItem item = new NavItem(theme.colors, glyph, label, () -> showPage(id));
        item.setAlignmentX(LEFT_ALIGNMENT);
        navItems.put(id, item);
        drawer.add(item);
        drawer.add(Box.createVerticalStrut(4));
    }

    private void registerPages() {
        pages.put("home", new HomePage(context));
        pages.put("versions", new VersionsPage(context));
        pages.put("mods", new ModsPage(context));
        pages.put("accounts", new AccountsPage(context));
        pages.put("logs", new LogPage(context));
        pages.put("settings", new SettingsPage(context));
    }

    /** Switches page and updates the drawer selection. */
    public void showPage(String id) {
        Page page = pages.get(id);
        if (page == null) {
            return;
        }
        navItems.forEach((key, item) -> item.setActive(key.equals(id)));
        host.removeAll();
        host.add(page, BorderLayout.CENTER);
        host.revalidate();
        host.repaint();
        page.onShow();
    }

    private void applyTheme(Theme newTheme) {
        this.theme = newTheme;
        getContentPane().setBackground(newTheme.colors.surface);
        for (Page page : pages.values()) {
            page.onThemeChanged(newTheme);
        }
        // The drawer is rebuilt by recreating the window content; simpler and
        // guarantees every component picks up the new scheme.
        JPanel root = (JPanel) getContentPane();
        root.removeAll();
        root.setBackground(newTheme.colors.surface);
        root.add(buildDrawer(), BorderLayout.WEST);
        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(host, BorderLayout.CENTER);
        statusStrip.setTheme(newTheme);
        content.add(statusStrip, BorderLayout.SOUTH);
        root.add(content, BorderLayout.CENTER);
        Toast newToast = toast;
        root.add(newToast, BorderLayout.NORTH);
        root.revalidate();
        root.repaint();
        showPage("home");
    }

    private void shutdown() {
        context.shutdown();
        dispose();
        System.exit(0);
    }

    public AppContext context() {
        return context;
    }

    public static LauncherWindow instance() {
        return instance;
    }

    /** Shows a short message above the content area. */
    public static void toast(String message) {
        SwingUtilities.invokeLater(() -> {
            if (instance != null) {
                instance.toast.show(message);
            }
        });
    }

    /** Bottom status strip: last action on the left, environment on the right. */
    private static final class StatusStrip extends JPanel {

        private Theme theme;
        private String message = "Ready";

        StatusStrip(Theme theme) {
            this.theme = theme;
            setOpaque(false);
            setPreferredSize(new Dimension(0, 32));
        }

        void setMessage(String value) {
            this.message = value == null ? "" : value;
            repaint();
        }

        void setTheme(Theme value) {
            this.theme = value;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(theme.colors.surfaceContainer);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setFont(TypeScale.bodySmall());
            g.setColor(theme.colors.onSurfaceVariant);
            String text = message.length() > 140 ? message.substring(0, 137) + "..." : message;
            g.drawString(text, 20, 21);
            String right = "Java " + System.getProperty("java.version")
                    + "  ·  " + Os.mojangOs() + "/" + Os.mojangArch();
            int width = g.getFontMetrics().stringWidth(right);
            g.drawString(right, getWidth() - width - 20, 21);
            g.dispose();
        }
    }

    /** A short-lived message pill. */
    private static final class Toast extends JPanel {

        private Theme theme;
        private String message = "";

        Toast(Theme theme) {
            this.theme = theme;
            setOpaque(false);
            setPreferredSize(new Dimension(0, 0));
        }

        void setTheme(Theme value) {
            this.theme = value;
            repaint();
        }

        void show(String text) {
            this.message = text == null ? "" : text;
            setPreferredSize(new Dimension(0, 46));
            revalidate();
            repaint();
            Timer timer = new Timer(2800, event -> {
                setPreferredSize(new Dimension(0, 0));
                revalidate();
                repaint();
                ((Timer) event.getSource()).stop();
            });
            timer.setRepeats(false);
            timer.start();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (message == null || message.isBlank()) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int width = Math.min(getWidth() - 48, 640);
            int x = (getWidth() - width) / 2;
            g.setColor(theme.colors.inverseSurface);
            g.fillRoundRect(x, 10, width, 32, 16, 16);
            g.setColor(theme.colors.onInverseSurface);
            g.setFont(TypeScale.bodyMedium());
            String text = message.length() > 92 ? message.substring(0, 89) + "..." : message;
            var metrics = g.getFontMetrics();
            g.drawString(text, x + (width - metrics.stringWidth(text)) / 2, 31);
            g.dispose();
        }
    }

    /** Entry point used by Main. */
    public static void launch(AppContext context) {
        SwingUtilities.invokeLater(() -> new LauncherWindow(context).setVisible(true));
    }

    static Color transparent() {
        return new Color(0, 0, 0, 0);
    }

    static MdButton button(AppContext context, String text, MdButton.Variant variant) {
        return new MdButton(context.theme().colors, text, variant);
    }
}
