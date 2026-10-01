package org.tkit.onecx.ai.provider.common.services.version;

import java.util.Arrays;
import java.util.Locale;

import org.tkit.onecx.ai.provider.domain.models.enums.ExecutionPolicy;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolPermission;

/**
 * Normalizes stored tool policies to the canonical snapshot vocabulary and records how the effective value was
 * derived (policy provenance).
 * <ul>
 * <li>canonical values are taken as they are ({@link Normalization#NONE})</li>
 * <li>legacy aliases ({@code ALLOW}, {@code NEVER_ASK}, non-canonical spelling) are mapped to their canonical value
 * ({@link Normalization#LEGACY_ALIAS})</li>
 * <li>missing values fall back to the safe default {@code ALWAYS_ASK}
 * ({@link Normalization#MISSING_VALUE_DEFAULTED})</li>
 * <li>unknown values are denied: tool permissions become {@code DENY}, execution policies become {@code ALWAYS_ASK}
 * so that no automatic execution is granted ({@link Normalization#UNKNOWN_VALUE_DENIED})</li>
 * </ul>
 */
public final class PolicyNormalizer {

    public enum Normalization {
        NONE,
        LEGACY_ALIAS,
        MISSING_VALUE_DEFAULTED,
        UNKNOWN_VALUE_DENIED
    }

    /**
     * Normalized policy value.
     *
     * @param effective canonical effective value
     * @param normalization how the effective value was derived
     * @param storedValue stored raw value, only set when the value was normalized from a legacy alias or unknown value
     */
    public record Result<T extends Enum<T>>(T effective, Normalization normalization, String storedValue) {
    }

    private PolicyNormalizer() {
    }

    public static Result<ToolPermission> toolPermission(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return new Result<>(ToolPermission.DEFAULT, Normalization.MISSING_VALUE_DEFAULTED, null);
        }
        if (!isKnown(ToolPermission.values(), storedValue)) {
            return new Result<>(ToolPermission.DENY, Normalization.UNKNOWN_VALUE_DENIED, storedValue);
        }
        var effective = ToolPermission.fromValueOrDefault(storedValue).toCanonical();
        return known(effective, storedValue);
    }

    public static Result<ToolPermission> toolPermission(ToolPermission value) {
        if (value == null) {
            return new Result<>(ToolPermission.DEFAULT, Normalization.MISSING_VALUE_DEFAULTED, null);
        }
        return known(value.toCanonical(), value.name());
    }

    public static Result<ExecutionPolicy> executionPolicy(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return new Result<>(ExecutionPolicy.DEFAULT, Normalization.MISSING_VALUE_DEFAULTED, null);
        }
        if (!isKnown(ExecutionPolicy.values(), storedValue)) {
            return new Result<>(ExecutionPolicy.ALWAYS_ASK, Normalization.UNKNOWN_VALUE_DENIED, storedValue);
        }
        var effective = ExecutionPolicy.fromValueOrDefault(storedValue).toCanonical();
        return known(effective, storedValue);
    }

    public static Result<ExecutionPolicy> executionPolicy(ExecutionPolicy value) {
        if (value == null) {
            return new Result<>(ExecutionPolicy.DEFAULT, Normalization.MISSING_VALUE_DEFAULTED, null);
        }
        return known(value.toCanonical(), value.name());
    }

    private static <T extends Enum<T>> Result<T> known(T effective, String storedValue) {
        if (effective.name().equals(storedValue)) {
            return new Result<>(effective, Normalization.NONE, null);
        }
        return new Result<>(effective, Normalization.LEGACY_ALIAS, storedValue);
    }

    private static boolean isKnown(Enum<?>[] values, String storedValue) {
        var normalized = storedValue.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values).anyMatch(v -> v.name().equals(normalized));
    }
}
