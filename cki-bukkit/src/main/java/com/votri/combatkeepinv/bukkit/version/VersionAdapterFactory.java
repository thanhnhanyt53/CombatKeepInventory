package com.votri.combatkeepinv.bukkit.version;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the running server's reported Bukkit version and selects
 * the matching {@link VersionAdapter}. The version groupings
 * below only need to be as fine-grained as this plugin's actual
 * API touchpoints require -- see {@link VersionAdapter}'s class
 * doc for what that is and is not.
 */
public final class VersionAdapterFactory {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private VersionAdapterFactory() {
    }

    /**
     * @param bukkitVersion typically {@code Bukkit.getBukkitVersion()},
     *                      e.g. {@code "1.20.4-R0.1-SNAPSHOT"}
     */
    public static VersionAdapter create(
            String bukkitVersion,
            Logger logger
    ) {

        int[] parsed =
                parse(bukkitVersion);

        VersionAdapter adapter =
                select(parsed, logger);

        logger.info(
                "Detected server version string '"
                        + bukkitVersion
                        + "' -> version adapter: "
                        + adapter.getLabel()
                        + " | TNT source tracking: "
                        + (adapter.supportsTntSourceTracking()
                                ? "enabled"
                                : "disabled")
        );

        return adapter;
    }

    private static VersionAdapter select(
            int[] parsed,
            Logger logger
    ) {

        if (parsed == null) {

            logger.warning(
                    "Could not parse the server's reported "
                            + "Bukkit version string; assuming "
                            + "the newest supported era "
                            + "(1.20.x+) rather than guessing "
                            + "something older."
            );

            return new Era1_20PlusAdapter(logger);
        }

        int major =
                parsed[0];

        int minor =
                parsed[1];

        /*
         * Minecraft moved from 1.x to calendar versioning in
         * 2026 (26.1, 26.2, 26.3, ...): the reported major number
         * is now the two-digit year, not "1". Those all land here
         * on purpose, since nothing this plugin touches is tied
         * to a specific 1.x-era API break -- same as any other
         * version newer than the 1.18/1.19 group below.
         */
        if (major != 1) {
            return new Era1_20PlusAdapter(logger);
        }

        
        

        if (minor == 18 || minor == 19) {
            return new Era1_18To1_19Adapter(logger);
        }

        if (minor < 17) {

            /*
             * plugin.yml's api-version: '1.17' should already
             * stop Paper/Spigot from loading the plugin at all
             * on an older server. This branch only exists in
             * case that check is ever bypassed (e.g. a fork that
             * ignores api-version) -- fail soft rather than NPE
             * somewhere unrelated later.
             */
            logger.severe(
                    "Detected Minecraft "
                            + major
                            + "."
                            + minor
                            + ", which is older than the 1.17 "
                            + "minimum this build supports. "
                            + "Using the 1.18.x adapter as a "
                            + "best-effort fallback; expect "
                            + "reduced compatibility and please "
                            + "use a build targeting your "
                            + "version instead."
            );
        }

            

        /*
         * 1.20.x, 1.21.x, and anything newer/unrecognized above
         * 1.17 share the same adapter -- nothing this plugin
         * touches has changed across that range.
         */
        return new Era1_20PlusAdapter(logger);
    }

    private static int[] parse(
            String bukkitVersion
    ) {

        if (bukkitVersion == null) {
            return null;
        }

        Matcher matcher =
                VERSION_PATTERN.matcher(
                        bukkitVersion.trim()
                );

        if (!matcher.find()) {
            return null;
        }

        try {

            int major =
                    Integer.parseInt(matcher.group(1));

            int minor =
                    Integer.parseInt(matcher.group(2));

            int patch =
                    matcher.group(3) == null
                            ? 0
                            : Integer.parseInt(matcher.group(3));

            return new int[] {major, minor, patch};

        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
