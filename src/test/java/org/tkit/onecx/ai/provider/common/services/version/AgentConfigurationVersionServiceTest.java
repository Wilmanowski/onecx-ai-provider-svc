package org.tkit.onecx.ai.provider.common.services.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.tkit.onecx.ai.provider.domain.daos.AgentDAO;
import org.tkit.onecx.ai.provider.domain.daos.AgentMcpToolRuleDAO;
import org.tkit.onecx.ai.provider.domain.models.Agent;
import org.tkit.onecx.ai.provider.domain.models.AgentMcpToolRule;
import org.tkit.onecx.ai.provider.rs.external.v1.mappers.AgentConfigurationVersionMapper;
import org.tkit.onecx.ai.provider.test.AbstractTest;
import org.tkit.quarkus.context.ApplicationContext;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentConfigurationVersionDTOV1;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AgentConfigurationVersionServiceTest extends AbstractTest {

    @Inject
    AgentConfigurationVersionService service;

    @InjectMock
    AgentDAO agentDAO;

    @InjectMock
    AgentMcpToolRuleDAO agentMcpToolRuleDAO;

    @InjectMock
    AgentConfigurationVersionMapper mapper;

    @Test
    void getVersion_returnsEmpty_whenAgentDoesNotExist() {
        when(agentDAO.findById("missing")).thenReturn(null);

        var result = service.getVersion("missing");

        assertThat(result).isEmpty();
        verify(agentDAO).findById("missing");
        verifyNoInteractions(agentMcpToolRuleDAO, mapper);
    }

    @Test
    void getVersion_usesAgentTenant_whenNoRequestContextExists() {
        var agent = new Agent();
        agent.setId("agent-1");
        agent.setTenantId("tenant-a");
        var rules = List.<AgentMcpToolRule> of();
        var dto = new AgentConfigurationVersionDTOV1();

        when(agentDAO.findById("agent-1")).thenReturn(agent);
        when(agentMcpToolRuleDAO.findByAgentId("agent-1")).thenReturn(rules);
        when(mapper.build(agent, rules, "tenant-a", null)).thenReturn(dto);

        try (MockedStatic<ApplicationContext> context = Mockito.mockStatic(ApplicationContext.class)) {
            context.when(ApplicationContext::get).thenReturn(null);

            var result = service.getVersion("agent-1");

            assertThat(result).containsSame(dto);
            verify(mapper).build(agent, rules, "tenant-a", null);
        }
    }

    @Test
    void getVersion_usesRequestContextData_whenContextExistsAndAgentTenantMissing() throws Exception {
        var agent = new Agent();
        agent.setId("agent-2");
        agent.setTenantId(null);
        var rules = List.<AgentMcpToolRule> of();
        var dto = new AgentConfigurationVersionDTOV1();

        when(agentDAO.findById("agent-2")).thenReturn(agent);
        when(agentMcpToolRuleDAO.findByAgentId("agent-2")).thenReturn(rules);
        when(mapper.build(agent, rules, "tenant-from-context", "alice")).thenReturn(dto);

        Method getMethod = ApplicationContext.class.getMethod("get");
        Class<?> returnType = getMethod.getReturnType();
        Answer<Object> answer = invocation -> switch (invocation.getMethod().getName()) {
            case "getTenantId" -> "tenant-from-context";
            case "getPrincipal" -> "alice";
            default -> null;
        };
        Object contextObject = Mockito.mock(returnType, answer);

        try (MockedStatic<ApplicationContext> context = Mockito.mockStatic(ApplicationContext.class)) {
            context.when(ApplicationContext::get).thenReturn(contextObject);

            var result = service.getVersion("agent-2");

            assertThat(result).containsSame(dto);
            verify(mapper).build(agent, rules, "tenant-from-context", "alice");
        }
    }

}
