package org.tkit.onecx.ai.provider.domain.models;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.TenantId;

import lombok.AccessLevel;
import lombok.Getter;

@Getter
@Entity
@Immutable
@Table(name = "AGENT_CONFIGURATION_VERSION", uniqueConstraints = {
        @UniqueConstraint(name = "uq_agent_configuration_version_owner", columnNames = { "AGENT_ID", "GUID" }),
        @UniqueConstraint(name = "uq_agent_configuration_version_name", columnNames = { "TENANT_ID", "AGENT_ID", "NAME" })
})
public class AgentConfigurationVersion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "GUID", updatable = false, nullable = false)
    private String id;

    @TenantId
    @Column(name = "TENANT_ID", updatable = false, nullable = false)
    private String tenantId;

    @Column(name = "AGENT_ID", updatable = false, nullable = false)
    private String agentId;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AGENT_ID", insertable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_configuration_version_agent"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Agent agent;

    @Column(name = "NAME", updatable = false, nullable = false)
    private String name;

    @Column(name = "CONFIGURATION", columnDefinition = "text", updatable = false, nullable = false)
    private String configuration;

    protected AgentConfigurationVersion() {
    }

    public AgentConfigurationVersion(Agent agent, String name, String configuration) {
        this.id = UUID.randomUUID().toString();
        this.tenantId = agent.getTenantId();
        this.agentId = agent.getId();
        this.name = name;
        this.configuration = configuration;
    }
}
