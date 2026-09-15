package org.tkit.onecx.ai.provider.domain.daos;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolPermission;
import org.tkit.onecx.ai.provider.test.AbstractTest;
import org.tkit.quarkus.test.WithDBData;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@WithDBData(value = "data/testdata-internal.xml", deleteBeforeInsert = true, deleteAfterTest = true, rinseAndRepeat = true)
class AgentMcpToolRuleDAOHappyPathTest extends AbstractTest {

    @Inject
    AgentMcpToolRuleDAO dao;

    @Test
    void findByAgentId_returnsRulesForAgent() {
        var rules = dao.findByAgentId("agent-11-111");
        assertThat(rules).hasSize(2);
        assertThat(rules).extracting(r -> r.getToolName()).contains("getProposal", "deleteProposal");
        assertThat(rules)
                .extracting(r -> r.getAllowed())
                .contains(ToolPermission.ALWAYS_ALLOW, ToolPermission.DENY);
    }

    @Test
    void findByAgentId_mapsLegacyNeverAskToAlwaysAllow() {
        var rules = dao.findByAgentId("agent-22-222");
        assertThat(rules).singleElement().satisfies(rule -> {
            assertThat(rule.getToolName()).isEqualTo("globalRead");
            assertThat(rule.getAllowed()).isEqualTo(ToolPermission.ALWAYS_ALLOW);
        });
    }
}
