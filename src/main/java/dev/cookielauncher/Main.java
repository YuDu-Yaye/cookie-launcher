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
import java.util.List;
import java.util.Locale;

/**
 * Entry point: GUI by default, command line when a command is present.
 *
 * <p>Global options may appear before or after the command, so
 * {@code --home /tmp/x doctor} and {@code doctor --home /tmp/x} both work. The
 * first non-option token is the command; everything after it belongs to the
 * command, and anything starting with {@code --} is a global option.</p>
 */
public final class Main {

    private static final List<String> COMMANDS = List.of(
            "list", "install", "launch", "repair", "java", "doctor",
            "accounts", "analyze", "version", "help");

    /** Options that consume the following token as their value. */
    private static final List<String> VALUE_OPTIONS = List.of(
            "--home", "--source", "--proxy", "--loader", "--loader-version");

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            startGui();
            return;
        }
        String command = firstCommand(args);
        if (command == null) {
            if (hasFlag(args, "--help") || hasFlag(args, "-h")) {
                Cli.printHelp();
                return;
            }
            startGui();
            return;
        }
        if ("help".equals(command)) {
            Cli.printHelp();
            return;
        }
        Log.setFile(AppPaths.launcherLog());
        Log.setDebug(hasFlag(args, "--debug"));
        Cli.run(args, command);
    }

    /** Returns the first non-option token, or null when there is none. */
    static String firstCommand(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            if (argument == null || argument.isBlank()) {
                continue;
            }
            if (argument.startsWith("-")) {
                if (VALUE_OPTIONS.contains(argument)) {
                    i++;
                }
                continue;
            }
            String candidate = argument.toLowerCase(Locale.ROOT);
            return COMMANDS.contains(candidate) || candidate.matches("[a-z]+") ? candidate : null;
        }
        return null;
    }

    static boolean hasFlag(String[] args, String flag) {
        return Arrays.asList(args).contains(flag);
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
