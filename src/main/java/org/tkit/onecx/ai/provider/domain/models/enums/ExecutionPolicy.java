package org.tkit.onecx.ai.provider.domain.models.enums;

import java.util.Locale;

public enum ExecutionPolicy {
    ALWAYS_ASK,
    ALWAYS_ALLOW,
    NEVER_ASK,
    ALLOW;

    public static final ExecutionPolicy DEFAULT = ALWAYS_ASK;

    public ExecutionPolicy toCanonical() {
        if (this == NEVER_ASK || this == ALLOW) {
            return ALWAYS_ALLOW;
        }
        return this;
    }

    public static ExecutionPolicy fromValueOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }

        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "ALWAYS_ASK" -> ALWAYS_ASK;
            case "ALWAYS_ALLOW", "NEVER_ASK", "ALLOW" -> ALWAYS_ALLOW;
            default -> DEFAULT;
        };
    }
}
