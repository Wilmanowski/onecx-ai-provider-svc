package org.tkit.onecx.ai.provider.rs.external.v1.controllers;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.tkit.onecx.ai.provider.test.AbstractTest;
import org.tkit.quarkus.security.test.GenerateKeycloakClient;
import org.tkit.quarkus.test.WithDBData;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.*;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@WithDBData(value = "data/testdata-internal.xml", deleteBeforeInsert = true, deleteAfterTest = true, rinseAndRepeat = true)
@GenerateKeycloakClient(clientName = "testClient", scopes = { "ocx-ai:read" })
class AgentRestV1ControllerTest extends AbstractTest {

    private static final String SEARCH_PATH = "/v1/agents/search";

    @Test
    void findAgentBySearchCriteriaTest() {
        var criteria = new AgentSearchCriteriaDTOV1();
        var data = given()
                .auth().oauth2(getKeycloakClientToken("testClient"))
                .contentType(APPLICATION_JSON)
                .body(criteria)
                .post(SEARCH_PATH)
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
                .post(SEARCH_PATH)
                .then()
                .statusCode(OK.getStatusCode())
                .extract()
                .as(AgentPageResultDTOV1.class);

        assertThat(data.getTotalElements()).isEqualTo(3);
        assertThat(data.getStream()).isNotNull().hasSize(1);
    }
}
