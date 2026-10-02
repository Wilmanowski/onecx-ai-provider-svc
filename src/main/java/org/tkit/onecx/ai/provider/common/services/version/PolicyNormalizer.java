package org.tkit.onecx.ai.provider.common.services.version;

import org.tkit.onecx.ai.provider.domain.models.enums.ExecutionPolicy;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolPermission;

/**
 * Canonicalizes tool policy enums loaded through JPA converters.
 */
public final class PolicyNormalizer {

    private PolicyNormalizer() {
    }

    public static ToolPermission toolPermission(ToolPermission value) {
        return (value == null ? ToolPermission.DEFAULT : value).toCanonical();
    }

    public static ExecutionPolicy executionPolicy(ExecutionPolicy value) {
        return (value == null ? ExecutionPolicy.DEFAULT : value).toCanonical();
    }
}
