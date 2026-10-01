package org.tkit.onecx.ai.provider.config;

import java.util.Set;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "onecx.ai.provider")
public interface AiProviderConfig {

    /**
     * The URL of the AI Runtime service.
     */
    @WithName("runtime")
    RuntimeClientConfig runtimeClient();

    /**
     * Voice pilot related configuration.
     */
    @WithName("voice-pilot")
    VoicePilotConfig voicePilot();

    /**
     * Agent Configuration version related configuration.
     */
    @WithName("version")
    VersionConfig version();

    interface VersionConfig {

        /**
         * When a tool server has no tool rules, all of its tools are allowed (legacy behaviour). Must match
         * {@code onecx.ai.dispatch.tool.legacy-allow-all} of onecx-ai-provider-runtime so that version consumers and
         * the text runtime enforce the same policy.
         */
        @WithName("legacy-allow-all")
        @WithDefault("true")
        boolean legacyAllowAll();
    }

    interface RuntimeClientConfig {

        /**
         * The URL of the AI provider runtime service.
         */
        @WithName("url")
        @WithDefault("http://onecx-ai-provider-runtime:8080")
        String url();
    }

    interface VoicePilotConfig {

        /**
         * Supported language codes accepted for voice pilot.
         */
        @WithName("supported-language-codes")
        @WithDefault("en")
        Set<String> supportedLanguageCodes();
    }
}
