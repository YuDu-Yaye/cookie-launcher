package dev.cookielauncher.game.diagnose;

import java.util.List;

/**
 * One diagnosis: what happened, why, and the concrete next step.
 *
 * <p>This is the differentiator the market research identified: every launcher
 * shows a log, none turns a log into an actionable cause. A diagnosis always
 * carries at least one fix, because "here is your stack trace" is not help.</p>
 */
public record Diagnosis(Severity severity, String title, String explanation,
                        List<String> fixes, String evidence) {

    public enum Severity {
        /** The game cannot start until this is fixed. */
        BLOCKER,
        /** The game starts but will fail or run badly. */
        ERROR,
        /** Worth knowing; may not be the cause. */
        WARNING
    }

    public String render() {
        StringBuilder builder = new StringBuilder();
        builder.append('[').append(severity).append("] ").append(title).append('\n');
        builder.append("  ").append(explanation).append('\n');
        if (evidence != null && !evidence.isBlank()) {
            builder.append("  Evidence: ").append(evidence).append('\n');
        }
        for (String fix : fixes) {
            builder.append("  -> ").append(fix).append('\n');
        }
        return builder.toString();
    }

    public boolean isBlocker() {
        return severity == Severity.BLOCKER;
    }
}
