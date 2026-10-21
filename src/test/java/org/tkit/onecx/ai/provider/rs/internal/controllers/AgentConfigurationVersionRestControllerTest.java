package org.tkit.onecx.ai.provider.rs.internal.controllers;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.SecurityContext;

import org.junit.jupiter.api.Test;
import org.tkit.onecx.ai.provider.common.services.agent.AgentConfigurationService;
import org.tkit.onecx.ai.provider.domain.daos.AgentConfigurationVersionDAO;
import org.tkit.onecx.ai.provider.domain.daos.AgentDAO;
import org.tkit.onecx.ai.provider.domain.daos.ProviderDAO;
import org.tkit.onecx.ai.provider.domain.models.AgentConfigurationVersion;
import org.tkit.onecx.ai.provider.domain.models.Provider;
import org.tkit.onecx.ai.provider.domain.models.enums.AuthMode;
import org.tkit.onecx.ai.provider.domain.models.enums.ProviderType;
import org.tkit.onecx.ai.provider.test.AbstractTest;
import org.tkit.quarkus.security.test.GenerateKeycloakClient;
import org.tkit.quarkus.test.WithDBData;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentConfigurationDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ProblemDetailResponseDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.AgentConfigurationVersionDTO;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@TestHTTPEndpoint(AgentRestController.class)
@WithDBData(value = "data/testdata-internal.xml", deleteBeforeInsert = true, deleteAfterTest = true, rinseAndRepeat = true)
@GenerateKeycloakClient(clientName = "versionEditor", scopes = { "ocx-ai:all", "ocx-ai:read", "ocx-ai:write" })
class AgentConfigurationVersionRestControllerTest extends AbstractTest {

    @Inject
    AgentDAO agentDAO;

    @Inject
    AgentConfigurationVersionDAO versionDAO;

    @Inject
    ProviderDAO providerDAO;

    @Inject
    AgentConfigurationService configurationService;

    @Test
    void absentVersionRequestReturnsBadRequest() {
        var controller = new AgentRestController();
        controller.configurationService = configurationService;
        controller.securityContext = mock(SecurityContext.class);
        when(controller.securityContext.getUserPrincipal()).thenReturn(() -> "versionEditor");

        try (var response = controller.createAgentConfigurationVersion("agent-11-111", null)) {
            assertThat(response.getStatus()).isEqualTo(BAD_REQUEST.getStatusCode());
        }
    }

    @Test
    void deletedSavedProviderPreventsActivationEvenWhenDraftProviderExists() {
        var providerId = QuarkusTransaction.requiringNew().call(() -> {
            var provider = new Provider();
            provider.setName("Temporary provider");
            provider.setType(ProviderType.OPENAI);
            provider.setAuthMode(AuthMode.API_KEY);
            provider.setApiKey("temporary-key");
            provider.setLlmUrl("http://provider.example");
            provider = providerDAO.create(provider);
            agentDAO.findById("agent-11-111").getModel().setProvider(provider);
            return provider.getId();
        });
        var first = createVersion("First");
        var second = createVersion("Second");
        QuarkusTransaction.requiringNew().run(() -> {
            agentDAO.findById("agent-11-111").getModel()
                    .setProvider(providerDAO.findById("provider-11-111"));
        });
        QuarkusTransaction.requiringNew().run(() -> providerDAO.deleteQueryById(providerId));

        var problem = given().auth().oauth2(getKeycloakClientToken("versionEditor"))
                .basePath("/internal/agents")
                .put("/agent-11-111/configuration/versions/" + second.getId() + "/activate")
                .then().statusCode(BAD_REQUEST.getStatusCode()).extract().as(ProblemDetailResponseDTOV1.class);
        assertThat(problem.getErrorCode()).isEqualTo("INVALID_AGENT_CONFIGURATION_DEPENDENCIES");
        assertThat(problem.getDetail()).contains(providerId, "does not exist");
        QuarkusTransaction.requiringNew().run(() -> {
            assertThat(agentDAO.findById("agent-11-111").getActiveConfigurationVersionId()).isEqualTo(first.getId());
        });
        assertThat(readVersion(second.getId())
                .getProvider().getId()).isEqualTo(providerId);
    }

