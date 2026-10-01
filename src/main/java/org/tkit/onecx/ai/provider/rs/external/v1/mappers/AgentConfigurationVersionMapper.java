package org.tkit.onecx.ai.provider.rs.external.v1.mappers;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import jakarta.enterprise.context.ApplicationScoped;

import org.tkit.onecx.ai.provider.common.services.version.CanonicalVersionHasher;
import org.tkit.onecx.ai.provider.common.services.version.PolicyNormalizer;
import org.tkit.onecx.ai.provider.common.services.version.StoredPolicyValues;
import org.tkit.onecx.ai.provider.common.services.version.VersionGenerationException;
import org.tkit.onecx.ai.provider.domain.models.AbstractSkill;
import org.tkit.onecx.ai.provider.domain.models.AbstractTool;
import org.tkit.onecx.ai.provider.domain.models.Agent;
import org.tkit.onecx.ai.provider.domain.models.AgentMcpToolRule;
import org.tkit.onecx.ai.provider.domain.models.GlobalScaffold;
import org.tkit.onecx.ai.provider.domain.models.Model;
import org.tkit.onecx.ai.provider.domain.models.Provider;
import org.tkit.onecx.ai.provider.domain.models.Scaffold;
import org.tkit.onecx.ai.provider.domain.models.enums.AuthMode;
import org.tkit.onecx.ai.provider.domain.models.enums.ExecutionPolicy;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolPermission;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolType;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentConfigurationVersionDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionAgentDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionCompatibilityDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionComponentSourceDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionContextDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionCredentialKindDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionCredentialOwnerTypeDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionCredentialReferenceDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionExecutionPolicyDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionModelDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionPolicyNormalizationDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionPolicyProvenanceDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionPolicySourceDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionProviderDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionScaffoldDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionSkillDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionToolPermissionDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionToolRuleDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionToolServerDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionVoiceSettingsDTOV1;

/**
 * Builds the canonical, immutable Agent Configuration version payload.
 * <p>
 * Every collection is ordered canonically so that equivalent configuration always results in the same
 * version payload
 * version, independent of the (unordered) collections loaded from the database. Raw credentials are never copied,
 * only credential references are published.
 */
@ApplicationScoped
public class AgentConfigurationVersionMapper {

    public static final String SCHEMA_VERSION = "1.0.0";

    public static final String CAPABILITY_A2A = "a2a";
    public static final String CAPABILITY_MCP = "mcp";
    public static final String CAPABILITY_MCP_TOOL_RULES = "mcp-tool-rules";
    public static final String CAPABILITY_PRINCIPAL_TOKEN = "principal-token-propagation";
    public static final String CAPABILITY_VOICE = "voice";

    static final String CREDENTIAL_REF_PREFIX = "credential://";

    private static final Comparator<String> STRING_ORDER = nullsFirst(naturalOrder());

    private static final Comparator<VersionSkillDTOV1> SKILL_ORDER = comparing(VersionSkillDTOV1::getName, STRING_ORDER)
            .thenComparing(s -> s.getSource().name())
            .thenComparing(VersionSkillDTOV1::getId, STRING_ORDER);

    private static final Comparator<ToolServerSource> SERVER_ORDER = comparing(
            (ToolServerSource s) -> s.tool().getName(), STRING_ORDER)
            .thenComparing(s -> s.source().name())
            .thenComparing(s -> s.tool().getId(), STRING_ORDER);

    /**
     * Tool assigned to the agent together with the agent rules for it.
     */
    private record ToolServerSource(AbstractTool tool, VersionComponentSourceDTOV1 source,
            VersionCredentialOwnerTypeDTOV1 ownerType, List<AgentMcpToolRule> rules) {
    }

    /**
     * Default of {@code onecx.ai.provider.version.legacy-allow-all}, aligned with the runtime default.
     */
    public static final boolean DEFAULT_LEGACY_ALLOW_ALL = true;

