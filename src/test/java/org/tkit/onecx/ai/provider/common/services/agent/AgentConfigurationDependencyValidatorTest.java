package org.tkit.onecx.ai.provider.common.services.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tkit.onecx.ai.provider.domain.daos.GlobalToolDAO;
import org.tkit.onecx.ai.provider.domain.daos.ModelDAO;
import org.tkit.onecx.ai.provider.domain.daos.ProviderDAO;
import org.tkit.onecx.ai.provider.domain.daos.ToolDAO;
import org.tkit.onecx.ai.provider.domain.models.GlobalTool;
import org.tkit.onecx.ai.provider.domain.models.Model;
import org.tkit.onecx.ai.provider.domain.models.Provider;
import org.tkit.onecx.ai.provider.domain.models.Tool;
import org.tkit.onecx.ai.provider.domain.models.enums.AuthMode;
import org.tkit.onecx.ai.provider.domain.models.enums.ProviderType;
import org.tkit.onecx.ai.provider.domain.models.enums.ToolType;

import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.AgentConfigurationDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.McpServerConfigurationDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ModelConfigurationDTOV1;
import gen.org.tkit.onecx.ai.provider.rs.external.v1.model.ProviderConfigurationDTOV1;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AgentConfigurationDependencyValidatorTest {

    private AgentConfigurationDependencyValidator validator;
    private AgentConfigurationDTOV1 configuration;
    private Provider provider;
    private Tool tool;

    @BeforeEach
    void setUp() {
        validator = new AgentConfigurationDependencyValidator();
        validator.providerDAO = mock(ProviderDAO.class);
        validator.modelDAO = mock(ModelDAO.class);
        validator.toolDAO = mock(ToolDAO.class);
        validator.globalToolDAO = mock(GlobalToolDAO.class);
        configuration = new AgentConfigurationDTOV1();
        configuration.setModel(new ModelConfigurationDTOV1().id("model"));
        configuration.setProvider(new ProviderConfigurationDTOV1().id("provider").authMode("API_KEY")
                .credentialRef("credential://provider/provider/api-key"));
        configuration.setMcpServers(List.of(new McpServerConfigurationDTOV1().id("tool")
                .credentialRef("credential://tool/tool/api-key")));
        provider = new Provider();
        provider.setType(ProviderType.OPENAI);
        provider.setAuthMode(AuthMode.API_KEY);
        provider.setApiKey("provider-secret");
        tool = new Tool();
        tool.setType(ToolType.MCP);
        tool.setAuthMode(AuthMode.API_KEY);
        tool.setApiKey("tool-secret");
        when(validator.modelDAO.findById("model")).thenReturn(new Model());
        when(validator.providerDAO.findById("provider")).thenReturn(provider);
        when(validator.toolDAO.findById("tool")).thenReturn(tool);
    }

    @Test
    void existingDependenciesAreValid() {
        assertThat(validator.validate(configuration)).isEmpty();
    }

    @Test
    void deletedDependenciesAreReportedTogether() {
        when(validator.modelDAO.findById("model")).thenReturn(null);
        when(validator.providerDAO.findById("provider")).thenReturn(null);
        when(validator.toolDAO.findById("tool")).thenReturn(null);

        assertThat(validator.validate(configuration)).containsExactly(
                "Referenced model 'model' does not exist",
                "Referenced provider 'provider' does not exist",
                "Referenced tool 'tool' does not exist");
    }

    @Test
    void globalMcpToolsAreResolved() {
        when(validator.toolDAO.findById("tool")).thenReturn(null);
        var global = new GlobalTool();
        global.setType(ToolType.MCP);
        global.setAuthMode(AuthMode.API_KEY);
        global.setApiKey("global-secret");
        when(validator.globalToolDAO.findById("tool")).thenReturn(global);

        assertThat(validator.validate(configuration)).isEmpty();
        global.setApiKey(null);
        assertThat(validator.validate(configuration))
                .containsExactly("Required credential for tool 'tool' is missing");
    }

    @Test
    void missingAndBlankCredentialsAreRejectedWithoutExposingSecrets() {
        provider.setApiKey(null);
        tool.setApiKey("  ");

        assertThat(validator.validate(configuration)).containsExactly(
                "Required credential for provider 'provider' is missing",
                "Required credential for tool 'tool' is missing");
    }

    @Test
    void credentialReferencesMustMatchTheirDependencies() {
        configuration.getProvider().setCredentialRef("credential://provider/other/api-key");
        configuration.getMcpServers().get(0).setCredentialRef(null);

        assertThat(validator.validate(configuration)).containsExactly(
                "Invalid credential reference for provider 'provider'",
                "Invalid credential reference for tool 'tool'");
    }

    @Test
    void changedDependencyTypesAreRejected() {
        provider.setType(null);
        tool.setType(ToolType.HTTP);

        assertThat(validator.validate(configuration)).containsExactly(
                "Referenced provider 'provider' has no type",
                "Referenced tool 'tool' is no longer an MCP tool");
    }

    @Test
    void snapshotApiKeyRequirementIsPreservedWhenProviderAuthModeChanges() {
        provider.setAuthMode(AuthMode.OAUTH);
        provider.setApiKey(null);

        assertThat(validator.validate(configuration))
                .containsExactly("Required credential for provider 'provider' is missing");
    }

    @Test
    void unauthenticatedDependenciesDoNotRequireApiKeys() {
        configuration.getProvider().setAuthMode(null);
        provider.setAuthMode(null);
        provider.setApiKey(null);
        tool.setAuthMode(null);
        tool.setApiKey(null);

        assertThat(validator.validate(configuration)).isEmpty();
    }

    @Test
    void missingIdentifiersAndProviderAreRejected() {
        configuration.getModel().setId(null);
        configuration.setProvider(null);
        configuration.getMcpServers().get(0).setId(" ");

        assertThat(validator.validate(configuration)).containsExactly(
                "Referenced model 'null' does not exist",
                "Referenced model has no provider configuration",
                "Referenced MCP tool has no identifier");
    }

    @Test
    void absentConfigurationIsRejectedButUnreferencedDependenciesAreOptional() {
        assertThat(validator.validate(null)).containsExactly("Saved configuration is missing");
        assertThat(validator.validate(new AgentConfigurationDTOV1())).isEmpty();
        var empty = new AgentConfigurationDTOV1();
        empty.setMcpServers(null);
        assertThat(validator.validate(empty)).isEmpty();
    }

    @Test
    void blankModelAndProviderIdentifiersAreRejected() {
        configuration.getModel().setId(" ");
        configuration.getProvider().setId(null);

        assertThat(validator.validate(configuration)).containsExactly(
                "Referenced model ' ' does not exist",
                "Referenced provider 'null' does not exist");
    }

    @Test
    void nullServersAndMissingServerIdentifiersAreReported() {
        configuration.setMcpServers(Arrays.asList(null,
                new McpServerConfigurationDTOV1(), new McpServerConfigurationDTOV1().id(" ")));

        assertThat(validator.validate(configuration)).containsExactly(
                "Referenced MCP tool has no identifier",
                "Referenced MCP tool has no identifier",
                "Referenced MCP tool has no identifier");
    }

    @Test
    void optionalCredentialsMayBeAbsentButMustBeValidWhenProvided() {
        configuration.getProvider().setAuthMode(null);
        configuration.getProvider().setCredentialRef(null);
        configuration.getMcpServers().get(0).setCredentialRef(null);
        provider.setAuthMode(AuthMode.OAUTH);
        tool.setAuthMode(AuthMode.OAUTH);
        provider.setApiKey(null);
        tool.setApiKey(null);

        assertThat(validator.validate(configuration)).isEmpty();

        configuration.getProvider().setCredentialRef("invalid");
        configuration.getMcpServers().get(0).setCredentialRef("invalid");

        assertThat(validator.validate(configuration)).containsExactly(
                "Invalid credential reference for provider 'provider'",
                "Invalid credential reference for tool 'tool'");
    }
}
