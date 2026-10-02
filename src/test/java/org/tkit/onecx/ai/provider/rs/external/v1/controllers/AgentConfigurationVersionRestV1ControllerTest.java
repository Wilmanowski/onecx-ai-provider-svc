package org.tkit.onecx.ai.provider.rs.external.v1.controllers;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import org.junit.jupiter.api.Test;
import org.tkit.onecx.ai.provider.rs.external.v1.mappers.AgentConfigurationVersionMapper;
import org.tkit.onecx.ai.provider.test.AbstractTest;
import org.tkit.quarkus.security.test.GenerateKeycloakClient;
import org.tkit.quarkus.test.WithDBData;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentConfigurationVersionDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ProblemDetailParamDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ProblemDetailResponseDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionComponentSourceDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionPolicySourceDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionSkillDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionToolPermissionDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionToolRuleDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.VersionToolServerDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.CreateAgentMcpToolRuleRequestDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.ToolPermissionDTO;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Version contract tests against persisted configuration.
 */
@QuarkusTest
@WithDBData(value = "data/testdata-version.xml", deleteBeforeInsert = true, deleteAfterTest = true, rinseAndRepeat = true)
@GenerateKeycloakClient(clientName = "testClient", scopes = { "ocx-ai:read", "ocx-ai:write" })
class AgentConfigurationVersionRestV1ControllerTest extends AbstractTest {

    private static final String VERSION_PATH = "/v1/agents/{id}/configuration-version";

    @Test
    void getVersion_returnsCanonicalVersionWithEtag() {
        var response = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .get(VERSION_PATH, "snap-agent-1")
                .then()
                .statusCode(OK.getStatusCode())
                .extract();
        var versionPayload = response.as(AgentConfigurationVersionDTOV1.class);

        assertThat(versionPayload.getVersion()).startsWith("sha256:");
        assertThat(response.header("ETag")).isEqualTo("\"" + versionPayload.getVersion() + "\"");
        assertThat(response.header("Cache-Control")).contains("no-cache");

        assertThat(versionPayload.getContext().getTenantId()).isEqualTo("default");
        assertThat(versionPayload.getAgent().getId()).isEqualTo("snap-agent-1");
        assertThat(versionPayload.getVoice().getEnabled()).isTrue();
        assertThat(versionPayload.getVoice().getLanguageCode()).isEqualTo("en");
        assertThat(versionPayload.getProvider().getId()).isEqualTo("snap-provider-1");
        assertThat(versionPayload.getProvider().getCredentialRef()).isEqualTo("credential://provider/snap-provider-1/api-key");
        assertThat(versionPayload.getModel().getModelIdentifier()).isEqualTo("gpt-4.1-mini");
        assertThat(versionPayload.getScaffold().getSystemPrompt()).isEqualTo("snapshot system prompt");
        assertThat(versionPayload.getSkills()).extracting(VersionSkillDTOV1::getName)
                .containsExactly("a-skill", "b-skill", "c-global-skill");
        assertThat(versionPayload.getMcpServers()).extracting(VersionToolServerDTOV1::getName)
                .containsExactly("global-mcp", "legacy-mcp", "unknown-mcp");
        assertThat(versionPayload.getMcpServers().getFirst().getSource()).isEqualTo(VersionComponentSourceDTOV1.GLOBAL);
        assertThat(versionPayload.getCompatibility().getSchemaVersion())
                .isEqualTo(AgentConfigurationVersionMapper.SCHEMA_VERSION);
    }

    @Test
    void getVersion_appliesResolvedPermissionAndPolicySource() {
        var versionPayload = getVersion("snap-agent-1");

        var legacy = server(versionPayload, "snap-tool-legacy");
        assertThat(legacy.getToolRules()).extracting(VersionToolRuleDTOV1::getToolName)
                .containsExactly("deleteItem", "readItem");

        var read = rule(legacy, "readItem");
        assertThat(read.getPermission()).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ALLOW);
        assertThat(read.getPolicySource()).isEqualTo(VersionPolicySourceDTOV1.AGENT_TOOL_RULE);