    public AgentConfigurationVersionDTOV1 build(Agent agent, Collection<AgentMcpToolRule> rules,
            StoredPolicyValues storedPolicies, String tenantId, String principal) {
        return build(agent, rules, storedPolicies, tenantId, principal, DEFAULT_LEGACY_ALLOW_ALL);
    }

    /**
     * @param legacyAllowAll when a tool server has no rules, allow all of its tools (legacy runtime behaviour)
     */
    public AgentConfigurationVersionDTOV1 build(Agent agent, Collection<AgentMcpToolRule> rules,
            StoredPolicyValues storedPolicies, String tenantId, String principal, boolean legacyAllowAll) {
        Objects.requireNonNull(agent, "agent must not be null");
        var stored = storedPolicies == null ? StoredPolicyValues.empty() : storedPolicies;
        var credentials = new ArrayList<VersionCredentialReferenceDTOV1>();

        var snapshot = new AgentConfigurationVersionDTOV1();
        snapshot.setContext(context(tenantId, principal));
        snapshot.setAgent(agent(agent));
        snapshot.setVoice(voice(agent));
        snapshot.setModel(model(agent.getModel()));
        snapshot.setProvider(provider(agent.getModel() != null ? agent.getModel().getProvider() : null, credentials));
        scaffoldAndSkills(agent, snapshot);

        var servers = toolServers(agent, rules, stored, credentials, legacyAllowAll);
        snapshot.setMcpServers(servers.stream().filter(s -> ToolType.MCP.name().equals(s.getType())).toList());
        snapshot.setTools(servers.stream().filter(s -> !ToolType.MCP.name().equals(s.getType())).toList());

        credentials.sort(comparing(VersionCredentialReferenceDTOV1::getRef, STRING_ORDER));
        snapshot.setCredentials(credentials);
        snapshot.setCompatibility(compatibility(snapshot));
        snapshot.setVersion(CanonicalVersionHasher.version(snapshot));
        return snapshot;
    }

    private VersionContextDTOV1 context(String tenantId, String principal) {
        var context = new VersionContextDTOV1();
        context.setTenantId(tenantId);
        context.setPrincipal(principal);
        return context;
    }

    private VersionAgentDTOV1 agent(Agent agent) {
        var dto = new VersionAgentDTOV1();
        dto.setId(agent.getId());
        dto.setName(agent.getName());
        dto.setDescription(agent.getDescription());
        dto.setAdditionalPrompt(agent.getAdditionalPrompt());
        dto.setStatus(agent.getStatus() != null ? agent.getStatus().name() : null);
        dto.setA2aEnabled(Boolean.TRUE.equals(agent.getA2aEnabled()));
        return dto;
    }

    private VersionVoiceSettingsDTOV1 voice(Agent agent) {
        var dto = new VersionVoiceSettingsDTOV1();
        dto.setEnabled(Boolean.TRUE.equals(agent.getVoiceEnabled()));
        var languageCode = agent.getLanguageCode();
        if (languageCode != null && !languageCode.isBlank()) {
            dto.setLanguageCode(languageCode.trim().toLowerCase(Locale.ROOT));
        }
        return dto;
    }

    private VersionModelDTOV1 model(Model model) {
        if (model == null) {
            return null;
        }
        var dto = new VersionModelDTOV1();
        dto.setId(model.getId());
        dto.setName(model.getName());
        dto.setModelIdentifier(model.getModelIdentifier());
        dto.setModelConfig(model.getModelConfig());
        dto.setCommunicationMode(model.getCommunicationMode() != null ? model.getCommunicationMode().name() : null);
        dto.setProviderId(model.getProvider() != null ? model.getProvider().getId() : null);
        return dto;
    }

