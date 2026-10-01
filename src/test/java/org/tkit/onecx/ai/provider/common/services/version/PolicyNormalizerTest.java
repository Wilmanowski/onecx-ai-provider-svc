package org.tkit.onecx.ai.provider.common.services.version;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.tkit.onecx.ai.provider.common.services.version.PolicyNormalizer.Normalization;
import org.tkit.onecx.ai.provider.domain.models.enums.ExecutionPolicy;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolPermission;

class PolicyNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "DENY, DENY, NONE",
            "ALWAYS_ASK, ALWAYS_ASK, NONE",
            "ALWAYS_ALLOW, ALWAYS_ALLOW, NONE",
            "ALLOW, ALWAYS_ALLOW, LEGACY_ALIAS",
            "NEVER_ASK, ALWAYS_ALLOW, LEGACY_ALIAS",
            "always_allow, ALWAYS_ALLOW, LEGACY_ALIAS",
            "' deny ', DENY, LEGACY_ALIAS"
    })
    void toolPermission_knownValues(String stored, ToolPermission expected, Normalization normalization) {
        var result = PolicyNormalizer.toolPermission(stored);
        assertThat(result.effective()).isEqualTo(expected);
        assertThat(result.normalization()).isEqualTo(normalization);
        assertThat(result.storedValue()).isEqualTo(normalization == Normalization.NONE ? null : stored);
    }

    @ParameterizedTest
    @ValueSource(strings = { "YES", "ALLOW_ALL", "ALWAYS", "true", "*" })
    void toolPermission_unknownValue_isDenied(String stored) {
        var result = PolicyNormalizer.toolPermission(stored);
        assertThat(result.effective()).isEqualTo(ToolPermission.DENY);
        assertThat(result.normalization()).isEqualTo(Normalization.UNKNOWN_VALUE_DENIED);
        assertThat(result.storedValue()).isEqualTo(stored);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    void toolPermission_missingValue_defaultsToAsk(String stored) {
        var result = PolicyNormalizer.toolPermission(stored);
        assertThat(result.effective()).isEqualTo(ToolPermission.ALWAYS_ASK);
        assertThat(result.normalization()).isEqualTo(Normalization.MISSING_VALUE_DEFAULTED);
        assertThat(result.storedValue()).isNull();
    }

    @Test
    void toolPermission_fromEntityValue() {
        assertThat(PolicyNormalizer.toolPermission((ToolPermission) null))
                .isEqualTo(new PolicyNormalizer.Result<>(ToolPermission.ALWAYS_ASK, Normalization.MISSING_VALUE_DEFAULTED,
                        null));
        assertThat(PolicyNormalizer.toolPermission(ToolPermission.NEVER_ASK))
                .isEqualTo(new PolicyNormalizer.Result<>(ToolPermission.ALWAYS_ALLOW, Normalization.LEGACY_ALIAS,
                        "NEVER_ASK"));
        assertThat(PolicyNormalizer.toolPermission(ToolPermission.DENY))
                .isEqualTo(new PolicyNormalizer.Result<>(ToolPermission.DENY, Normalization.NONE, null));
    }

    @ParameterizedTest
    @CsvSource({
            "ALWAYS_ASK, ALWAYS_ASK, NONE",
            "ALWAYS_ALLOW, ALWAYS_ALLOW, NONE",
            "ALLOW, ALWAYS_ALLOW, LEGACY_ALIAS",
            "NEVER_ASK, ALWAYS_ALLOW, LEGACY_ALIAS",
            "always_ask, ALWAYS_ASK, LEGACY_ALIAS"
    })
    void executionPolicy_knownValues(String stored, ExecutionPolicy expected, Normalization normalization) {
        var result = PolicyNormalizer.executionPolicy(stored);
        assertThat(result.effective()).isEqualTo(expected);
        assertThat(result.normalization()).isEqualTo(normalization);
    }

    @ParameterizedTest
    @ValueSource(strings = { "AUTO", "DENY", "ALWAYS" })
    void executionPolicy_unknownValue_deniesAutomaticExecution(String stored) {
        var result = PolicyNormalizer.executionPolicy(stored);
        assertThat(result.effective()).isEqualTo(ExecutionPolicy.ALWAYS_ASK);
        assertThat(result.normalization()).isEqualTo(Normalization.UNKNOWN_VALUE_DENIED);
        assertThat(result.storedValue()).isEqualTo(stored);
    }

    @Test
    void executionPolicy_missingAndEntityValues() {
        assertThat(PolicyNormalizer.executionPolicy((String) null).normalization())
                .isEqualTo(Normalization.MISSING_VALUE_DEFAULTED);
        assertThat(PolicyNormalizer.executionPolicy((ExecutionPolicy) null))
                .isEqualTo(new PolicyNormalizer.Result<>(ExecutionPolicy.ALWAYS_ASK,
                        Normalization.MISSING_VALUE_DEFAULTED, null));
        assertThat(PolicyNormalizer.executionPolicy(ExecutionPolicy.ALLOW))
                .isEqualTo(new PolicyNormalizer.Result<>(ExecutionPolicy.ALWAYS_ALLOW, Normalization.LEGACY_ALIAS,
                        "ALLOW"));
        assertThat(PolicyNormalizer.executionPolicy(ExecutionPolicy.ALWAYS_ALLOW))
                .isEqualTo(new PolicyNormalizer.Result<>(ExecutionPolicy.ALWAYS_ALLOW, Normalization.NONE, null));
    }
}