    @Test
    void configurationVersionsPersistAcrossDraftChangesAndActivation() {
        var first = createVersion("First");
        assertThat(first.getActive()).isTrue();
        var firstConfiguration = readVersion(first.getId());

        QuarkusTransaction.requiringNew().run(() -> {
            var agent = agentDAO.findById("agent-11-111");
            agent.setName("Updated draft");
        });
        var second = createVersion("Second");
        assertThat(second.getActive()).isFalse();
        assertThat(second.getId()).isNotEqualTo(first.getId());
        var secondConfiguration = readVersion(second.getId());

        assertThat(readConfiguration("/agent-11-111/configuration"))
                .usingRecursiveComparison().isEqualTo(firstConfiguration);
        var activated = given().auth().oauth2(getKeycloakClientToken("versionEditor"))
                .basePath("/internal/agents")
                .put("/agent-11-111/configuration/versions/" + second.getId() + "/activate")
                .then().statusCode(OK.getStatusCode()).extract().as(AgentConfigurationVersionDTO.class);
        assertThat(activated.getId()).isEqualTo(second.getId());
        assertThat(activated.getName()).isEqualTo(second.getName());
        assertThat(activated.getActive()).isTrue();

        assertThat(readConfiguration("/agent-11-111/configuration").getAgent().getName()).isEqualTo("Updated draft");
        assertThat(readConfiguration("/agent-11-111/configuration"))
                .usingRecursiveComparison().isEqualTo(secondConfiguration);
        assertThat(readVersion(first.getId()))
                .usingRecursiveComparison().isEqualTo(firstConfiguration);
        assertThat(readVersion(second.getId()))
                .usingRecursiveComparison().isEqualTo(secondConfiguration);

        given().auth().oauth2(getKeycloakClientToken("versionEditor"))
                .basePath("/internal/agents")
                .get("/agent-22-222/configuration/versions/" + first.getId())
                .then().statusCode(NOT_FOUND.getStatusCode());
        given().auth().oauth2(getKeycloakClientToken("versionEditor"))
                .basePath("/internal/agents")
                .put("/agent-22-222/configuration/versions/" + first.getId() + "/activate")
                .then().statusCode(NOT_FOUND.getStatusCode());
    }

    @Test
    void firstReadInitializesOneStableActiveVersion() {
        var initial = given().auth().oauth2(getKeycloakClientToken("versionEditor"))
                .basePath("/v1/agents").get("/agent-11-111/configuration")
                .then().statusCode(OK.getStatusCode()).extract().response();
        var location = initial.header("Content-Location");
        assertThat(location).isNotBlank();
        assertThat(URI.create(location).getPath()).startsWith("/internal/agents/agent-11-111/configuration/versions/");
        var initialConfiguration = initial.as(AgentConfigurationDTOV1.class);

        QuarkusTransaction.requiringNew().run(() -> agentDAO.findById("agent-11-111").setName("Changed draft"));
        var subsequent = given().auth().oauth2(getKeycloakClientToken("versionEditor"))
                .basePath("/v1/agents").get("/agent-11-111/configuration")
                .then().statusCode(OK.getStatusCode()).extract().response();
        assertThat(subsequent.header("Content-Location")).isEqualTo(location);
        assertThat(subsequent.as(AgentConfigurationDTOV1.class))
                .usingRecursiveComparison().isEqualTo(initialConfiguration);

        var saved = createVersion("New draft");
        assertThat(saved.getActive()).isFalse();
        assertThat(readConfiguration("/agent-11-111/configuration"))
                .usingRecursiveComparison().isEqualTo(initialConfiguration);
    }

    @Test
    void duplicateVersionNamesReturnBadRequestWithoutChangingActiveVersion() {
        var first = createVersion("Release 1");
        for (var name : List.of("Release 1", "  Release 1  ")) {
            var problem = given().auth().oauth2(getKeycloakClientToken("versionEditor"))
                    .basePath("/internal/agents").contentType(APPLICATION_JSON)
                    .body(Map.of("name", name))
                    .post("/agent-11-111/configuration/versions")
                    .then().statusCode(BAD_REQUEST.getStatusCode()).extract()
                    .as(ProblemDetailResponseDTOV1.class);
            assertThat(problem.getErrorCode()).isEqualTo("DUPLICATE_AGENT_CONFIGURATION_VERSION_NAME");
        }
        var response = given().auth().oauth2(getKeycloakClientToken("versionEditor"))
                .basePath("/v1/agents").get("/agent-11-111/configuration")
                .then().statusCode(OK.getStatusCode()).extract().response();
        assertThat(response.header("Content-Location")).endsWith("/" + first.getId());
        assertThat(response.as(AgentConfigurationDTOV1.class).getAgent().getName()).isEqualTo("agent1");
    }