    private VersionProviderDTOV1 provider(Provider provider, List<VersionCredentialReferenceDTOV1> credentials) {
        if (provider == null) {
            return null;
        }
        var dto = new VersionProviderDTOV1();
        dto.setId(provider.getId());
        dto.setName(provider.getName());
        dto.setType(provider.getType() != null ? provider.getType().name() : null);
        dto.setDescription(provider.getDescription());
        dto.setLlmUrl(provider.getLlmUrl());
        dto.setAuthMode(provider.getAuthMode() != null ? provider.getAuthMode().name() : null);
        dto.setCredentialRef(credentialRef(VersionCredentialOwnerTypeDTOV1.PROVIDER, provider.getId(),
                provider.getAuthMode(), provider.getApiKey(), credentials));
        return dto;
    }

    private void scaffoldAndSkills(Agent agent, AgentConfigurationVersionDTOV1 snapshot) {
        var skills = new ArrayList<VersionSkillDTOV1>();
        if (agent.getGlobalScaffold() != null) {
            GlobalScaffold scaffold = agent.getGlobalScaffold();
            snapshot.setScaffold(scaffold(scaffold.getId(), scaffold.getName(), scaffold.getSystemPrompt(),
                    VersionComponentSourceDTOV1.GLOBAL));
            addSkills(skills, scaffold.getSkills(), VersionComponentSourceDTOV1.GLOBAL);
        } else if (agent.getScaffold() != null) {
            Scaffold scaffold = agent.getScaffold();
            snapshot.setScaffold(scaffold(scaffold.getId(), scaffold.getName(), scaffold.getSystemPrompt(),
                    VersionComponentSourceDTOV1.TENANT));
            addSkills(skills, scaffold.getSkills(), VersionComponentSourceDTOV1.TENANT);
            addSkills(skills, scaffold.getGlobalSkills(), VersionComponentSourceDTOV1.GLOBAL);
        }
        skills.sort(SKILL_ORDER);
        for (int i = 0; i < skills.size(); i++) {
            skills.get(i).setPosition(i);
        }
        snapshot.setSkills(skills);
    }

    private VersionScaffoldDTOV1 scaffold(String id, String name, String systemPrompt,
            VersionComponentSourceDTOV1 source) {
        var dto = new VersionScaffoldDTOV1();
        dto.setId(id);
        dto.setName(name);
        dto.setSystemPrompt(systemPrompt);
        dto.setSource(source);
        return dto;
    }

    private void addSkills(List<VersionSkillDTOV1> target, Set<? extends AbstractSkill> skills,
            VersionComponentSourceDTOV1 source) {
        if (skills == null) {
            return;
        }
        skills.stream().filter(Objects::nonNull).forEach(skill -> {
            var dto = new VersionSkillDTOV1();
            dto.setId(skill.getId());
            dto.setName(skill.getName());
            dto.setDescription(skill.getDescription());
            dto.setInstruction(skill.getInstruction());
            dto.setSource(source);
            target.add(dto);
        });
    }

