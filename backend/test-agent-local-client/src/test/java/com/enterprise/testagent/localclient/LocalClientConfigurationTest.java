package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LocalClientConfigurationTest {

    private static final Path PLACEHOLDER = Path.of("target", "local-client-configuration-test");

    @Test
    void onlyAllowsHttpsOrExplicitHttpWithoutUrlCredentials() {
        assertThatThrownBy(() -> configuration(URI.create("http://127.0.0.1:8080"), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuration(URI.create("ftp://127.0.0.1:8080"), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuration(URI.create("https://user:secret@127.0.0.1:8080"), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appliesTheSameTransportPolicyToWebUrl() {
        assertThatThrownBy(() -> new LocalClientConfiguration(
                URI.create("https://platform.example"),
                URI.create("http://127.0.0.1:3000"),
                "configuration-test",
                PLACEHOLDER.resolve("opencode"),
                PLACEHOLDER.resolve("config"),
                PLACEHOLDER.resolve("data"),
                4096,
                4195,
                false,
                null,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRequireCompleteTrustedSelfUpdateConfiguration() {
        LocalClientConfiguration configured = new LocalClientConfiguration(
                URI.create("https://platform.example.internal"),
                URI.create("https://platform.example.internal"),
                "configuration-test",
                PLACEHOLDER.resolve("opencode"),
                PLACEHOLDER.resolve("config"),
                PLACEHOLDER.resolve("data"),
                4096,
                4195,
                false,
                URI.create("http://downloads.example/local-opencode-client/"),
                "cHVibGljLWtleQ==",
                PLACEHOLDER.resolve("runtime"));

        assertThat(configured.selfUpdateConfigured()).isTrue();
        assertThatThrownBy(() -> new LocalClientConfiguration(
                configured.serverBaseUri(),
                configured.webBaseUri(),
                configured.clientName(),
                configured.opencodeExecutable(),
                configured.opencodeConfigDirectory(),
                configured.opencodeDataDirectory(),
                configured.portMin(),
                configured.portMax(),
                configured.allowInsecureControl(),
                configured.downloadBaseUri(),
                null,
                configured.installRoot()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("self-update");
    }

    private static LocalClientConfiguration configuration(URI serverUri, boolean allowInsecure) {
        return new LocalClientConfiguration(
                serverUri,
                serverUri,
                "configuration-test",
                PLACEHOLDER.resolve("opencode"),
                PLACEHOLDER.resolve("config"),
                PLACEHOLDER.resolve("data"),
                4096,
                4195,
                allowInsecure);
    }
}
