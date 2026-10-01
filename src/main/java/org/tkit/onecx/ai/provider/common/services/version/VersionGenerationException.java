package org.tkit.onecx.ai.provider.common.services.version;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;

/**
 * Thrown when the agent configuration is invalid and no deterministic version can be generated.
 */
@Getter
public class VersionGenerationException extends RuntimeException {

    public enum ErrorKeys {
        DUPLICATE_TOOL_RULE
    }

    private final ErrorKeys errorKey;

    private final transient Map<String, String> params;

    public VersionGenerationException(ErrorKeys errorKey, String message, Map<String, String> params) {
        super(message);
        this.errorKey = errorKey;
        this.params = params == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }
}
