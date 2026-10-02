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

import jakarta.enterprise.context.ApplicationScoped;

import org.tkit.onecx.ai.provider.common.services.version.CanonicalVersionHasher;
import org.tkit.onecx.ai.provider.common.services.version.PolicyNormalizer;
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
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionModelDTOV1;
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
 * version, independent of the (unordered) collections loaded from the database.
 */
@ApplicationScoped
public class AgentConfigurationVersionMapper {

    public static final String SCHEMA_VERSION = "2.0.0";

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
            CredentialOwner ownerType, List<AgentMcpToolRule> rules) {
    }

    private enum CredentialOwner {
        PROVIDER("provider"),
        TOOL("tool"),
        GLOBAL_TOOL("global-tool");

        private final String pathSegment;

        CredentialOwner(String pathSegment) {
            this.pathSegment = pathSegment;
        }
    }

    private record PermissionDecision(VersionToolPermissionDTOV1 permission, VersionPolicySourceDTOV1 source) {
    }

    public AgentConfigurationVersionDTOV1 build(Agent agent, Collection<AgentMcpToolRule> rules,
            String tenantId, String principal) {
        Objects.requireNonNull(agent, "agent must not be null");

        var snapshot = new AgentConfigurationVersionDTOV1();
        snapshot.setContext(context(tenantId, principal));
        snapshot.setAgent(agent(agent));
        snapshot.setVoice(voice(agent));
        snapshot.setModel(model(agent.getModel()));
        snapshot.setProvider(provider(agent.getModel() != null ? agent.getModel().getProvider() : null));
        scaffoldAndSkills(agent, snapshot);

        snapshot.setMcpServers(toolServers(agent, rules));
        snapshot.setCompatibility(compatibility());
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

    private VersionProviderDTOV1 provider(Provider provider) {
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
        dto.setCredentialRef(credentialRef(CredentialOwner.PROVIDER, provider.getId(), provider.getAuthMode(),
                provider.getApiKey()));
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

    private List<VersionToolServerDTOV1> toolServers(Agent agent, Collection<AgentMcpToolRule> rules) {
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
            agent.getTools().stream().filter(Objects::nonNull).filter(AgentConfigurationVersionMapper::isMcpTool)
                    .forEach(tool -> candidates.add(new ToolServerSource(tool, VersionComponentSourceDTOV1.TENANT,
                            CredentialOwner.TOOL,
                            rulesByToolId.getOrDefault(tool.getId(), List.of()))));
        }
        if (agent.getGlobalTools() != null) {
            agent.getGlobalTools().stream().filter(Objects::nonNull).filter(AgentConfigurationVersionMapper::isMcpTool)
                    .forEach(tool -> candidates.add(new ToolServerSource(tool, VersionComponentSourceDTOV1.GLOBAL,
                            CredentialOwner.GLOBAL_TOOL,
                            rulesByGlobalToolId.getOrDefault(tool.getId(), List.of()))));
        }
        // Canonical order first: both the published order and the reported duplicate (if several servers have
        // duplicate rules) must not depend on the order of the collections loaded from the database.
        candidates.sort(SERVER_ORDER);
        candidates.forEach(candidate -> rejectDuplicateRules(agent, candidate.tool(), candidate.rules()));
        return candidates.stream()
                .map(this::toolServer)
                .toList();
    }

    private static boolean isMcpTool(AbstractTool tool) {
        return tool.getType() == ToolType.MCP;
    }

    private VersionToolServerDTOV1 toolServer(ToolServerSource source) {
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
        dto.setCredentialRef(credentialRef(source.ownerType(), tool.getId(), tool.getAuthMode(), tool.getApiKey()));

        var executionPolicyValue = tool.getExecutionPolicy();
        var executionPolicy = PolicyNormalizer.executionPolicy(executionPolicyValue);
        var executionPolicySource = executionPolicyValue == null ? VersionPolicySourceDTOV1.SYSTEM_DEFAULT
                : VersionPolicySourceDTOV1.TOOL_EXECUTION_POLICY;

        dto.setToolRules(rules.stream()
                .map(rule -> toolRule(rule, executionPolicy, executionPolicySource))
                .sorted(comparing(VersionToolRuleDTOV1::getToolName, STRING_ORDER))
                .toList());
        var unlisted = unlistedToolPermission(executionPolicy, executionPolicySource, !rules.isEmpty());
        dto.setUnlistedToolPermission(unlisted.permission());
        dto.setUnlistedToolPermissionSource(unlisted.source());
        return dto;
    }

    /**
     * Mirrors runtime behaviour: when rules exist they form an allow-list and tools without a rule are denied.
     * Without rules, tools are allowed and combined with the execution policy.
     */
    private PermissionDecision unlistedToolPermission(ExecutionPolicy executionPolicy,
            VersionPolicySourceDTOV1 executionPolicySource, boolean hasRules) {
        if (hasRules) {
            return new PermissionDecision(VersionToolPermissionDTOV1.DENY, VersionPolicySourceDTOV1.SYSTEM_DEFAULT);
        }
        return new PermissionDecision(effectivePermission(ToolPermission.ALWAYS_ALLOW, executionPolicy),
                executionPolicySource);
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

    private VersionToolRuleDTOV1 toolRule(AgentMcpToolRule rule, ExecutionPolicy executionPolicy,
            VersionPolicySourceDTOV1 executionPolicySource) {
        var storedPermission = rule.getAllowed();
        var permission = PolicyNormalizer.toolPermission(storedPermission);
        var source = storedPermission == null ? VersionPolicySourceDTOV1.SYSTEM_DEFAULT
                : VersionPolicySourceDTOV1.AGENT_TOOL_RULE;

        var resolved = toolPermission(permission);
        if (permission == ToolPermission.ALWAYS_ALLOW && executionPolicy != ExecutionPolicy.ALWAYS_ALLOW) {
            resolved = VersionToolPermissionDTOV1.ALWAYS_ASK;
            source = executionPolicySource;
        }

        var dto = new VersionToolRuleDTOV1();
        dto.setToolName(rule.getToolName());
        dto.setDescription(rule.getToolDescription());
        dto.setPermission(resolved);
        dto.setPolicySource(source);
        return dto;
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

    private String credentialRef(CredentialOwner ownerType, String ownerId, AuthMode authMode,
            String secret) {
        var hasSecret = secret != null && !secret.isBlank();
        String suffix;
        if (authMode == AuthMode.OAUTH) {
            suffix = "/principal-token";
        } else if (authMode == AuthMode.API_KEY || hasSecret) {
            suffix = "/api-key";
        } else {
            return null;
        }
        return CREDENTIAL_REF_PREFIX + ownerType.pathSegment + "/" + ownerId + suffix;
    }

    private VersionCompatibilityDTOV1 compatibility() {
        var dto = new VersionCompatibilityDTOV1();
        dto.setSchemaVersion(SCHEMA_VERSION);
        return dto;
    }
}
