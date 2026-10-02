package org.tkit.onecx.ai.provider.rs.external.v1.mappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tkit.onecx.ai.provider.common.services.version.CanonicalVersionHasher;
import org.tkit.onecx.ai.provider.common.services.version.VersionGenerationException;
import org.tkit.onecx.ai.provider.domain.models.Agent;
import org.tkit.onecx.ai.provider.domain.models.AgentMcpToolRule;
import org.tkit.onecx.ai.provider.domain.models.GlobalScaffold;
import org.tkit.onecx.ai.provider.domain.models.GlobalSkill;
import org.tkit.onecx.ai.provider.domain.models.GlobalTool;
import org.tkit.onecx.ai.provider.domain.models.Model;
import org.tkit.onecx.ai.provider.domain.models.Provider;
import org.tkit.onecx.ai.provider.domain.models.Scaffold;
import org.tkit.onecx.ai.provider.domain.models.Skill;
import org.tkit.onecx.ai.provider.domain.models.Tool;
import org.tkit.onecx.ai.provider.domain.models.enums.AgentStatus;
import org.tkit.onecx.ai.provider.domain.models.enums.AuthMode;
import org.tkit.onecx.ai.provider.domain.models.enums.CommunicationMode;
import org.tkit.onecx.ai.provider.domain.models.enums.ExecutionPolicy;
import org.tkit.onecx.ai.provider.domain.models.enums.ProviderType;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolPermission;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolType;

import com.fasterxml.jackson.databind.ObjectMapper;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentConfigurationVersionDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionComponentSourceDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionPolicySourceDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionSkillDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionToolPermissionDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionToolRuleDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionToolServerDTOV1;

/**
 * Contract tests for the Agent Configuration version.
 */
class AgentConfigurationVersionMapperTest {

    private static final String PROVIDER_SECRET = "provider-secret-value";
    private static final String TOOL_SECRET = "tool-secret-value";
    private static final String GLOBAL_TOOL_SECRET = "global-tool-secret-value";

    private final AgentConfigurationVersionMapper mapper = new AgentConfigurationVersionMapper();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Tool mcpTool;
    private Tool httpTool;
    private GlobalTool globalMcpTool;

    @BeforeEach
    void setUp() {
        mcpTool = tool("tool-mcp", "mcp-server", ToolType.MCP, AuthMode.API_KEY, TOOL_SECRET, ExecutionPolicy.ALWAYS_ASK);
        httpTool = tool("tool-http", "http-tool", ToolType.HTTP, AuthMode.OAUTH, TOOL_SECRET, ExecutionPolicy.ALWAYS_ALLOW);
        globalMcpTool = globalTool("gtool-mcp", "global-mcp-server", AuthMode.API_KEY, GLOBAL_TOOL_SECRET,
                ExecutionPolicy.ALWAYS_ALLOW);
    }

    // ------------------------------------------------------------------ content

    @Test
    void version_containsCompleteAgentConfiguration() {
        var snapshot = mapper.build(agent(false), rules(false), "tenant-a", "alice");

        assertThat(snapshot.getVersion()).startsWith("sha256:").hasSize("sha256:".length() + 64);
        assertSnapshotContext(snapshot);
        assertSnapshotAgent(snapshot);
        assertSnapshotVoice(snapshot);
        assertSnapshotProvider(snapshot);
        assertSnapshotModel(snapshot);
        assertSnapshotScaffoldAndSkills(snapshot);
        assertSnapshotMcpServers(snapshot);
        assertThat(snapshot.getCompatibility().getSchemaVersion()).isEqualTo(AgentConfigurationVersionMapper.SCHEMA_VERSION);
    }

    @Test
    void version_prefersGlobalScaffold() {
        var agent = agent(false);
        var globalScaffold = new GlobalScaffold();
        globalScaffold.setId("gscaffold-1");
        globalScaffold.setName("global-scaffold");
        globalScaffold.setSkills(Set.of(globalSkill("gskill-2", "z-skill"), globalSkill("gskill-1", "y-skill")));
        agent.setGlobalScaffold(globalScaffold);

        var snapshot = mapper.build(agent, List.of(), "tenant-a", null);

        assertThat(snapshot.getScaffold().getId()).isEqualTo("gscaffold-1");
        assertThat(snapshot.getScaffold().getSource()).isEqualTo(VersionComponentSourceDTOV1.GLOBAL);
        assertThat(snapshot.getSkills()).extracting(VersionSkillDTOV1::getName).containsExactly("y-skill", "z-skill");
    }

