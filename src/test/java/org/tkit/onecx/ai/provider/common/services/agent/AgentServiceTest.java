package org.tkit.onecx.ai.provider.common.services.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.tkit.onecx.ai.provider.domain.daos.AgentDAO;
import org.tkit.onecx.ai.provider.domain.models.Agent;
import org.tkit.onecx.ai.provider.domain.models.Filter;
import org.tkit.onecx.ai.provider.domain.models.enums.FilterKey;
import org.tkit.onecx.ai.provider.test.AbstractTest;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentFilterDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.RequestContextDTOV1;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AgentServiceTest extends AbstractTest {

    @Inject
    AgentService agentService;

    @InjectMock
    AgentDAO agentDAO;

    @Test
    void findAgentByRequestContext_withoutFilterValue_returnsAgentWithNonNullFilterAndNullValue() {
        var filter = new Filter();
        filter.setKey(FilterKey.APP_ID);

        var agent = new Agent();
        agent.setId("a1");
        agent.setFilter(filter);

        when(agentDAO.findAllAgentsByFilterKey("APP_ID")).thenReturn(List.of(agent));

        var requestContext = new RequestContextDTOV1()
                .filter(new AgentFilterDTOV1().key(AgentFilterDTOV1.KeyEnum.APP_ID));

        assertThat(agentService.findAgentByRequestContext(requestContext)).isEqualTo(agent);
    }

    @Test
    void findAgentByRequestContext_withFilterValue_dropsAgentWithNullFilterValue() {
        var filter = new Filter();
        filter.setKey(FilterKey.APP_ID);

        var agent = new Agent();
        agent.setId("a1");
        agent.setFilter(filter);

        when(agentDAO.findAllAgentsByFilterKey("APP_ID")).thenReturn(List.of(agent));

        var requestContext = new RequestContextDTOV1()
                .filter(new AgentFilterDTOV1().key(AgentFilterDTOV1.KeyEnum.APP_ID).value("app-1"));

        assertThat(agentService.findAgentByRequestContext(requestContext)).isNull();
    }
}