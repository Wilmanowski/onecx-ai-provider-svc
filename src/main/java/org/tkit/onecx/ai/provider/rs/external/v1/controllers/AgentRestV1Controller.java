package org.tkit.onecx.ai.provider.rs.external.v1.controllers;

import static jakarta.transaction.Transactional.TxType.NOT_SUPPORTED;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import org.tkit.onecx.ai.provider.common.services.agent.AgentConfigurationService;
import org.tkit.onecx.ai.provider.domain.daos.AgentDAO;
import org.tkit.onecx.ai.provider.rs.external.v1.mappers.AgentMapper;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.AgentV1Api;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentSearchCriteriaDTOV1;

@ApplicationScoped
@Transactional(value = NOT_SUPPORTED)
public class AgentRestV1Controller implements AgentV1Api {

    @Inject
    AgentDAO dao;

    @Inject
    AgentMapper mapper;

    @Inject
    AgentConfigurationService configurationService;

    @Context
    SecurityContext securityContext;

    @Override
    public Response findAgentBySearchCriteria(AgentSearchCriteriaDTOV1 agentSearchCriteriaDTO) {
        var criteria = mapper.mapCriteria(agentSearchCriteriaDTO);
        var result = dao.findAgentsByCriteria(criteria);
        return Response.ok(mapper.mapPage(result)).build();
    }

    @Override
    public Response getAgentConfiguration(String id) {
        return configurationService.getAgentConfiguration(id, securityContext.getUserPrincipal().getName());
    }
}
