package org.tkit.onecx.ai.provider.rs.external.v1.mappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tkit.onecx.ai.provider.domain.daos.AgentMcpToolRuleDAO;
import org.tkit.onecx.ai.provider.domain.models.Agent;
import org.tkit.onecx.ai.provider.domain.models.Model;
import org.tkit.onecx.ai.provider.domain.models.Provider;
import org.tkit.onecx.ai.provider.domain.models.enums.ProviderType;
import org.tkit.onecx.ai.provider.test.AbstractTest;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ChatMessageDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ChatRequestDTOV1;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class RuntimeSnapshotMapperTest extends AbstractTest {

    @Inject
    RuntimeSnapshotMapper mapper;

    @InjectMock
    AgentMcpToolRuleDAO agentMcpToolRuleDAO;

    @BeforeEach
    void setUp() {
        when(agentMcpToolRuleDAO.findByAgentAndToolIds(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(agentMcpToolRuleDAO.findByAgentAndGlobalToolIds(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
    }

    @Test
    void mapAgent_null_returnsNull() {
        assertThat(mapper.mapAgent(null)).isNull();
        assertThat(mapper.mapAgent(null, List.of())).isNull();
    }

    @Test
    void mapRuntimeChatMessage_nullMessage_returnsEmptyString() {
        var msg = mapper.mapRuntimeChatMessage(null, "conv-1");
        assertThat(msg.getMessage()).isEmpty();
        assertThat(msg.getType()).isEqualTo(ChatMessageDTOV1.TypeEnum.ASSISTANT);
        assertThat(msg.getConversationId()).isEqualTo("conv-1");
    }

    @Test
    void toRuntimeRequest_mapsModelAndProvider() {
        var provider = new Provider();
        provider.setType(ProviderType.OLLAMA);
        provider.setLlmUrl("http://ollama.local");

        var model = new Model();
        model.setProvider(provider);
        model.setModelIdentifier("mistral");

        var agent = new Agent();
        agent.setName("agent-1");
        agent.setModel(model);

        var request = new ChatRequestDTOV1();
        var msg = new ChatMessageDTOV1();
        msg.setType(ChatMessageDTOV1.TypeEnum.USER);
        msg.setMessage("hello");
        msg.setConversationId("conv-1");
        request.setChatMessage(msg);

        var result = mapper.toRuntimeRequest(agent, request, List.of());
        assertThat(result.getRootAgent().getName()).isEqualTo("agent-1");
        assertThat(result.getRootAgent().getModel().getModelIdentifier()).isEqualTo("mistral");
        assertThat(result.getRootAgent().getModel().getProvider().getType()).isEqualTo("OLLAMA");
        assertThat(result.getChatRequest().getChatMessage().getMessage()).isEqualTo("hello");
    }
}
