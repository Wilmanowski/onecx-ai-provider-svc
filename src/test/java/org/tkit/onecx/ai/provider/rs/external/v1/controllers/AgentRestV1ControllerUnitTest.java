package org.tkit.onecx.ai.provider.rs.external.v1.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Optional;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.tkit.onecx.ai.provider.common.services.version.AgentConfigurationVersionService;
import org.tkit.onecx.ai.provider.common.services.version.VersionGenerationException;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentConfigurationVersionDTOV1;

class AgentRestV1ControllerUnitTest {

    @Test
    void getAgentConfigurationVersion_returnsNotFound_whenVersionMissing() {
        var controller = new AgentRestV1Controller();
        var versionService = mock(AgentConfigurationVersionService.class);
        controller.versionService = versionService;

        when(versionService.getVersion("agent-1")).thenReturn(Optional.empty());

        var response = controller.getAgentConfigurationVersion("agent-1", null);

        assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    void getAgentConfigurationVersion_returnsNotModified_whenIfNoneMatchMatches() {
        var controller = new AgentRestV1Controller();
        var versionService = mock(AgentConfigurationVersionService.class);
        controller.versionService = versionService;

        var payload = new AgentConfigurationVersionDTOV1();
        payload.setVersion("sha256:abc");

        when(versionService.getVersion("agent-1")).thenReturn(Optional.of(payload));

        var response = controller.getAgentConfigurationVersion("agent-1", "\"sha256:abc\"");

        assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_MODIFIED.getStatusCode());
        assertThat(response.getHeaderString(HttpHeaders.ETAG)).isEqualTo("\"sha256:abc\"");
        assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL)).contains("no-cache", "private");
    }

    @Test
    void getAgentConfigurationVersion_returnsOkAndPayload_whenHeaderDoesNotMatch() {
        var controller = new AgentRestV1Controller();
        var versionService = mock(AgentConfigurationVersionService.class);
        controller.versionService = versionService;

        var payload = new AgentConfigurationVersionDTOV1();
        payload.setVersion("sha256:xyz");

        when(versionService.getVersion("agent-1")).thenReturn(Optional.of(payload));

        var response = controller.getAgentConfigurationVersion("agent-1", "\"sha256:other\"");

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isSameAs(payload);
        assertThat(response.getHeaderString(HttpHeaders.ETAG)).isEqualTo("\"sha256:xyz\"");
        assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL)).contains("no-cache", "private");
    }

    @Test
    void versionGenerationException_mapsConflictPayload() {
        var controller = new AgentRestV1Controller();
        var params = new LinkedHashMap<String, String>();
        params.put("toolId", "tool-1");
        params.put("toolName", "readItem");

        var ex = new VersionGenerationException(
                VersionGenerationException.ErrorKeys.DUPLICATE_TOOL_RULE,
                "duplicate",
                params);

        try (var response = controller.versionGenerationException(ex)) {
            assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
            assertThat(response.getEntity().getErrorCode()).isEqualTo("DUPLICATE_TOOL_RULE");
            assertThat(response.getEntity().getDetail()).isEqualTo("duplicate");
            assertThat(response.getEntity().getParams()).hasSize(2);
        }
    }
}
