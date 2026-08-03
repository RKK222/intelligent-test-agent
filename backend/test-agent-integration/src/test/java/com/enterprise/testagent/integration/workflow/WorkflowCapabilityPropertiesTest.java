package com.enterprise.testagent.integration.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowCapabilityPropertiesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldLoadRunnerPublicKeyFromAbsoluteReadOnlyProvisionedFile() throws Exception {
        Path publicKey = temporaryDirectory.resolve("runner-public.pem");
        Files.writeString(
                publicKey,
                "-----BEGIN PUBLIC KEY-----\nYWJj\n-----END PUBLIC KEY-----\n");
        WorkflowCapabilityProperties properties = new WorkflowCapabilityProperties();
        properties.setRunnerPublicKeyPath(publicKey.toString());

        assertThat(properties.configuredRunnerPublicKey())
                .startsWith("-----BEGIN PUBLIC KEY-----")
                .endsWith(System.lineSeparator());
    }

    @Test
    void shouldRejectAmbiguousRunnerPublicKeySources() {
        WorkflowCapabilityProperties properties = new WorkflowCapabilityProperties();

        assertThatThrownBy(properties::configuredRunnerPublicKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("必须且只能");
    }

    @Test
    void shouldRequireAnAbsoluteHttpModelGatewayUrlWithoutCredentialsOrQuery() {
        WorkflowCapabilityProperties properties = new WorkflowCapabilityProperties();

        assertThatThrownBy(properties::getModelGatewayUrl)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("绝对HTTP地址");

        properties.setModelGatewayUrl("http://10.20.30.40:8080/api/internal/platform/model-gateway/v1");
        assertThat(properties.getModelGatewayUrl())
                .isEqualTo("http://10.20.30.40:8080/api/internal/platform/model-gateway/v1");

        properties.setModelGatewayUrl("https://user:secret@example.test/v1?token=secret");
        assertThatThrownBy(properties::getModelGatewayUrl)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不得包含凭据、查询或片段");
    }
}
