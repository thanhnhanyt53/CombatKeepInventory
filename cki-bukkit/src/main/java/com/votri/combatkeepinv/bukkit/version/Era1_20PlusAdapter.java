package com.votri.combatkeepinv.bukkit.version;

import java.util.logging.Logger;

/**
 * 1.20 and newer, including the current 1.21.x line. This is also
 * the fallback adapter {@link VersionAdapterFactory} picks for any
 * server version string it cannot parse/recognize, under the
 * assumption that an unrecognized version is more likely to be
 * newer than this plugin's release than older than 1.18.
 */
final class Era1_20PlusAdapter extends AbstractVersionAdapter {

    Era1_20PlusAdapter(
            Logger logger
    ) {
        super(logger);
    }

    @Override
    public String getLabel() {
        return "1.20.x+";
    }
}
