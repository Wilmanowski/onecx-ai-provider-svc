package org.tkit.onecx.ai.provider.rs.external.v1.mappers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.mapstruct.Mapper;
import org.tkit.onecx.ai.provider.domain.daos.AgentMcpToolRuleDAO;
import org.tkit.onecx.ai.provider.domain.models.AbstractTool;
import org.tkit.onecx.ai.provider.domain.models.Agent;
import org.tkit.onecx.ai.provider.domain.models.AgentGroup;
import org.tkit.onecx.ai.provider.domain.models.AgentMcpToolRule;
import org.tkit.onecx.ai.provider.domain.models.ExternalAgent;
import org.tkit.onecx.ai.provider.domain.models.GlobalScaffold;
import org.tkit.onecx.ai.provider.domain.models.GlobalSkill;
import org.tkit.onecx.ai.provider.domain.models.GlobalTool;
import org.tkit.onecx.ai.provider.domain.models.Model;
import org.tkit.onecx.ai.provider.domain.models.Provider;
import org.tkit.onecx.ai.provider.domain.models.Scaffold;
import org.tkit.onecx.ai.provider.domain.models.Skill;
import org.tkit.onecx.ai.provider.domain.models.Tool;
import org.tkit.onecx.ai.provider.domain.models.enums.ExecutionPolicy;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolPermission;
import org.tkit.quarkus.rs.mappers.OffsetDateTimeMapper;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ChatMessageDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ChatRequestDTOV1;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.AgentGroupSnapshot;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.AgentSnapshot;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.ChatMessage;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.ChatRequest;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.Conversation;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.ExternalAgentSnapshot;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.ModelSnapshot;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.ProviderSnapshot;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.RequestContext;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.RuntimeChatRequest;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.ScaffoldSnapshot;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.SkillSnapshot;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.ToolRuleSnapshot;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.ToolSnapshot;

@Mapper(uses = OffsetDateTimeMapper.class)
public abstract class RuntimeSnapshotMapper {

    @Inject
    AgentMcpToolRuleDAO agentMcpToolRuleDAO;

    public RuntimeChatRequest toRuntimeRequest(Agent agent, ChatRequestDTOV1 chatRequestDTO,
            List<AgentGroupSnapshot> groups) {
        var request = new RuntimeChatRequest();
        request.setChatRequest(mapChatRequest(chatRequestDTO));
        request.setRootAgent(mapAgent(agent, groups));
        return request;
    }

    public AgentSnapshot mapAgent(Agent agent) {
        return mapAgent(agent, List.of());
    }

    public AgentSnapshot mapAgent(Agent agent, List<AgentGroupSnapshot> groups) {
        if (agent == null) {
            return null;
        }
        var snapshot = new AgentSnapshot();
        snapshot.setName(agent.getName());
        snapshot.setDescription(agent.getDescription());
        snapshot.setAdditionalPrompt(agent.getAdditionalPrompt());
        snapshot.setA2aEnabled(agent.getA2aEnabled());
        snapshot.setModel(mapModel(agent.getModel()));
        snapshot.setScaffold(agent.getGlobalScaffold() != null ? mapGlobalScaffold(agent.getGlobalScaffold())
                : mapScaffold(agent.getScaffold()));
        snapshot.setTools(mapTools(agent));
        snapshot.setGroups(groups);
        return snapshot;
    }

    public AgentGroupSnapshot mapGroup(AgentGroup group, List<AgentSnapshot> agents,
            List<ExternalAgentSnapshot> externalAgents) {
        if (group == null) {
            return null;
        }
        var snapshot = new AgentGroupSnapshot();
        snapshot.setName(group.getName());
        snapshot.setDescription(group.getDescription());
        snapshot.setRoutingInstructions(group.getRoutingInstructions());
        snapshot.setOrchestrationMode(group.getOrchestrationMode() != null ? group.getOrchestrationMode().name() : null);
        snapshot.setResponseStrategy(group.getResponseStrategy() != null ? group.getResponseStrategy().name() : null);
        snapshot.setAgents(agents != null ? agents : List.of());
        snapshot.setExternalAgents(externalAgents != null ? externalAgents : List.of());
        return snapshot;
    }

    public ExternalAgentSnapshot mapExternalAgent(ExternalAgent agent) {
        if (agent == null) {
            return null;
        }
        var snapshot = new ExternalAgentSnapshot();
        snapshot.setName(agent.getName());
        snapshot.setDescription(agent.getDescription());
        snapshot.setDiscoveryUrl(agent.getDiscoveryUrl());
        snapshot.setApiKey(agent.getApiKey());
        snapshot.setAuthMode(agent.getAuthMode() != null ? agent.getAuthMode().name() : null);
        snapshot.setEnabled(agent.getEnabled());
        return snapshot;
    }