    private List<VersionToolServerDTOV1> toolServers(Agent agent, Collection<AgentMcpToolRule> rules,
            StoredPolicyValues stored, List<VersionCredentialReferenceDTOV1> credentials, boolean legacyAllowAll) {
        var rulesByToolId = new HashMap<String, List<AgentMcpToolRule>>();
        var rulesByGlobalToolId = new HashMap<String, List<AgentMcpToolRule>>();
        if (rules != null) {
            rules.stream().filter(Objects::nonNull).forEach(rule -> {
                if (rule.getTool() != null) {
                    rulesByToolId.computeIfAbsent(rule.getTool().getId(), k -> new ArrayList<>()).add(rule);
                } else if (rule.getGlobalTool() != null) {
                    rulesByGlobalToolId.computeIfAbsent(rule.getGlobalTool().getId(), k -> new ArrayList<>()).add(rule);
                }
            });
        }

        var candidates = new ArrayList<ToolServerSource>();
        if (agent.getTools() != null) {
            agent.getTools().stream().filter(Objects::nonNull)
                    .forEach(tool -> candidates.add(new ToolServerSource(tool, VersionComponentSourceDTOV1.TENANT,
                            VersionCredentialOwnerTypeDTOV1.TOOL,
                            rulesByToolId.getOrDefault(tool.getId(), List.of()))));
        }
        if (agent.getGlobalTools() != null) {
            agent.getGlobalTools().stream().filter(Objects::nonNull)
                    .forEach(tool -> candidates.add(new ToolServerSource(tool, VersionComponentSourceDTOV1.GLOBAL,
                            VersionCredentialOwnerTypeDTOV1.GLOBAL_TOOL,
                            rulesByGlobalToolId.getOrDefault(tool.getId(), List.of()))));
        }
        // Canonical order first: both the published order and the reported duplicate (if several servers have
        // duplicate rules) must not depend on the order of the collections loaded from the database.
        candidates.sort(SERVER_ORDER);
        candidates.forEach(candidate -> rejectDuplicateRules(agent, candidate.tool(), candidate.rules()));
        return candidates.stream()
                .map(candidate -> toolServer(candidate, stored, credentials, legacyAllowAll))
                .toList();
    }

    private VersionToolServerDTOV1 toolServer(ToolServerSource source, StoredPolicyValues stored,
            List<VersionCredentialReferenceDTOV1> credentials, boolean legacyAllowAll) {
        var tool = source.tool();
        var rules = source.rules();

        var dto = new VersionToolServerDTOV1();
        dto.setId(tool.getId());
        dto.setName(tool.getName());
        dto.setDescription(tool.getDescription());
        dto.setType(tool.getType() != null ? tool.getType().name() : null);
        dto.setUrl(tool.getUrl());
        dto.setSource(source.source());
        dto.setAuthMode(tool.getAuthMode() != null ? tool.getAuthMode().name() : null);
        dto.setCredentialRef(credentialRef(source.ownerType(), tool.getId(), tool.getAuthMode(), tool.getApiKey(),
                credentials));

        var policy = stored.hasExecutionPolicy(tool.getId())
                ? PolicyNormalizer.executionPolicy(stored.executionPolicy(tool.getId()))
                : PolicyNormalizer.executionPolicy(tool.getExecutionPolicy());
        dto.setExecutionPolicy(executionPolicy(policy.effective()));
        var defaulted = policy.normalization() == PolicyNormalizer.Normalization.MISSING_VALUE_DEFAULTED;
        dto.setExecutionPolicyProvenance(provenance(
                defaulted ? VersionPolicySourceDTOV1.SYSTEM_DEFAULT : VersionPolicySourceDTOV1.TOOL_EXECUTION_POLICY,
                defaulted ? null : tool.getId(), policy));

        dto.setToolRules(rules.stream()
                .map(rule -> toolRule(rule, stored, policy.effective()))
                .sorted(comparing(VersionToolRuleDTOV1::getToolName, STRING_ORDER))
                .toList());
        unlistedToolPermission(dto, policy.effective(), !rules.isEmpty(), legacyAllowAll);
        return dto;
    }

    /**
     * Mirrors the tool filter of onecx-ai-provider-runtime: when rules exist they form an allow-list and tools
     * without a rule are denied; without rules all tools are allowed (legacy allow-all) unless disabled.
     */
    private void unlistedToolPermission(VersionToolServerDTOV1 dto, ExecutionPolicy executionPolicy, boolean hasRules,
            boolean legacyAllowAll) {
        var provenance = new VersionPolicyProvenanceDTOV1();
        provenance.setNormalization(VersionPolicyNormalizationDTOV1.NONE);
        if (hasRules) {
            dto.setUnlistedToolPermission(VersionToolPermissionDTOV1.DENY);
            provenance.setSource(VersionPolicySourceDTOV1.ALLOW_LIST_DEFAULT);
        } else if (legacyAllowAll) {
            dto.setUnlistedToolPermission(effectivePermission(ToolPermission.ALWAYS_ALLOW, executionPolicy));
            provenance.setSource(VersionPolicySourceDTOV1.LEGACY_ALLOW_ALL);
        } else {
            dto.setUnlistedToolPermission(VersionToolPermissionDTOV1.DENY);
            provenance.setSource(VersionPolicySourceDTOV1.SYSTEM_DEFAULT);
        }
        dto.setUnlistedToolPermissionProvenance(provenance);
    }