    @Test
    void version_minimalAgent() {
        var agent = new Agent();
        agent.setId("agent-min");

        var snapshot = mapper.build(agent, null, null, null);

        assertThat(snapshot.getVersion()).startsWith("sha256:");
        assertThat(snapshot.getProvider()).isNull();
        assertThat(snapshot.getModel()).isNull();
        assertThat(snapshot.getScaffold()).isNull();
        assertThat(snapshot.getSkills()).isEmpty();
        assertThat(snapshot.getMcpServers()).isEmpty();
        assertThat(snapshot.getVoice().getEnabled()).isFalse();
        assertThat(snapshot.getVoice().getLanguageCode()).isNull();
        assertThat(snapshot.getAgent().getA2aEnabled()).isFalse();
    }

    // ------------------------------------------------------------------ canonical hash

    @Test
    void hash_isStableForEquivalentConfigurationIndependentOfCollectionOrder() {
        var first = mapper.build(agent(false), rules(false), "tenant-a", "alice");
        var second = mapper.build(agent(true), rules(true), "tenant-a", "alice");

        assertThat(second.getVersion()).isEqualTo(first.getVersion());
        assertThat(CanonicalVersionHasher.canonicalBytes(second))
                .isEqualTo(CanonicalVersionHasher.canonicalBytes(first));
    }

    @Test
    void hash_excludesRequestContext() {
        var alice = mapper.build(agent(false), rules(false), "tenant-a", "alice");
        var bob = mapper.build(agent(false), rules(false), "tenant-a", "bob");

        assertThat(bob.getVersion()).isEqualTo(alice.getVersion());
        var canonical = new String(CanonicalVersionHasher.canonicalBytes(alice), StandardCharsets.UTF_8);
        assertThat(canonical.contains("alice")).isFalse();
        assertThat(canonical.contains("tenant-a")).isFalse();
        assertThat(canonical.contains("\"version\"")).isFalse();
    }

    @Test
    void hash_changesWhenConfigurationChanges() {
        var base = mapper.build(agent(false), rules(false), "t", null).getVersion();

        var changedPrompt = agent(false);
        changedPrompt.getScaffold().setSystemPrompt("other prompt");
        assertThat(mapper.build(changedPrompt, rules(false), "t", null).getVersion()).isNotEqualTo(base);

        var changedRules = rules(false);
        changedRules.get(0).setAllowed(ToolPermission.DENY);
        assertThat(mapper.build(agent(false), changedRules, "t", null).getVersion()).isNotEqualTo(base);

        var changedVoice = agent(false);
        changedVoice.setLanguageCode("en");
        assertThat(mapper.build(changedVoice, rules(false), "t", null).getVersion()).isNotEqualTo(base);
    }

    @Test
    void hash_isNotAffectedByCredentialValues() {
        var base = mapper.build(agent(false), rules(false), "t", null).getVersion();

        var rotated = agent(false);
        rotated.getModel().getProvider().setApiKey("rotated-secret");
        rotated.getTools().forEach(t -> t.setApiKey("rotated-tool-secret"));

        assertThat(mapper.build(rotated, rules(false), "t", null).getVersion()).isEqualTo(base);
    }

    @Test
    void canonicalBytes_haveSortedPropertiesAndNoNulls() {
        var snapshot = mapper.build(agent(false), rules(false), "t", null);
        var json = new String(CanonicalVersionHasher.canonicalBytes(snapshot), StandardCharsets.UTF_8);

        assertThat(json.contains("null")).isFalse();
        assertThat(json.contains("\n")).isFalse();
        assertThat(json.contains(": ")).isFalse();
        assertThat(json.indexOf("\"agent\"")).isLessThan(json.indexOf("\"compatibility\""));
        assertThat(json.indexOf("\"compatibility\"")).isLessThan(json.indexOf("\"mcpServers\""));
    }

    // ------------------------------------------------------------------ duplicate rules