        var delete = rule(legacy, "deleteItem");
        assertThat(delete.getPermission()).isEqualTo(VersionToolPermissionDTOV1.DENY);
        assertThat(delete.getPolicySource()).isEqualTo(VersionPolicySourceDTOV1.AGENT_TOOL_RULE);

        assertThat(legacy.getUnlistedToolPermission()).isEqualTo(VersionToolPermissionDTOV1.DENY);
        assertThat(legacy.getUnlistedToolPermissionSource()).isEqualTo(VersionPolicySourceDTOV1.SYSTEM_DEFAULT);

        var global = rule(server(versionPayload, "snap-gtool-1"), "globalRead");
        assertThat(global.getPermission()).isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ALLOW);
        assertThat(global.getPolicySource()).isEqualTo(VersionPolicySourceDTOV1.AGENT_TOOL_RULE);
    }

    @Test
    void getVersion_unknownStoredValues_areAlreadyCanonicalizedByConverters() {
        var versionPayload = getVersion("snap-agent-1");

        var unknown = server(versionPayload, "snap-tool-unknown");
        var write = rule(unknown, "writeItem");

        assertThat(write.getPermission()).isEqualTo(VersionToolPermissionDTOV1.DENY);
        assertThat(write.getPolicySource()).isEqualTo(VersionPolicySourceDTOV1.AGENT_TOOL_RULE);
        assertThat(unknown.getUnlistedToolPermission()).isEqualTo(VersionToolPermissionDTOV1.DENY);
        assertThat(unknown.getUnlistedToolPermissionSource()).isEqualTo(VersionPolicySourceDTOV1.SYSTEM_DEFAULT);
    }

    @Test
    void getVersion_doesNotExposeRawCredentials() {
        var body = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .get(VERSION_PATH, "snap-agent-1")
                .then()
                .statusCode(OK.getStatusCode())
                .extract().asString();

        assertThat(body).doesNotContain("snapshot-provider-secret", "snapshot-tool-secret",
                "snapshot-oauth-tool-secret", "snapshot-global-secret")
                .doesNotContainIgnoringCase("apiKey")
                .doesNotContainIgnoringCase("authorization")
                .doesNotContainIgnoringCase("bearer");
    }

    @Test
    void conditionalRetrieval_distinguishesUnchangedFromNewVersion() {
        var version = getVersion("snap-agent-1").getVersion();

        // repeated retrieval of unchanged configuration yields the same immutable version
        assertThat(getVersion("snap-agent-1").getVersion()).isEqualTo(version);

        // unchanged: 304 for strong, weak and bare entity tags
        for (var tag : new String[] { "\"" + version + "\"", "W/\"" + version + "\"", version,
                "\"sha256:stale\", \"" + version + "\"" }) {
            var notModified = given()
                    .auth().oauth2(getKeycloakClientToken("testClient"))
                    .header("If-None-Match", tag)
                    .get(VERSION_PATH, "snap-agent-1")
                    .then()
                    .statusCode(NOT_MODIFIED.getStatusCode())
                    .extract();
            assertThat(notModified.header("ETag")).isEqualTo("\"" + version + "\"");
            assertThat(notModified.asString()).isEmpty();
        }

        // configuration change: new rule for the legacy MCP server
        given()
                .log().ifValidationFails()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .body(createRuleRequest("archiveItem", "archives items", ToolPermissionDTO.ALWAYS_ALLOW))
                .post("/internal/agents/{agentId}/tools/{toolId}/mcp-tool-rules", "snap-agent-1", "snap-tool-legacy")
                .then()
                .statusCode(CREATED.getStatusCode());

        // changed: the pinned version is stale, a new immutable version is delivered
        var changed = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .header("If-None-Match", "\"" + version + "\"")
                .get(VERSION_PATH, "snap-agent-1")
                .then()
                .statusCode(OK.getStatusCode())
                .extract();
        var newVersion = changed.as(AgentConfigurationVersionDTOV1.class);
        assertThat(newVersion.getVersion()).isNotEqualTo(version);
        assertThat(changed.header("ETag")).isEqualTo("\"" + newVersion.getVersion() + "\"");
        assertThat(rule(server(newVersion, "snap-tool-legacy"), "archiveItem").getPermission())
                .isEqualTo(VersionToolPermissionDTOV1.ALWAYS_ALLOW);

        // the new version is pinned again
        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .header("If-None-Match", "\"" + newVersion.getVersion() + "\"")
                .get(VERSION_PATH, "snap-agent-1")
                .then()
                .statusCode(NOT_MODIFIED.getStatusCode());
    }

    @Test
    void duplicateExactToolRules_invalidateVersion() {
        var problem = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .get(VERSION_PATH, "snap-agent-dup")
                .then()
                .statusCode(CONFLICT.getStatusCode())
                .extract().as(ProblemDetailResponseDTOV1.class);

        assertThat(problem.getErrorCode()).isEqualTo("DUPLICATE_TOOL_RULE");
        assertThat(problem.getParams()).extracting(ProblemDetailParamDTOV1::getKey, ProblemDetailParamDTOV1::getValue)
                .contains(tuple("toolId", "snap-tool-legacy"),
                        tuple("toolName", "readItem"),
                        tuple("ruleIds", "snap-rule-dup-1,snap-rule-dup-2"));

        // a pinned version never masks an invalid configuration
        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .header("If-None-Match", "*")
                .get(VERSION_PATH, "snap-agent-dup")
                .then()
                .statusCode(CONFLICT.getStatusCode());
    }

    @Test
    void duplicateRuleCreatedLater_invalidatesVersion() {
        getVersion("snap-agent-1");

        given()
                .log().ifValidationFails()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .body(createRuleRequest("readItem", "reads items", ToolPermissionDTO.ALWAYS_ALLOW))
                .post("/internal/agents/{agentId}/tools/{toolId}/mcp-tool-rules", "snap-agent-1", "snap-tool-legacy")
                .then()
                .statusCode(CREATED.getStatusCode());

        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .get(VERSION_PATH, "snap-agent-1")
                .then()
                .statusCode(CONFLICT.getStatusCode());
    }

    @Test
    void getVersion_unknownAgent_returnsNotFound() {
        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .get(VERSION_PATH, "does-not-exist")
                .then()
                .statusCode(NOT_FOUND.getStatusCode());
    }

    @Test
    void getVersion_withoutToken_isUnauthorized() {
        given()
                .get(VERSION_PATH, "snap-agent-1")
                .then()
                .statusCode(UNAUTHORIZED.getStatusCode());
    }

    private AgentConfigurationVersionDTOV1 getVersion(String agentId) {
        return given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .get(VERSION_PATH, agentId)
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(AgentConfigurationVersionDTOV1.class);
    }

    private static CreateAgentMcpToolRuleRequestDTO createRuleRequest(String toolName, String toolDescription,
            ToolPermissionDTO permission) {
        var dto = new CreateAgentMcpToolRuleRequestDTO();
        dto.setToolName(toolName);
        dto.setToolDescription(toolDescription);
        dto.setAllowed(permission);
        return dto;
    }

    private static VersionToolServerDTOV1 server(AgentConfigurationVersionDTOV1 versionPayload, String id) {
        return versionPayload.getMcpServers().stream().filter(s -> id.equals(s.getId())).findFirst().orElseThrow();
    }

    private static VersionToolRuleDTOV1 rule(VersionToolServerDTOV1 server, String toolName) {
        return server.getToolRules().stream().filter(r -> toolName.equals(r.getToolName())).findFirst()
                .orElseThrow();
    }
}