    @Test
    void namesAreCaseSensitiveAndCanBeReusedByOtherAgents() {
        var first = createVersion("  Release 1  ");
        assertThat(first.getName()).isEqualTo("Release 1");
        var differentCase = createVersion("release 1");
        assertThat(differentCase.getId()).isNotEqualTo(first.getId());
        assertThat(differentCase.getActive()).isFalse();

        var otherAgentVersion = given().auth().oauth2(getKeycloakClientToken("versionEditor"))
                .basePath("/internal/agents").contentType(APPLICATION_JSON)
                .body(Map.of("name", "Release 1"))
                .post("/agent-22-222/configuration/versions")
                .then().statusCode(CREATED.getStatusCode()).extract().as(AgentConfigurationVersionDTO.class);
        assertThat(otherAgentVersion.getId()).isNotEqualTo(first.getId());
        assertThat(otherAgentVersion.getActive()).isTrue();
    }

    @Test
    void databaseRejectsDuplicateNamesEvenWhenBypassingController() {
        createVersion("Release 1");
        assertThatThrownBy(() -> QuarkusTransaction.requiringNew().run(() -> {
            var agent = agentDAO.findById("agent-11-111");
            versionDAO.create(new AgentConfigurationVersion(agent, "Release 1", "{}"));
        })).hasStackTraceContaining("uq_agent_configuration_version_name");
    }

    @Test
    void invalidVersionNamesAndMissingAgentsAreRejected() {
        for (var name : List.of("", "  ", "a".repeat(256))) {
            given().auth().oauth2(getKeycloakClientToken("versionEditor"))
                    .basePath("/internal/agents").contentType(APPLICATION_JSON)
                    .body(Map.of("name", name))
                    .post("/agent-11-111/configuration/versions")
                    .then().statusCode(BAD_REQUEST.getStatusCode());
        }
        given().auth().oauth2(getKeycloakClientToken("versionEditor"))
                .basePath("/internal/agents").contentType(APPLICATION_JSON)
                .body(Map.of("name", "Missing"))
                .post("/missing/configuration/versions")
                .then().statusCode(NOT_FOUND.getStatusCode());
    }

    private AgentConfigurationVersionDTO createVersion(String name) {
        var response = given().auth().oauth2(getKeycloakClientToken("versionEditor"))
                .basePath("/internal/agents").contentType(APPLICATION_JSON)
                .body(Map.of("name", name))
                .post("/agent-11-111/configuration/versions")
                .then().statusCode(CREATED.getStatusCode()).extract().response();
        var version = response.as(AgentConfigurationVersionDTO.class);
        assertThat(URI.create(response.header("Location")).getPath())
                .isEqualTo("/internal/agents/agent-11-111/configuration/versions/" + version.getId());
        return version;
    }

    private AgentConfigurationDTOV1 readVersion(String versionId) {
        var response = given().auth().oauth2(getKeycloakClientToken("versionEditor"))
                .basePath("/internal/agents")
                .get("/agent-11-111/configuration/versions/" + versionId)
                .then().statusCode(OK.getStatusCode()).extract().response();
        assertThat(URI.create(response.header("Content-Location")).getPath())
                .isEqualTo("/internal/agents/agent-11-111/configuration/versions/" + versionId);
        return response.as(AgentConfigurationDTOV1.class);
    }

    private AgentConfigurationDTOV1 readConfiguration(String path) {
        return given().auth().oauth2(getKeycloakClientToken("versionEditor"))
                .basePath("/v1/agents").get(path)
                .then().statusCode(OK.getStatusCode()).extract().as(AgentConfigurationDTOV1.class);
    }
}