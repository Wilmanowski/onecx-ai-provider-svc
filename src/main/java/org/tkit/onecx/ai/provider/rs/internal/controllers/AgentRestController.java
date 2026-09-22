package org.tkit.onecx.ai.provider.rs.internal.controllers;

import static jakarta.transaction.Transactional.TxType.NOT_SUPPORTED;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.tkit.onecx.ai.provider.common.services.agent.AgentService;
import org.tkit.onecx.ai.provider.config.AiProviderConfig;
import org.tkit.onecx.ai.provider.domain.daos.AgentDAO;
import org.tkit.onecx.ai.provider.domain.daos.AgentGroupDAO;
import org.tkit.onecx.ai.provider.domain.daos.AgentMcpToolRuleDAO;
import org.tkit.onecx.ai.provider.domain.daos.GlobalToolDAO;
import org.tkit.onecx.ai.provider.domain.daos.ModelDAO;
import org.tkit.onecx.ai.provider.domain.daos.ScaffoldDAO;
import org.tkit.onecx.ai.provider.domain.daos.ToolDAO;
import org.tkit.onecx.ai.provider.domain.models.Agent;
import org.tkit.onecx.ai.provider.domain.models.AgentGroup;
import org.tkit.onecx.ai.provider.domain.models.AgentMcpToolRule;
import org.tkit.onecx.ai.provider.domain.models.GlobalTool;
import org.tkit.onecx.ai.provider.domain.models.Model;
import org.tkit.onecx.ai.provider.domain.models.Scaffold;
import org.tkit.onecx.ai.provider.domain.models.Tool;
import org.tkit.onecx.ai.provider.rs.internal.mappers.AgentGroupMapper;
import org.tkit.onecx.ai.provider.rs.internal.mappers.AgentMapper;
import org.tkit.onecx.ai.provider.rs.internal.mappers.AgentMcpToolRuleMapper;
import org.tkit.onecx.ai.provider.rs.internal.mappers.ExceptionMapper;
import org.tkit.onecx.ai.provider.rs.internal.mappers.ModelMapper;
import org.tkit.onecx.ai.provider.rs.internal.mappers.ScaffoldMapper;
import org.tkit.onecx.ai.provider.rs.internal.mappers.ToolMapper;

import gen.org.tkit.onecx.ai.provider.rs.internal.AgentInternalApi;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.AgentMcpToolRuleListDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.AgentSearchCriteriaDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.CreateAgentMcpToolRuleRequestDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.CreateAgentRequestDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.ProblemDetailInvalidParamDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.ProblemDetailResponseDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.ToolDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.UpdateAgentMcpToolRuleRequestDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.UpdateAgentRequestDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@Transactional(value = NOT_SUPPORTED)
public class AgentRestController implements AgentInternalApi {

    @Inject
    AgentDAO dao;

    @Inject
    ToolDAO toolDAO;

    @Inject
    ModelDAO modelDAO;

    @Inject
    ScaffoldDAO scaffoldDAO;

    @Inject
    AgentGroupDAO agentGroupDAO;

    @Inject
    AgentMcpToolRuleDAO agentMcpToolRuleDAO;

    @Inject
    GlobalToolDAO globalToolDAO;

    @Inject
    ExceptionMapper exceptionMapper;

    @Inject
    AgentMapper mapper;

    @Inject
    ToolMapper toolMapper;

    @Inject
    ModelMapper modelMapper;

    @Inject
    ScaffoldMapper scaffoldMapper;

    @Inject
    AgentGroupMapper agentGroupMapper;

    @Inject
    AgentMcpToolRuleMapper agentRuleMapper;

    @Inject
    AgentService agentService;

    @Inject
    AiProviderConfig config;

