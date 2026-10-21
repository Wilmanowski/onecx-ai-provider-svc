package org.tkit.onecx.ai.provider.common.services.agent;

import static jakarta.transaction.Transactional.TxType.NOT_SUPPORTED;
import static jakarta.transaction.Transactional.TxType.REQUIRED;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

import org.tkit.onecx.ai.provider.domain.daos.AgentConfigurationVersionDAO;
import org.tkit.onecx.ai.provider.domain.daos.AgentDAO;
import org.tkit.onecx.ai.provider.domain.daos.AgentMcpToolRuleDAO;
import org.tkit.onecx.ai.provider.domain.models.Agent;
import org.tkit.onecx.ai.provider.domain.models.AgentConfigurationVersion;
import org.tkit.onecx.ai.provider.domain.models.AgentMcpToolRule;
import org.tkit.onecx.ai.provider.domain.models.GlobalTool;
import org.tkit.onecx.ai.provider.domain.models.Tool;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolType;
import org.tkit.onecx.ai.provider.rs.external.v1.mappers.AgentConfigurationMapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentConfigurationDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ProblemDetailResponseDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.AgentConfigurationVersionDTO;

@ApplicationScoped
@Transactional(value = NOT_SUPPORTED)
public class AgentConfigurationService {

    private static final String ERROR_DUPLICATE_TOOL_RULE = "DUPLICATE_AGENT_TOOL_RULE";

    @Inject
    AgentDAO dao;

    @Inject
    AgentMcpToolRuleDAO agentMcpToolRuleDAO;

    @Inject
    AgentConfigurationMapper agentConfigurationMapper;

    @Inject
    AgentConfigurationVersionDAO versionDAO;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AgentConfigurationDependencyValidator dependencyValidator;

