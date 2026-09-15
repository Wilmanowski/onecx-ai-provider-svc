package org.tkit.onecx.ai.provider.rs.external.v1.mappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tkit.onecx.ai.provider.domain.daos.AgentMcpToolRuleDAO;
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
import org.tkit.onecx.ai.provider.domain.models.enums.AgentGroupOrchestrationMode;
import org.tkit.onecx.ai.provider.domain.models.enums.AgentGroupResponseStrategy;
import org.tkit.onecx.ai.provider.domain.models.enums.AuthMode;
import org.tkit.onecx.ai.provider.domain.models.enums.ExecutionPolicy;
import org.tkit.onecx.ai.provider.domain.models.enums.ProviderType;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolPermission;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolType;
import org.tkit.onecx.ai.provider.test.AbstractTest;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ChatMessageDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ChatRequestDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ConversationDTOV1;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.AgentGroupSnapshot;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.ToolRuleSnapshot.AllowedEnum;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.ToolSnapshot;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class RuntimeSnapshotMapperTest extends AbstractTest {

    @Inject
    RuntimeSnapshotMapper mapper;

    @InjectMock
    AgentMcpToolRuleDAO agentMcpToolRuleDAO;

    @BeforeEach
    void setUp() {
        when(agentMcpToolRuleDAO.findByAgentAndToolIds(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(agentMcpToolRuleDAO.findByAgentAndGlobalToolIds(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
    }

    @Test
    void mapAgent_null_returnsNull() {
        assertThat(mapper.mapAgent(null)).isNull();
        assertThat(mapper.mapAgent(null, List.of())).isNull();
    }

    @Test
    void mapAgent_withGlobalScaffold_preferredOverScaffold() {
        var agent = new Agent();
        agent.setName("agent-1");
        agent.setGlobalScaffold(globalScaffold("global-scaffold"));
        agent.setScaffold(scaffold("local-scaffold"));

        var snapshot = mapper.mapAgent(agent);
        assertThat(snapshot.getScaffold().getName()).isEqualTo("global-scaffold");
    }

    @Test
    void mapAgent_withLocalScaffold_whenNoGlobalScaffold() {
        var agent = new Agent();
        agent.setName("agent-1");
        agent.setScaffold(scaffold("local-scaffold"));

        var snapshot = mapper.mapAgent(agent);
        assertThat(snapshot.getScaffold().getName()).isEqualTo("local-scaffold");
    }

    @Test
    void mapAgent_withGroups_setsGroups() {
        var agent = new Agent();
        agent.setName("agent-1");
        var groupSnapshot = new AgentGroupSnapshot();
        groupSnapshot.setName("group-1");

        var snapshot = mapper.mapAgent(agent, List.of(groupSnapshot));
        assertThat(snapshot.getGroups()).hasSize(1);
        assertThat(snapshot.getGroups().get(0).getName()).isEqualTo("group-1");
    }

    @Test
    void mapGroup_null_returnsNull() {
        assertThat(mapper.mapGroup(null, List.of(), List.of())).isNull();
    }

    @Test
    void mapGroup_withNullAgentsAndExternalAgents_defaultsToEmptyLists() {
        var group = new AgentGroup();
        group.setName("group-1");
        group.setOrchestrationMode(AgentGroupOrchestrationMode.SUPERVISOR_ROUTED);
        group.setResponseStrategy(AgentGroupResponseStrategy.SCORED);

        var snapshot = mapper.mapGroup(group, null, null);
        assertThat(snapshot.getName()).isEqualTo("group-1");
        assertThat(snapshot.getOrchestrationMode()).isEqualTo("SUPERVISOR_ROUTED");
        assertThat(snapshot.getResponseStrategy()).isEqualTo("SCORED");
        assertThat(snapshot.getAgents()).isEmpty();
        assertThat(snapshot.getExternalAgents()).isEmpty();
    }

    @Test
    void mapGroup_withNullOrchestrationModeAndResponseStrategy_setsNull() {
        var group = new AgentGroup();
        group.setName("group-1");
        group.setOrchestrationMode(null);
        group.setResponseStrategy(null);

        var snapshot = mapper.mapGroup(group, List.of(), List.of());
        assertThat(snapshot.getOrchestrationMode()).isNull();
        assertThat(snapshot.getResponseStrategy()).isNull();
    }

    @Test
    void mapExternalAgent_null_returnsNull() {
        assertThat(mapper.mapExternalAgent(null)).isNull();
    }

    @Test
    void mapExternalAgent_withNullAuthMode_setsNull() {
        var agent = new ExternalAgent();
        agent.setName("ext-1");
        agent.setAuthMode(null);

        var snapshot = mapper.mapExternalAgent(agent);
        assertThat(snapshot.getAuthMode()).isNull();
    }

    @Test
    void mapExternalAgent_withAuthMode_setsName() {
        var agent = new ExternalAgent();
        agent.setName("ext-1");
        agent.setAuthMode(AuthMode.API_KEY);

        var snapshot = mapper.mapExternalAgent(agent);
        assertThat(snapshot.getAuthMode()).isEqualTo("API_KEY");
    }

    @Test
    void mapRuntimeChatMessage_nullMessage_returnsEmptyString() {
        var msg = mapper.mapRuntimeChatMessage(null, "conv-1");
        assertThat(msg.getMessage()).isEmpty();
        assertThat(msg.getType()).isEqualTo(ChatMessageDTOV1.TypeEnum.ASSISTANT);
        assertThat(msg.getConversationId()).isEqualTo("conv-1");
    }

    @Test
    void mapChatRequest_null_returnsNull() {
        assertThat(mapper.mapChatRequest(null)).isNull();
    }

    @Test
    void mapChatRequest_withNullChatMessage_setsNullChatMessage() {
        var request = new ChatRequestDTOV1();
        var result = mapper.mapChatRequest(request);
        assertThat(result.getChatMessage()).isNull();
    }

    @Test
    void mapChatRequest_withNullRequestContext_setsNullRequestContext() {
        var request = new ChatRequestDTOV1();
        var result = mapper.mapChatRequest(request);
        assertThat(result.getRequestContext()).isNull();
    }

    @Test
    void mapChatRequest_withRequestContext_mapsAiContext() {
        var request = new ChatRequestDTOV1();
        var rc = new gen.org.tkit.onecx.ai.provider.rs.external.v1.model.RequestContextDTOV1();
        rc.setAiContext(java.util.List.of("context-item"));
        request.setRequestContext(rc);

        var result = mapper.mapChatRequest(request);
        assertThat(result.getRequestContext()).isNotNull();
        assertThat(result.getRequestContext().getAiContext()).containsExactly("context-item");
    }

    @Test
    void mapChatRequest_withNullConversation_setsNullConversation() {
        var request = new ChatRequestDTOV1();
        var result = mapper.mapChatRequest(request);
        assertThat(result.getConversation()).isNull();
    }

    @Test
    void mapChatRequest_withConversation_nullConversationType_setsNull() {
        var request = new ChatRequestDTOV1();
        var conv = new ConversationDTOV1();
        conv.setConversationId("conv-1");
        conv.setConversationType(null);
        request.setConversation(conv);

        var result = mapper.mapChatRequest(request);
        assertThat(result.getConversation()).isNotNull();
        assertThat(result.getConversation().getConversationType()).isNull();
    }

    @Test
    void mapChatRequest_withConversation_nullHistory_defaultsToEmptyList() {
        var request = new ChatRequestDTOV1();
        var conv = new ConversationDTOV1();
        conv.setConversationId("conv-1");
        conv.setConversationType(ConversationDTOV1.ConversationTypeEnum.Q_AND_A);
        conv.setHistory(null);
        request.setConversation(conv);

        var result = mapper.mapChatRequest(request);
        assertThat(result.getConversation().getHistory()).isEmpty();
        assertThat(result.getConversation().getConversationType()).isEqualTo("Q_AND_A");
    }

    @Test
    void mapChatRequest_withConversation_andHistory_mapsMessages() {
        var request = new ChatRequestDTOV1();
        var conv = new ConversationDTOV1();
        conv.setConversationId("conv-1");
        var historyMsg = new ChatMessageDTOV1();
        historyMsg.setType(ChatMessageDTOV1.TypeEnum.USER);
        historyMsg.setMessage("hello");
        historyMsg.setConversationId("conv-1");
        conv.setHistory(List.of(historyMsg));
        request.setConversation(conv);

        var result = mapper.mapChatRequest(request);
        assertThat(result.getConversation().getHistory()).hasSize(1);
        assertThat(result.getConversation().getHistory().get(0).getMessage()).isEqualTo("hello");
    }

    @Test
    void mapChatMessage_null_returnsNull() {
        assertThat(mapper.mapChatMessage((ChatMessageDTOV1) null)).isNull();
    }

    @Test
    void mapChatMessage_withNullType_setsNull() {
        var msg = new ChatMessageDTOV1();
        msg.setMessage("hello");
        msg.setType(null);

        var result = mapper.mapChatMessage(msg);
        assertThat(result.getType()).isNull();
        assertThat(result.getMessage()).isEqualTo("hello");
    }

    @Test
    void mapModel_null_returnsNull() {
        assertThat(mapper.mapModel(null)).isNull();
    }

    @Test
    void mapModel_withNullCommunicationMode_setsNull() {
        var model = new Model();
        model.setName("model-1");
        model.setCommunicationMode(null);

        var snapshot = mapper.mapModel(model);
        assertThat(snapshot.getCommunicationMode()).isNull();
    }

    @Test
    void mapModel_withNullProvider_setsNullProvider() {
        var model = new Model();
        model.setName("model-1");
        model.setProvider(null);

        var snapshot = mapper.mapModel(model);
        assertThat(snapshot.getProvider()).isNull();
    }

    @Test
    void mapProvider_null_returnsNull() {
        assertThat(mapper.mapProvider(null)).isNull();
    }

    @Test
    void mapProvider_withNullType_setsNull() {
        var provider = new Provider();
        provider.setName("p-1");
        provider.setType(null);

        var snapshot = mapper.mapProvider(provider);
        assertThat(snapshot.getType()).isNull();
    }

    @Test
    void mapProvider_withNullAuthMode_setsNull() {
        var provider = new Provider();
        provider.setName("p-1");
        provider.setAuthMode(null);

        var snapshot = mapper.mapProvider(provider);
        assertThat(snapshot.getAuthMode()).isNull();
    }

    @Test
    void mapScaffold_null_returnsNull() {
        assertThat(mapper.mapScaffold(null)).isNull();
    }

    @Test
    void mapScaffold_withNullSkills_returnsEmptySkills() {
        var scaffold = new Scaffold();
        scaffold.setName("s-1");
        scaffold.setSkills(null);
        scaffold.setGlobalSkills(null);

        var snapshot = mapper.mapScaffold(scaffold);
        assertThat(snapshot.getSkills()).isEmpty();
    }

    @Test
    void mapScaffold_withSkillsAndGlobalSkills_mergesBoth() {
        var scaffold = new Scaffold();
        scaffold.setName("s-1");
        scaffold.setSkills(Set.of(skill("local-skill")));
        scaffold.setGlobalSkills(Set.of(globalSkill("global-skill")));

        var snapshot = mapper.mapScaffold(scaffold);
        assertThat(snapshot.getSkills()).hasSize(2);
    }

    @Test
    void mapGlobalScaffold_null_returnsNull() {
        assertThat(mapper.mapGlobalScaffold(null)).isNull();
    }

    @Test
    void mapGlobalScaffold_withNullSkills_returnsEmptySkills() {
        var scaffold = new GlobalScaffold();
        scaffold.setName("gs-1");
        scaffold.setSkills(null);

        var snapshot = mapper.mapGlobalScaffold(scaffold);
        assertThat(snapshot.getSkills()).isEmpty();
    }

    @Test
    void mapGlobalScaffold_withSkills_mapsSkills() {
        var scaffold = new GlobalScaffold();
        scaffold.setName("gs-1");
        scaffold.setSkills(Set.of(globalSkill("g-skill")));

        var snapshot = mapper.mapGlobalScaffold(scaffold);
        assertThat(snapshot.getSkills()).hasSize(1);
        assertThat(snapshot.getSkills().get(0).getName()).isEqualTo("g-skill");
    }

    @Test
    void mapTools_agentWithNullToolsAndNullGlobalTools_returnsEmptyList() {
        var agent = new Agent();
        agent.setId("agent-1");

        var tools = mapper.mapTools(agent);
        assertThat(tools).isEmpty();
    }

    @Test
    void mapTools_agentWithTools_mapsToolRules() {
        var tool = new Tool();
        tool.setId("tool-1");
        tool.setName("searchTool");
        tool.setType(ToolType.MCP);
        tool.setUrl("http://mcp.local");
        tool.setAuthMode(AuthMode.API_KEY);
        tool.setExecutionPolicy(ExecutionPolicy.ALWAYS_ASK);

        var agent = new Agent();
        agent.setId("agent-1");
        agent.setTools(Set.of(tool));

        var rule = new AgentMcpToolRule();
        rule.setTool(tool);
        rule.setToolName("searchTool");
        rule.setAllowed(ToolPermission.ALLOW);

        when(agentMcpToolRuleDAO.findByAgentAndToolIds("agent-1", List.of("tool-1")))
                .thenReturn(List.of(rule));

        var tools = mapper.mapTools(agent);
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).getName()).isEqualTo("searchTool");
        assertThat(tools.get(0).getType()).isEqualTo("MCP");
        assertThat(tools.get(0).getAuthMode()).isEqualTo("API_KEY");
        assertThat(tools.get(0).getExecutionPolicy()).isEqualTo(ToolSnapshot.ExecutionPolicyEnum.ALWAYS_ASK);
        assertThat(tools.get(0).getToolRules()).hasSize(1);
        assertThat(tools.get(0).getToolRules().get(0).getAllowed()).isEqualTo(
                gen.org.tkit.onecx.ai.provider.runtime.client.model.ToolRuleSnapshot.AllowedEnum.ALLOW);
    }

    @Test
    void mapTools_agentWithLegacyAllowPolicies_mapsRuntimeCompatibilityValues() {
        var tool = new Tool();
        tool.setId("tool-1");
        tool.setName("searchTool");
        tool.setType(ToolType.MCP);
        tool.setUrl("http://mcp.local");
        tool.setAuthMode(AuthMode.API_KEY);
        tool.setExecutionPolicy(ExecutionPolicy.NEVER_ASK);

        var agent = new Agent();
        agent.setId("agent-1");
        agent.setTools(Set.of(tool));

        var rule = new AgentMcpToolRule();
        rule.setTool(tool);
        rule.setToolName("searchTool");
        rule.setAllowed(ToolPermission.NEVER_ASK);

        when(agentMcpToolRuleDAO.findByAgentAndToolIds("agent-1", List.of("tool-1")))
                .thenReturn(List.of(rule));

        var tools = mapper.mapTools(agent);
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).getExecutionPolicy()).isEqualTo(ToolSnapshot.ExecutionPolicyEnum.NEVER_ASK);
        assertThat(tools.get(0).getToolRules()).hasSize(1);
        assertThat(tools.get(0).getToolRules().get(0).getAllowed()).isEqualTo(
                AllowedEnum.ALLOW);
    }

    @Test
    void mapTools_agentWithGlobalTools_mapsGlobalToolRules() {
        var globalTool = new GlobalTool();
        globalTool.setId("gtool-1");
        globalTool.setName("globalRead");
        globalTool.setType(ToolType.MCP);
        globalTool.setUrl("http://mcp.global");
        globalTool.setAuthMode(null);
        globalTool.setExecutionPolicy(null);

        var agent = new Agent();
        agent.setId("agent-1");
        agent.setGlobalTools(Set.of(globalTool));

        var rule = new AgentMcpToolRule();
        rule.setGlobalTool(globalTool);
        rule.setToolName("globalRead");
        rule.setAllowed(null);

        when(agentMcpToolRuleDAO.findByAgentAndGlobalToolIds("agent-1", List.of("gtool-1")))
                .thenReturn(List.of(rule));

        var tools = mapper.mapTools(agent);
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).getName()).isEqualTo("globalRead");
        assertThat(tools.get(0).getAuthMode()).isNull();
        assertThat(tools.get(0).getExecutionPolicy()).isEqualTo(ToolSnapshot.ExecutionPolicyEnum.ALWAYS_ASK);
        assertThat(tools.get(0).getToolRules()).hasSize(1);
        assertThat(tools.get(0).getToolRules().get(0).getAllowed()).isEqualTo(
                AllowedEnum.ALWAYS_ASK);
    }

    @Test
    void mapTools_agentWithNullToolType_setsNullType() {
        var tool = new Tool();
        tool.setId("tool-1");
        tool.setName("toolNoType");
        tool.setType(null);

        var agent = new Agent();
        agent.setId("agent-1");
        agent.setTools(Set.of(tool));

        var tools = mapper.mapTools(agent);
        assertThat(tools.get(0).getType()).isNull();
    }

    @Test
    void toRuntimeRequest_mapsFullRequest() {
        var provider = new Provider();
        provider.setType(ProviderType.OLLAMA);
        provider.setLlmUrl("http://ollama.local");

        var model = new Model();
        model.setProvider(provider);
        model.setModelIdentifier("mistral");

        var agent = new Agent();
        agent.setName("agent-1");
        agent.setModel(model);

        var request = new ChatRequestDTOV1();
        var msg = new ChatMessageDTOV1();
        msg.setType(ChatMessageDTOV1.TypeEnum.USER);
        msg.setMessage("hello");
        msg.setConversationId("conv-1");
        request.setChatMessage(msg);

        var result = mapper.toRuntimeRequest(agent, request, List.of());
        assertThat(result.getRootAgent().getName()).isEqualTo("agent-1");
        assertThat(result.getRootAgent().getModel().getModelIdentifier()).isEqualTo("mistral");
        assertThat(result.getChatRequest().getChatMessage().getMessage()).isEqualTo("hello");
    }

    private Scaffold scaffold(String name) {
        var s = new Scaffold();
        s.setName(name);
        s.setSystemPrompt("prompt");
        return s;
    }

    private GlobalScaffold globalScaffold(String name) {
        var s = new GlobalScaffold();
        s.setName(name);
        s.setSystemPrompt("prompt");
        return s;
    }

    private Skill skill(String name) {
        var s = new Skill();
        s.setName(name);
        s.setDescription("desc");
        s.setInstruction("instruction");
        return s;
    }

    private GlobalSkill globalSkill(String name) {
        var s = new GlobalSkill();
        s.setName(name);
        s.setDescription("desc");
        s.setInstruction("instruction");
        return s;
    }
}
