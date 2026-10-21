package dev.cookielauncher.game.diagnose;

import java.util.List;

/**
 * A log signature and the advice it triggers. Rules are data: contributors add
 * entries to the table instead of writing new engine code.
 */
public record Rule(String id, Diagnosis.Severity severity, String title,
                   List<String> needles, String explanation, List<String> fixes) {

    /** Returns the first matching line, or null when this rule does not apply. */
    public String match(List<String> lines) {
        for (String line : lines) {
            String lower = line.toLowerCase();
            for (String needle : needles) {
                if (lower.contains(needle.toLowerCase())) {
                    return line;
                }
            }
        }
        return null;
    }
}
