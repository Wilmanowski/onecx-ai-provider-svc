package org.tkit.onecx.ai.provider.rs.external.v1.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * RFC 9110 {@code If-None-Match} evaluation of {@link AgentRestV1Controller#matches(String, String)}.
 */
class AgentRestV1ControllerIfNoneMatchTest {

    private static final String VERSION = "sha256:0123456789abcdef";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t" })
    void matches_absentOrBlankHeader_neverMatches(String ifNoneMatch) {
        assertThat(AgentRestV1Controller.matches(ifNoneMatch, VERSION)).isFalse();
    }

    @Test
    void matches_absentVersion_neverMatches() {
        assertThat(AgentRestV1Controller.matches("*", null)).isFalse();
        assertThat(AgentRestV1Controller.matches("\"" + VERSION + "\"", null)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("matchingHeaders")
    void matches_matchingHeader_matchesVersion(String ifNoneMatch) {
        assertThat(AgentRestV1Controller.matches(ifNoneMatch, VERSION)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("nonMatchingHeaders")
    void matches_nonMatchingHeader_doesNotMatchVersion(String ifNoneMatch) {
        assertThat(AgentRestV1Controller.matches(ifNoneMatch, VERSION)).isFalse();
    }

    private static Stream<Arguments> matchingHeaders() {
        return Stream.of(
                // strong entity tag, quotes stripped
                Arguments.of("\"" + VERSION + "\""),
                // weak entity tag, weak prefix stripped
                Arguments.of("W/\"" + VERSION + "\""),
                // bare version without quotes
                Arguments.of(VERSION),
                // wildcard: any existing representation
                Arguments.of("*"),
                // whitespace only
                Arguments.of("   *   "),
                // comma separated list, the matching entry is not the first one
                Arguments.of("\"sha256:other\", W/\"" + VERSION + "\", \"sha256:third\""),
                // weak wildcard
                Arguments.of("W/*"));
    }

    private static Stream<Arguments> nonMatchingHeaders() {
        return Stream.of(
                // different strong entity tag
                Arguments.of("\"sha256:other\""),
                // different weak entity tag
                Arguments.of("W/\"sha256:other\""),
                // bare different version
                Arguments.of("sha256:other"),
                // matching prefix only, the version is longer than the tag
                Arguments.of("\"sha256:0123456789\""),
                // only opening quote, quotes are not stripped
                Arguments.of("\"" + VERSION),
                // too short to be an entity tag, quotes are not stripped
                Arguments.of("\""),
                // weak prefix leaves an empty tag behind
                Arguments.of("W/"),
                // neither wildcard nor version
                Arguments.of("garbage"));
    }
}
