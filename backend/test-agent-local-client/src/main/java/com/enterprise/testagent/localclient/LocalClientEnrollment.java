package com.enterprise.testagent.localclient;

import java.io.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/** 首次接入只从交互终端读取统一认证号和隐藏 Client key，并在服务端校验后落盘。 */
final class LocalClientEnrollment {

    private LocalClientEnrollment() {
    }

    static void enroll(Path configDirectory, Terminal terminal, CredentialVerifier verifier) throws Exception {
        Objects.requireNonNull(configDirectory, "configDirectory must not be null");
        Objects.requireNonNull(terminal, "terminal must not be null");
        Objects.requireNonNull(verifier, "verifier must not be null");
        String unifiedAuthId = terminal.readLine("统一认证号: ");
        char[] clientKey = terminal.readSecret("Client key: ");
        if (clientKey == null) {
            throw new IllegalArgumentException("client key is required");
        }
        try {
            LocalClientCredentialFile.Credentials credentials = LocalClientCredentialFile.credentials(
                    unifiedAuthId, new String(clientKey));
            verifier.verify(credentials);
            LocalClientCredentialFile.write(
                    configDirectory, credentials.unifiedAuthId(), credentials.clientKey());
        } finally {
            Arrays.fill(clientKey, '\0');
        }
    }

    static Terminal systemTerminal() {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("首次接入必须在交互终端中执行");
        }
        return new Terminal() {
            @Override
            public String readLine(String prompt) {
                return console.readLine("%s", prompt);
            }

            @Override
            public char[] readSecret(String prompt) {
                return console.readPassword("%s", prompt);
            }
        };
    }

    interface Terminal {
        String readLine(String prompt) throws IOException;

        char[] readSecret(String prompt) throws IOException;
    }

    @FunctionalInterface
    interface CredentialVerifier {
        void verify(LocalClientCredentialFile.Credentials credentials) throws Exception;
    }

    /** 对外只暴露统一认证失败，避免泄露是统一认证号还是 Key 不匹配。 */
    static final class AuthenticationException extends Exception {
        AuthenticationException() {
            super("本地客户端认证失败");
        }

        AuthenticationException(Throwable cause) {
            super("本地客户端认证失败", cause);
        }
    }
}
