package org.tkit.onecx.ai.provider.common.services.version;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.tkit.onecx.ai.provider.domain.daos.AgentDAO;
import org.tkit.onecx.ai.provider.domain.daos.AgentMcpToolRuleDAO;
import org.tkit.onecx.ai.provider.rs.external.v1.mappers.AgentConfigurationVersionMapper;
import org.tkit.quarkus.context.ApplicationContext;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentConfigurationVersionDTOV1;

/**
 * Publishes canonical, content-addressed Agent Configuration versions.
 */
@ApplicationScoped
public class AgentConfigurationVersionService {

    @Inject
    AgentDAO agentDAO;

    @Inject
    AgentMcpToolRuleDAO agentMcpToolRuleDAO;

    @Inject
    AgentConfigurationVersionMapper mapper;

    /**
     * @return the current configuration version payload of the agent or empty if the agent does not exist
     * @throws VersionGenerationException if the configuration is invalid
     */
    public Optional<AgentConfigurationVersionDTOV1> getVersion(String agentId) {
        var agent = agentDAO.findById(agentId);
        if (agent == null) {
            return Optional.empty();
        }
        var rules = agentMcpToolRuleDAO.findByAgentId(agent.getId());

        var context = ApplicationContext.get();
        String tenantId = agent.getTenantId();
        String principal = null;
        if (context != null) {
            tenantId = tenantId != null ? tenantId : context.getTenantId();
            principal = context.getPrincipal();
        }
        return Optional.of(mapper.build(agent, rules, tenantId, principal));
    }
}
