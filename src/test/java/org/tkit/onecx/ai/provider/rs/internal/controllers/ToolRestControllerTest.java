package org.tkit.onecx.ai.provider.rs.internal.controllers;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import jakarta.ws.rs.HttpMethod;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;
import org.tkit.onecx.ai.provider.test.AbstractTest;
import org.tkit.quarkus.security.test.GenerateKeycloakClient;
import org.tkit.quarkus.test.WithDBData;

import gen.org.tkit.onecx.ai.provider.rs.internal.model.*;
import io.quarkiverse.mockserver.test.InjectMockServerClient;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@TestHTTPEndpoint(ToolRestController.class)
@WithDBData(value = "data/testdata-internal.xml", deleteBeforeInsert = true, deleteAfterTest = true, rinseAndRepeat = true)
@GenerateKeycloakClient(clientName = "testClient", scopes = { "ocx-ai:all", "ocx-ai:read", "ocx-ai:write", "ocx-ai:delete" })
class ToolRestControllerTest extends AbstractTest {

    @InjectMockServerClient
    MockServerClient mockServerClient;

    static final String MOCK_ID = "TOOL_MOCK";

    @BeforeEach
    void resetExpectation() {
        try {
            mockServerClient.clear(MOCK_ID);
        } catch (Exception _) {
            // mockId not existing
        }
    }

    @Test
    void createToolTest() {
        var dto = new CreateToolRequestDTO();
        dto.setName("tool-created");
        dto.setType(ToolTypeDTO.HTTP);

        var created = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .body(dto)
                .post()
                .then()
                .statusCode(CREATED.getStatusCode())
                .extract()
                .as(ToolDTO.class);

        assertThat(created).isNotNull();
        assertThat(created.getName()).isEqualTo("tool-created");
        assertThat(created.getType()).isEqualTo(ToolTypeDTO.HTTP);
    }

    @Test
    void findToolByCriteriaTest() {
        var criteria = new ToolSearchCriteriaDTO();
        criteria.setPageNumber(0);
        criteria.setPageSize(10);

        var data = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .body(criteria)
                .post("/search")
                .then()
                .statusCode(OK.getStatusCode())
                .extract()
                .as(ToolPageResultDTO.class);

        assertThat(data).isNotNull();
        assertThat(data.getTotalElements()).isEqualTo(4);
        assertThat(data.getStream()).isNotNull().hasSize(4);

        criteria.setType(ToolTypeDTO.MCP);
        data = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .body(criteria)
                .post("/search")
                .then()
                .statusCode(OK.getStatusCode())
                .extract()
                .as(ToolPageResultDTO.class);

        assertThat(data.getTotalElements()).isEqualTo(1);
        assertThat(data.getStream()).isNotNull().hasSize(1);
    }

    @Test
    void getToolByIdTest() {
        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .pathParam("id", "tool-none-exists-id")
                .get("/{id}")
                .then().statusCode(NOT_FOUND.getStatusCode());

        var dto = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .pathParam("id", "tool-11-111")
                .get("/{id}")
                .then().statusCode(OK.getStatusCode())
                .extract().as(ToolDTO.class);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo("tool-11-111");
        assertThat(dto.getType()).isEqualTo(ToolTypeDTO.MCP);

        var legacyPolicyTool = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .pathParam("id", "tool-22-222")
                .get("/{id}")
                .then().statusCode(OK.getStatusCode())
                .extract().as(ToolDTO.class);

        assertThat(legacyPolicyTool.getExecutionPolicy()).isEqualTo(ExecutionPolicyDTO.ALWAYS_ALLOW);
    }

    @Test
    void deleteToolTest() {
        var create = new CreateToolRequestDTO();
        create.setName("tool-delete");
        create.setType(ToolTypeDTO.CUSTOM);

        var created = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .body(create)
                .post()
                .then()
                .statusCode(CREATED.getStatusCode())
                .extract().as(ToolDTO.class);

        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .pathParam("id", created.getId())
                .delete("/{id}")
                .then().statusCode(NO_CONTENT.getStatusCode());

        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .pathParam("id", created.getId())
                .get("/{id}")
                .then().statusCode(NOT_FOUND.getStatusCode());
    }

