package com.votri.combatkeepinv.bukkit.version;

import java.util.logging.Logger;

/**
 * 1.17 - 1.17.1 (Java 16 server requirement).
 */
final class Era1_17Adapter extends AbstractVersionAdapter {

    Era1_17Adapter(
            Logger logger
    ) {
        super(logger);
    }

    @Override
    public String getLabel() {
        return "1.17.x";
    }
}
