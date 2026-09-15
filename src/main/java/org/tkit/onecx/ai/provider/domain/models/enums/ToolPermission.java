package org.tkit.onecx.ai.provider.domain.models.enums;

import java.util.Locale;

public enum ToolPermission {
    DENY,
    ALWAYS_ASK,
    ALWAYS_ALLOW,
    NEVER_ASK,
    ALLOW;

    public static final ToolPermission DEFAULT = ALWAYS_ASK;

    public ToolPermission toCanonical() {
        if (this == ALLOW || this == NEVER_ASK) {
            return ALWAYS_ALLOW;
        }
        return this;
    }

    public static ToolPermission fromValueOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }

        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "DENY" -> DENY;
            case "ALWAYS_ASK" -> ALWAYS_ASK;
            case "ALWAYS_ALLOW", "ALLOW", "NEVER_ASK" -> ALWAYS_ALLOW;
            default -> DENY;
        };
    }
}
