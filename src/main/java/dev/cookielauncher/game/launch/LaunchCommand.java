package dev.cookielauncher.game.launch;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A fully resolved command line plus the working directory and environment. */
public final class LaunchCommand {

    private final List<String> command;
    private final Path workingDirectory;
    private final Map<String, String> environment;

    public LaunchCommand(List<String> command, Path workingDirectory, Map<String, String> environment) {
        this.command = new ArrayList<>(command);
        this.workingDirectory = workingDirectory;
        this.environment = new LinkedHashMap<>(environment);
    }

    public List<String> parts() {
        return new ArrayList<>(command);
    }

    public Path workingDirectory() {
        return workingDirectory;
    }

    public Map<String, String> environment() {
        return new LinkedHashMap<>(environment);
    }

    /** Redacts the access token so the command can be logged and shared safely. */
    public String display() {
        List<String> safe = new ArrayList<>();
        for (int i = 0; i < command.size(); i++) {
            String part = command.get(i);
            if ("--accessToken".equals(command.get(i - 1 < 0 ? 0 : i - 1)) && i > 0) {
                safe.add("<redacted>");
            } else {
                safe.add(part.contains(" ") ? "\"" + part + "\"" : part);
            }
        }
        return String.join(" ", safe);
    }

    @Override
    public String toString() {
        return display();
    }
}
