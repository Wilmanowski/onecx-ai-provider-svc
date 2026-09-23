package org.tkit.onecx.ai.provider.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.TenantId;
import org.tkit.onecx.ai.provider.domain.models.converters.ToolPermissionConverter;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolPermission;
import org.tkit.quarkus.jpa.models.TraceableEntity;

import lombok.Getter;
import lombok.Setter;

@SuppressWarnings("squid:S2160")
@Getter
@Setter
@Entity
@Table(name = "AGENT_MCP_TOOL_RULE", indexes = {
        @Index(name = "idx_agent_mcp_tool_rule_agent", columnList = "agent_id"),
        @Index(name = "idx_agent_mcp_tool_rule_tool", columnList = "tool_id"),
        @Index(name = "idx_agent_mcp_tool_rule_global_tool", columnList = "global_tool_id")
})
public class AgentMcpToolRule extends TraceableEntity {

    @TenantId
    @Column(name = "TENANT_ID", nullable = false)
    private String tenantId;

    @ManyToOne
    @JoinColumn(name = "AGENT_ID")
    private Agent agent;

    @ManyToOne
    @JoinColumn(name = "TOOL_ID")
    private Tool tool;

    @ManyToOne
    @JoinColumn(name = "GLOBAL_TOOL_ID")
    private GlobalTool globalTool;

    @Column(name = "TOOL_NAME", nullable = false)
    private String toolName;

    @Column(name = "TOOL_DESCRIPTION", length = 1024)
    private String toolDescription;

    @Column(name = "ALLOWED", nullable = false)
    @Convert(converter = ToolPermissionConverter.class)
    private ToolPermission allowed;
}
