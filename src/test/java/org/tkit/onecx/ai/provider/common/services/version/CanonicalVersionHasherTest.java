package org.tkit.onecx.ai.provider.common.services.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.node.POJONode;

class CanonicalVersionHasherTest {

    @Test
    void constructor_isAccessibleByReflection() throws Exception {
        Constructor<CanonicalVersionHasher> constructor = CanonicalVersionHasher.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatNoException().isThrownBy(() -> constructor.newInstance());
    }

    @Test
    void canonicalBytes_sortsPropertiesAndOmitsNulls() {
        var payload = payload();

        assertThat(new String(CanonicalVersionHasher.canonicalBytes(payload), StandardCharsets.UTF_8))
                .isEqualTo("{\"agent\":{\"enabled\":true,\"name\":\"agent\"},\"empty\":[]}");
    }

    @Test
    void canonicalBytes_excludesVersionAndContext() {
        var payload = payload();

        var canonical = new String(CanonicalVersionHasher.canonicalBytes(payload), StandardCharsets.UTF_8);

        assertThat(canonical).doesNotContain("version", "context");
    }

    @Test
    void canonicalBytes_isIndependentOfInsertionOrder() {
        var ordered = new LinkedHashMap<String, Object>();
        ordered.put("agent", Map.of("name", "agent", "enabled", true));
        ordered.put("empty", List.of());

        var reversed = new LinkedHashMap<String, Object>();
        reversed.put("empty", List.of());
        reversed.put("agent", Map.of("enabled", true, "name", "agent"));

        assertThat(CanonicalVersionHasher.canonicalBytes(reversed))
                .isEqualTo(CanonicalVersionHasher.canonicalBytes(ordered));
    }

    @Test
    void canonicalBytes_unserializablePayload_failsOnConversion() {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("agent", new POJONode(new Object()));

        // the same serialization pass runs twice: the embedded object already fails while the payload is converted
        // into the canonical tree, so it never reaches the canonical byte writer
        assertThatThrownBy(() -> CanonicalVersionHasher.canonicalBytes(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No serializer found for class java.lang.Object")
                .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        assertThatThrownBy(() -> CanonicalVersionHasher.version(payload))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void version_isContentAddressedAndPrefixed() {
        var version = CanonicalVersionHasher.version(payload());

        assertThat(version).startsWith(CanonicalVersionHasher.VERSION_PREFIX);
        assertThat(version.substring(CanonicalVersionHasher.VERSION_PREFIX.length()))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void version_isStableForTheSameContent() {
        assertThat(CanonicalVersionHasher.version(payload())).isEqualTo(CanonicalVersionHasher.version(payload()));
    }

    @Test
    void version_ignoresExcludedEnvelopeProperties() {
        var pinned = payload();
        pinned.put("version", "sha256:ignored");
        pinned.put("context", Map.of("tenantId", "other"));

        assertThat(CanonicalVersionHasher.version(pinned)).isEqualTo(CanonicalVersionHasher.version(payload()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "sha256:a", "sha256:b", "sha256:c" })
    void version_differsForDifferentContent(String agentName) {
        assertThat(CanonicalVersionHasher.version(payload())).isNotEqualTo(versionForAgent(agentName));
    }

    private static String versionForAgent(String agentName) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("agent", Map.of("name", agentName, "enabled", true));
        payload.put("empty", List.of());
        return CanonicalVersionHasher.version(payload);
    }

    private static LinkedHashMap<String, Object> payload() {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("context", Map.of("tenantId", "default"));
        payload.put("version", "sha256:previous");
        payload.put("agent", Map.of("name", "agent", "enabled", true));
        payload.put("empty", List.of());
        return payload;
    }
}
