package org.tkit.onecx.ai.provider.rs.external.v1.mappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.tkit.onecx.ai.provider.domain.models.enums.CommunicationMode;
import org.tkit.onecx.ai.provider.domain.models.enums.ExecutionPolicy;
import org.tkit.onecx.ai.provider.domain.models.enums.ProviderType;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolPermission;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolType;
import org.tkit.onecx.ai.provider.test.AbstractTest;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ChatMessageDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ChatRequestDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ConversationDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.RequestContextDTOV1;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.AgentGroupSnapshot;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.AgentSnapshot;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.ExternalAgentSnapshot;
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
        when(agentMcpToolRuleDAO.findByAgentAndToolIds(any(), any())).thenReturn(List.of());
        when(agentMcpToolRuleDAO.findByAgentAndGlobalToolIds(any(), any())).thenReturn(List.of());
    }

    @Test
    void mapAgent_null_returnsNull() {
        assertThat(mapper.mapAgent(null)).isNull();
        assertThat(mapper.mapAgent(null, List.of())).isNull();
    }

    @Test
    void mapAgent_mapsMainFields_prefersGlobalScaffold_andIncludesGroups() {
        var tenantScaffold = new Scaffold();
        tenantScaffold.setName("tenant");
        tenantScaffold.setSystemPrompt("tenant prompt");
        tenantScaffold.setSkills(Set.of(skill("tenant-skill")));

        var globalScaffold = new GlobalScaffold();
        globalScaffold.setName("global");
        globalScaffold.setSystemPrompt("global prompt");
        globalScaffold.setSkills(Set.of(globalSkill("global-skill")));

        var model = model();
        var tool = tool("tool-1", ExecutionPolicy.ALWAYS_ASK, AuthMode.API_KEY);
        var globalTool = globalTool("gtool-1", ExecutionPolicy.ALWAYS_ALLOW);

        var agent = new Agent();
        agent.setId("agent-1");
        agent.setName("Agent");
        agent.setDescription("desc");
        agent.setAdditionalPrompt("prompt");
        agent.setA2aEnabled(true);
        agent.setModel(model);
        agent.setScaffold(tenantScaffold);
        agent.setGlobalScaffold(globalScaffold);
        agent.setTools(Set.of(tool));
        agent.setGlobalTools(Set.of(globalTool));

        var group = new AgentGroupSnapshot();
        group.setName("group-1");

        var snapshot = mapper.mapAgent(agent, List.of(group));

        assertThat(snapshot.getName()).isEqualTo("Agent");
        assertThat(snapshot.getDescription()).isEqualTo("desc");
        assertThat(snapshot.getAdditionalPrompt()).isEqualTo("prompt");
        assertThat(snapshot.getA2aEnabled()).isTrue();
        assertThat(snapshot.getGroups()).hasSize(1);
        assertThat(snapshot.getScaffold().getName()).isEqualTo("global");
        assertThat(snapshot.getScaffold().getSkills()).extracting("name").containsExactly("global-skill");
        assertThat(snapshot.getTools()).extracting("name").containsExactlyInAnyOrder("tool-1", "gtool-1");
    }

    @Test
    void mapGroup_null_returnsNull() {
        assertThat(mapper.mapGroup(null, List.of(), List.of())).isNull();
    }

    @Test
    void mapGroup_mapsEnumsAndDefaultsNullCollectionsToEmpty() {
        var group = new AgentGroup();
        group.setName("ops");
        group.setDescription("Operations");
        group.setRoutingInstructions("route");
        group.setOrchestrationMode(AgentGroupOrchestrationMode.PARALLEL);
        group.setResponseStrategy(AgentGroupResponseStrategy.SUMMARY);

        var mapped = mapper.mapGroup(group, null, null);

        assertThat(mapped.getName()).isEqualTo("ops");
        assertThat(mapped.getDescription()).isEqualTo("Operations");
        assertThat(mapped.getRoutingInstructions()).isEqualTo("route");
        assertThat(mapped.getOrchestrationMode()).isEqualTo("PARALLEL");
        assertThat(mapped.getResponseStrategy()).isEqualTo("SUMMARY");
        assertThat(mapped.getAgents()).isEmpty();
        assertThat(mapped.getExternalAgents()).isEmpty();
    }

    @Test
    void mapExternalAgent_null_returnsNull() {
        assertThat(mapper.mapExternalAgent(null)).isNull();
    }

    @Test
    void mapExternalAgent_mapsAllFields() {
        var externalAgent = new ExternalAgent();
        externalAgent.setName("travel");
        externalAgent.setDescription("desc");
        externalAgent.setDiscoveryUrl("http://discover");
        externalAgent.setApiKey("secret");
        externalAgent.setAuthMode(AuthMode.OAUTH);
        externalAgent.setEnabled(true);

        var mapped = mapper.mapExternalAgent(externalAgent);

        assertThat(mapped.getName()).isEqualTo("travel");
        assertThat(mapped.getDescription()).isEqualTo("desc");
        assertThat(mapped.getDiscoveryUrl()).isEqualTo("http://discover");
        assertThat(mapped.getApiKey()).isEqualTo("secret");
        assertThat(mapped.getAuthMode()).isEqualTo("OAUTH");
        assertThat(mapped.getEnabled()).isTrue();
    }

    @Test
    void mapRuntimeChatMessage_nullMessage_returnsEmptyString() {
        var msg = mapper.mapRuntimeChatMessage(null, "conv-1");
        assertThat(msg.getMessage()).isEmpty();
        assertThat(msg.getType()).isEqualTo(ChatMessageDTOV1.TypeEnum.ASSISTANT);
        assertThat(msg.getConversationId()).isEqualTo("conv-1");
        assertThat(msg.getCreationDate()).isNotNull();
    }

    @Test
    void mapChatRequest_null_returnsNull() {
        assertThat(mapper.mapChatRequest(null)).isNull();
    }

    @Test
    void mapChatRequest_mapsMessageContextAndConversation() {
        var request = new ChatRequestDTOV1();
        request.setChatMessage(chatMessage("user message", ChatMessageDTOV1.TypeEnum.USER, "conv-1", 123L));

        var requestContext = new RequestContextDTOV1();
        requestContext.setAiContext(List.of("ctx-a", "ctx-b"));
        request.setRequestContext(requestContext);

        var conversation = new ConversationDTOV1();
        conversation.setConversationId("conv-1");
        conversation.setConversationType(ConversationDTOV1.ConversationTypeEnum.Q_AND_A);
        conversation.setHistory(List.of(
                chatMessage("h1", ChatMessageDTOV1.TypeEnum.USER, "conv-1", 1L),
                chatMessage("h2", ChatMessageDTOV1.TypeEnum.ASSISTANT, "conv-1", 2L)));
        request.setConversation(conversation);

        var mapped = mapper.mapChatRequest(request);

        assertThat(mapped.getChatMessage().getMessage()).isEqualTo("user message");
        assertThat(mapped.getChatMessage().getType()).isEqualTo("USER");
        assertThat(mapped.getRequestContext().getAiContext()).containsExactly("ctx-a", "ctx-b");
        assertThat(mapped.getConversation().getConversationId()).isEqualTo("conv-1");
        assertThat(mapped.getConversation().getConversationType()).isEqualTo("Q_AND_A");
        assertThat(mapped.getConversation().getHistory()).hasSize(2);
        assertThat(mapped.getConversation().getHistory().get(1).getType()).isEqualTo("ASSISTANT");
    }

    @Test
    void mapRequestContext_withoutContext_returnsNull() {
        var request = new ChatRequestDTOV1();
        assertThat(mapper.mapRequestContext(request)).isNull();
    }

    @Test
    void mapConversation_withoutConversation_returnsNull() {
        var request = new ChatRequestDTOV1();
        assertThat(mapper.mapConversation(request)).isNull();
    }

    @Test
    void mapConversation_nullHistory_returnsEmptyList() {
        var request = new ChatRequestDTOV1();
        var conversation = new ConversationDTOV1();
        conversation.setConversationId("conv-2");
        request.setConversation(conversation);

        var mapped = mapper.mapConversation(request);

        assertThat(mapped.getConversationId()).isEqualTo("conv-2");
        assertThat(mapped.getHistory()).isEmpty();
    }

    @Test
    void mapChatMessage_null_returnsNull() {
        assertThat(mapper.mapChatMessage(null)).isNull();
    }

    @Test
    void mapModel_andProvider_null_returnNull() {
        assertThat(mapper.mapModel(null)).isNull();
        assertThat(mapper.mapProvider(null)).isNull();
    }

    @Test
    void mapModel_mapsProviderDetails() {
        var mapped = mapper.mapModel(model());

        assertThat(mapped.getName()).isEqualTo("model-1");
        assertThat(mapped.getModelIdentifier()).isEqualTo("mistral");
        assertThat(mapped.getModelConfig()).isEqualTo("temp=0.2");
        assertThat(mapped.getCommunicationMode()).isEqualTo("SYNC");
        assertThat(mapped.getProvider().getType()).isEqualTo("OLLAMA");
        assertThat(mapped.getProvider().getAuthMode()).isEqualTo("API_KEY");
    }

    @Test
    void mapScaffold_mergesTenantAndGlobalSkills() {
        var scaffold = new Scaffold();
        scaffold.setName("tenant");
        scaffold.setSystemPrompt("prompt");
        scaffold.setSkills(Set.of(skill("tenant-1"), skill("tenant-2")));
        scaffold.setGlobalSkills(Set.of(globalSkill("global-1")));

        var mapped = mapper.mapScaffold(scaffold);

        assertThat(mapped.getName()).isEqualTo("tenant");
        assertThat(mapped.getSystemPrompt()).isEqualTo("prompt");
        assertThat(mapped.getSkills()).extracting("name")
                .containsExactlyInAnyOrder("tenant-1", "tenant-2", "global-1");
    }

    @Test
    void mapGlobalScaffold_mapsOnlyGlobalSkills_andEmptyWhenNull() {
        var scaffold = new GlobalScaffold();
        scaffold.setName("global");
        scaffold.setSystemPrompt("global-prompt");
        scaffold.setSkills(Set.of(globalSkill("global-a")));

        var mapped = mapper.mapGlobalScaffold(scaffold);

        assertThat(mapped.getName()).isEqualTo("global");
        assertThat(mapped.getSystemPrompt()).isEqualTo("global-prompt");
        assertThat(mapped.getSkills()).extracting("name").containsExactly("global-a");

        var empty = new GlobalScaffold();
        empty.setName("empty");
        assertThat(mapper.mapGlobalScaffold(empty).getSkills()).isEmpty();
    }

    @Test
    void mapTools_groupsRulesByToolAndGlobalTool() {
        var tool = tool("tool-1", ExecutionPolicy.ALWAYS_ASK, AuthMode.API_KEY);
        var globalTool = globalTool("gtool-1", ExecutionPolicy.ALWAYS_ALLOW);

        var ruleTenant = new AgentMcpToolRule();
        ruleTenant.setTool(tool);
        ruleTenant.setToolName("search");
        ruleTenant.setAllowed(ToolPermission.ALLOW);

        var ruleGlobal = new AgentMcpToolRule();
        ruleGlobal.setGlobalTool(globalTool);
        ruleGlobal.setToolName("summarize");
        ruleGlobal.setAllowed(ToolPermission.DENY);

        var ignored = new AgentMcpToolRule();
        ignored.setToolName("ignored");

        when(agentMcpToolRuleDAO.findByAgentAndToolIds("agent-1", List.of("tool-1")))
                .thenReturn(List.of(ruleTenant, ignored));
        when(agentMcpToolRuleDAO.findByAgentAndGlobalToolIds("agent-1", List.of("gtool-1")))
                .thenReturn(List.of(ruleGlobal, ignored));

        var agent = new Agent();
        agent.setId("agent-1");
        agent.setTools(Set.of(tool));
        agent.setGlobalTools(Set.of(globalTool));

        var mapped = mapper.mapTools(agent);

        assertThat(mapped).extracting("name").containsExactlyInAnyOrder("tool-1", "gtool-1");

        var mappedTool = mapped.stream().filter(t -> "tool-1".equals(t.getName())).findFirst().orElseThrow();
        assertThat(mappedTool.getExecutionPolicy().name()).isEqualTo("ALWAYS_ASK");
        assertThat(mappedTool.getToolRules()).hasSize(1);
        assertThat(mappedTool.getToolRules().get(0).getToolName()).isEqualTo("search");
        assertThat(mappedTool.getToolRules().get(0).getAllowed().name()).isEqualTo("ALLOW");

        var mappedGlobal = mapped.stream().filter(t -> "gtool-1".equals(t.getName())).findFirst().orElseThrow();
        assertThat(mappedGlobal.getExecutionPolicy().name()).isEqualTo("NEVER_ASK");
        assertThat(mappedGlobal.getToolRules()).hasSize(1);
        assertThat(mappedGlobal.getToolRules().get(0).getAllowed().name()).isEqualTo("DENY");

        verify(agentMcpToolRuleDAO).findByAgentAndToolIds("agent-1", List.of("tool-1"));
        verify(agentMcpToolRuleDAO).findByAgentAndGlobalToolIds("agent-1", List.of("gtool-1"));
    }

    @Test
    void mapTools_emptyAssignments_returnEmpty_withoutDaoCalls() {
        var agent = new Agent();
        agent.setId("agent-1");

        var mapped = mapper.mapTools(agent);

        assertThat(mapped).isEmpty();
        verifyNoInteractions(agentMcpToolRuleDAO);
    }

    @Test
    void mapTool_andGlobalTool_canonicalizePolicyAliases() {
        var localTool = tool("tool-2", ExecutionPolicy.ALLOW, AuthMode.OAUTH);

        var ruleAllowAlias = new AgentMcpToolRule();
        ruleAllowAlias.setToolName("approve");
        ruleAllowAlias.setAllowed(ToolPermission.NEVER_ASK);

        var ruleDefault = new AgentMcpToolRule();
        ruleDefault.setToolName("confirm");
        ruleDefault.setAllowed(null);

        var mappedLocal = mapper.mapTool(localTool, List.of(ruleAllowAlias, ruleDefault));
        assertThat(mappedLocal.getExecutionPolicy().name()).isEqualTo("NEVER_ASK");
        assertThat(mappedLocal.getToolRules()).extracting("allowed")
                .extracting(Object::toString)
                .containsExactly("ALLOW", "ALWAYS_ASK");

        var mappedGlobal = mapper.mapGlobalTool(globalTool("gtool-2", ExecutionPolicy.NEVER_ASK), List.of(ruleAllowAlias));
        assertThat(mappedGlobal.getExecutionPolicy().name()).isEqualTo("NEVER_ASK");
        assertThat(mappedGlobal.getToolRules()).hasSize(1);
        assertThat(mappedGlobal.getToolRules().get(0).getAllowed().name()).isEqualTo("ALLOW");
    }

    @Test
    void toRuntimeRequest_mapsModelAndProvider() {
        var agent = new Agent();
        agent.setName("agent-1");
        agent.setModel(model());

        var request = new ChatRequestDTOV1();
        request.setChatMessage(chatMessage("hello", ChatMessageDTOV1.TypeEnum.USER, "conv-1", 17L));

        var groupSnapshot = new AgentGroupSnapshot();
        groupSnapshot.setName("default-group");

        var result = mapper.toRuntimeRequest(agent, request, List.of(groupSnapshot));
        assertThat(result.getRootAgent().getName()).isEqualTo("agent-1");
        assertThat(result.getRootAgent().getModel().getModelIdentifier()).isEqualTo("mistral");
        assertThat(result.getRootAgent().getModel().getProvider().getType()).isEqualTo("OLLAMA");
        assertThat(result.getRootAgent().getGroups()).extracting(AgentGroupSnapshot::getName)
                .containsExactly("default-group");
        assertThat(result.getChatRequest().getChatMessage().getMessage()).isEqualTo("hello");
    }

    @Test
    void mapGroup_keepsProvidedAgentAndExternalAgentLists() {
        var group = new AgentGroup();
        group.setName("routing");

        var agentSnapshot = new AgentSnapshot();
        agentSnapshot.setName("worker-a");
        var externalSnapshot = new ExternalAgentSnapshot();
        externalSnapshot.setName("partner-x");

        var mapped = mapper.mapGroup(group, List.of(agentSnapshot), List.of(externalSnapshot));

        assertThat(mapped.getAgents()).extracting(AgentSnapshot::getName).containsExactly("worker-a");
        assertThat(mapped.getExternalAgents()).extracting(ExternalAgentSnapshot::getName).containsExactly("partner-x");
    }

    private static Model model() {
        var provider = new Provider();
        provider.setName("provider-1");
        provider.setType(ProviderType.OLLAMA);
        provider.setDescription("Provider");
        provider.setLlmUrl("http://ollama.local");
        provider.setApiKey("api-key");
        provider.setAuthMode(AuthMode.API_KEY);

        var model = new Model();
        model.setName("model-1");
        model.setModelIdentifier("mistral");
        model.setModelConfig("temp=0.2");
        model.setCommunicationMode(CommunicationMode.SYNC);
        model.setProvider(provider);
        return model;
    }

    private static Tool tool(String name, ExecutionPolicy executionPolicy, AuthMode authMode) {
        var tool = new Tool();
        tool.setId(name);
        tool.setName(name);
        tool.setDescription(name + " description");
        tool.setType(ToolType.MCP);
        tool.setUrl("http://" + name);
        tool.setApiKey("secret-" + name);
        tool.setAuthMode(authMode);
        tool.setExecutionPolicy(executionPolicy);
        return tool;
    }

    private static GlobalTool globalTool(String name, ExecutionPolicy executionPolicy) {
        var tool = new GlobalTool();
        tool.setId(name);
        tool.setName(name);
        tool.setDescription(name + " description");
        tool.setType(ToolType.MCP);
        tool.setUrl("http://" + name);
        tool.setApiKey("secret-" + name);
        tool.setAuthMode(AuthMode.API_KEY);
        tool.setExecutionPolicy(executionPolicy);
        return tool;
    }

    private static Skill skill(String name) {
        var skill = new Skill();
        skill.setName(name);
        skill.setDescription(name + " desc");
        skill.setInstruction(name + " instruction");
        return skill;
    }

    private static GlobalSkill globalSkill(String name) {
        var skill = new GlobalSkill();
        skill.setName(name);
        skill.setDescription(name + " desc");
        skill.setInstruction(name + " instruction");
        return skill;
    }

    private static ChatMessageDTOV1 chatMessage(String text, ChatMessageDTOV1.TypeEnum type, String conversationId,
            Long creationDate) {
        var message = new ChatMessageDTOV1();
        message.setMessage(text);
        message.setType(type);
        message.setConversationId(conversationId);
        message.setCreationDate(creationDate);
        return message;
    }
}