    public ChatMessageDTOV1 mapRuntimeChatMessage(String message, String conversationId) {
        var chatMessage = new ChatMessageDTOV1();
        chatMessage.setMessage(message != null ? message : "");
        chatMessage.setType(ChatMessageDTOV1.TypeEnum.ASSISTANT);
        chatMessage.setConversationId(conversationId);
        chatMessage.setCreationDate(System.currentTimeMillis());
        return chatMessage;
    }

    public ChatRequest mapChatRequest(ChatRequestDTOV1 request) {
        if (request == null) {
            return null;
        }
        var runtimeChatRequest = new ChatRequest();
        runtimeChatRequest.setChatMessage(mapChatMessage(request.getChatMessage()));
        runtimeChatRequest.setRequestContext(mapRequestContext(request));
        runtimeChatRequest.setConversation(mapConversation(request));
        return runtimeChatRequest;
    }

    public RequestContext mapRequestContext(ChatRequestDTOV1 request) {
        if (request.getRequestContext() == null) {
            return null;
        }
        var requestContext = new RequestContext();
        requestContext.setAiContext(request.getRequestContext().getAiContext());
        return requestContext;
    }

    public Conversation mapConversation(ChatRequestDTOV1 request) {
        if (request.getConversation() == null) {
            return null;
        }
        var conversation = new Conversation();
        conversation.setConversationId(request.getConversation().getConversationId());
        conversation.setConversationType(request.getConversation().getConversationType() != null
                ? request.getConversation().getConversationType().value()
                : null);
        conversation.setHistory(request.getConversation().getHistory() != null
                ? request.getConversation().getHistory().stream().map(this::mapChatMessage).toList()
                : List.of());
        return conversation;
    }

    public ChatMessage mapChatMessage(ChatMessageDTOV1 message) {
        if (message == null) {
            return null;
        }
        var runtimeMessage = new ChatMessage();
        runtimeMessage.setConversationId(message.getConversationId());
        runtimeMessage.setMessage(message.getMessage());
        runtimeMessage.setType(message.getType() != null ? message.getType().value() : null);
        runtimeMessage.setCreationDate(message.getCreationDate());
        return runtimeMessage;
    }

    public ModelSnapshot mapModel(Model model) {
        if (model == null) {
            return null;
        }
        var snapshot = new ModelSnapshot();
        snapshot.setName(model.getName());
        snapshot.setModelIdentifier(model.getModelIdentifier());
        snapshot.setModelConfig(model.getModelConfig());
        snapshot.setCommunicationMode(model.getCommunicationMode() != null ? model.getCommunicationMode().name() : null);
        snapshot.setProvider(mapProvider(model.getProvider()));
        return snapshot;
    }

    public ProviderSnapshot mapProvider(Provider provider) {
        if (provider == null) {
            return null;
        }
        var snapshot = new ProviderSnapshot();
        snapshot.setName(provider.getName());
        snapshot.setType(provider.getType() != null ? provider.getType().name() : null);
        snapshot.setDescription(provider.getDescription());
        snapshot.setLlmUrl(provider.getLlmUrl());
        snapshot.setApiKey(provider.getApiKey());
        snapshot.setAuthMode(provider.getAuthMode() != null ? provider.getAuthMode().name() : null);
        return snapshot;
    }

    public ScaffoldSnapshot mapScaffold(Scaffold scaffold) {
        if (scaffold == null) {
            return null;
        }
        var skills = new ArrayList<SkillSnapshot>();
        if (scaffold.getSkills() != null) {
            skills.addAll(scaffold.getSkills().stream().map(this::mapSkill).toList());
        }
        if (scaffold.getGlobalSkills() != null) {
            skills.addAll(scaffold.getGlobalSkills().stream().map(this::mapGlobalSkill).toList());
        }
        var snapshot = new ScaffoldSnapshot();
        snapshot.setName(scaffold.getName());
        snapshot.setSystemPrompt(scaffold.getSystemPrompt());
        snapshot.setSkills(skills);
        return snapshot;
    }

    public ScaffoldSnapshot mapGlobalScaffold(GlobalScaffold scaffold) {
        if (scaffold == null) {
            return null;
        }
        var snapshot = new ScaffoldSnapshot();
        snapshot.setName(scaffold.getName());
        snapshot.setSystemPrompt(scaffold.getSystemPrompt());
        snapshot.setSkills(scaffold.getSkills() != null
                ? scaffold.getSkills().stream().map(this::mapGlobalSkill).toList()
                : List.of());
        return snapshot;
    }

    public SkillSnapshot mapSkill(Skill skill) {
        var snapshot = new SkillSnapshot();
        snapshot.setName(skill.getName());
        snapshot.setDescription(skill.getDescription());
        snapshot.setInstruction(skill.getInstruction());
        return snapshot;
    }

