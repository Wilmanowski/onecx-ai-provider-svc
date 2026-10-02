package org.tkit.onecx.ai.provider.rs.external.v1.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.tkit.onecx.ai.provider.domain.criteria.AgentSearchCriteria;
import org.tkit.onecx.ai.provider.domain.models.Agent;
import org.tkit.onecx.ai.provider.domain.models.Filter;
import org.tkit.quarkus.jpa.daos.PageResult;
import org.tkit.quarkus.rs.mappers.OffsetDateTimeMapper;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentAbstractDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentFilterDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentPageResultDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentSearchCriteriaDTOV1;

@Mapper(uses = { OffsetDateTimeMapper.class, })
public interface AgentMapper {

    @Mapping(target = "name", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "status", constant = "LIVE")
    AgentSearchCriteria mapCriteria(AgentSearchCriteriaDTOV1 agentSearchCriteriaDTOV1);

    @Mapping(target = "removeStreamItem", ignore = true)
    AgentPageResultDTOV1 mapPage(PageResult<Agent> result);

    @Mapping(target = "voiceEnabled", defaultExpression = "java(Boolean.TRUE.equals(agent.getVoiceEnabled()))")
    AgentAbstractDTOV1 mapToAbstract(Agent agent);

    Filter map(AgentFilterDTOV1 filterDTOV1);
}
