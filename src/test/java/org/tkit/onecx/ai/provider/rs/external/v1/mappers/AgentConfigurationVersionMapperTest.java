package org.tkit.onecx.ai.provider.rs.external.v1.mappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tkit.onecx.ai.provider.common.services.version.CanonicalVersionHasher;
import org.tkit.onecx.ai.provider.common.services.version.StoredPolicyValues;
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
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionCredentialKindDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionCredentialReferenceDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionExecutionPolicyDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionPolicyNormalizationDTOV1;
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
        var snapshot = mapper.build(agent(false), rules(false), StoredPolicyValues.empty(), "tenant-a", "alice");

        assertThat(snapshot.getVersion()).startsWith("sha256:").hasSize("sha256:".length() + 64);
        assertThat(snapshot.getContext().getTenantId()).isEqualTo("tenant-a");
        assertThat(snapshot.getContext().getPrincipal()).isEqualTo("alice");

        assertThat(snapshot.getAgent().getId()).isEqualTo("agent-1");
        assertThat(snapshot.getAgent().getName()).isEqualTo("agent");
        assertThat(snapshot.getAgent().getAdditionalPrompt()).isEqualTo("be precise");
        assertThat(snapshot.getAgent().getStatus()).isEqualTo("LIVE");
        assertThat(snapshot.getAgent().getA2aEnabled()).isTrue();

        assertThat(snapshot.getVoice().getEnabled()).isTrue();
        assertThat(snapshot.getVoice().getLanguageCode()).isEqualTo("de");

        assertThat(snapshot.getProvider().getId()).isEqualTo("provider-1");
        assertThat(snapshot.getProvider().getType()).isEqualTo("OPENAI");
        assertThat(snapshot.getProvider().getLlmUrl()).isEqualTo("http://llm");
        assertThat(snapshot.getProvider().getCredentialRef()).isEqualTo("credential://provider/provider-1/api-key");

        assertThat(snapshot.getModel().getId()).isEqualTo("model-1");
        assertThat(snapshot.getModel().getModelIdentifier()).isEqualTo("gpt-4.1");
        assertThat(snapshot.getModel().getModelConfig()).isEqualTo("temperature=0.2");
        assertThat(snapshot.getModel().getCommunicationMode()).isEqualTo("SYNC");
        assertThat(snapshot.getModel().getProviderId()).isEqualTo("provider-1");

        assertThat(snapshot.getScaffold().getId()).isEqualTo("scaffold-1");
        assertThat(snapshot.getScaffold().getSystemPrompt()).isEqualTo("system prompt");
        assertThat(snapshot.getScaffold().getSource()).isEqualTo(VersionComponentSourceDTOV1.TENANT);

        assertThat(snapshot.getSkills()).extracting(VersionSkillDTOV1::getName)
                .containsExactly("a-skill", "b-global-skill", "c-skill");
        assertThat(snapshot.getSkills()).extracting(VersionSkillDTOV1::getPosition).containsExactly(0, 1, 2);
        assertThat(snapshot.getSkills().get(1).getSource()).isEqualTo(VersionComponentSourceDTOV1.GLOBAL);

        assertThat(snapshot.getMcpServers()).extracting(VersionToolServerDTOV1::getName)
                .containsExactly("global-mcp-server", "mcp-server");
        assertThat(snapshot.getTools()).extracting(VersionToolServerDTOV1::getName).containsExactly("http-tool");

        var server = snapshot.getMcpServers().get(1);
        assertThat(server.getUrl()).isEqualTo("http://tool-mcp");
        assertThat(server.getSource()).isEqualTo(VersionComponentSourceDTOV1.TENANT);
        assertThat(server.getExecutionPolicy()).isEqualTo(VersionExecutionPolicyDTOV1.ALWAYS_ASK);
        assertThat(server.getToolRules()).extracting(VersionToolRuleDTOV1::getToolName)
                .containsExactly("createItem", "deleteItem", "readItem");
        assertThat(server.getToolRules()).extracting(VersionToolRuleDTOV1::getPermission)
                .containsExactly(VersionToolPermissionDTOV1.ALWAYS_ASK, VersionToolPermissionDTOV1.DENY,
                        VersionToolPermissionDTOV1.ALWAYS_ALLOW);
        assertThat(server.getToolRules().get(1).getProvenance().getSource())
                .isEqualTo(VersionPolicySourceDTOV1.AGENT_TOOL_RULE);
        assertThat(server.getToolRules().get(1).getProvenance().getSourceId()).isEqualTo("rule-delete");
        assertThat(server.getExecutionPolicyProvenance().getSource())
                .isEqualTo(VersionPolicySourceDTOV1.TOOL_EXECUTION_POLICY);
        assertThat(server.getExecutionPolicyProvenance().getSourceId()).isEqualTo("tool-mcp");

        assertThat(snapshot.getCompatibility().getSchemaVersion())
                .isEqualTo(AgentConfigurationVersionMapper.SCHEMA_VERSION);
        assertThat(snapshot.getCompatibility().getHashAlgorithm()).isEqualTo("SHA-256");
        assertThat(snapshot.getCompatibility().getCanonicalization())
                .isEqualTo(CanonicalVersionHasher.CANONICALIZATION);
        assertThat(snapshot.getCompatibility().getRequiredCapabilities()).containsExactly("a2a", "mcp",
                "mcp-tool-rules", "principal-token-propagation", "voice");
    }

    @Test
    void version_prefersGlobalScaffold() {
        var agent = agent(false);
        var globalScaffold = new GlobalScaffold();
        globalScaffold.setId("gscaffold-1");
        globalScaffold.setName("global-scaffold");
        globalScaffold.setSkills(Set.of(globalSkill("gskill-2", "z-skill"), globalSkill("gskill-1", "y-skill")));
        agent.setGlobalScaffold(globalScaffold);

        var snapshot = mapper.build(agent, List.of(), null, "tenant-a", null);

        assertThat(snapshot.getScaffold().getId()).isEqualTo("gscaffold-1");
        assertThat(snapshot.getScaffold().getSource()).isEqualTo(VersionComponentSourceDTOV1.GLOBAL);
        assertThat(snapshot.getSkills()).extracting(VersionSkillDTOV1::getName).containsExactly("y-skill", "z-skill");
    }

    @Test
    void version_minimalAgent() {
        var agent = new Agent();
        agent.setId("agent-min");

        var snapshot = mapper.build(agent, null, null, null, null);

        assertThat(snapshot.getVersion()).startsWith("sha256:");
        assertThat(snapshot.getProvider()).isNull();
        assertThat(snapshot.getModel()).isNull();
        assertThat(snapshot.getScaffold()).isNull();
        assertThat(snapshot.getSkills()).isEmpty();
        assertThat(snapshot.getMcpServers()).isEmpty();
        assertThat(snapshot.getTools()).isEmpty();
        assertThat(snapshot.getCredentials()).isEmpty();
        assertThat(snapshot.getVoice().getEnabled()).isFalse();
        assertThat(snapshot.getVoice().getLanguageCode()).isNull();
        assertThat(snapshot.getAgent().getA2aEnabled()).isFalse();
        assertThat(snapshot.getCompatibility().getRequiredCapabilities()).isEmpty();
    }

    // ------------------------------------------------------------------ canonical hash

    @Test
    void hash_isStableForEquivalentConfigurationIndependentOfCollectionOrder() {
        var first = mapper.build(agent(false), rules(false), StoredPolicyValues.empty(), "tenant-a", "alice");
        var second = mapper.build(agent(true), rules(true), StoredPolicyValues.empty(), "tenant-a", "alice");

        assertThat(second.getVersion()).isEqualTo(first.getVersion());
        assertThat(CanonicalVersionHasher.canonicalBytes(second))
                .isEqualTo(CanonicalVersionHasher.canonicalBytes(first));
    }

    @Test
    void hash_excludesRequestContext() {
        var alice = mapper.build(agent(false), rules(false), StoredPolicyValues.empty(), "tenant-a", "alice");
        var bob = mapper.build(agent(false), rules(false), StoredPolicyValues.empty(), "tenant-a", "bob");

        assertThat(bob.getVersion()).isEqualTo(alice.getVersion());
        assertThat(new String(CanonicalVersionHasher.canonicalBytes(alice), StandardCharsets.UTF_8))
                .doesNotContain("alice", "tenant-a", "\"version\"");
    }

    @Test
    void hash_changesWhenConfigurationChanges() {
        var base = mapper.build(agent(false), rules(false), StoredPolicyValues.empty(), "t", null).getVersion();

        var changedPrompt = agent(false);
        changedPrompt.getScaffold().setSystemPrompt("other prompt");
        assertThat(mapper.build(changedPrompt, rules(false), null, "t", null).getVersion()).isNotEqualTo(base);

        var changedRules = rules(false);
        changedRules.get(0).setAllowed(ToolPermission.DENY);
        assertThat(mapper.build(agent(false), changedRules, null, "t", null).getVersion()).isNotEqualTo(base);

        var changedVoice = agent(false);
        changedVoice.setLanguageCode("en");
        assertThat(mapper.build(changedVoice, rules(false), null, "t", null).getVersion()).isNotEqualTo(base);
    }

    @Test
    void hash_isNotAffectedByCredentialValues() {
        var base = mapper.build(agent(false), rules(false), null, "t", null).getVersion();

        var rotated = agent(false);
        rotated.getModel().getProvider().setApiKey("rotated-secret");
        rotated.getTools().forEach(t -> t.setApiKey("rotated-tool-secret"));

        assertThat(mapper.build(rotated, rules(false), null, "t", null).getVersion()).isEqualTo(base);
    }

    @Test
    void canonicalBytes_haveSortedPropertiesAndNoNulls() {
        var snapshot = mapper.build(agent(false), rules(false), null, "t", null);
        var json = new String(CanonicalVersionHasher.canonicalBytes(snapshot), StandardCharsets.UTF_8);

        assertThat(json).doesNotContain("null").doesNotContain("\n").doesNotContain(": ");
        assertThat(json.indexOf("\"agent\"")).isLessThan(json.indexOf("\"compatibility\""));
        assertThat(json.indexOf("\"compatibility\"")).isLessThan(json.indexOf("\"credentials\""));
        assertThat(json.indexOf("\"credentials\"")).isLessThan(json.indexOf("\"mcpServers\""));
    }

    // ------------------------------------------------------------------ duplicate rules

    @Test
    void duplicateExactToolRules_invalidateVersion_independentOfOrder() {
        var duplicates = new ArrayList<>(rules(false));
        duplicates.add(rule("rule-read-2", mcpTool, null, "readItem", ToolPermission.DENY));

        var reversed = new ArrayList<>(duplicates);
        Collections.reverse(reversed);

        for (var ruleList : List.of(duplicates, reversed)) {
            assertThatThrownBy(() -> mapper.build(agent(false), ruleList, null, "t", null))
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

        assertThatThrownBy(() -> mapper.build(agent(false), duplicates, null, "t", null))
                .isInstanceOf(VersionGenerationException.class);
    }

    @Test
    void duplicateToolRules_onGlobalTool_invalidateVersion() {
        var duplicates = new ArrayList<>(rules(false));
        duplicates.add(rule("rule-global-2", null, globalMcpTool, "globalRead", ToolPermission.DENY));

        assertThatThrownBy(() -> mapper.build(agent(false), duplicates, null, "t", null))
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

            // "global-mcp-server" precedes "mcp-server" in canonical order (name -> source -> id)
            assertThatThrownBy(() -> mapper.build(agent(reversed), duplicates, null, "t", null))
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

        var snapshot = mapper.build(agent(false), rules, null, "t", null);

        assertThat(snapshot.getMcpServers().get(1).getToolRules()).extracting(VersionToolRuleDTOV1::getToolName)
                .containsExactly("READITEM", "createItem", "deleteItem", "readItem");
    }

    @Test
    void rulesOfUnassignedTools_areIgnored() {
        var foreignTool = tool("tool-foreign", "foreign", ToolType.MCP, null, null, null);
        var rules = new ArrayList<>(rules(false));
        rules.add(rule("rule-foreign-1", foreignTool, null, "x", ToolPermission.DENY));
        rules.add(rule("rule-foreign-2", foreignTool, null, "x", ToolPermission.DENY));

        var snapshot = mapper.build(agent(false), rules, null, "t", null);

        assertThat(snapshot.getMcpServers()).extracting(VersionToolServerDTOV1::getId)
                .doesNotContain("tool-foreign");
    }

    // ------------------------------------------------------------------ policy normalization / provenance

    @Test
    void legacyPolicyValues_areNormalized_withProvenance() {
        var stored = new StoredPolicyValues(
                Map.of("rule-read", "ALLOW", "rule-create", "NEVER_ASK", "rule-delete", "DENY"),
                Map.of("tool-mcp", "NEVER_ASK", "gtool-mcp", "ALWAYS_ALLOW"));

        var snapshot = mapper.build(agent(false), rules(false), stored, "t", null);
        var server = server(snapshot, "tool-mcp");

        assertThat(server.getExecutionPolicy()).isEqualTo(VersionExecutionPolicyDTOV1.ALWAYS_ALLOW);
        assertThat(server.getExecutionPolicyProvenance().getNormalization())
                .isEqualTo(VersionPolicyNormalizationDTOV1.LEGACY_ALIAS);
        assertThat(server.getExecutionPolicyProvenance().getStoredValue()).isEqualTo("NEVER_ASK");

        var read = rule(server, "readItem");
        assertThat(read.getPermission()).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ALLOW);
        assertThat(read.getProvenance().getNormalization()).isEqualTo(VersionPolicyNormalizationDTOV1.LEGACY_ALIAS);
        assertThat(read.getProvenance().getStoredValue()).isEqualTo("ALLOW");

        var create = rule(server, "createItem");
        assertThat(create.getPermission()).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ALLOW);
        assertThat(create.getProvenance().getStoredValue()).isEqualTo("NEVER_ASK");

        var delete = rule(server, "deleteItem");
        assertThat(delete.getPermission()).isEqualTo(VersionToolPermissionDTOV1.DENY);
        assertThat(delete.getProvenance().getNormalization()).isEqualTo(VersionPolicyNormalizationDTOV1.NONE);
        assertThat(delete.getProvenance().getStoredValue()).isNull();

        var global = server(snapshot, "gtool-mcp");
        assertThat(global.getExecutionPolicyProvenance().getNormalization())
                .isEqualTo(VersionPolicyNormalizationDTOV1.NONE);
    }

    @Test
    void legacyEntityEnumValues_areNormalized() {
        var rules = rules(false);
        rules.get(0).setAllowed(ToolPermission.ALLOW);
        var agent = agent(false);
        mcpTool.setExecutionPolicy(ExecutionPolicy.NEVER_ASK);

        var snapshot = mapper.build(agent, rules, null, "t", null);
        var server = server(snapshot, "tool-mcp");

        assertThat(server.getExecutionPolicy()).isEqualTo(VersionExecutionPolicyDTOV1.ALWAYS_ALLOW);
        assertThat(server.getExecutionPolicyProvenance().getStoredValue()).isEqualTo("NEVER_ASK");
        assertThat(rule(server, "readItem").getPermission()).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ALLOW);
        assertThat(rule(server, "readItem").getProvenance().getNormalization())
                .isEqualTo(VersionPolicyNormalizationDTOV1.LEGACY_ALIAS);
    }

    @Test
    void unknownPolicyValues_areDenied() {
        var stored = new StoredPolicyValues(Map.of("rule-read", "ALLOW_EVERYTHING"),
                Map.of("tool-mcp", "AUTO_APPROVE"));

        var snapshot = mapper.build(agent(false), rules(false), stored, "t", null);
        var server = server(snapshot, "tool-mcp");

        var read = rule(server, "readItem");
        assertThat(read.getPermission()).isEqualTo(VersionToolPermissionDTOV1.DENY);
        assertThat(read.getProvenance().getNormalization())
                .isEqualTo(VersionPolicyNormalizationDTOV1.UNKNOWN_VALUE_DENIED);
        assertThat(read.getProvenance().getStoredValue()).isEqualTo("ALLOW_EVERYTHING");

        assertThat(server.getExecutionPolicy()).isEqualTo(VersionExecutionPolicyDTOV1.ALWAYS_ASK);
        assertThat(server.getExecutionPolicyProvenance().getNormalization())
                .isEqualTo(VersionPolicyNormalizationDTOV1.UNKNOWN_VALUE_DENIED);
        assertThat(server.getExecutionPolicyProvenance().getStoredValue()).isEqualTo("AUTO_APPROVE");
    }

    @Test
    void missingPolicyValues_defaultToAsk_withSystemDefaultProvenance() {
        var toolPermissions = new HashMap<String, String>();
        toolPermissions.put("rule-read", null);
        var executionPolicies = new HashMap<String, String>();
        executionPolicies.put("tool-mcp", null);

        var snapshot = mapper.build(agent(false), rules(false),
                new StoredPolicyValues(toolPermissions, executionPolicies), "t", null);
        var server = server(snapshot, "tool-mcp");

        assertThat(server.getExecutionPolicy()).isEqualTo(VersionExecutionPolicyDTOV1.ALWAYS_ASK);
        assertThat(server.getExecutionPolicyProvenance().getSource()).isEqualTo(VersionPolicySourceDTOV1.SYSTEM_DEFAULT);
        assertThat(server.getExecutionPolicyProvenance().getSourceId()).isNull();
        assertThat(server.getExecutionPolicyProvenance().getNormalization())
                .isEqualTo(VersionPolicyNormalizationDTOV1.MISSING_VALUE_DEFAULTED);
        assertThat(rule(server, "readItem").getPermission()).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ASK);
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
    void ruleEffectivePermission_respectsServerExecutionPolicy() {
        var snapshot = mapper.build(agent(false), rules(false), null, "t", null);

        // server policy ALWAYS_ASK: an allowed rule still requires confirmation
        var read = rule(server(snapshot, "tool-mcp"), "readItem");
        assertThat(read.getPermission()).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ALLOW);
        assertThat(read.getEffectivePermission()).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ASK);
        assertThat(rule(server(snapshot, "tool-mcp"), "deleteItem").getEffectivePermission())
                .isEqualTo(VersionToolPermissionDTOV1.DENY);

        // server policy ALWAYS_ALLOW: an allowed rule executes without confirmation
        assertThat(rule(server(snapshot, "gtool-mcp"), "globalRead").getEffectivePermission())
                .isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ALLOW);
    }

    @Test
    void unlistedTools_deniedWhenRulesExist() {
        var snapshot = mapper.build(agent(false), rules(false), null, "t", null);
        var server = server(snapshot, "tool-mcp");

        assertThat(server.getUnlistedToolPermission()).isEqualTo(VersionToolPermissionDTOV1.DENY);
        assertThat(server.getUnlistedToolPermissionProvenance().getSource())
                .isEqualTo(VersionPolicySourceDTOV1.ALLOW_LIST_DEFAULT);
    }

    @Test
    void unlistedTools_withoutRules_followLegacyAllowAll() {
        var noRules = mapper.build(agent(false), List.of(), null, "t", null, true);

        var mcp = server(noRules, "tool-mcp");
        assertThat(mcp.getToolRules()).isEmpty();
        assertThat(mcp.getUnlistedToolPermission()).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ASK);
        assertThat(mcp.getUnlistedToolPermissionProvenance().getSource())
                .isEqualTo(VersionPolicySourceDTOV1.LEGACY_ALLOW_ALL);
        assertThat(server(noRules, "gtool-mcp").getUnlistedToolPermission())
                .isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ALLOW);

        var denied = mapper.build(agent(false), List.of(), null, "t", null, false);
        var deniedServer = server(denied, "tool-mcp");
        assertThat(deniedServer.getUnlistedToolPermission()).isEqualTo(VersionToolPermissionDTOV1.DENY);
        assertThat(deniedServer.getUnlistedToolPermissionProvenance().getSource())
                .isEqualTo(VersionPolicySourceDTOV1.SYSTEM_DEFAULT);

        assertThat(denied.getVersion()).isNotEqualTo(noRules.getVersion());
    }

    // ------------------------------------------------------------------ credentials

    @Test
    void rawCredentialsAndAuthorizationHeaders_areAbsent() throws Exception {
        var snapshot = mapper.build(agent(false), rules(false), null, "t", "alice");
        var json = objectMapper.writeValueAsString(snapshot);

        assertThat(json).doesNotContain(PROVIDER_SECRET, TOOL_SECRET, GLOBAL_TOOL_SECRET)
                .doesNotContainIgnoringCase("apiKey")
                .doesNotContainIgnoringCase("authorization")
                .doesNotContainIgnoringCase("bearer");
    }

    @Test
    void credentialReferences_areCanonical() {
        var snapshot = mapper.build(agent(false), rules(false), null, "t", null);

        assertThat(snapshot.getCredentials()).extracting(VersionCredentialReferenceDTOV1::getRef).containsExactly(
                "credential://global-tool/gtool-mcp/api-key",
                "credential://provider/provider-1/api-key",
                "credential://tool/tool-http/principal-token",
                "credential://tool/tool-mcp/api-key");
        var httpServer = snapshot.getTools().get(0);
        assertThat(httpServer.getCredentialRef()).isEqualTo("credential://tool/tool-http/principal-token");
        assertThat(snapshot.getCredentials().get(2).getKind()).isEqualTo(VersionCredentialKindDTOV1.PRINCIPAL_TOKEN);
        assertThat(snapshot.getCredentials()).allSatisfy(c -> assertThat(c.getConfigured()).isTrue());
    }

    @Test
    void credentialReferences_reportMissingSecretAndNoAuth() {
        var agent = agent(false);
        agent.getModel().getProvider().setApiKey(" ");
        mcpTool.setAuthMode(null);
        mcpTool.setApiKey(null);

        var snapshot = mapper.build(agent, rules(false), null, "t", null);

        var provider = snapshot.getCredentials().stream()
                .filter(c -> c.getRef().equals(snapshot.getProvider().getCredentialRef())).findFirst().orElseThrow();
        assertThat(provider.getKind()).isEqualTo(VersionCredentialKindDTOV1.STORED_SECRET);
        assertThat(provider.getConfigured()).isFalse();
        assertThat(server(snapshot, "tool-mcp").getCredentialRef()).isNull();
        assertThat(snapshot.getCredentials()).noneMatch(c -> "tool-mcp".equals(c.getOwnerId()));
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
