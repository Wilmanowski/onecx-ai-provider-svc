package org.tkit.onecx.ai.provider.common.services.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

class VersionGenerationExceptionTest {

    @Test
    void constructor_usesEmptyMap_whenParamsAreNull() {
        var ex = new VersionGenerationException(VersionGenerationException.ErrorKeys.DUPLICATE_TOOL_RULE,
                "duplicate rule", null);

        assertThat(ex.getErrorKey()).isEqualTo(VersionGenerationException.ErrorKeys.DUPLICATE_TOOL_RULE);
        assertThat(ex.getMessage()).isEqualTo("duplicate rule");
        assertThat(ex.getParams()).isEmpty();
    }

    @Test
    void constructor_copiesAndFreezesParams() {
        var params = new LinkedHashMap<String, String>();
        params.put("toolId", "tool-1");

        var ex = new VersionGenerationException(VersionGenerationException.ErrorKeys.DUPLICATE_TOOL_RULE,
                "duplicate rule", params);

        params.put("toolId", "mutated-value");

        assertThat(ex.getParams()).containsEntry("toolId", "tool-1");
        assertThatThrownBy(() -> ex.getParams().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
