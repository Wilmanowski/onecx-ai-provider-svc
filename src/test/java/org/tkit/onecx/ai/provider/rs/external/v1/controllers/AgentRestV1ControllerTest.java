package org.tkit.onecx.ai.provider.rs.external.v1.controllers;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.tkit.onecx.ai.provider.test.AbstractTest;
import org.tkit.quarkus.security.test.GenerateKeycloakClient;
import org.tkit.quarkus.test.WithDBData;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.*;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@TestHTTPEndpoint(AgentRestV1Controller.class)
@WithDBData(value = "data/testdata-internal.xml", deleteBeforeInsert = true, deleteAfterTest = true, rinseAndRepeat = true)
@GenerateKeycloakClient(clientName = "testClient", scopes = { "ocx-ai:read" })
class AgentRestV1ControllerTest extends AbstractTest {

    @Test
    void readOnlyCallerCannotCreateOrActivateVersions() {
        given().auth().oauth2(getKeycloakClientToken("testClient"))
                .basePath("/v1/agents").contentType(APPLICATION_JSON)
                .body(Map.of("name", "Unauthorized"))
                .post("/agent-11-111/configuration/versions")
                .then().statusCode(NOT_FOUND.getStatusCode());
        given().auth().oauth2(getKeycloakClientToken("testClient"))
                .basePath("/internal/agents").contentType(APPLICATION_JSON)
                .body(Map.of("name", "Unauthorized"))
                .post("/agent-11-111/configuration/versions")
                .then().statusCode(FORBIDDEN.getStatusCode());
        given().auth().oauth2(getKeycloakClientToken("testClient"))
                .basePath("/v1/agents")
                .put("/agent-11-111/configuration/versions/missing/activate")
                .then().statusCode(NOT_FOUND.getStatusCode());
        given().auth().oauth2(getKeycloakClientToken("testClient"))
                .basePath("/v1/agents")
                .get("/agent-11-111/configuration/versions/missing")
                .then().statusCode(NOT_FOUND.getStatusCode());
        given().auth().oauth2(getKeycloakClientToken("testClient"))
                .basePath("/internal/agents")
                .get("/agent-11-111/configuration/versions/missing")
                .then().statusCode(NOT_FOUND.getStatusCode());
        given().auth().oauth2(getKeycloakClientToken("testClient"))
                .basePath("/internal/agents")
                .put("/agent-11-111/configuration/versions/missing/activate")
                .then().statusCode(FORBIDDEN.getStatusCode());
    }

    @Test
    void findAgentBySearchCriteriaTest() {
        var criteria = new AgentSearchCriteriaDTOV1();
        var data = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .body(criteria)
                .post("/search")
                .then()
                .statusCode(OK.getStatusCode())
                .extract()
                .as(AgentPageResultDTOV1.class);

        assertThat(data).isNotNull();
        assertThat(data.getTotalElements()).isEqualTo(3);
        assertThat(data.getStream()).isNotNull().hasSize(3);

        var voiceAgent = data.getStream().stream().filter(a -> a.getId().equals("agent-44-444")).findFirst().orElseThrow();
        assertThat(voiceAgent.getVoiceEnabled()).isTrue();
        assertThat(voiceAgent.getLanguageCode()).isEqualTo("en");

        var plainAgent = data.getStream().stream().filter(a -> a.getId().equals("agent-11-111")).findFirst().orElseThrow();
        assertThat(plainAgent.getVoiceEnabled()).isFalse();
        assertThat(plainAgent.getLanguageCode()).isNull();

        criteria.setPageNumber(1);
        criteria.setPageSize(2);
        data = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .body(criteria)
                .post("/search")
                .then()
                .statusCode(OK.getStatusCode())
                .extract()
                .as(AgentPageResultDTOV1.class);

        assertThat(data.getTotalElements()).isEqualTo(3);
        assertThat(data.getStream()).isNotNull().hasSize(1);
    }

