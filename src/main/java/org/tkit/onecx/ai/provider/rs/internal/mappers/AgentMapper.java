package org.tkit.onecx.ai.provider.rs.internal.mappers;

import java.util.HashSet;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.tkit.onecx.ai.provider.domain.criteria.AgentSearchCriteria;
import org.tkit.onecx.ai.provider.domain.models.Agent;
import org.tkit.onecx.ai.provider.domain.models.AgentGroup;
import org.tkit.onecx.ai.provider.domain.models.Filter;
import org.tkit.onecx.ai.provider.domain.models.Model;
import org.tkit.onecx.ai.provider.domain.models.Scaffold;
import org.tkit.onecx.ai.provider.domain.models.Tool;
import org.tkit.quarkus.jpa.daos.PageResult;
import org.tkit.quarkus.rs.mappers.OffsetDateTimeMapper;

import gen.org.tkit.onecx.ai.provider.rs.internal.model.AgentAbstractDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.AgentDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.AgentFilterDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.AgentPageResultDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.AgentSearchCriteriaDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.CreateAgentRequestDTO;
import gen.org.tkit.onecx.ai.provider.rs.internal.model.UpdateAgentRequestDTO;

@Mapper(uses = { OffsetDateTimeMapper.class, ProviderMapper.class, ToolMapper.class,
        ModelMapper.class, ScaffoldMapper.class, AgentGroupMapper.class })
public interface AgentMapper {

    AgentSearchCriteria mapCriteria(AgentSearchCriteriaDTO agentSearchCriteriaDTO);

    @Mapping(target = "removeStreamItem", ignore = true)
    AgentPageResultDTO mapPage(PageResult<Agent> result);

    AgentAbstractDTO mapToAbstract(Agent agent);

    @Mapping(target = "removeToolsItem", ignore = true)
    @Mapping(target = "removeGroupsItem", ignore = true)
    AgentDTO map(Agent item);

    AgentFilterDTO map(Filter filter);

    Filter map(AgentFilterDTO filterDTO);

    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "persisted", ignore = true)
    @Mapping(target = "modificationUser", ignore = true)
    @Mapping(target = "modificationDate", ignore = true)
    @Mapping(target = "modificationCount", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creationUser", ignore = true)
    @Mapping(target = "creationDate", ignore = true)
    @Mapping(target = "controlTraceabilityManual", ignore = true)
    @Mapping(target = "globalScaffold", ignore = true)
    @Mapping(target = "globalTools", ignore = true)
    Agent mapCreate(CreateAgentRequestDTO createAgentRequestDTO);

    @Mapping(target = "modificationCount", source = "updateDTO.modificationCount")
    @Mapping(target = "name", source = "updateDTO.name")
    @Mapping(target = "description", source = "updateDTO.description")
    @Mapping(target = "additionalPrompt", source = "updateDTO.additionalPrompt")
    @Mapping(target = "a2aEnabled", source = "updateDTO.a2aEnabled")
    @Mapping(target = "voiceEnabled", source = "updateDTO.voiceEnabled")
    @Mapping(target = "languageCode", source = "updateDTO.languageCode")
    @Mapping(target = "filter", source = "updateDTO.filter")
    @Mapping(target = "model", source = "model")
    @Mapping(target = "scaffold", source = "scaffold")
    @Mapping(target = "groups", source = "groups")
    @Mapping(target = "tools", source = "tools")
    @Mapping(target = "globalScaffold", ignore = true)
    @Mapping(target = "globalTools", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "persisted", ignore = true)
    @Mapping(target = "modificationUser", ignore = true)
    @Mapping(target = "modificationDate", ignore = true)
    @Mapping(target = "creationDate", ignore = true)
    @Mapping(target = "creationUser", ignore = true)
    @Mapping(target = "controlTraceabilityManual", ignore = true)
    void mapUpdate(@MappingTarget Agent agent, UpdateAgentRequestDTO updateDTO, HashSet<Tool> tools,
            Model model, Scaffold scaffold, HashSet<AgentGroup> groups);
}
