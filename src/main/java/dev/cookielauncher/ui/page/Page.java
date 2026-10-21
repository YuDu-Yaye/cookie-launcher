package dev.cookielauncher.ui.page;

import dev.cookielauncher.ui.AppContext;
import dev.cookielauncher.ui.LauncherWindow;
import dev.cookielauncher.ui.theme.Theme;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.function.Consumer;

/** Base class for top-level pages. */
public abstract class Page extends JPanel {

    protected final AppContext context;
    protected Theme theme;

    protected Page(AppContext context) {
        this.context = context;
        this.theme = context.theme();
        setOpaque(true);
        setBackground(theme.colors.surface);
        setLayout(new BorderLayout());
    }

    /** Called every time the page becomes visible; rebuild dynamic content here. */
    public void onShow() {
    }

    /** Called on a theme change so the page rebuilds with the new tokens. */
    public void onThemeChanged(Theme newTheme) {
        this.theme = newTheme;
        setBackground(newTheme.colors.surface);
        rebuild();
    }

    /** Recreates the page content. */
    protected void rebuild() {
        removeAll();
        onShow();
        revalidate();
        repaint();
    }

    /** Runs work off the EDT, then reports completion or failure on the EDT. */
    protected void background(String name, Task task, Runnable onSuccess,
                              Consumer<Exception> onFailure) {
        Thread thread = new Thread(() -> {
            try {
                task.run();
                if (onSuccess != null) {
                    SwingUtilities.invokeLater(onSuccess);
                }
            } catch (Exception e) {
                dev.cookielauncher.core.log.Log.error(name + " failed", e);
                SwingUtilities.invokeLater(() -> {
                    if (onFailure != null) {
                        onFailure.accept(e);
                    } else {
                        LauncherWindow.toast(name + ": " + e.getMessage());
                    }
                });
            }
        }, "cookie-" + name.toLowerCase().replace(' ', '-'));
        thread.setDaemon(true);
        thread.start();
    }

    protected void ui(Runnable action) {
        SwingUtilities.invokeLater(action);
    }

    /** A task that may throw; keeps page code free of try/catch noise. */
    @FunctionalInterface
    protected interface Task {
        void run() throws Exception;
    }
}