    @Transactional(REQUIRED)
    public Response getAgentConfiguration(String id, String principal) {
        Agent agent = dao.findById(id);
        if (agent == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        dao.lockConfiguration(agent);
        if (agent.getActiveConfigurationVersionId() == null) {
            Response initializationError = null;
            try (var resolved = resolveConfiguration(agent, principal)) {
                if (resolved.getStatus() != Response.Status.OK.getStatusCode()) {
                    initializationError = Response.fromResponse(resolved).build();
                } else {
                    saveVersion(agent, "Initial configuration", (AgentConfigurationDTOV1) resolved.getEntity());
                }
            }
            if (initializationError != null) {
                return initializationError;
            }
        }
        return configuration(versionDAO.findByAgentAndId(id, agent.getActiveConfigurationVersionId()), principal);
    }

    @Transactional(REQUIRED)
    public Response createVersion(String id, String requestedName, String principal) {
        if (requestedName == null || requestedName.isBlank() || requestedName.length() > 255) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        var agent = dao.findById(id);
        if (agent == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        dao.lockConfiguration(agent);
        var name = requestedName.strip();
        if (versionDAO.existsByAgentAndName(id, name)) {
            var problem = new ProblemDetailResponseDTOV1();
            problem.setErrorCode("DUPLICATE_AGENT_CONFIGURATION_VERSION_NAME");
            problem.setDetail("Agent already has a configuration version named '" + name + "'");
            return Response.status(Response.Status.BAD_REQUEST).entity(problem).build();
        }
        Response response;
        try (var resolved = resolveConfiguration(agent, principal)) {
            if (resolved.getStatus() != Response.Status.OK.getStatusCode()) {
                response = Response.fromResponse(resolved).build();
            } else {
                var version = saveVersion(agent, name, (AgentConfigurationDTOV1) resolved.getEntity());
                var metadata = new AgentConfigurationVersionDTO();
                metadata.setId(version.getId());
                metadata.setName(version.getName());
                metadata.setActive(version.getId().equals(agent.getActiveConfigurationVersionId()));
                response = Response.created(versionLocation(version))
                        .entity(metadata).build();
            }
        }
        return response;
    }

    public Response getAgentConfigurationVersion(String id, String versionId, String principal) {
        if (dao.findById(id) == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return configuration(versionDAO.findByAgentAndId(id, versionId), principal);
    }

    @Transactional(REQUIRED)
    public Response activateAgentConfigurationVersion(String id, String versionId) {
        var agent = dao.findById(id);
        if (agent == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        dao.lockConfiguration(agent);
        var version = versionDAO.findByAgentAndId(id, versionId);
        if (version == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        var errors = dependencyValidator.validate(readConfiguration(version));
        if (!errors.isEmpty()) {
            return invalidDependencies(errors);
        }
        agent.setActiveConfigurationVersionId(version.getId());
        return Response.ok(versionMetadata(agent, version)).build();
    }

    private AgentConfigurationVersion saveVersion(Agent agent, String name, AgentConfigurationDTOV1 payload) {
        try {
            if (payload != null && payload.getContext() != null) {
                payload.getContext().setPrincipal(null);
            }
            var version = new AgentConfigurationVersion(agent, name, objectMapper.writeValueAsString(payload));
            versionDAO.create(version);
            if (agent.getActiveConfigurationVersionId() == null) {
                agent.setActiveConfigurationVersionId(version.getId());
            }
            return version;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize agent configuration", ex);
        }
    }

    private Response configuration(AgentConfigurationVersion version, String principal) {
        if (version == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        var payload = readConfiguration(version);
        if (payload != null && payload.getContext() != null) {
            payload.getContext().setPrincipal(principal);
        }
        return Response.ok(payload).contentLocation(versionLocation(version)).build();
    }

    private AgentConfigurationDTOV1 readConfiguration(AgentConfigurationVersion version) {
        try {
            return objectMapper.readValue(version.getConfiguration(), AgentConfigurationDTOV1.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot deserialize agent configuration version " + version.getId(), ex);
        }
    }

    private URI versionLocation(AgentConfigurationVersion version) {
        return UriBuilder.fromPath("/internal/agents/{id}/configuration/versions/{versionId}")
                .build(version.getAgentId(), version.getId());
    }

    private AgentConfigurationVersionDTO versionMetadata(Agent agent, AgentConfigurationVersion version) {
        var result = new AgentConfigurationVersionDTO();
        result.setId(version.getId());
        result.setName(version.getName());
        result.setActive(version.getId().equals(agent.getActiveConfigurationVersionId()));
        return result;
    }

    private Response resolveConfiguration(Agent agent, String principal) {
        var rules = loadRules(agent);
        var duplicateToolRuleName = duplicateToolRuleName(rules);
        if (duplicateToolRuleName != null) {
            var problem = new ProblemDetailResponseDTOV1();
            problem.setErrorCode(ERROR_DUPLICATE_TOOL_RULE);
            problem.setDetail("Agent has duplicate MCP tool rules for tool '" + duplicateToolRuleName + "'");
            return Response.status(Response.Status.BAD_REQUEST).entity(problem).build();
        }

        var payload = agentConfigurationMapper.build(agent, rules, agent.getTenantId(), principal);
        if (agent.getActiveConfigurationVersionId() == null) {
            var errors = dependencyValidator.validate(payload);
            if (!errors.isEmpty()) {
                return invalidDependencies(errors);
            }
        }
        return Response.ok(payload).build();
    }

    private Response invalidDependencies(List<String> errors) {
        var problem = new ProblemDetailResponseDTOV1();
        problem.setErrorCode("INVALID_AGENT_CONFIGURATION_DEPENDENCIES");
        problem.setDetail(String.join("; ", errors));
        return Response.status(Response.Status.BAD_REQUEST).entity(problem).build();
    }

    private List<AgentMcpToolRule> loadRules(Agent agent) {
        var rules = new ArrayList<AgentMcpToolRule>();
        var toolIds = agent.getTools().stream()
                .filter(tool -> tool.getType() == ToolType.MCP)
                .map(Tool::getId)
                .toList();
        if (!toolIds.isEmpty()) {
            rules.addAll(agentMcpToolRuleDAO.findByAgentAndToolIds(agent.getId(), toolIds));
        }

        var globalToolIds = agent.getGlobalTools().stream()
                .filter(tool -> tool.getType() == ToolType.MCP)
                .map(GlobalTool::getId)
                .toList();
        if (!globalToolIds.isEmpty()) {
            rules.addAll(agentMcpToolRuleDAO.findByAgentAndGlobalToolIds(agent.getId(), globalToolIds));
        }
        return rules;
    }

    private String duplicateToolRuleName(List<AgentMcpToolRule> rules) {
        var seen = new HashSet<String>();
        for (var rule : rules) {
            var key = ruleServerKey(rule) + ' ' + rule.getToolName();
            if (!seen.add(key)) {
                return rule.getToolName();
            }
        }
        return null;
    }

    private String ruleServerKey(AgentMcpToolRule rule) {
        if (rule.getTool() != null) {
            return "tool:" + rule.getTool().getId();
        }
        return "globalTool:" + rule.getGlobalTool().getId();
    }

}
