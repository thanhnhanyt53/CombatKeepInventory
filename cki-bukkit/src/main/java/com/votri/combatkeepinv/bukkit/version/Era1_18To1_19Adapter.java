package com.votri.combatkeepinv.bukkit.version;

import java.util.logging.Logger;

/**
 * 1.18 - 1.19.4 (Java 17 server requirement).
 */
final class Era1_18To1_19Adapter extends AbstractVersionAdapter {

    Era1_18To1_19Adapter(
            Logger logger
    ) {
        super(logger);
    }

    @Override
    public String getLabel() {
        return "1.18.x - 1.19.x";
    }
}
