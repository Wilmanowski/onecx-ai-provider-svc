package org.tkit.onecx.ai.provider.rs.internal.mappers;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.tkit.onecx.ai.provider.domain.models.Agent;
import org.tkit.onecx.ai.provider.domain.models.AgentMcpToolRule;
import org.tkit.onecx.ai.provider.domain.models.GlobalTool;
import org.tkit.onecx.ai.provider.domain.models.Tool;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolPermission;
import org.tkit.quarkus.rs.mappers.OffsetDateTimeMapper;

import gen.org.tkit.onecx.ai.provider.rs.internal.model.AgentMcpToolRuleDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.CreateAgentMcpToolRuleRequestDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.ToolPermissionDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.UpdateAgentMcpToolRuleRequestDTO;

@Mapper(uses = { OffsetDateTimeMapper.class })
public interface AgentMcpToolRuleMapper {

    @Mapping(target = "id", source = "id")
    AgentMcpToolRuleDTO map(AgentMcpToolRule rule);

    List<AgentMcpToolRuleDTO> map(List<AgentMcpToolRule> rules);

    @Mapping(target = "persisted", ignore = true)
    @Mapping(target = "modificationUser", ignore = true)
    @Mapping(target = "modificationDate", ignore = true)
    @Mapping(target = "modificationCount", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creationUser", ignore = true)
    @Mapping(target = "creationDate", ignore = true)
    @Mapping(target = "controlTraceabilityManual", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "agent", ignore = true)
    @Mapping(target = "tool", ignore = true)
    @Mapping(target = "globalTool", ignore = true)
    AgentMcpToolRule create(CreateAgentMcpToolRuleRequestDTO dto);

    @Mapping(target = "persisted", ignore = true)
    @Mapping(target = "modificationUser", ignore = true)
    @Mapping(target = "modificationDate", ignore = true)
    @Mapping(target = "modificationCount", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creationUser", ignore = true)
    @Mapping(target = "creationDate", ignore = true)
    @Mapping(target = "controlTraceabilityManual", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "agent", source = "agent")
    @Mapping(target = "tool", source = "tool")
    @Mapping(target = "globalTool", source = "globalTool")
    AgentMcpToolRule create(CreateAgentMcpToolRuleRequestDTO dto, Agent agent, Tool tool,
            GlobalTool globalTool);

    @Mapping(target = "persisted", ignore = true)
    @Mapping(target = "modificationUser", ignore = true)
    @Mapping(target = "modificationDate", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creationUser", ignore = true)
    @Mapping(target = "creationDate", ignore = true)
    @Mapping(target = "controlTraceabilityManual", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "agent", ignore = true)
    @Mapping(target = "tool", ignore = true)
    @Mapping(target = "globalTool", ignore = true)
    @Mapping(target = "toolName", ignore = true)
    @Mapping(target = "toolDescription", ignore = true)
    void update(@MappingTarget AgentMcpToolRule rule, UpdateAgentMcpToolRuleRequestDTO dto);

    default ToolPermission mapToolPermission(ToolPermissionDTO permissionDTO) {
        if (permissionDTO == null) {
            return ToolPermission.DEFAULT;
        }
        return ToolPermission.fromValueOrDefault(permissionDTO.toString()).toCanonical();
    }

    default ToolPermissionDTO mapToolPermission(ToolPermission permission) {
        var canonical = (permission == null ? ToolPermission.DEFAULT : permission).toCanonical();
        try {
            return ToolPermissionDTO.fromValue(canonical.name());
        } catch (IllegalArgumentException ignored) {
            if (canonical == ToolPermission.ALWAYS_ALLOW) {
                try {
                    return ToolPermissionDTO.fromValue("ALLOW");
                } catch (IllegalArgumentException ignored2) {
                    return ToolPermissionDTO.ALWAYS_ASK;
                }
            }
            return canonical == ToolPermission.DENY ? ToolPermissionDTO.DENY : ToolPermissionDTO.ALWAYS_ASK;
        }
    }
}