    @Test
    void getAgentConfiguration_resolvesSecretFreeConfiguration() {
        var response = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .basePath("/v1/agents")
                .pathParam("id", "agent-11-111")
                .get("/{id}/configuration");

        assertThat(response.statusCode()).isEqualTo(OK.getStatusCode());

        var config = response.as(AgentConfigurationDTOV1.class);
        assertThat(List.of(
                config.getSchemaVersion(),
                config.getContext().getTenantId(),
                config.getAgent().getId(),
                config.getAgent().getName(),
                config.getModel().getModelIdentifier(),
                config.getProvider().getType(),
                config.getProvider().getCredentialRef(),
                config.getScaffold().getSystemPrompt()))
                .containsExactly(
                        "1",
                        "default",
                        "agent-11-111",
                        "agent1",
                        "gpt-4.1-mini",
                        "OPENAI",
                        "credential://provider/provider-11-111/api-key",
                        "tenant scaffold prompt");

        // Tenant skills first (ordered), then global skills
        var skills = config.getSkills();
        assertThat(skills).extracting(
                skill -> tuple(skill.getId(), skill.getSource()))
                .containsExactly(
                        tuple("skill-11-111", SkillConfigurationDTOV1.SourceEnum.TENANT),
                        tuple("skill-22-222", SkillConfigurationDTOV1.SourceEnum.TENANT),
                        tuple("gskill-11-111", SkillConfigurationDTOV1.SourceEnum.GLOBAL));
        assertThat(skills).extracting(SkillConfigurationDTOV1::getPosition).containsExactly(0, 1, 2);

        // One configured MCP server with resolved permissions
        var server = config.getMcpServers();
        assertThat(server).hasSize(1);
        var mcp = server.get(0);
        assertThat(tuple(
                mcp.getId(),
                mcp.getUrl(),
                mcp.getCredentialRef(),
                mcp.getUnlistedToolPermission(),
                mcp.getUnlistedToolPermissionSource()))
                .isEqualTo(tuple(
                        "tool-11-111",
                        "http://mcp.server.org",
                        "credential://tool/tool-11-111/api-key",
                        McpServerConfigurationDTOV1.UnlistedToolPermissionEnum.ALWAYS_ASK,
                        McpServerConfigurationDTOV1.UnlistedToolPermissionSourceEnum.TOOL_EXECUTION_POLICY));
        var rules = mcp.getToolRules().stream()
                .collect(java.util.stream.Collectors.toMap(ToolRuleConfigurationDTOV1::getToolName, r -> r));
        assertThat(List.of(
                tuple(rules.get("getProposal").getPermission(), rules.get("getProposal").getPolicySource()),
                tuple(rules.get("deleteProposal").getPermission(), rules.get("deleteProposal").getPolicySource())))
                .containsExactly(
                        tuple(ToolRuleConfigurationDTOV1.PermissionEnum.ALWAYS_ALLOW,
                                ToolRuleConfigurationDTOV1.PolicySourceEnum.AGENT_TOOL_RULE),
                        tuple(ToolRuleConfigurationDTOV1.PermissionEnum.DENY,
                                ToolRuleConfigurationDTOV1.PolicySourceEnum.AGENT_TOOL_RULE));

        // Raw credentials must never be exposed
        assertThat(response.getBody().asString()).doesNotContain("api_key_1");
    }

    @Test
    void getAgentConfiguration_unknownAgent_returnsNotFound() {
        var response = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .basePath("/v1/agents")
                .pathParam("id", "agent-not-exists")
                .get("/{id}/configuration");

        assertThat(response.statusCode()).isEqualTo(NOT_FOUND.getStatusCode());
    }

    @Test
    void getAgentConfiguration_duplicateToolRules_returnsBadRequest() {
        var response = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .basePath("/v1/agents")
                .pathParam("id", "agent-33-333")
                .get("/{id}/configuration");

        assertThat(response.statusCode()).isEqualTo(BAD_REQUEST.getStatusCode());
        assertThat(response.jsonPath().getString("errorCode")).isEqualTo("DUPLICATE_AGENT_TOOL_RULE");
    }

    @Test
    void getAgentConfiguration_nonMcpToolsAreIgnored_returnsNoMcpServers() {
        var response = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .basePath("/v1/agents")
                .pathParam("id", "agent-22-222")
                .get("/{id}/configuration");

        assertThat(response.statusCode()).isEqualTo(OK.getStatusCode());
        var config = response.as(AgentConfigurationDTOV1.class);
        assertThat(config.getMcpServers()).isEmpty();
    }

    @Test
    void getAgentConfiguration_globalMcpRulesAreLoaded() {
        var response = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .basePath("/v1/agents")
                .pathParam("id", "agent-44-444")
                .get("/{id}/configuration");

        assertThat(response.statusCode()).isEqualTo(OK.getStatusCode());
        var config = response.as(AgentConfigurationDTOV1.class);
        assertThat(config.getMcpServers()).hasSize(1);

        var server = config.getMcpServers().get(0);
        assertThat(server.getId()).isEqualTo("gtool-22-222");
        assertThat(server.getToolRules()).extracting(ToolRuleConfigurationDTOV1::getToolName)
                .containsExactly("globalMcpRead");
    }
}
