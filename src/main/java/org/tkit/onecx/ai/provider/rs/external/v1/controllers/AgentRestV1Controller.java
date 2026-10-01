package org.tkit.onecx.ai.provider.rs.external.v1.controllers;

import static jakarta.transaction.Transactional.TxType.NOT_SUPPORTED;

import java.util.Arrays;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.tkit.onecx.ai.provider.common.services.version.AgentConfigurationVersionService;
import org.tkit.onecx.ai.provider.common.services.version.VersionGenerationException;
import org.tkit.onecx.ai.provider.domain.daos.AgentDAO;
import org.tkit.onecx.ai.provider.rs.external.v1.mappers.AgentMapper;
import org.tkit.onecx.ai.provider.rs.external.v1.mappers.ExceptionMapper;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.AgentV1Api;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentSearchCriteriaDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ProblemDetailParamDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ProblemDetailResponseDTOV1;

@ApplicationScoped
@Transactional(value = NOT_SUPPORTED)
public class AgentRestV1Controller implements AgentV1Api {

    @Inject
    AgentDAO dao;

    @Inject
    ExceptionMapper exceptionMapper;

    @Inject
    AgentMapper mapper;

    @Inject
    AgentConfigurationVersionService versionService;

    @Override
    public Response findAgentBySearchCriteria(AgentSearchCriteriaDTOV1 agentSearchCriteriaDTO) {
        var criteria = mapper.mapCriteria(agentSearchCriteriaDTO);
        var result = dao.findAgentsByCriteria(criteria);
        return Response.ok(mapper.mapPage(result)).build();
    }

    @Override
    public Response getAgentConfigurationVersion(String id, String ifNoneMatch) {
        var versionPayload = versionService.getVersion(id);
        if (versionPayload.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        var version = versionPayload.get().getVersion();
        var tag = new EntityTag(version);
        var cacheControl = new CacheControl();
        cacheControl.setPrivate(true);
        cacheControl.setNoCache(true);
        if (matches(ifNoneMatch, version)) {
            return Response.notModified(tag).cacheControl(cacheControl).build();
        }
        return Response.ok(versionPayload.get()).tag(tag).cacheControl(cacheControl).build();
    }

    /**
     * Evaluates {@code If-None-Match} (RFC 9110): a list of (weak or strong) entity tags or {@code *}. Unquoted values
     * are accepted for clients that send the bare version.
     */
    static boolean matches(String ifNoneMatch, String version) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank() || version == null) {
            return false;
        }
        return Arrays.stream(ifNoneMatch.split(","))
                .map(String::trim)
                .map(v -> v.startsWith("W/") ? v.substring(2) : v)
                .map(v -> v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"") ? v.substring(1, v.length() - 1) : v)
                .anyMatch(v -> "*".equals(v) || version.equals(v));
    }

    @ServerExceptionMapper
    public RestResponse<ProblemDetailResponseDTOV1> versionGenerationException(VersionGenerationException ex) {
        var dto = new ProblemDetailResponseDTOV1();
        dto.setErrorCode(ex.getErrorKey().name());
        dto.setDetail(ex.getMessage());
        List<ProblemDetailParamDTOV1> params = ex.getParams().entrySet().stream().map(e -> {
            var param = new ProblemDetailParamDTOV1();
            param.setKey(e.getKey());
            param.setValue(e.getValue());
            return param;
        }).toList();
        dto.setParams(params);
        return RestResponse.status(Response.Status.CONFLICT, dto);
    }
}