    @Test
    void deleteToolCascadesRulesTest() {
        var create = new CreateToolRequestDTO();
        create.setName("tool-cascade");
        create.setType(ToolTypeDTO.MCP);

        var createdTool = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .body(create)
                .post()
                .then().statusCode(CREATED.getStatusCode())
                .extract().as(ToolDTO.class);

        var rule = new CreateAgentMcpToolRuleRequestDTO();
        rule.setToolName("someTool");
        rule.setAllowed(ToolPermissionDTO.ALLOW);

        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .body(rule)
                .basePath("/internal/agents")
                .pathParam("agentId", "agent-11-111")
                .pathParam("toolId", createdTool.getId())
                .post("/{agentId}/tools/{toolId}/mcp-tool-rules")
                .then().statusCode(CREATED.getStatusCode());

        // verify rule exists
        var rules = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .basePath("/internal/agents")
                .pathParam("agentId", "agent-11-111")
                .pathParam("toolId", createdTool.getId())
                .get("/{agentId}/tools/{toolId}/mcp-tool-rules")
                .then().statusCode(OK.getStatusCode())
                .extract().as(AgentMcpToolRuleListDTO.class);
        assertThat(rules.getRules()).hasSize(1);

        // delete tool cascades to rules
        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .pathParam("id", createdTool.getId())
                .delete("/{id}")
                .then().statusCode(NO_CONTENT.getStatusCode());

        // rules are gone
        var afterDelete = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .basePath("/internal/agents")
                .pathParam("agentId", "agent-11-111")
                .pathParam("toolId", createdTool.getId())
                .get("/{agentId}/tools/{toolId}/mcp-tool-rules")
                .then().statusCode(OK.getStatusCode())
                .extract().as(AgentMcpToolRuleListDTO.class);
        assertThat(afterDelete.getRules()).isEmpty();
    }

    @Test
    void updateToolByIdTest() {
        var dto = new UpdateToolRequestDTO();
        dto.setName("tool-updated");

        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .body(dto)
                .pathParam("id", "does-not-exists")
                .put("/{id}")
                .then().statusCode(BAD_REQUEST.getStatusCode());

        dto.setModificationCount(0);
        dto.setType(ToolTypeDTO.HTTP);
        dto.setExecutionPolicy(ExecutionPolicyDTO.ALWAYS_ASK);

        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .body(dto)
                .pathParam("id", "does-not-exists")
                .put("/{id}")
                .then().statusCode(NOT_FOUND.getStatusCode());

        var updated = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .body(dto)
                .pathParam("id", "tool-11-111")
                .put("/{id}")
                .then().statusCode(OK.getStatusCode())
                .extract().as(ToolDTO.class);

        assertThat(updated).isNotNull();
        assertThat(updated.getName()).isEqualTo("tool-updated");
        assertThat(updated.getModificationCount()).isNotEqualTo(dto.getModificationCount());

        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .body(dto)
                .pathParam("id", "tool-11-111")
                .put("/{id}")
                .then().statusCode(BAD_REQUEST.getStatusCode());
    }

    @Test
    void getDiscoveredTools_nullToolsInBody_noAgentId_returnsEmptyList() {
        // covers else branch: body != null but body.getTools() == null → List.of()
        // covers line 112: agentId is null → List.of()
        mockServerClient.when(request().withPath("/ai/internal/runtime/tools/discover").withMethod(HttpMethod.POST))
                .withId(MOCK_ID)
                .respond(httpRequest -> response().withStatusCode(OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"tools\":null}"));

        var result = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .pathParam("toolId", "tool-11-111")
                .post("/{toolId}/discovered-tools")
                .then().statusCode(OK.getStatusCode())
                .extract().as(DiscoveredToolInfoListDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.getTools()).isNotNull().isEmpty();
    }

    @Test
    void getDiscoveredTools_blankAgentId_returnsEmptyRules() {
        // covers else branch: body.getTools() == null → List.of()
        // covers line 112: agentId is blank → existingRules = List.of()
        mockServerClient.when(request().withPath("/ai/internal/runtime/tools/discover").withMethod(HttpMethod.POST))
                .withId(MOCK_ID)
                .respond(httpRequest -> response().withStatusCode(OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"tools\":[]}"));

        var result = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .pathParam("toolId", "tool-11-111")
                .queryParam("agentId", "  ")
                .post("/{toolId}/discovered-tools")
                .then().statusCode(OK.getStatusCode())
                .extract().as(DiscoveredToolInfoListDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.getTools()).isNotNull().isEmpty();
    }

