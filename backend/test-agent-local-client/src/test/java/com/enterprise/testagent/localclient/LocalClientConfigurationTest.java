package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private static LocalClientConfiguration configuration(URI serverUri, boolean allowInsecure) {
        return new LocalClientConfiguration(
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