    @Override
    public Response createAgent(CreateAgentRequestDTO createAgentRequestDTO) {
        var agent = mapper.mapCreate(createAgentRequestDTO);
        normalizeLanguageCode(agent);
        var validationError = validateVoicePilot(agent.getVoiceEnabled(), agent.getLanguageCode());
        if (validationError != null) {
            return validationError;
        }

        var context = agentService.createAgent(agent);
        return Response.status(Response.Status.CREATED).entity(mapper.map(context)).build();
    }

    @Override
    public Response deleteAgent(String id) {
        dao.deleteQueryById(id);
        return Response.noContent().build();
    }

    @Override
    public Response findAgentBySearchCriteria(AgentSearchCriteriaDTO agentSearchCriteriaDTO) {
        var criteria = mapper.mapCriteria(agentSearchCriteriaDTO);
        var result = dao.findAgentsByCriteria(criteria);
        return Response.ok(mapper.mapPage(result)).build();
    }

    @Override
    public Response getAgent(String id) {
        var item = dao.findById(id);
        if (item == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(mapper.map(item)).build();
    }

    @Override
    public Response updateAgent(String id, UpdateAgentRequestDTO updateAgentRequestDTO) {
        var item = dao.findById(id);
        if (item == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Resolve tools
        var toolsToAdd = resolveTools(updateAgentRequestDTO.getTools());

        // Resolve model
        Model model = null;
        if (updateAgentRequestDTO.getModel() != null) {
            var modelId = updateAgentRequestDTO.getModel().getId();
            model = modelDAO.findById(modelId);
            if (model == null) {
                model = modelDAO.create(modelMapper.map(updateAgentRequestDTO.getModel()));
            }
        }

        // Resolve scaffold
        Scaffold scaffold = null;
        if (updateAgentRequestDTO.getScaffold() != null) {
            var scaffoldId = updateAgentRequestDTO.getScaffold().getId();
            scaffold = scaffoldDAO.findById(scaffoldId);
            if (scaffold == null) {
                scaffold = scaffoldDAO.create(scaffoldMapper.map(updateAgentRequestDTO.getScaffold()));
            }
        }

        // Resolve groups
        var groupsToAdd = new HashSet<AgentGroup>();
        if (updateAgentRequestDTO.getGroups() != null && !updateAgentRequestDTO.getGroups().isEmpty()) {
            updateAgentRequestDTO.getGroups().forEach(groupDto -> {
                var existing = agentGroupDAO.findById(groupDto.getId());
                if (existing == null) {
                    existing = agentGroupDAO.create(agentGroupMapper.map(groupDto));
                }
                groupsToAdd.add(existing);
            });
        }

        mapper.mapUpdate(item, updateAgentRequestDTO, toolsToAdd, model, scaffold, groupsToAdd);
        normalizeLanguageCode(item);
        var validationError = validateVoicePilot(item.getVoiceEnabled(), item.getLanguageCode());
        if (validationError != null) {
            return validationError;
        }

        item = dao.update(item);
        return Response.status(Response.Status.OK).entity(mapper.map(item)).build();
    }

    @Override
    public Response getAgentMcpToolRules(String agentId, String toolId) {
        var agent = dao.findById(agentId);
        if (agent == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        var rules = agentMcpToolRuleDAO.findByAgentAndToolId(agentId, toolId);
        var result = new AgentMcpToolRuleListDTO();
        result.setRules(agentRuleMapper.map(rules));
        return Response.ok(result).build();
    }

    @Override
    public Response createAgentMcpToolRule(String agentId, String toolId,
            CreateAgentMcpToolRuleRequestDTO createAgentMcpToolRuleRequestDTO) {
        var agent = dao.findById(agentId);
        if (agent == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Tool tool = toolDAO.findById(toolId);
        GlobalTool globalTool = null;
        if (tool == null) {
            globalTool = globalToolDAO.findById(toolId);
            if (globalTool == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        }
        var rule = agentRuleMapper.create(createAgentMcpToolRuleRequestDTO, agent, tool, globalTool);
        rule = agentMcpToolRuleDAO.create(rule);
        return Response.status(Response.Status.CREATED).entity(agentRuleMapper.map(rule)).build();
    }

    @Override
    public Response updateAgentMcpToolRule(String agentId, String toolId, String ruleId,
            UpdateAgentMcpToolRuleRequestDTO updateAgentMcpToolRuleRequestDTO) {
        var rule = agentMcpToolRuleDAO.findById(ruleId);
        if (rule == null || !agentRuleBelongsToAgentAndTool(rule, agentId, toolId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        agentRuleMapper.update(rule, updateAgentMcpToolRuleRequestDTO);
        rule = agentMcpToolRuleDAO.update(rule);
        return Response.ok(agentRuleMapper.map(rule)).build();
    }

    @Override
    public Response deleteAgentMcpToolRule(String agentId, String toolId, String ruleId) {
        var rule = agentMcpToolRuleDAO.findById(ruleId);
        if (rule == null || !agentRuleBelongsToAgentAndTool(rule, agentId, toolId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        agentMcpToolRuleDAO.deleteQueryById(ruleId);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    private HashSet<Tool> resolveTools(List<ToolDTO> tools) {
        var toolsToAdd = new HashSet<Tool>();
        if (tools == null) {
            return toolsToAdd;
        }
        tools.forEach(tool -> {
            var existing = toolDAO.findById(tool.getId());
            if (existing == null) {
                existing = toolDAO.create(toolMapper.map(tool));
            }
            toolsToAdd.add(existing);
        });
        return toolsToAdd;
    }

    private boolean agentRuleBelongsToAgentAndTool(AgentMcpToolRule rule, String agentId, String toolId) {
        if (rule.getAgent() == null || !agentId.equals(rule.getAgent().getId())) {
            return false;
        }
        if (rule.getTool() != null && toolId.equals(rule.getTool().getId())) {
            return true;
        }
        return rule.getGlobalTool() != null && toolId.equals(rule.getGlobalTool().getId());
    }

    private void normalizeLanguageCode(Agent agent) {
        if (agent == null || agent.getLanguageCode() == null) {
            return;
        }

        var normalized = agent.getLanguageCode().trim().toLowerCase(Locale.ROOT);
        agent.setLanguageCode(normalized.isEmpty() ? null : normalized);
    }

    private Response validateVoicePilot(Boolean voiceEnabled, String languageCode) {
        if (!Boolean.TRUE.equals(voiceEnabled)) {
            return null;
        }

        if (languageCode == null || languageCode.isBlank()) {
            return voicePilotBadRequest("languageCode",
                    "Enabling voice requires a supported pilot language code.");
        }

        var normalizedLanguageCode = languageCode.trim().toLowerCase(Locale.ROOT);
        if (!supportedLanguageCodes().contains(normalizedLanguageCode)) {
            return voicePilotBadRequest("languageCode",
                    "Language code '" + languageCode + "' is not supported by the voice pilot.");
        }

        return null;
    }

    private Set<String> supportedLanguageCodes() {
        var configured = config.voicePilot().supportedLanguageCodes();
        if (configured == null || configured.isEmpty()) {
            return Set.of();
        }

        return configured.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private Response voicePilotBadRequest(String fieldName, String message) {
        var response = exceptionMapper.exception("INVALID_VOICE_PILOT_CONFIGURATION", message);
        var invalidParam = new ProblemDetailInvalidParamDTO();
        invalidParam.setName(fieldName);
        invalidParam.setMessage(message);
        response.setInvalidParams(List.of(invalidParam));
        return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
    }

    @ServerExceptionMapper
    public RestResponse<ProblemDetailResponseDTO> constraint(ConstraintViolationException ex) {
        return exceptionMapper.constraint(ex);
    }

    @ServerExceptionMapper
    public RestResponse<ProblemDetailResponseDTO> optimisticLockException(OptimisticLockException ex) {
        return exceptionMapper.optimisticLock(ex);
    }
}
