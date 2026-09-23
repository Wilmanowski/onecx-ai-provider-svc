package org.tkit.onecx.ai.provider.domain.models;

import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.tkit.onecx.ai.provider.domain.models.enums.AgentStatus;
import org.tkit.quarkus.jpa.models.TraceableEntity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "AGENT")
public class Agent extends TraceableEntity {

    @TenantId
    @Column(name = "TENANT_ID")
    private String tenantId;

    @Column(name = "NAME")
    private String name;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "ADDITIONAL_PROMPT")
    private String additionalPrompt;

    @Column(name = "A2A_ENABLED")
    private Boolean a2aEnabled;

    @Column(name = "VOICE_ENABLED")
    private Boolean voiceEnabled;

    @Column(name = "LANGUAGE_CODE", length = 32)
    private String languageCode;

    @Column(name = "STATUS")
    @Enumerated(EnumType.STRING)
    private AgentStatus status;

    @ManyToOne
    @JoinColumn(name = "MODEL_ID")
    private Model model;

    @ManyToOne
    @JoinColumn(name = "SCAFFOLD_ID")
    private Scaffold scaffold;

    @ManyToOne
    @JoinColumn(name = "GLOBAL_SCAFFOLD_ID")
    private GlobalScaffold globalScaffold;

    @Embedded
    private Filter filter;

    @ManyToMany
    @JoinTable(name = "AGENT_TOOL_RL", joinColumns = @JoinColumn(name = "AGENT_ID"), inverseJoinColumns = @JoinColumn(name = "TOOL_ID"))
    private Set<Tool> tools;

    @ManyToMany
    @JoinTable(name = "AGENT_GLOBAL_TOOL_RL", joinColumns = @JoinColumn(name = "AGENT_ID"), inverseJoinColumns = @JoinColumn(name = "GLOBAL_TOOL_ID"))
    private Set<GlobalTool> globalTools;

    @ManyToMany
    @JoinTable(name = "AGENT_GROUP_RL", joinColumns = @JoinColumn(name = "AGENT_ID"), inverseJoinColumns = @JoinColumn(name = "GROUP_ID"))
    private Set<AgentGroup> groups;
}