    /**
     * Mirrors the runtime confirmation logic: a tool call requires confirmation when the rule or the server
     * execution policy is {@code ALWAYS_ASK}; {@code DENY} always wins.
     */
    static VersionToolPermissionDTOV1 effectivePermission(ToolPermission permission, ExecutionPolicy executionPolicy) {
        if (permission == ToolPermission.DENY) {
            return VersionToolPermissionDTOV1.DENY;
        }
        if (permission == ToolPermission.ALWAYS_ASK || executionPolicy != ExecutionPolicy.ALWAYS_ALLOW) {
            return VersionToolPermissionDTOV1.ALWAYS_ASK;
        }
        return VersionToolPermissionDTOV1.ALWAYS_ALLOW;
    }

    /**
     * Two rules with exactly the same tool name for the same tool server are ambiguous: the effective permission would
     * depend on collection order. Such configuration invalidates the version payload. The reported duplicate is chosen
     * deterministically: servers are checked in canonical order, the smallest duplicate tool name is reported and the
     * rule ids are sorted.
     */
    private void rejectDuplicateRules(Agent agent, AbstractTool tool, List<AgentMcpToolRule> rules) {
        var byToolName = new HashMap<String, List<AgentMcpToolRule>>();
        rules.forEach(rule -> byToolName.computeIfAbsent(rule.getToolName(), k -> new ArrayList<>()).add(rule));
        byToolName.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .min(STRING_ORDER)
                .ifPresent(toolName -> {
                    var ruleIds = byToolName.get(toolName).stream()
                            .map(AgentMcpToolRule::getId)
                            .sorted(STRING_ORDER)
                            .toList();
                    var params = new LinkedHashMap<String, String>();
                    params.put("agentId", agent.getId());
                    params.put("toolId", tool.getId());
                    params.put("toolName", toolName);
                    params.put("ruleIds", String.join(",", ruleIds));
                    throw new VersionGenerationException(VersionGenerationException.ErrorKeys.DUPLICATE_TOOL_RULE,
                            "Tool server '" + tool.getName() + "' has " + ruleIds.size()
                                    + " rules for tool name '" + toolName + "'",
                            params);
                });
    }

    private VersionToolRuleDTOV1 toolRule(AgentMcpToolRule rule, StoredPolicyValues stored,
            ExecutionPolicy executionPolicy) {
        var permission = stored.hasToolPermission(rule.getId())
                ? PolicyNormalizer.toolPermission(stored.toolPermission(rule.getId()))
                : PolicyNormalizer.toolPermission(rule.getAllowed());
        var dto = new VersionToolRuleDTOV1();
        dto.setToolName(rule.getToolName());
        dto.setDescription(rule.getToolDescription());
        dto.setPermission(toolPermission(permission.effective()));
        dto.setEffectivePermission(effectivePermission(permission.effective(), executionPolicy));
        dto.setProvenance(provenance(VersionPolicySourceDTOV1.AGENT_TOOL_RULE, rule.getId(), permission));
        return dto;
    }

    private VersionPolicyProvenanceDTOV1 provenance(VersionPolicySourceDTOV1 source, String sourceId,
            PolicyNormalizer.Result<?> result) {
        var dto = new VersionPolicyProvenanceDTOV1();
        dto.setSource(source);
        dto.setSourceId(sourceId);
        dto.setNormalization(VersionPolicyNormalizationDTOV1.valueOf(result.normalization().name()));
        dto.setStoredValue(result.storedValue());
        return dto;
    }

