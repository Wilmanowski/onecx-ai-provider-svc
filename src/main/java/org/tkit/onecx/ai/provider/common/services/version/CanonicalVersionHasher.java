package org.tkit.onecx.ai.provider.common.services.version;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Computes the content-addressed version of a configuration version payload.
 * <p>
 * Canonical form ({@value #CANONICALIZATION}): UTF-8 JSON without insignificant whitespace, object properties sorted by
 * name, {@code null} properties omitted, array order as produced by the version builder (which orders every list
 * canonically). The properties listed in {@link #EXCLUDED_PROPERTIES} are not part of the hashed content.
 */
public final class CanonicalVersionHasher {

    public static final String HASH_ALGORITHM = "SHA-256";

    public static final String CANONICALIZATION = "onecx-ai-canonical-json-v1";

    public static final String VERSION_PREFIX = "sha256:";

    /**
     * Envelope properties that are not part of the versioned content.
     */
    public static final Set<String> EXCLUDED_PROPERTIES = Set.of("version", "context");

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.INDENT_OUTPUT)
            .defaultPropertyInclusion(
                    JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .build();

    private static final TypeReference<TreeMap<String, Object>> TREE = new TypeReference<>() {
    };

    private CanonicalVersionHasher() {
    }

    /**
     * @return the canonical JSON bytes of the hashed version content
     */
    public static byte[] canonicalBytes(Object versionPayload) {
        Map<String, Object> content = MAPPER.convertValue(versionPayload, TREE);
        EXCLUDED_PROPERTIES.forEach(content::remove);
        try {
            return MAPPER.writeValueAsBytes(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Version payload cannot be serialized to canonical JSON", e);
        }
    }

    /**
     * @return content-addressed version {@code sha256:<hex>}
     */
    public static String version(Object versionPayload) {
        try {
            var digest = MessageDigest.getInstance(HASH_ALGORITHM).digest(canonicalBytes(versionPayload));
            return VERSION_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " not available", e);
        }
    }
}
