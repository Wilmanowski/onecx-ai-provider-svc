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
import org.tkit.onecx.ai.provider.common.services.version.CanonicalVersionHasher;
import org.tkit.onecx.ai.provider.common.services.version.VersionGenerationException;
import org.tkit.onecx.ai.provider.domain.criteria.AgentSearchCriteria;
import org.tkit.onecx.ai.provider.domain.daos.AgentDAO;
import org.tkit.onecx.ai.provider.domain.models.Agent;
import org.tkit.onecx.ai.provider.rs.external.v1.mappers.AgentMapper;
import org.tkit.quarkus.jpa.daos.PageResult;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentConfigurationVersionDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentPageResultDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentSearchCriteriaDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionContextDTOV1;

class AgentRestV1ControllerUnitTest {

    @Test
    void findAgentBySearchCriteria_returnsMappedPage() {
        var controller = new AgentRestV1Controller();
        var dao = mock(AgentDAO.class);
        var mapper = mock(AgentMapper.class);
        controller.dao = dao;
        controller.mapper = mapper;

        var request = new AgentSearchCriteriaDTOV1();
        var criteria = new AgentSearchCriteria();
        @SuppressWarnings("unchecked")
        var pageResult = (PageResult<Agent>) mock(PageResult.class);
        var mappedPage = new AgentPageResultDTOV1();

        when(mapper.mapCriteria(request)).thenReturn(criteria);
        when(dao.findAgentsByCriteria(criteria)).thenReturn(pageResult);
        when(mapper.mapPage(pageResult)).thenReturn(mappedPage);

        var response = controller.findAgentBySearchCriteria(request);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isSameAs(mappedPage);
    }

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
        var validator = CanonicalVersionHasher.validator(payload);

        when(versionService.getVersion("agent-1")).thenReturn(Optional.of(payload));

        var response = controller.getAgentConfigurationVersion("agent-1", "\"" + validator + "\"");

        assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_MODIFIED.getStatusCode());
        assertThat(response.getHeaderString(HttpHeaders.ETAG)).isEqualTo("\"" + validator + "\"");
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
        var validator = CanonicalVersionHasher.validator(payload);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isSameAs(payload);
        assertThat(response.getHeaderString(HttpHeaders.ETAG)).isEqualTo("\"" + validator + "\"");
        assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL)).contains("no-cache", "private");
    }

    @Test
    void getAgentConfigurationVersion_doesNotReturnNotModified_forDifferentPrincipalWithSameVersion() {
        var controller = new AgentRestV1Controller();
        var versionService = mock(AgentConfigurationVersionService.class);
        controller.versionService = versionService;

        var alicePayload = payload("sha256:same", "alice");
        var bobPayload = payload("sha256:same", "bob");
        var aliceValidator = CanonicalVersionHasher.validator(alicePayload);
        var bobValidator = CanonicalVersionHasher.validator(bobPayload);

        when(versionService.getVersion("agent-1")).thenReturn(Optional.of(bobPayload));

        var response = controller.getAgentConfigurationVersion("agent-1", "\"" + aliceValidator + "\"");

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isSameAs(bobPayload);
        assertThat(response.getHeaderString(HttpHeaders.ETAG)).isEqualTo("\"" + bobValidator + "\"");
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

    private static AgentConfigurationVersionDTOV1 payload(String version, String principal) {
        var payload = new AgentConfigurationVersionDTOV1();
        payload.setVersion(version);
        var context = new VersionContextDTOV1();
        context.setPrincipal(principal);
        payload.setContext(context);
        return payload;
    }
}