    @Test
    void getDiscoveredTools_withTools_andAgentId_returnsDiscoveredTools() {
        // covers if branch: body != null && body.getTools() != null → body.getTools()
        // covers line 112: agentId is provided → findByAgentAndToolId
        mockServerClient.when(request().withPath("/ai/internal/runtime/tools/discover").withMethod(HttpMethod.POST))
                .withId(MOCK_ID)
                .respond(httpRequest -> response().withStatusCode(OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"tools\":[{\"name\":\"getProposal\",\"description\":\"Reads a proposal\"}]}"));

        var result = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .pathParam("toolId", "tool-11-111")
                .queryParam("agentId", "agent-11-111")
                .post("/{toolId}/discovered-tools")
                .then().statusCode(OK.getStatusCode())
                .extract().as(DiscoveredToolInfoListDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.getTools()).isNotNull().hasSize(2);
        // getProposal is discovered and matches rule-11-111
        var discovered = result.getTools().stream()
                .filter(t -> "getProposal".equals(t.getName())).findFirst().orElseThrow();
        assertThat(discovered.getExistingRule()).isNotNull();
        assertThat(discovered.getExistingRule().getId()).isEqualTo("rule-11-111");
        assertThat(discovered.getOrphaned()).isFalse();
        // deleteProposal is an orphaned rule (not in discovered tools)
        var orphaned = result.getTools().stream()
                .filter(t -> "deleteProposal".equals(t.getName())).findFirst().orElseThrow();
        assertThat(orphaned.getExistingRule()).isNotNull();
        assertThat(orphaned.getExistingRule().getId()).isEqualTo("rule-22-222");
        assertThat(orphaned.getOrphaned()).isTrue();
    }

    @Test
    void getDiscoveredTools_duplicateRuleNames_mergeFunctionKeepsFirstRule() {
        var ruleDto = new CreateAgentMcpToolRuleRequestDTO();
        ruleDto.setToolName("dupRule");
        ruleDto.setToolDescription("Duplicate rule");
        ruleDto.setAllowed(ToolPermissionDTO.ALLOW);

        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .body(ruleDto)
                .basePath("/internal/agents")
                .pathParam("agentId", "agent-11-111")
                .pathParam("toolId", "tool-11-111")
                .post("/{agentId}/tools/{toolId}/mcp-tool-rules")
                .then().statusCode(CREATED.getStatusCode());

        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .body(ruleDto)
                .basePath("/internal/agents")
                .pathParam("agentId", "agent-11-111")
                .pathParam("toolId", "tool-11-111")
                .post("/{agentId}/tools/{toolId}/mcp-tool-rules")
                .then().statusCode(CREATED.getStatusCode());

        mockServerClient.when(request().withPath("/ai/internal/runtime/tools/discover").withMethod(HttpMethod.POST))
                .withId(MOCK_ID)
                .respond(httpRequest -> response().withStatusCode(OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"tools\":[{\"name\":\"dupRule\",\"description\":\"Duplicate rule\"}]}"));

        var result = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .pathParam("toolId", "tool-11-111")
                .queryParam("agentId", "agent-11-111")
                .post("/{toolId}/discovered-tools")
                .then().statusCode(OK.getStatusCode())
                .extract().as(DiscoveredToolInfoListDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.getTools()).hasSize(3);
        var dup = result.getTools().stream()
                .filter(t -> "dupRule".equals(t.getName())).findFirst().orElseThrow();
        assertThat(dup.getExistingRule()).isNotNull();
        assertThat(dup.getOrphaned()).isFalse();
    }

    @Test
    void getDiscoveredTools_nullBody_returnsEmptyList() {
        // covers line 115 branch: body == null → else → List.of()
        // JSON literal "null" deserializes to null (empty body would throw)
        mockServerClient.when(request().withPath("/ai/internal/runtime/tools/discover").withMethod(HttpMethod.POST))
                .withId(MOCK_ID)
                .respond(httpRequest -> response().withStatusCode(OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("null"));

        var result = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .pathParam("toolId", "tool-11-111")
                .post("/{toolId}/discovered-tools")
                .then().statusCode(OK.getStatusCode())
                .extract().as(DiscoveredToolInfoListDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.getTools()).isNotNull().isEmpty();
    }

    @Test
    void getDiscoveredTools_runtimeError_returnsBadGateway() {
        // covers BAD_GATEWAY branch: runtime returns non-200 (204 No Content
        // avoids ClientWebApplicationException which is thrown for 4xx/5xx
        // when return-response=true)
        mockServerClient.when(request().withPath("/ai/internal/runtime/tools/discover").withMethod(HttpMethod.POST))
                .withId(MOCK_ID)
                .respond(httpRequest -> response().withStatusCode(NO_CONTENT.getStatusCode()));

        given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .pathParam("toolId", "tool-11-111")
                .post("/{toolId}/discovered-tools")
                .then().statusCode(BAD_GATEWAY.getStatusCode());
    }
}