    @Test
    void duplicateExactToolRules_invalidateVersion_independentOfOrder() {
        var duplicates = new ArrayList<>(rules(false));
        duplicates.add(rule("rule-read-2", mcpTool, null, "readItem", ToolPermission.DENY));
        var mappedAgent = agent(false);

        var reversed = new ArrayList<>(duplicates);
        Collections.reverse(reversed);

        for (var ruleList : List.of(duplicates, reversed)) {
            assertThatThrownBy(() -> mapper.build(mappedAgent, ruleList, "t", null))
                    .isInstanceOfSatisfying(VersionGenerationException.class, ex -> {
                        assertThat(ex.getErrorKey())
                                .isEqualTo(VersionGenerationException.ErrorKeys.DUPLICATE_TOOL_RULE);
                        assertThat(ex.getParams()).containsEntry("toolId", "tool-mcp")
                                .containsEntry("toolName", "readItem")
                                .containsEntry("ruleIds", "rule-read,rule-read-2")
                                .containsEntry("agentId", "agent-1");
                    });
        }
    }

    @Test
    void duplicateToolRules_withSamePermission_stillInvalidateVersion() {
        var duplicates = new ArrayList<>(rules(false));
        duplicates.add(rule("rule-read-2", mcpTool, null, "readItem", ToolPermission.ALWAYS_ALLOW));
        var mappedAgent = agent(false);

        assertThatThrownBy(() -> mapper.build(mappedAgent, duplicates, "t", null))
                .isInstanceOf(VersionGenerationException.class);
    }

    @Test
    void duplicateToolRules_onGlobalTool_invalidateVersion() {
        var duplicates = new ArrayList<>(rules(false));
        duplicates.add(rule("rule-global-2", null, globalMcpTool, "globalRead", ToolPermission.DENY));
        var mappedAgent = agent(false);

        assertThatThrownBy(() -> mapper.build(mappedAgent, duplicates, "t", null))
                .isInstanceOfSatisfying(VersionGenerationException.class,
                        ex -> assertThat(ex.getParams()).containsEntry("toolId", "gtool-mcp"));
    }

    @Test
    void duplicateToolRules_onSeveralServers_reportCanonicallyFirstServer_independentOfOrder() {
        for (var reversed : new boolean[] { false, true }) {
            var duplicates = new ArrayList<>(rules(reversed));
            duplicates.add(rule("rule-read-2", mcpTool, null, "readItem", ToolPermission.DENY));
            duplicates.add(rule("rule-global-2", null, globalMcpTool, "globalRead", ToolPermission.DENY));
            if (reversed) {
                Collections.reverse(duplicates);
            }
            var mappedAgent = agent(reversed);

            // "global-mcp-server" precedes "mcp-server" in canonical order (name -> source -> id)
            assertThatThrownBy(() -> mapper.build(mappedAgent, duplicates, "t", null))
                    .isInstanceOfSatisfying(VersionGenerationException.class,
                            ex -> assertThat(ex.getParams()).containsEntry("toolId", "gtool-mcp")
                                    .containsEntry("toolName", "globalRead")
                                    .containsEntry("ruleIds", "rule-global,rule-global-2"));
        }
    }

    @Test
    void sameToolNameOnDifferentServers_andCaseVariants_areNotDuplicates() {
        var rules = new ArrayList<>(rules(false));
        rules.add(rule("rule-global-read", null, globalMcpTool, "readItem", ToolPermission.DENY));
        rules.add(rule("rule-read-upper", mcpTool, null, "READITEM", ToolPermission.DENY));

        var snapshot = mapper.build(agent(false), rules, "t", null);

        assertThat(snapshot.getMcpServers().get(1).getToolRules()).extracting(VersionToolRuleDTOV1::getToolName)
                .containsExactly("READITEM", "createItem", "deleteItem", "readItem");
    }

    @Test
    void rulesOfUnassignedTools_areIgnored() {
        var foreignTool = tool("tool-foreign", "foreign", ToolType.MCP, null, null, null);
        var rules = new ArrayList<>(rules(false));
        rules.add(rule("rule-foreign-1", foreignTool, null, "x", ToolPermission.DENY));
        rules.add(rule("rule-foreign-2", foreignTool, null, "x", ToolPermission.DENY));

        var snapshot = mapper.build(agent(false), rules, "t", null);

        assertThat(snapshot.getMcpServers()).extracting(VersionToolServerDTOV1::getId)
                .doesNotContain("tool-foreign");
    }

    // ------------------------------------------------------------------ policy source