    public SkillSnapshot mapGlobalSkill(GlobalSkill skill) {
        var snapshot = new SkillSnapshot();
        snapshot.setName(skill.getName());
        snapshot.setDescription(skill.getDescription());
        snapshot.setInstruction(skill.getInstruction());
        return snapshot;
    }

    public List<ToolSnapshot> mapTools(Agent agent) {
        var tools = new ArrayList<ToolSnapshot>();
        Map<String, List<AgentMcpToolRule>> rulesByToolId = agentRulesByToolId(agent);
        Map<String, List<AgentMcpToolRule>> rulesByGlobalToolId = agentRulesByGlobalToolId(agent);
        if (agent.getTools() != null) {
            tools.addAll(agent.getTools().stream()
                    .map(tool -> mapTool(tool, rulesByToolId.getOrDefault(tool.getId(), List.of())))
                    .toList());
        }
        if (agent.getGlobalTools() != null) {
            tools.addAll(agent.getGlobalTools().stream()
                    .map(tool -> mapGlobalTool(tool, rulesByGlobalToolId.getOrDefault(tool.getId(), List.of())))
                    .toList());
        }
        return tools;
    }

    private Map<String, List<AgentMcpToolRule>> agentRulesByToolId(Agent agent) {
        if (agent.getTools() == null || agent.getTools().isEmpty()) {
            return Map.of();
        }
        var ids = agent.getTools().stream().map(Tool::getId).toList();
        return agentMcpToolRuleDAO.findByAgentAndToolIds(agent.getId(), ids).stream()
                .filter(rule -> rule.getTool() != null)
                .collect(Collectors.groupingBy(rule -> rule.getTool().getId()));
    }

    private Map<String, List<AgentMcpToolRule>> agentRulesByGlobalToolId(Agent agent) {
        if (agent.getGlobalTools() == null || agent.getGlobalTools().isEmpty()) {
            return Map.of();
        }
        var ids = agent.getGlobalTools().stream().map(GlobalTool::getId).toList();
        return agentMcpToolRuleDAO.findByAgentAndGlobalToolIds(agent.getId(), ids).stream()
                .filter(rule -> rule.getGlobalTool() != null)
                .collect(Collectors.groupingBy(rule -> rule.getGlobalTool().getId()));
    }

    public ToolSnapshot mapTool(Tool tool, List<AgentMcpToolRule> rules) {
        return buildToolSnapshot(tool, rules);
    }

    public ToolSnapshot mapGlobalTool(GlobalTool tool, List<AgentMcpToolRule> rules) {
        return buildToolSnapshot(tool, rules);
    }

    private ToolSnapshot buildToolSnapshot(AbstractTool tool, List<AgentMcpToolRule> rules) {
        var snapshot = new ToolSnapshot();
        snapshot.setName(tool.getName());
        snapshot.setDescription(tool.getDescription());
        snapshot.setType(tool.getType() != null ? tool.getType().name() : null);
        snapshot.setUrl(tool.getUrl());
        snapshot.setApiKey(tool.getApiKey());
        snapshot.setAuthMode(tool.getAuthMode() != null ? tool.getAuthMode().name() : null);
        snapshot.setExecutionPolicy(mapExecutionPolicy(tool.getExecutionPolicy()));
        snapshot.setToolRules(mapRules(rules));
        return snapshot;
    }

    private List<ToolRuleSnapshot> mapRules(List<AgentMcpToolRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        return rules.stream().map(rule -> {
            var snapshot = new ToolRuleSnapshot();
            snapshot.setToolName(rule.getToolName());
            snapshot.setAllowed(mapToolPermission(rule.getAllowed()));
            return snapshot;
        }).toList();
    }

    private ToolSnapshot.ExecutionPolicyEnum mapExecutionPolicy(ExecutionPolicy policy) {
        var canonical = (policy == null ? ExecutionPolicy.DEFAULT : policy).toCanonical();
        if (canonical == ExecutionPolicy.ALWAYS_ALLOW) {
            return ToolSnapshot.ExecutionPolicyEnum.NEVER_ASK;
        }
        return ToolSnapshot.ExecutionPolicyEnum.ALWAYS_ASK;
    }

    private ToolRuleSnapshot.AllowedEnum mapToolPermission(ToolPermission permission) {
        var canonical = (permission == null ? ToolPermission.DEFAULT : permission).toCanonical();
        if (canonical == ToolPermission.ALWAYS_ALLOW) {
            return ToolRuleSnapshot.AllowedEnum.ALLOW;
        }
        if (canonical == ToolPermission.DENY) {
            return ToolRuleSnapshot.AllowedEnum.DENY;
        }
        return ToolRuleSnapshot.AllowedEnum.ALWAYS_ASK;
    }
}
