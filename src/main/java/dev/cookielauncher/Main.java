package dev.cookielauncher;

import dev.cookielauncher.cli.Cli;
import dev.cookielauncher.core.AppInfo;
import dev.cookielauncher.core.AppPaths;
import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.ui.AppContext;
import dev.cookielauncher.ui.LauncherWindow;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.Arrays;
import java.util.Locale;

/** Entry point: GUI by default, command line when arguments are given. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Log.setFile(AppPaths.launcherLog());
        String mode = args.length == 0 ? "gui" : args[0].toLowerCase(Locale.ROOT);
        if (mode.startsWith("-")) {
            if (mode.equals("--help") || mode.equals("-h")) {
                Cli.printHelp();
                return;
            }
            Cli.run(Arrays.copyOfRange(args, 1, args.length), mode.startsWith("--")
                    ? mode.substring(2) : mode.substring(1));
            return;
        }
        switch (mode) {
            case "gui" -> startGui();
            case "help" -> Cli.printHelp();
            case "version", "list", "install", "launch", "repair", "java", "doctor",
                 "accounts", "analyze" -> Cli.run(Arrays.copyOfRange(args, 1, args.length), mode);
            default -> {
                System.err.println("Unknown command: " + mode);
                Cli.printHelp();
                System.exit(2);
            }
        }
    }

    private static void startGui() {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The custom painted UI does not depend on the platform look and feel.
        }
        try {
            AppContext context = AppContext.bootstrap();
            SwingUtilities.invokeLater(() -> new LauncherWindow(context).setVisible(true));
        } catch (Throwable error) {
            Log.error("Cannot start the launcher", error);
            javax.swing.JOptionPane.showMessageDialog(null,
                    AppInfo.NAME + " could not start:\n" + error.getMessage()
                            + "\n\nSee " + AppPaths.launcherLog() + " for details.",
                    "Startup failed", javax.swing.JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}