    @Test
    void legacyEntityEnumValues_areCanonicalized() {
        var localRules = rules(false);
        localRules.get(0).setAllowed(ToolPermission.ALLOW);
        localRules.get(2).setAllowed(ToolPermission.NEVER_ASK);
        mcpTool.setExecutionPolicy(ExecutionPolicy.NEVER_ASK);

        var snapshot = mapper.build(agent(false), localRules, "t", null);
        var server = server(snapshot, "tool-mcp");

        var read = rule(server, "readItem");
        assertThat(read.getPermission()).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ALLOW);
        assertThat(read.getPolicySource()).isEqualTo(VersionPolicySourceDTOV1.AGENT_TOOL_RULE);

        var create = rule(server, "createItem");
        assertThat(create.getPermission()).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ALLOW);
        assertThat(create.getPolicySource()).isEqualTo(VersionPolicySourceDTOV1.AGENT_TOOL_RULE);

        var delete = rule(server, "deleteItem");
        assertThat(delete.getPermission()).isEqualTo(VersionToolPermissionDTOV1.DENY);
        assertThat(delete.getPolicySource()).isEqualTo(VersionPolicySourceDTOV1.AGENT_TOOL_RULE);
    }

    @Test
    void permission_resolvedByExecutionPolicy_reportsExecutionSource() {
        var localRules = rules(false);
        mcpTool.setExecutionPolicy(ExecutionPolicy.ALWAYS_ASK);

        var snapshot = mapper.build(agent(false), localRules, "t", null);
        var server = server(snapshot, "tool-mcp");

        assertThat(rule(server, "readItem").getPermission()).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ASK);
        assertThat(rule(server, "readItem").getPolicySource())
                .isEqualTo(VersionPolicySourceDTOV1.TOOL_EXECUTION_POLICY);
    }

    @Test
    void nullRulePermission_fallsBackToSystemDefaultSource() {
        var localRules = rules(false);
        localRules.get(0).setAllowed(null);

        var snapshot = mapper.build(agent(false), localRules, "t", null);
        var server = server(snapshot, "tool-mcp");

        assertThat(rule(server, "readItem").getPermission()).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ASK);
        assertThat(rule(server, "readItem").getPolicySource()).isEqualTo(VersionPolicySourceDTOV1.SYSTEM_DEFAULT);
    }

    // ------------------------------------------------------------------ runtime-aligned enforcement semantics

    @Test
    void effectivePermission_combinesRuleAndExecutionPolicyLikeRuntime() {
        assertThat(AgentConfigurationVersionMapper.effectivePermission(ToolPermission.DENY, ExecutionPolicy.ALWAYS_ALLOW))
                .isEqualTo(VersionToolPermissionDTOV1.DENY);
        assertThat(AgentConfigurationVersionMapper.effectivePermission(ToolPermission.DENY, ExecutionPolicy.ALWAYS_ASK))
                .isEqualTo(VersionToolPermissionDTOV1.DENY);
        assertThat(AgentConfigurationVersionMapper.effectivePermission(ToolPermission.ALWAYS_ASK,
                ExecutionPolicy.ALWAYS_ALLOW)).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ASK);
        assertThat(AgentConfigurationVersionMapper.effectivePermission(ToolPermission.ALWAYS_ALLOW,
                ExecutionPolicy.ALWAYS_ASK)).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ASK);
        assertThat(AgentConfigurationVersionMapper.effectivePermission(ToolPermission.ALWAYS_ALLOW,
                ExecutionPolicy.ALWAYS_ALLOW)).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ALLOW);
    }

    @Test
    void rulePermission_respectsServerExecutionPolicy() {
        var snapshot = mapper.build(agent(false), rules(false), "t", null);

        // server policy ALWAYS_ASK: an allowed rule still requires confirmation
        var read = rule(server(snapshot, "tool-mcp"), "readItem");
        assertThat(read.getPermission()).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ASK);
        assertThat(read.getPolicySource()).isEqualTo(VersionPolicySourceDTOV1.TOOL_EXECUTION_POLICY);
        assertThat(rule(server(snapshot, "tool-mcp"), "deleteItem").getPermission())
                .isEqualTo(VersionToolPermissionDTOV1.DENY);

        // server policy ALWAYS_ALLOW: an allowed rule executes without confirmation
        assertThat(rule(server(snapshot, "gtool-mcp"), "globalRead").getPermission())
                .isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ALLOW);
    }

    @Test
    void unlistedTools_deniedWhenRulesExist() {
        var snapshot = mapper.build(agent(false), rules(false), "t", null);
        var server = server(snapshot, "tool-mcp");

        assertThat(server.getUnlistedToolPermission()).isEqualTo(VersionToolPermissionDTOV1.DENY);
        assertThat(server.getUnlistedToolPermissionSource()).isEqualTo(VersionPolicySourceDTOV1.SYSTEM_DEFAULT);
    }

    @Test
    void unlistedTools_withoutRules_followExecutionPolicy() {
        var noRules = mapper.build(agent(false), List.of(), "t", null);

        var mcp = server(noRules, "tool-mcp");
        assertThat(mcp.getToolRules()).isEmpty();
        assertThat(mcp.getUnlistedToolPermission()).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ASK);
        assertThat(mcp.getUnlistedToolPermissionSource()).isEqualTo(VersionPolicySourceDTOV1.TOOL_EXECUTION_POLICY);
        assertThat(server(noRules, "gtool-mcp").getUnlistedToolPermission())
                .isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ALLOW);
        assertThat(server(noRules, "gtool-mcp").getUnlistedToolPermissionSource())
                .isEqualTo(VersionPolicySourceDTOV1.TOOL_EXECUTION_POLICY);
    }

    @Test
    void unlistedTools_withoutRules_andDefaultExecutionPolicy_reportSystemDefault() {
        mcpTool.setExecutionPolicy(null);
        var snapshot = mapper.build(agent(false), List.of(), "t", null);
        var mcp = server(snapshot, "tool-mcp");

        assertThat(mcp.getUnlistedToolPermission()).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ASK);
        assertThat(mcp.getUnlistedToolPermissionSource()).isEqualTo(VersionPolicySourceDTOV1.SYSTEM_DEFAULT);
    }

    // ------------------------------------------------------------------ credentials

    @Test
    void rawCredentialsAndAuthorizationHeaders_areAbsent() throws Exception {
        var snapshot = mapper.build(agent(false), rules(false), "t", "alice");
        var json = objectMapper.writeValueAsString(snapshot);

        assertThat(json.contains(PROVIDER_SECRET)).isFalse();
        assertThat(json.contains(TOOL_SECRET)).isFalse();
        assertThat(json.contains(GLOBAL_TOOL_SECRET)).isFalse();
        assertThat(json).doesNotContainIgnoringCase("apiKey")
                .doesNotContainIgnoringCase("authorization")
                .doesNotContainIgnoringCase("bearer");
    }

    @Test
    void credentialReferences_arePublishedInline() {
        var snapshot = mapper.build(agent(false), rules(false), "t", null);

        assertThat(snapshot.getProvider().getCredentialRef()).isEqualTo("credential://provider/provider-1/api-key");
        assertThat(server(snapshot, "tool-mcp").getCredentialRef()).isEqualTo("credential://tool/tool-mcp/api-key");
        assertThat(server(snapshot, "gtool-mcp").getCredentialRef())
                .isEqualTo("credential://global-tool/gtool-mcp/api-key");
    }

    @Test
    void credentialReferences_followAuthModeAndSecretPresence() {
        var agent = agent(false);
        agent.getModel().getProvider().setApiKey(" ");
        mcpTool.setAuthMode(null);
        mcpTool.setApiKey(null);

        var snapshot = mapper.build(agent, rules(false), "t", null);

        // API_KEY auth keeps a credential reference even when secret content is blank.
        assertThat(snapshot.getProvider().getCredentialRef()).isEqualTo("credential://provider/provider-1/api-key");
        assertThat(server(snapshot, "tool-mcp").getCredentialRef()).isNull();
    }

    private static void assertSnapshotContext(AgentConfigurationVersionDTOV1 snapshot) {
        assertThat(snapshot.getContext().getTenantId()).isEqualTo("tenant-a");
        assertThat(snapshot.getContext().getPrincipal()).isEqualTo("alice");
    }

    private static void assertSnapshotAgent(AgentConfigurationVersionDTOV1 snapshot) {
        assertThat(snapshot.getAgent().getId()).isEqualTo("agent-1");
        assertThat(snapshot.getAgent().getName()).isEqualTo("agent");
        assertThat(snapshot.getAgent().getAdditionalPrompt()).isEqualTo("be precise");
        assertThat(snapshot.getAgent().getStatus()).isEqualTo("LIVE");
        assertThat(snapshot.getAgent().getA2aEnabled()).isTrue();
    }

    private static void assertSnapshotVoice(AgentConfigurationVersionDTOV1 snapshot) {
        assertThat(snapshot.getVoice().getEnabled()).isTrue();
        assertThat(snapshot.getVoice().getLanguageCode()).isEqualTo("de");
    }

    private static void assertSnapshotProvider(AgentConfigurationVersionDTOV1 snapshot) {
        assertThat(snapshot.getProvider().getId()).isEqualTo("provider-1");
        assertThat(snapshot.getProvider().getType()).isEqualTo("OPENAI");
        assertThat(snapshot.getProvider().getLlmUrl()).isEqualTo("http://llm");
        assertThat(snapshot.getProvider().getCredentialRef()).isEqualTo("credential://provider/provider-1/api-key");
    }

    private static void assertSnapshotModel(AgentConfigurationVersionDTOV1 snapshot) {
        assertThat(snapshot.getModel().getId()).isEqualTo("model-1");
        assertThat(snapshot.getModel().getModelIdentifier()).isEqualTo("gpt-4.1");
        assertThat(snapshot.getModel().getModelConfig()).isEqualTo("temperature=0.2");
        assertThat(snapshot.getModel().getCommunicationMode()).isEqualTo("SYNC");
        assertThat(snapshot.getModel().getProviderId()).isEqualTo("provider-1");
    }

    private static void assertSnapshotScaffoldAndSkills(AgentConfigurationVersionDTOV1 snapshot) {
        assertThat(snapshot.getScaffold().getId()).isEqualTo("scaffold-1");
        assertThat(snapshot.getScaffold().getSystemPrompt()).isEqualTo("system prompt");
        assertThat(snapshot.getScaffold().getSource()).isEqualTo(VersionComponentSourceDTOV1.TENANT);
        assertThat(snapshot.getSkills()).extracting(VersionSkillDTOV1::getName)
                .containsExactly("a-skill", "b-global-skill", "c-skill");
        assertThat(snapshot.getSkills()).extracting(VersionSkillDTOV1::getPosition).containsExactly(0, 1, 2);
        assertThat(snapshot.getSkills().get(1).getSource()).isEqualTo(VersionComponentSourceDTOV1.GLOBAL);
    }

    private static void assertSnapshotMcpServers(AgentConfigurationVersionDTOV1 snapshot) {
        assertThat(snapshot.getMcpServers()).extracting(VersionToolServerDTOV1::getName)
                .containsExactly("global-mcp-server", "mcp-server");

        var server = snapshot.getMcpServers().get(1);
        assertThat(server.getUrl()).isEqualTo("http://tool-mcp");
        assertThat(server.getSource()).isEqualTo(VersionComponentSourceDTOV1.TENANT);
        assertThat(server.getToolRules()).extracting(VersionToolRuleDTOV1::getToolName)
                .containsExactly("createItem", "deleteItem", "readItem");
        assertThat(server.getToolRules()).extracting(VersionToolRuleDTOV1::getPermission)
                .containsExactly(VersionToolPermissionDTOV1.ALWAYS_ASK, VersionToolPermissionDTOV1.DENY,
                        VersionToolPermissionDTOV1.ALWAYS_ASK);
        assertThat(server.getToolRules()).extracting(VersionToolRuleDTOV1::getPolicySource)
                .containsExactly(VersionPolicySourceDTOV1.AGENT_TOOL_RULE, VersionPolicySourceDTOV1.AGENT_TOOL_RULE,
                        VersionPolicySourceDTOV1.TOOL_EXECUTION_POLICY);
        assertThat(server.getUnlistedToolPermission()).isEqualTo(VersionToolPermissionDTOV1.DENY);
        assertThat(server.getUnlistedToolPermissionSource()).isEqualTo(VersionPolicySourceDTOV1.SYSTEM_DEFAULT);
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * @param reversed build every collection in reverse insertion order
     */
    private Agent agent(boolean reversed) {
        var provider = new Provider();
        provider.setId("provider-1");
        provider.setName("provider");
        provider.setType(ProviderType.OPENAI);
        provider.setLlmUrl("http://llm");
        provider.setApiKey(PROVIDER_SECRET);
        provider.setAuthMode(AuthMode.API_KEY);

        var model = new Model();
        model.setId("model-1");
        model.setName("model");
        model.setModelIdentifier("gpt-4.1");
        model.setModelConfig("temperature=0.2");
        model.setCommunicationMode(CommunicationMode.SYNC);
        model.setProvider(provider);

        var scaffold = new Scaffold();
        scaffold.setId("scaffold-1");
        scaffold.setName("scaffold");
        scaffold.setSystemPrompt("system prompt");
        scaffold.setSkills(ordered(reversed, skill("skill-c", "c-skill"), skill("skill-a", "a-skill")));
        scaffold.setGlobalSkills(ordered(reversed, globalSkill("gskill-b", "b-global-skill")));

        var agent = new Agent();
        agent.setId("agent-1");
        agent.setTenantId("tenant-a");
        agent.setName("agent");
        agent.setDescription("description");
        agent.setAdditionalPrompt("be precise");
        agent.setA2aEnabled(true);
        agent.setVoiceEnabled(true);
        agent.setLanguageCode(" DE ");
        agent.setStatus(AgentStatus.LIVE);
        agent.setModel(model);
        agent.setScaffold(scaffold);
        agent.setTools(ordered(reversed, mcpTool, httpTool));
        agent.setGlobalTools(ordered(reversed, globalMcpTool));
        return agent;
    }

    private List<AgentMcpToolRule> rules(boolean reversed) {
        var rules = new ArrayList<>(List.of(
                rule("rule-read", mcpTool, null, "readItem", ToolPermission.ALWAYS_ALLOW),
                rule("rule-delete", mcpTool, null, "deleteItem", ToolPermission.DENY),
                rule("rule-create", mcpTool, null, "createItem", ToolPermission.ALWAYS_ASK),
                rule("rule-global", null, globalMcpTool, "globalRead", ToolPermission.ALWAYS_ALLOW)));
        if (reversed) {
            Collections.reverse(rules);
        }
        return rules;
    }

    @SafeVarargs
    private static <T> Set<T> ordered(boolean reversed, T... items) {
        var list = new ArrayList<>(List.of(items));
        if (reversed) {
            Collections.reverse(list);
        }
        return new LinkedHashSet<>(list);
    }

    private static Tool tool(String id, String name, ToolType type, AuthMode authMode, String apiKey,
            ExecutionPolicy policy) {
        var tool = new Tool();
        tool.setId(id);
        tool.setName(name);
        tool.setDescription(name + " description");
        tool.setType(type);
        tool.setUrl("http://" + id);
        tool.setAuthMode(authMode);
        tool.setApiKey(apiKey);
        tool.setExecutionPolicy(policy);
        return tool;
    }

    private static GlobalTool globalTool(String id, String name, AuthMode authMode, String apiKey,
            ExecutionPolicy policy) {
        var tool = new GlobalTool();
        tool.setId(id);
        tool.setName(name);
        tool.setType(ToolType.MCP);
        tool.setUrl("http://" + id);
        tool.setAuthMode(authMode);
        tool.setApiKey(apiKey);
        tool.setExecutionPolicy(policy);
        return tool;
    }

    private static Skill skill(String id, String name) {
        var skill = new Skill();
        skill.setId(id);
        skill.setName(name);
        skill.setInstruction(name + " instruction");
        return skill;
    }

    private static GlobalSkill globalSkill(String id, String name) {
        var skill = new GlobalSkill();
        skill.setId(id);
        skill.setName(name);
        skill.setInstruction(name + " instruction");
        return skill;
    }

    private static AgentMcpToolRule rule(String id, Tool tool, GlobalTool globalTool, String toolName,
            ToolPermission permission) {
        var rule = new AgentMcpToolRule();
        rule.setId(id);
        rule.setTool(tool);
        rule.setGlobalTool(globalTool);
        rule.setToolName(toolName);
        rule.setAllowed(permission);
        return rule;
    }

    private static VersionToolServerDTOV1 server(AgentConfigurationVersionDTOV1 versionPayload, String id) {
        return versionPayload.getMcpServers().stream().filter(s -> id.equals(s.getId())).findFirst().orElseThrow();
    }

    private static VersionToolRuleDTOV1 rule(VersionToolServerDTOV1 server, String toolName) {
        return server.getToolRules().stream().filter(r -> toolName.equals(r.getToolName())).findFirst()
                .orElseThrow();
    }
}
