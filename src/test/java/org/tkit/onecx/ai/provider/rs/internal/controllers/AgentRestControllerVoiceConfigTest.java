package org.tkit.onecx.ai.provider.rs.internal.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tkit.onecx.ai.provider.config.AiProviderConfig;
import org.tkit.onecx.ai.provider.domain.models.Agent;
import org.tkit.onecx.ai.provider.rs.internal.mappers.ExceptionMapper;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AgentRestControllerVoiceConfigTest {

    private AgentRestController controller;
    private AiProviderConfig originalConfig;
    private AiProviderConfig.VoicePilotConfig voicePilotConfig;

    @Inject
    ExceptionMapper exceptionMapper;

    private Method supportedLanguageCodes;
    private Method normalizeLanguageCode;

    @BeforeEach
    @SuppressWarnings("java:S3011")
    void setUp() throws Exception {
        controller = Arc.container().instance(AgentRestController.class).get();

        Field configField = AgentRestController.class.getDeclaredField("config");
        configField.setAccessible(true);
        originalConfig = (AiProviderConfig) configField.get(controller);

        voicePilotConfig = mock(AiProviderConfig.VoicePilotConfig.class);
        AiProviderConfig mockConfig = mock(AiProviderConfig.class);
        when(mockConfig.voicePilot()).thenReturn(voicePilotConfig);
        configField.set(controller, mockConfig);

        Field exceptionMapperField = AgentRestController.class.getDeclaredField("exceptionMapper");
        exceptionMapperField.setAccessible(true);
        exceptionMapperField.set(controller, exceptionMapper);

        supportedLanguageCodes = AgentRestController.class.getDeclaredMethod("supportedLanguageCodes");
        supportedLanguageCodes.setAccessible(true);
        normalizeLanguageCode = AgentRestController.class.getDeclaredMethod("normalizeLanguageCode", Agent.class);
        normalizeLanguageCode.setAccessible(true);
    }

    @AfterEach
    @SuppressWarnings("java:S3011")
    void restoreConfig() throws Exception {
        Field configField = AgentRestController.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(controller, originalConfig);
    }

    @Test
    @SuppressWarnings("unchecked")
    void supportedLanguageCodes_returnsEmptySetForNullConfig() throws Exception {
        when(voicePilotConfig.supportedLanguageCodes()).thenReturn(null);

        assertThat((Set<String>) supportedLanguageCodes.invoke(controller)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void supportedLanguageCodes_returnsEmptySetForEmptyConfig() throws Exception {
        when(voicePilotConfig.supportedLanguageCodes()).thenReturn(Set.of());

        assertThat((Set<String>) supportedLanguageCodes.invoke(controller)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void supportedLanguageCodes_filtersBlankAndNormalizesValues() throws Exception {
        when(voicePilotConfig.supportedLanguageCodes()).thenReturn(Set.of("  DE ", "", "en"));

        assertThat((Set<String>) supportedLanguageCodes.invoke(controller))
                .containsExactlyInAnyOrder("de", "en");
    }

    @Test
    @SuppressWarnings("java:S3011")
    void normalizeLanguageCode_withNullAgent_returnsSilently() throws Exception {
        var result = normalizeLanguageCode.invoke(controller, (Object) null);

        assertThat(result).isNull();
    }

    @Test
    @SuppressWarnings("java:S3011")
    void validateVoicePilot_blankLanguageCode_returnsBadRequest() throws Exception {
        when(voicePilotConfig.supportedLanguageCodes()).thenReturn(Set.of("en"));

        Method validateVoicePilot = AgentRestController.class.getDeclaredMethod(
                "validateVoicePilot", Boolean.class, String.class);
        validateVoicePilot.setAccessible(true);

        var response = (Response) validateVoicePilot.invoke(controller, true, "  ");

        assertThat(response).isNotNull();
        assertThat(response.getStatus())
                .isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    }
}
