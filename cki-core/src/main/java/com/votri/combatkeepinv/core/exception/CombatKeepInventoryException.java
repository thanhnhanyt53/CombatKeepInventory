package com.votri.combatkeepinv.core.exception;

public class CombatKeepInventoryException extends RuntimeException {

    public CombatKeepInventoryException(String message) {
        super(message);
    }

    public CombatKeepInventoryException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}