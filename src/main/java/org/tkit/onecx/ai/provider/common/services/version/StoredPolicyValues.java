package org.tkit.onecx.ai.provider.common.services.version;

import java.util.HashMap;
import java.util.Map;

/**
 * Raw policy values as stored in the database, before the JPA converters normalize them. Used to report policy
 * provenance (legacy aliases, unknown values) in the snapshot. When no raw value is known for an id, the value of the
 * loaded entity is used instead.
 */
public final class StoredPolicyValues {

    private final Map<String, String> toolPermissionsByRuleId;

    private final Map<String, String> executionPoliciesByToolId;

    public StoredPolicyValues(Map<String, String> toolPermissionsByRuleId, Map<String, String> executionPoliciesByToolId) {
        this.toolPermissionsByRuleId = toolPermissionsByRuleId == null ? Map.of()
                : new HashMap<>(toolPermissionsByRuleId);
        this.executionPoliciesByToolId = executionPoliciesByToolId == null ? Map.of()
                : new HashMap<>(executionPoliciesByToolId);
    }

    public static StoredPolicyValues empty() {
        return new StoredPolicyValues(Map.of(), Map.of());
    }

    public boolean hasToolPermission(String ruleId) {
        return ruleId != null && toolPermissionsByRuleId.containsKey(ruleId);
    }

    public String toolPermission(String ruleId) {
        return toolPermissionsByRuleId.get(ruleId);
    }

    public boolean hasExecutionPolicy(String toolId) {
        return toolId != null && executionPoliciesByToolId.containsKey(toolId);
    }

    public String executionPolicy(String toolId) {
        return executionPoliciesByToolId.get(toolId);
    }
}
