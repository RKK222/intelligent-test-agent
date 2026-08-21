package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientEnrollmentTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldVerifyPairedCredentialsBeforePersistingAndEraseInputBuffer() throws Exception {
        FakeTerminal terminal = new FakeTerminal(" UC-001 ", "tack_v1_secret".toCharArray());
        List<String> verified = new ArrayList<>();

        LocalClientEnrollment.enroll(
                temporaryDirectory.resolve("config"),
                terminal,
                credentials -> verified.add(credentials.unifiedAuthId() + ":" + credentials.clientKey()));

        assertThat(verified).containsExactly("UC-001:tack_v1_secret");
        assertThat(LocalClientCredentialFile.read(temporaryDirectory.resolve("config")).unifiedAuthId())
                .isEqualTo("UC-001");
        assertThat(terminal.secret).containsOnly('\0');
        assertThat(terminal.prompts).noneMatch(prompt -> prompt.contains("tack_v1_secret"));
    }

    @Test
    void shouldNotPersistCredentialsWhenServerVerificationFails() {
        FakeTerminal terminal = new FakeTerminal("UC-001", "tack_v1_wrong".toCharArray());
        Path configDirectory = temporaryDirectory.resolve("rejected-config");

        assertThatThrownBy(() -> LocalClientEnrollment.enroll(
                configDirectory,
                terminal,
                credentials -> {
                    throw new LocalClientEnrollment.AuthenticationException();
                }))
                .isInstanceOf(LocalClientEnrollment.AuthenticationException.class)
                .hasMessage("本地客户端认证失败");

        assertThat(Files.exists(configDirectory.resolve("credentials.properties"))).isFalse();
        assertThat(terminal.secret).containsOnly('\0');
    }

    private static final class FakeTerminal implements LocalClientEnrollment.Terminal {
        private final String unifiedAuthId;
        private final char[] secret;
        private final List<String> prompts = new ArrayList<>();

        private FakeTerminal(String unifiedAuthId, char[] secret) {
            this.unifiedAuthId = unifiedAuthId;
            this.secret = secret;
        }

        @Override
        public String readLine(String prompt) {
            prompts.add(prompt);
            return unifiedAuthId;
        }

        @Override
        public char[] readSecret(String prompt) {
            prompts.add(prompt);
            return secret;
        }
    }
}
