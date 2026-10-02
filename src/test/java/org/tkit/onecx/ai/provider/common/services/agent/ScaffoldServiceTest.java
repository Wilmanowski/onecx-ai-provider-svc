package org.tkit.onecx.ai.provider.common.services.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.tkit.onecx.ai.provider.domain.daos.ScaffoldDAO;
import org.tkit.onecx.ai.provider.domain.daos.SkillDAO;
import org.tkit.onecx.ai.provider.domain.models.Scaffold;
import org.tkit.onecx.ai.provider.domain.models.Skill;
import org.tkit.onecx.ai.provider.rs.internal.mappers.SkillMapper;
import org.tkit.onecx.ai.provider.test.AbstractTest;

import gen.org.tkit.onecx.ai.provider.rs.internal.model.SkillDTO;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ScaffoldServiceTest extends AbstractTest {

    @Inject
    ScaffoldService service;

    @InjectMock
    ScaffoldDAO scaffoldDAO;

    @InjectMock
    SkillDAO skillDAO;

    @InjectMock
    SkillMapper skillMapper;

    @Test
    void resolveSkills_returnsEmptySet_whenInputIsNullOrEmpty() {
        assertThat(service.resolveSkills(null)).isEmpty();
        assertThat(service.resolveSkills(List.of())).isEmpty();
    }

    @Test
    void resolveSkills_createsNewSkills_whenDtosHaveNoId() {
        var dto = new SkillDTO().name("skill-a").instruction("inst-a");
        var mapped = new Skill();
        mapped.setName("skill-a");
        var created = new Skill();
        created.setId("skill-1");

        when(skillMapper.mapCreate(dto)).thenReturn(mapped);
        when(skillDAO.create(mapped)).thenReturn(created);

        var result = service.resolveSkills(List.of(dto));

        assertThat(result).containsExactly(created);
        verify(skillMapper).mapCreate(dto);
        verify(skillDAO).create(mapped);
    }

    @Test
    void resolveSkills_fetchesExistingSkillsAndSkipsMissingIds() {
        var existingDto = new SkillDTO().id("skill-1");
        var missingDto = new SkillDTO().id("missing-id");
        var existing = new Skill();
        existing.setId("skill-1");

        when(skillDAO.findById("skill-1")).thenReturn(existing);
        when(skillDAO.findById("missing-id")).thenReturn(null);

        var result = service.resolveSkills(List.of(existingDto, missingDto));

        assertThat(result).containsExactly(existing);
        verify(skillDAO).findById("skill-1");
        verify(skillDAO).findById("missing-id");
        verify(skillMapper, never()).mapCreate(existingDto);
    }

    @Test
    void updateScaffold_resolvesSkillsAndDelegatesToDao() {
        var scaffold = new Scaffold();
        scaffold.setId("scaffold-1");
        var dto = new SkillDTO().id("skill-1");
        var skill = new Skill();
        skill.setId("skill-1");

        when(skillDAO.findById("skill-1")).thenReturn(skill);
        when(scaffoldDAO.update(scaffold)).thenReturn(scaffold);

        var result = service.updateScaffold(scaffold, List.of(dto));

        assertThat(result).isSameAs(scaffold);
        assertThat(scaffold.getSkills()).containsExactly(skill);
        verify(scaffoldDAO).update(scaffold);
    }

    @Test
    void createScaffold_resolvesSkillsAndDelegatesToDao() {
        var scaffold = new Scaffold();
        var dto = new SkillDTO().name("skill-a").instruction("inst-a");
        var mapped = new Skill();
        mapped.setName("skill-a");
        var createdSkill = new Skill();
        createdSkill.setId("skill-1");

        when(skillMapper.mapCreate(dto)).thenReturn(mapped);
        when(skillDAO.create(mapped)).thenReturn(createdSkill);
        when(scaffoldDAO.create(scaffold)).thenReturn(scaffold);

        var result = service.createScaffold(scaffold, List.of(dto));

        assertThat(result).isSameAs(scaffold);
        assertThat(scaffold.getSkills()).containsExactly(createdSkill);
        verify(scaffoldDAO).create(scaffold);
    }
}
