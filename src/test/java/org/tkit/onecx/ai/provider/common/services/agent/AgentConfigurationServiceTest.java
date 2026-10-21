package org.tkit.onecx.ai.provider.common.services.agent;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tkit.onecx.ai.provider.domain.daos.AgentConfigurationVersionDAO;
import org.tkit.onecx.ai.provider.domain.daos.AgentDAO;
import org.tkit.onecx.ai.provider.domain.daos.AgentMcpToolRuleDAO;
import org.tkit.onecx.ai.provider.domain.models.Agent;
import org.tkit.onecx.ai.provider.domain.models.AgentConfigurationVersion;
import org.tkit.onecx.ai.provider.domain.models.AgentMcpToolRule;
import org.tkit.onecx.ai.provider.domain.models.GlobalTool;
import org.tkit.onecx.ai.provider.domain.models.Tool;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolPermission;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolType;
import org.tkit.onecx.ai.provider.rs.external.v1.mappers.AgentConfigurationMapper;
import org.tkit.onecx.ai.provider.rs.external.v1.mappers.AgentConfigurationMapperImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentConfigurationDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ProblemDetailResponseDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.AgentConfigurationVersionDTO;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AgentConfigurationServiceTest {

    private static final String AGENT_ID = "agent-config";

    private AgentDAO dao;

    private AgentMcpToolRuleDAO agentMcpToolRuleDAO;

    private AgentConfigurationService service;

    @BeforeEach
    void setUp() {
        dao = mock(AgentDAO.class);
        agentMcpToolRuleDAO = mock(AgentMcpToolRuleDAO.class);
        service = new AgentConfigurationService();
        service.dao = dao;
        service.agentMcpToolRuleDAO = agentMcpToolRuleDAO;
        service.agentConfigurationMapper = new AgentConfigurationMapperImpl();
        service.objectMapper = new ObjectMapper().findAndRegisterModules();
        service.versionDAO = mock(AgentConfigurationVersionDAO.class);
        service.dependencyValidator = mock(AgentConfigurationDependencyValidator.class);
        var versions = new HashMap<String, AgentConfigurationVersion>();
        doAnswer(invocation -> {
            AgentConfigurationVersion version = invocation.getArgument(0);
            versions.put(version.getId(), version);
            return null;
        }).when(service.versionDAO).create(any());
        when(service.versionDAO.existsByAgentAndName(anyString(), anyString())).thenAnswer(invocation -> versions.values()
                .stream().anyMatch(version -> version.getAgentId().equals(invocation.getArgument(0))
                        && version.getName().equals(invocation.getArgument(1))));
        when(service.versionDAO.findByAgentAndId(anyString(), anyString())).thenAnswer(invocation -> {
            var version = versions.get(invocation.getArgument(1));
            return version != null && version.getAgentId().equals(invocation.getArgument(0)) ? version : null;
        });
    }

    @Test
    void sameToolNameAcrossDifferentServers_isNotADuplicate() {
        var toolA = mcpTool("tool-a");
        var toolB = mcpTool("tool-b");
        var agent = agentWithTools(Set.of(toolA, toolB));
        when(dao.findById(AGENT_ID)).thenReturn(agent);
        when(agentMcpToolRuleDAO.findByAgentAndToolIds(anyString(), anyList()))
                .thenReturn(List.of(
                        rule(toolA, "search"),
                        rule(toolB, "search")));

        var response = service.getAgentConfiguration(AGENT_ID, "tester");

        assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
        // Both servers publish the same tool name legitimately, so the configuration is returned
        var servers = response.getEntity();
        assertThat(servers).isNotNull();
    }

    @Test
    void sameToolNameOnSameServer_isADuplicate() {
        var toolA = mcpTool("tool-a");
        var agent = agentWithTools(Set.of(toolA));
        when(dao.findById(AGENT_ID)).thenReturn(agent);
        when(agentMcpToolRuleDAO.findByAgentAndToolIds(anyString(), anyList()))
                .thenReturn(List.of(
                        rule(toolA, "search"),
                        rule(toolA, "search")));

        var response = service.getAgentConfiguration(AGENT_ID, "tester");

        assertThat(response.getStatus()).isEqualTo(BAD_REQUEST.getStatusCode());
        assertThat(response.getEntity()).isNotNull();
    }

    private Agent agentWithTools(Set<Tool> tools) {
        var agent = new Agent();
        agent.setId(AGENT_ID);
        agent.setName("agent-config");
        agent.setTenantId("default");
        agent.setTools(tools);
        agent.setGlobalTools(Set.of());
        return agent;
    }

    @Test
    void unknownAgent_returnsNotFoundWithoutLoadingRules() {
        try (var response = service.getAgentConfiguration(AGENT_ID, "tester")) {
            assertThat(response.getStatus()).isEqualTo(NOT_FOUND.getStatusCode());
        }
        verifyNoInteractions(agentMcpToolRuleDAO);
    }

    @Test
    void nonMcpTools_doNotLoadRules() {
        var tool = mcpTool("tool-a");
        tool.setType(null);
        var globalTool = new GlobalTool();
        globalTool.setId("global-a");
        globalTool.setType(null);
        var agent = agentWithTools(Set.of(tool));
        agent.setGlobalTools(Set.of(globalTool));
        when(dao.findById(AGENT_ID)).thenReturn(agent);

        try (var response = service.getAgentConfiguration(AGENT_ID, "tester")) {
            assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
        }
        verifyNoInteractions(agentMcpToolRuleDAO);
    }

    @Test
    void sameToolNameOnSameGlobalServer_returnsDetailedBadRequest() {
        var globalTool = new GlobalTool();
        globalTool.setId("global-a");
        globalTool.setType(ToolType.MCP);
        var agent = agentWithTools(Set.of());
        agent.setGlobalTools(Set.of(globalTool));
        var rule = rule(null, "search");
        rule.setGlobalTool(globalTool);
        when(dao.findById(AGENT_ID)).thenReturn(agent);
        when(agentMcpToolRuleDAO.findByAgentAndGlobalToolIds(AGENT_ID, List.of("global-a")))
                .thenReturn(List.of(rule, rule));

        try (var response = service.getAgentConfiguration(AGENT_ID, "tester")) {
            assertThat(response.getStatus()).isEqualTo(BAD_REQUEST.getStatusCode());
            var problem = (ProblemDetailResponseDTOV1) response.getEntity();
            assertThat(problem.getErrorCode()).isEqualTo("DUPLICATE_AGENT_TOOL_RULE");
            assertThat(problem.getDetail()).contains("'search'");
        }
    }

    @Test
    void authenticatedPrincipal_isPassedToConfigurationMapper() {
        var agent = agentWithTools(Set.of());
        when(dao.findById(AGENT_ID)).thenReturn(agent);
        service.agentConfigurationMapper = mock(AgentConfigurationMapper.class);

        try (var response = service.getAgentConfiguration(AGENT_ID, "alice")) {
            assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
        }
        verify(service.agentConfigurationMapper).build(agent, List.of(), "default", "alice");
    }

    @Test
    void versionsRemainStableAcrossLiveEditsAndActivation() {
        var agent = agentWithTools(Set.of());
        when(dao.findById(AGENT_ID)).thenReturn(agent);
        var first = saveVersion("First");
        assertThat(first.getActive()).isTrue();

        agent.setName("Changed draft");
        var second = saveVersion("Second");
        assertThat(second.getActive()).isFalse();
        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(activeConfiguration().getAgent().getName()).isEqualTo("agent-config");

        try (var response = service.activateAgentConfigurationVersion(AGENT_ID, second.getId())) {
            assertThat(response.getStatus()).isEqualTo(200);
            var metadata = (AgentConfigurationVersionDTO) response.getEntity();
            assertThat(metadata.getId()).isEqualTo(second.getId());
            assertThat(metadata.getName()).isEqualTo("Second");
            assertThat(metadata.getActive()).isTrue();
        }
        assertThat(activeConfiguration().getAgent().getName()).isEqualTo("Changed draft");
        try (var response = service.getAgentConfigurationVersion(AGENT_ID, first.getId(), "tester")) {
            assertThat(((AgentConfigurationDTOV1) response.getEntity()).getAgent().getName()).isEqualTo("agent-config");
            assertThat(response.getHeaderString("Content-Location"))
                    .isEqualTo("/internal/agents/" + AGENT_ID + "/configuration/versions/" + first.getId());
        }
        service.activateAgentConfigurationVersion(AGENT_ID, first.getId()).close();
        assertThat(agent.getActiveConfigurationVersionId()).isEqualTo(first.getId());
        assertThat(activeConfiguration().getAgent().getName()).isEqualTo("agent-config");
    }

    @Test
    void invalidDependenciesRejectActivationWithoutChangingActiveVersion() {
        var agent = agentWithTools(Set.of());
        when(dao.findById(AGENT_ID)).thenReturn(agent);
        var first = saveVersion("First");
        agent.setName("Saved draft");
        var second = saveVersion("Second");
        agent.setName("Current draft");
        when(service.dependencyValidator.validate(any())).thenAnswer(invocation -> {
            AgentConfigurationDTOV1 configuration = invocation.getArgument(0);
            assertThat(configuration.getAgent().getName()).isEqualTo("Saved draft");
            return List.of("Referenced provider 'deleted' does not exist");
        });

        try (var response = service.activateAgentConfigurationVersion(AGENT_ID, second.getId())) {
            assertThat(response.getStatus()).isEqualTo(BAD_REQUEST.getStatusCode());
            var problem = (ProblemDetailResponseDTOV1) response.getEntity();
            assertThat(problem.getErrorCode()).isEqualTo("INVALID_AGENT_CONFIGURATION_DEPENDENCIES");
            assertThat(problem.getDetail()).contains("deleted");
        }
        assertThat(agent.getActiveConfigurationVersionId()).isEqualTo(first.getId());
    }

    @Test
    void invalidDependenciesPreventImplicitActivation() {
        var agent = agentWithTools(Set.of());
        when(dao.findById(AGENT_ID)).thenReturn(agent);
        when(service.dependencyValidator.validate(any())).thenReturn(List.of("Referenced tool is missing"));

        try (var response = service.createVersion(AGENT_ID, "First", "tester")) {
            assertThat(response.getStatus()).isEqualTo(BAD_REQUEST.getStatusCode());
        }
        try (var response = service.getAgentConfiguration(AGENT_ID, "tester")) {
            assertThat(response.getStatus()).isEqualTo(BAD_REQUEST.getStatusCode());
        }
        assertThat(agent.getActiveConfigurationVersionId()).isNull();
        verify(service.versionDAO, never()).create(any());
    }

    @Test
    void repeatedReadsInitializeOnlyOneStableDefaultVersion() {
        var agent = agentWithTools(Set.of());
        when(dao.findById(AGENT_ID)).thenReturn(agent);
        assertThat(activeConfiguration().getAgent().getName()).isEqualTo("agent-config");
        var initialId = agent.getActiveConfigurationVersionId();
        agent.setName("Unsaved draft");
        assertThat(activeConfiguration().getAgent().getName()).isEqualTo("agent-config");
        assertThat(agent.getActiveConfigurationVersionId()).isEqualTo(initialId);
        verify(service.versionDAO).create(any());
    }

    @Test
    void identicalConfigurationsStillGetUniqueVersionIds() {
        when(dao.findById(AGENT_ID)).thenReturn(agentWithTools(Set.of()));
        assertThat(saveVersion("First").getId()).isNotEqualTo(saveVersion("Second").getId());
    }

    @Test
    void duplicateNamesAreRejectedAfterTrimmingWithoutChangingActiveVersion() {
        var agent = agentWithTools(Set.of());
        when(dao.findById(AGENT_ID)).thenReturn(agent);
        var first = saveVersion("Release 1");
        for (var name : List.of("Release 1", "  Release 1  ")) {
            try (var response = service.createVersion(AGENT_ID, name, "tester")) {
                assertThat(response.getStatus()).isEqualTo(BAD_REQUEST.getStatusCode());
                assertThat(((ProblemDetailResponseDTOV1) response.getEntity()).getErrorCode())
                        .isEqualTo("DUPLICATE_AGENT_CONFIGURATION_VERSION_NAME");
            }
        }
        verify(service.versionDAO).create(any());
        assertThat(agent.getActiveConfigurationVersionId()).isEqualTo(first.getId());
    }

    @Test
    void savedConfigurationUsesRetrievingPrincipal() throws Exception {
        when(dao.findById(AGENT_ID)).thenReturn(agentWithTools(Set.of()));
        var version = saveVersion("First");
        var storedVersion = service.versionDAO.findByAgentAndId(AGENT_ID, version.getId());
        var storedConfiguration = storedVersion.getConfiguration();
        var versionConfiguration = service.objectMapper.readValue(storedConfiguration, AgentConfigurationDTOV1.class);
        assertThat(versionConfiguration.getContext().getPrincipal()).isNull();
        try (var response = service.getAgentConfigurationVersion(AGENT_ID, version.getId(), "runtime-user")) {
            assertThat(((AgentConfigurationDTOV1) response.getEntity()).getContext().getPrincipal())
                    .isEqualTo("runtime-user");
        }
        assertThat(storedVersion.getConfiguration()).isEqualTo(storedConfiguration);
    }

    @Test
    void foreignAndMissingVersionsCannotBeReadOrActivated() {
        var agent = agentWithTools(Set.of());
        when(dao.findById(AGENT_ID)).thenReturn(agent);
        var version = saveVersion("First");
        var other = agentWithTools(Set.of());
        other.setId("other-agent");
        when(dao.findById("other-agent")).thenReturn(other);
        try (var response = service.getAgentConfigurationVersion("other-agent", version.getId(), "tester")) {
            assertThat(response.getStatus()).isEqualTo(404);
        }
        try (var response = service.activateAgentConfigurationVersion("other-agent", version.getId())) {
            assertThat(response.getStatus()).isEqualTo(404);
        }
        try (var response = service.activateAgentConfigurationVersion(AGENT_ID, "missing")) {
            assertThat(response.getStatus()).isEqualTo(404);
        }
        try (var response = service.getAgentConfigurationVersion(AGENT_ID, "missing", "tester")) {
            assertThat(response.getStatus()).isEqualTo(404);
        }
        assertThat(agent.getActiveConfigurationVersionId()).isEqualTo(version.getId());
        assertThat(other.getActiveConfigurationVersionId()).isNull();
    }

    @Test
    void invalidVersionNamesAreRejectedWithoutPersistence() {
        for (var name : List.of("", " ", "a".repeat(256))) {
            try (var response = service.createVersion(AGENT_ID, name, "tester")) {
                assertThat(response.getStatus()).isEqualTo(400);
            }
        }
        try (var response = service.createVersion(AGENT_ID, null, "tester")) {
            assertThat(response.getStatus()).isEqualTo(400);
        }
        verifyNoInteractions(service.versionDAO);
    }

    @Test
    void duplicateRulesPreventVersionCreation() {
        var tool = mcpTool("tool-a");
        var agent = agentWithTools(Set.of(tool));
        when(dao.findById(AGENT_ID)).thenReturn(agent);
        when(agentMcpToolRuleDAO.findByAgentAndToolIds(anyString(), anyList()))
                .thenReturn(List.of(rule(tool, "search"), rule(tool, "search")));
        try (var response = service.createVersion(AGENT_ID, "Invalid", "tester")) {
            assertThat(response.getStatus()).isEqualTo(BAD_REQUEST.getStatusCode());
        }
        verify(service.versionDAO, never()).create(any());
        assertThat(agent.getActiveConfigurationVersionId()).isNull();
    }

    @Test
    void unknownAgentCannotCreateReadOrActivateVersions() {
        try (var response = service.createVersion(AGENT_ID, "First", "tester")) {
            assertThat(response.getStatus()).isEqualTo(NOT_FOUND.getStatusCode());
        }
        try (var response = service.getAgentConfigurationVersion(AGENT_ID, "missing", "tester")) {
            assertThat(response.getStatus()).isEqualTo(NOT_FOUND.getStatusCode());
        }
        try (var response = service.activateAgentConfigurationVersion(AGENT_ID, "missing")) {
            assertThat(response.getStatus()).isEqualTo(NOT_FOUND.getStatusCode());
        }
        verifyNoInteractions(service.versionDAO);
    }

    @Test
    void configurationsWithoutContextCanBeSavedAndRead() {
        var agent = agentWithTools(Set.of());
        when(dao.findById(AGENT_ID)).thenReturn(agent);
        service.agentConfigurationMapper = mock(AgentConfigurationMapper.class);
        when(service.agentConfigurationMapper.build(agent, List.of(), "default", "tester"))
                .thenReturn(new AgentConfigurationDTOV1());

        var version = saveVersion("Without context");

        try (var response = service.getAgentConfigurationVersion(AGENT_ID, version.getId(), "reader")) {
            assertThat(response.getStatus()).isEqualTo(OK.getStatusCode());
            assertThat(((AgentConfigurationDTOV1) response.getEntity()).getContext()).isNull();
        }
    }

    @Test
    void serializationFailureIsReportedWithoutPersistingOrActivatingVersion() throws Exception {
        var agent = agentWithTools(Set.of());
        when(dao.findById(AGENT_ID)).thenReturn(agent);
        service.objectMapper = mock(ObjectMapper.class);
        var failure = new JsonProcessingException("Cannot write configuration") {
        };
        when(service.objectMapper.writeValueAsString(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.createVersion(AGENT_ID, "First", "tester"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot serialize agent configuration")
                .hasCause(failure);
        verify(service.versionDAO, never()).create(any());
        assertThat(agent.getActiveConfigurationVersionId()).isNull();
    }

    @Test
    void malformedSavedConfigurationCannotBeReadOrActivated() {
        var agent = agentWithTools(Set.of());
        when(dao.findById(AGENT_ID)).thenReturn(agent);
        var first = saveVersion("First");
        var malformed = new AgentConfigurationVersion(agent, "Malformed", "{invalid");
        service.versionDAO.create(malformed);
        var malformedId = malformed.getId();

        assertThatThrownBy(() -> service.getAgentConfigurationVersion(AGENT_ID, malformedId, "tester"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot deserialize agent configuration version " + malformedId)
                .hasCauseInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> service.activateAgentConfigurationVersion(AGENT_ID, malformedId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot deserialize agent configuration version " + malformedId)
                .hasCauseInstanceOf(JsonProcessingException.class);
        assertThat(agent.getActiveConfigurationVersionId()).isEqualTo(first.getId());
    }

    private AgentConfigurationVersionDTO saveVersion(String name) {
        try (var response = service.createVersion(AGENT_ID, name, "tester")) {
            assertThat(response.getStatus()).isEqualTo(201);
            var version = (AgentConfigurationVersionDTO) response.getEntity();
            assertThat(version.getName()).isEqualTo(name);
            assertThat(response.getLocation().toString()).endsWith("/" + version.getId());
            return version;
        }
    }

    private AgentConfigurationDTOV1 activeConfiguration() {
        try (var response = service.getAgentConfiguration(AGENT_ID, "tester")) {
            assertThat(response.getStatus()).isEqualTo(200);
            return (AgentConfigurationDTOV1) response.getEntity();
        }
    }

    private Tool mcpTool(String id) {
        var tool = new Tool();
        tool.setId(id);
        tool.setName(id);
        tool.setType(ToolType.MCP);
        tool.setUrl("http://" + id + ".org");
        return tool;
    }

    private AgentMcpToolRule rule(Tool tool, String toolName) {
        var rule = new AgentMcpToolRule();
        rule.setTool(tool);
        rule.setToolName(toolName);
        rule.setToolDescription(toolName + " description");
        rule.setAllowed(ToolPermission.ALLOW);
        return rule;
    }
}
