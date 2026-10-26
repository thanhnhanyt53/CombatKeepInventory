package com.votri.combatkeepinv.bukkit.config;

import java.util.List;

/**
 * Outcome of {@code /cki reload}.
 *
 * @param applied true when the new configuration is now active;
 *                false when it was rejected and the previous
 *                configuration is still running
 * @param issues  everything the validator reported
 * @param notes   extra information, e.g. settings that only take
 *                effect after a restart
 */
public record ReloadResult(
        boolean applied,
        List<ConfigValidator.Issue> issues,
        List<String> notes
) {

    public long errorCount() {
        return issues.stream()
                .filter(issue -> issue.severity()
                        == ConfigValidator.Severity.ERROR)
                .count();
    }

    public long warningCount() {
        return issues.stream()
                .filter(issue -> issue.severity()
                        == ConfigValidator.Severity.WARNING)
                .count();
    }
}
