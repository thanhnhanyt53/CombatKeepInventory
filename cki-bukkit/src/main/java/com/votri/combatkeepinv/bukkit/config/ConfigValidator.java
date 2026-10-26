package com.votri.combatkeepinv.bukkit.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks a loaded {@code config.yml} for values that would be
 * silently ignored or would not do what the admin most likely
 * intended.
 *
 * <p>Bukkit's typed getters quietly fall back to a default when a
 * value has the wrong type, and the plugin's own parsers fall back
 * the same way for unknown names, so a typo such as
 * {@code action: DORP} or {@code keep-main: "yes"} used to change
 * behavior without a single log line. This class reports those
 * cases instead.</p>
 *
 * <ul>
 *   <li>{@link Severity#ERROR}: the value is unusable (wrong type,
 *       unknown name, out of range). A reload containing an ERROR
 *       is rejected and the running configuration is kept.</li>
 *   <li>{@link Severity#WARNING}: the value works but is probably
 *       not what was meant. The configuration is still applied.</li>
 * </ul>
 *
 * <p>It only reads the section it is given, so it can be run on a
 * candidate configuration before that configuration is applied.</p>
 */
public final class ConfigValidator {

    public enum Severity {
        WARNING,
        ERROR
    }

    public record Issue(
            Severity severity,
            String path,
            String message
    ) {

        @Override
        public String toString() {
            return severity + " " + path + ": " + message;
        }
    }

    private static final Set<String> ACTIONS =
            Set.of("KEEP", "DROP", "PARTIAL", "VANILLA");

    private static final Set<String> PRIORITIES =
            Set.of(
                    "LOWEST",
                    "LOW",
                    "NORMAL",
                    "HIGH",
                    "HIGHEST",
                    "MONITOR"
            );

    private static final Set<String> PLATFORM_MODES =
            Set.of(
                    "auto",
                    "bukkit",
                    "spigot",
                    "paper",
                    "purpur"
            );

    private static final List<String> POLICIES =
            Arrays.asList(
                    "combat-death",
                    "player-kill",
                    "pve-death",
                    "unknown"
            );

    private static final List<String> POLICY_FLAGS =
            Arrays.asList(
                    "keep-main",
                    "keep-armor",
                    "keep-offhand",
                    "keep-hotbar",
                    "keep-experience",
                    "keep-levels"
            );

    /** Upper bound above which a combat duration looks like a typo. */
    static final long LONG_DURATION_SECONDS = 600L;

    private ConfigValidator() {
    }

    public static List<Issue> validate(
            ConfigurationSection config
    ) {

        List<Issue> issues =
                new ArrayList<>();

        if (config == null) {

            issues.add(
                    new Issue(
                            Severity.ERROR,
                            "(root)",
                            "configuration is missing or empty"
                    )
            );

            return issues;
        }

        validatePlatform(config, issues);
        validateCombat(config, issues);
        validateListenerPriority(config, issues);
        validatePolicies(config, issues);
        validateWorlds(config, issues);
        validateWorldGuard(config, issues);
        validateDebug(config, issues);

        return issues;
    }

    public static boolean hasErrors(
            List<Issue> issues
    ) {

        for (Issue issue : issues) {

            if (issue.severity() == Severity.ERROR) {
                return true;
            }
        }

        return false;
    }

    /*
     * ==========================================================
     * SECTIONS
     * ==========================================================
     */

    private static void validatePlatform(
            ConfigurationSection config,
            List<Issue> issues
    ) {

        Object mode =
                config.get("platform.mode");

        if (mode != null) {

            String normalized =
                    String.valueOf(mode)
                            .trim()
                            .toLowerCase(Locale.ROOT);

            if (!normalized.isEmpty()
                    && !PLATFORM_MODES.contains(normalized)) {

                issues.add(
                        new Issue(
                                Severity.WARNING,
                                "platform.mode",
                                "unknown mode '"
                                        + mode
                                        + "' (expected one of "
                                        + new TreeSet<>(PLATFORM_MODES)
                                        + "); it will never match "
                                        + "the detected platform"
                        )
                );
            }
        }

        checkBoolean(config, "platform.strict", issues);
    }

    private static void validateCombat(
            ConfigurationSection config,
            List<Issue> issues
    ) {

        checkBoolean(config, "combat.enabled", issues);

        String path =
                "combat.duration-seconds";

        Object value =
                config.get(path);

        if (value == null) {
            return;
        }

        if (!(value instanceof Number number)
                || number.doubleValue() != Math.rint(number.doubleValue())) {

            issues.add(
                    new Issue(
                            Severity.ERROR,
                            path,
                            "must be a whole number of seconds, got '"
                                    + value
                                    + "'"
                    )
            );

            return;
        }

        long seconds =
                number.longValue();

        if (seconds < 1) {

            issues.add(
                    new Issue(
                            Severity.ERROR,
                            path,
                            "must be at least 1, got "
                                    + seconds
                    )
            );

        } else if (seconds > LONG_DURATION_SECONDS) {

            issues.add(
                    new Issue(
                            Severity.WARNING,
                            path,
                            seconds
                                    + " seconds is unusually long "
                                    + "(more than "
                                    + LONG_DURATION_SECONDS
                                    + "); players would stay tagged "
                                    + "for a very long time"
                    )
            );
        }
    }

    private static void validateListenerPriority(
            ConfigurationSection config,
            List<Issue> issues
    ) {

        Object value =
                config.get("listener-priority");

        if (value == null) {
            return;
        }

        String name =
                String.valueOf(value)
                        .trim()
                        .toUpperCase(Locale.ROOT);

        if (!PRIORITIES.contains(name)) {

            issues.add(
                    new Issue(
                            Severity.ERROR,
                            "listener-priority",
                            "unknown event priority '"
                                    + value
                                    + "' (expected one of "
                                    + new TreeSet<>(PRIORITIES)
                                    + ")"
                    )
            );

        } else if (name.equals("MONITOR")) {

            issues.add(
                    new Issue(
                            Severity.WARNING,
                            "listener-priority",
                            "MONITOR is for observing only; the "
                                    + "plugin changes drops and "
                                    + "keepInventory, which must not "
                                    + "be done at MONITOR priority"
                    )
            );
        }
    }

    private static void validatePolicies(
            ConfigurationSection config,
            List<Issue> issues
    ) {

        Integer combatPriority = null;
        Integer playerKillPriority = null;

        for (String policy : POLICIES) {

            String base =
                    "inventory.policies."
                            + policy;

            if (!config.contains(base)) {
                continue;
            }

            String actionPath =
                    base + ".action";

            Object action =
                    config.get(actionPath);

            if (action != null) {

                String name =
                        String.valueOf(action)
                                .trim()
                                .toUpperCase(Locale.ROOT);

                if (!ACTIONS.contains(name)) {

                    issues.add(
                            new Issue(
                                    Severity.ERROR,
                                    actionPath,
                                    "unknown action '"
                                            + action
                                            + "' (expected one of "
                                            + new TreeSet<>(ACTIONS)
                                            + ")"
                            )
                    );

                } else if (name.equals("PARTIAL")) {

                    warnIfPartialIsPointless(
                            config,
                            base,
                            issues
                    );
                }
            }

            Integer priority =
                    readPriority(
                            config,
                            base + ".priority",
                            issues
                    );

            if (policy.equals("combat-death")) {
                combatPriority = priority;
            }

            if (policy.equals("player-kill")) {
                playerKillPriority = priority;
            }

            for (String flag : POLICY_FLAGS) {
                checkBoolean(config, base + "." + flag, issues);
            }
        }

        if (combatPriority != null
                && playerKillPriority != null
                && playerKillPriority > combatPriority) {

            issues.add(
                    new Issue(
                            Severity.WARNING,
                            "inventory.policies.player-kill.priority",
                            "player-kill ("
                                    + playerKillPriority
                                    + ") outranks combat-death ("
                                    + combatPriority
                                    + "), so a PvP kill uses the "
                                    + "player-kill policy even while "
                                    + "the victim has an active "
                                    + "CombatTag. The shipped "
                                    + "default is the other way "
                                    + "round (300 vs 400). Ignore "
                                    + "this if it is intended."
                    )
            );
        }
    }

    /**
     * Reads a priority. Returns null (and reports) when it is not a
     * whole number, in which case the plugin silently used its
     * built-in default.
     */
    private static Integer readPriority(
            ConfigurationSection config,
            String path,
            List<Issue> issues
    ) {

        Object value =
                config.get(path);

        if (value == null) {
            return null;
        }

        if (value instanceof Number number
                && number.doubleValue() == Math.rint(number.doubleValue())) {

            return number.intValue();
        }

        issues.add(
                new Issue(
                        Severity.ERROR,
                        path,
                        "priority must be a whole number, got '"
                                + value
                                + "'"
                )
        );

        return null;
    }

    private static void warnIfPartialIsPointless(
            ConfigurationSection config,
            String base,
            List<Issue> issues
    ) {

        Set<Boolean> flags =
                new HashSet<>();

        for (String flag : Arrays.asList(
                "keep-main",
                "keep-armor",
                "keep-offhand",
                "keep-hotbar"
        )) {

            Object value =
                    config.get(base + "." + flag);

            if (value instanceof Boolean bool) {
                flags.add(bool);
            }
        }

        if (flags.size() == 1) {

            boolean all =
                    flags.iterator().next();

            issues.add(
                    new Issue(
                            Severity.WARNING,
                            base + ".action",
                            "PARTIAL with every keep-* flag set to "
                                    + all
                                    + " behaves exactly like "
                                    + (all ? "KEEP" : "DROP")
                                    + "; use that action instead"
                    )
            );
        }
    }

    private static void validateWorlds(
            ConfigurationSection config,
            List<Issue> issues
    ) {

        checkBoolean(config, "worlds.enabled", issues);
        checkList(config, "worlds.disabled-worlds", issues);
    }

    private static void validateWorldGuard(
            ConfigurationSection config,
            List<Issue> issues
    ) {

        checkBoolean(config, "worldguard.enabled", issues);
        checkBoolean(config, "worldguard.fail-open", issues);
        checkBoolean(
                config,
                "worldguard.require-both-players-in-region",
                issues
        );
        checkBoolean(
                config,
                "worldguard.restrict-to-enabled-regions",
                issues
        );

        checkList(config, "worldguard.excluded-regions", issues);
        checkList(config, "worldguard.enabled-regions", issues);

        List<String> excluded =
                lower(config.getStringList("worldguard.excluded-regions"));

        List<String> enabled =
                lower(config.getStringList("worldguard.enabled-regions"));

        if (config.getBoolean("worldguard.restrict-to-enabled-regions", false)
                && enabled.isEmpty()) {

            issues.add(
                    new Issue(
                            Severity.WARNING,
                            "worldguard.restrict-to-enabled-regions",
                            "is true but worldguard.enabled-regions is "
                                    + "empty, so combat tagging can never "
                                    + "happen anywhere"
                    )
            );
        }

        for (String region : enabled) {

            if (excluded.contains(region)) {

                issues.add(
                        new Issue(
                                Severity.WARNING,
                                "worldguard.enabled-regions",
                                "region '"
                                        + region
                                        + "' is listed in both "
                                        + "enabled-regions and "
                                        + "excluded-regions; excluded "
                                        + "wins"
                        )
                );
            }
        }
    }

    private static void validateDebug(
            ConfigurationSection config,
            List<Issue> issues
    ) {

        for (String key : Arrays.asList(
                "combat",
                "damage",
                "death",
                "bridge",
                "worldguard"
        )) {
            checkBoolean(config, "debug." + key, issues);
        }
    }

    /*
     * ==========================================================
     * HELPERS
     * ==========================================================
     */

    private static void checkBoolean(
            ConfigurationSection config,
            String path,
            List<Issue> issues
    ) {

        Object value =
                config.get(path);

        if (value != null
                && !(value instanceof Boolean)) {

            issues.add(
                    new Issue(
                            Severity.WARNING,
                            path,
                            "expected true or false but got '"
                                    + value
                                    + "'; the value is ignored and "
                                    + "the default is used"
                    )
            );
        }
    }

    private static void checkList(
            ConfigurationSection config,
            String path,
            List<Issue> issues
    ) {

        Object value =
                config.get(path);

        if (value != null
                && !(value instanceof List<?>)) {

            issues.add(
                    new Issue(
                            Severity.WARNING,
                            path,
                            "expected a list but got '"
                                    + value
                                    + "'; it is treated as empty"
                    )
            );
        }
    }

    private static List<String> lower(
            List<String> values
    ) {

        List<String> result =
                new ArrayList<>(values.size());

        for (String value : values) {

            if (value != null) {
                result.add(
                        value.trim()
                                .toLowerCase(Locale.ROOT)
                );
            }
        }

        return result;
    }
}
