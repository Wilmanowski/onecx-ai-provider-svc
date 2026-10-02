package org.tkit.onecx.ai.provider.common.services.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.tkit.onecx.ai.provider.domain.models.enums.ExecutionPolicy;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolPermission;

class PolicyNormalizerTest {

    @Test
    void constructor_isAccessibleByReflection() throws Exception {
        Constructor<PolicyNormalizer> constructor = PolicyNormalizer.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatNoException().isThrownBy(() -> constructor.newInstance());
    }

    @ParameterizedTest
    @CsvSource({
            "DENY, DENY",
            "ALWAYS_ASK, ALWAYS_ASK",
            "ALWAYS_ALLOW, ALWAYS_ALLOW",
            "ALLOW, ALWAYS_ALLOW",
            "NEVER_ASK, ALWAYS_ALLOW"
    })
    void toolPermission_canonicalizes(ToolPermission source, ToolPermission expected) {
        assertThat(PolicyNormalizer.toolPermission(source)).isEqualTo(expected);
    }

    @Test
    void toolPermission_null_defaultsToSafeAsk() {
        assertThat(PolicyNormalizer.toolPermission(null)).isEqualTo(ToolPermission.ALWAYS_ASK);
    }

    @ParameterizedTest
    @CsvSource({
            "ALWAYS_ASK, ALWAYS_ASK",
            "ALWAYS_ALLOW, ALWAYS_ALLOW",
            "ALLOW, ALWAYS_ALLOW",
            "NEVER_ASK, ALWAYS_ALLOW"
    })
    void executionPolicy_canonicalizes(ExecutionPolicy source, ExecutionPolicy expected) {
        assertThat(PolicyNormalizer.executionPolicy(source)).isEqualTo(expected);
    }

    @Test
    void executionPolicy_null_defaultsToSafeAsk() {
        assertThat(PolicyNormalizer.executionPolicy(null)).isEqualTo(ExecutionPolicy.ALWAYS_ASK);
    }
}
