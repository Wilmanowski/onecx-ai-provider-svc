package org.tkit.onecx.ai.provider.rs.internal.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.tkit.onecx.ai.provider.common.services.DangerClassificationService;
import org.tkit.onecx.ai.provider.domain.criteria.ToolSearchCriteria;
import org.tkit.onecx.ai.provider.domain.models.AbstractTool;
import org.tkit.onecx.ai.provider.domain.models.Tool;
import org.tkit.onecx.ai.provider.domain.models.enums.ExecutionPolicy;
import org.tkit.quarkus.jpa.daos.PageResult;
import org.tkit.quarkus.rs.mappers.OffsetDateTimeMapper;

import gen.org.tkit.onecx.ai.provider.rs.internal.model.CreateToolRequestDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.DangerLevelDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.DiscoveredToolAnnotationsDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.DiscoveredToolInfoDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.ExecutionPolicyDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.ToolDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.ToolPageResultDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.ToolSearchCriteriaDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.UpdateToolRequestDTO;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.DiscoveredTool;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.DiscoveredToolAnnotations;
import gen.org.tkit.onecx.ai.provider.runtime.client.model.ToolDiscoveryRequest;

@Mapper(uses = { OffsetDateTimeMapper.class })
public interface ToolMapper {

    @Mapping(target = "persisted", ignore = true)
    @Mapping(target = "modificationUser", ignore = true)
    @Mapping(target = "modificationDate", ignore = true)
    @Mapping(target = "modificationCount", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creationUser", ignore = true)
    @Mapping(target = "creationDate", ignore = true)
    @Mapping(target = "controlTraceabilityManual", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "source", ignore = true)
    Tool create(CreateToolRequestDTO createToolRequestDTO);

    ToolDTO map(Tool tool);

    @Mapping(target = "persisted", ignore = true)
    @Mapping(target = "controlTraceabilityManual", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "source", ignore = true)
    Tool map(ToolDTO toolDTO);

    @Mapping(target = "persisted", ignore = true)
    @Mapping(target = "modificationUser", ignore = true)
    @Mapping(target = "modificationDate", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creationUser", ignore = true)
    @Mapping(target = "creationDate", ignore = true)
    @Mapping(target = "controlTraceabilityManual", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "source", ignore = true)
    void update(@MappingTarget Tool tool, UpdateToolRequestDTO updateToolRequestDTO);

    ToolSearchCriteria mapCriteria(ToolSearchCriteriaDTO criteriaDTO);

    @Mapping(target = "removeStreamItem", ignore = true)
    ToolPageResultDTO mapPageResult(PageResult<Tool> result);

    @Mapping(target = "authMode", expression = "java(tool.getAuthMode() != null ? tool.getAuthMode().name() : null)")
    ToolDiscoveryRequest mapDiscoveryRequest(AbstractTool tool);

    DiscoveredToolAnnotationsDTO mapAnnotations(DiscoveredToolAnnotations annotations);

    @Mapping(target = "autoDangerLevel", ignore = true)
    @Mapping(target = "existingRule", ignore = true)
    @Mapping(target = "orphaned", ignore = true)
    DiscoveredToolInfoDTO mapDiscoveredToolBase(DiscoveredTool dt);

    default DiscoveredToolInfoDTO mapDiscoveredTool(DiscoveredTool dt, DangerClassificationService service) {
        var info = mapDiscoveredToolBase(dt);
        var annotations = dt.getAnnotations();
        var auto = service.classify(dt.getName(), dt.getDescription(),
                annotations != null ? annotations.getReadOnlyHint() : null,
                annotations != null ? annotations.getDestructiveHint() : null,
                annotations != null ? annotations.getIdempotentHint() : null,
                annotations != null ? annotations.getOpenWorldHint() : null);
        info.setAutoDangerLevel(DangerLevelDTO.fromValue(auto.name()));
        return info;
    }

    default ExecutionPolicy mapExecutionPolicy(ExecutionPolicyDTO executionPolicyDTO) {
        if (executionPolicyDTO == null) {
            return ExecutionPolicy.DEFAULT;
        }
        return ExecutionPolicy.fromValueOrDefault(executionPolicyDTO.toString()).toCanonical();
    }

    default ExecutionPolicyDTO mapExecutionPolicy(ExecutionPolicy executionPolicy) {
        var canonical = (executionPolicy == null ? ExecutionPolicy.DEFAULT : executionPolicy).toCanonical();
        try {
            return ExecutionPolicyDTO.fromValue(canonical.name());
        } catch (IllegalArgumentException ignored) {
            return ExecutionPolicyDTO.ALWAYS_ASK;
        }
    }
}
