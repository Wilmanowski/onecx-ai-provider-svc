package org.tkit.onecx.ai.provider.domain.daos;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.tkit.onecx.ai.provider.domain.models.AgentConfigurationVersion;

@ApplicationScoped
public class AgentConfigurationVersionDAO {

    @Inject
    EntityManager entityManager;

    public void create(AgentConfigurationVersion version) {
        entityManager.persist(version);
        entityManager.flush();
    }

    public boolean existsByAgentAndName(String agentId, String name) {
        return entityManager.createQuery(
                "select count(v) from AgentConfigurationVersion v where v.agentId = :agentId and v.name = :name",
                Long.class)
                .setParameter("agentId", agentId)
                .setParameter("name", name)
                .getSingleResult() > 0;
    }

    public AgentConfigurationVersion findByAgentAndId(String agentId, String versionId) {
        return entityManager.createQuery(
                "select v from AgentConfigurationVersion v where v.agentId = :agentId and v.id = :versionId",
                AgentConfigurationVersion.class)
                .setParameter("agentId", agentId)
                .setParameter("versionId", versionId)
                .getResultStream().findFirst().orElse(null);
    }
}