    private VersionExecutionPolicyDTOV1 executionPolicy(ExecutionPolicy policy) {
        return policy == ExecutionPolicy.ALWAYS_ALLOW ? VersionExecutionPolicyDTOV1.ALWAYS_ALLOW
                : VersionExecutionPolicyDTOV1.ALWAYS_ASK;
    }

    private VersionToolPermissionDTOV1 toolPermission(ToolPermission permission) {
        if (permission == ToolPermission.ALWAYS_ALLOW) {
            return VersionToolPermissionDTOV1.ALWAYS_ALLOW;
        }
        if (permission == ToolPermission.ALWAYS_ASK) {
            return VersionToolPermissionDTOV1.ALWAYS_ASK;
        }
        return VersionToolPermissionDTOV1.DENY;
    }

    /**
     * Registers a credential reference and returns its id. The secret itself is never part of the version payload.
     */
    private String credentialRef(VersionCredentialOwnerTypeDTOV1 ownerType, String ownerId, AuthMode authMode,
            String secret, List<VersionCredentialReferenceDTOV1> credentials) {
        var hasSecret = secret != null && !secret.isBlank();
        VersionCredentialKindDTOV1 kind;
        if (authMode == AuthMode.OAUTH) {
            kind = VersionCredentialKindDTOV1.PRINCIPAL_TOKEN;
        } else if (authMode == AuthMode.API_KEY || hasSecret) {
            kind = VersionCredentialKindDTOV1.STORED_SECRET;
        } else {
            return null;
        }
        var ref = CREDENTIAL_REF_PREFIX + ownerType.name().toLowerCase(Locale.ROOT).replace('_', '-') + "/" + ownerId
                + (kind == VersionCredentialKindDTOV1.PRINCIPAL_TOKEN ? "/principal-token" : "/api-key");
        var dto = new VersionCredentialReferenceDTOV1();
        dto.setRef(ref);
        dto.setOwnerType(ownerType);
        dto.setOwnerId(ownerId);
        dto.setAuthMode(authMode != null ? authMode.name() : null);
        dto.setKind(kind);
        dto.setConfigured(kind == VersionCredentialKindDTOV1.PRINCIPAL_TOKEN || hasSecret);
        credentials.add(dto);
        return ref;
    }

    private VersionCompatibilityDTOV1 compatibility(AgentConfigurationVersionDTOV1 snapshot) {
        var capabilities = new TreeSet<String>();
        if (Boolean.TRUE.equals(snapshot.getAgent().getA2aEnabled())) {
            capabilities.add(CAPABILITY_A2A);
        }
        if (Boolean.TRUE.equals(snapshot.getVoice().getEnabled())) {
            capabilities.add(CAPABILITY_VOICE);
        }
        if (!snapshot.getMcpServers().isEmpty()) {
            capabilities.add(CAPABILITY_MCP);
        }
        if (snapshot.getMcpServers().stream().anyMatch(s -> !s.getToolRules().isEmpty())) {
            capabilities.add(CAPABILITY_MCP_TOOL_RULES);
        }
        if (snapshot.getCredentials().stream()
                .anyMatch(c -> c.getKind() == VersionCredentialKindDTOV1.PRINCIPAL_TOKEN)) {
            capabilities.add(CAPABILITY_PRINCIPAL_TOKEN);
        }
        var dto = new VersionCompatibilityDTOV1();
        dto.setSchemaVersion(SCHEMA_VERSION);
        dto.setHashAlgorithm(CanonicalVersionHasher.HASH_ALGORITHM);
        dto.setCanonicalization(CanonicalVersionHasher.CANONICALIZATION);
        dto.setRequiredCapabilities(List.copyOf(capabilities));
        return dto;
    }
}
