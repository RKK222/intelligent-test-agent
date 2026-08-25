package com.enterprise.testagent.integration.aam;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.auth.AamLoginTokenVerifier;
import com.enterprise.testagent.integration.tcds.TcdsIntegrationConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** AAM 配置绑定和多外部 HTTP client 装配测试。 */
class AamIntegrationConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AamIntegrationConfig.class, TcdsIntegrationConfig.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues(
                    "test-agent.third-party-api.base-url=https://tcds.internal",
                    "test-agent.aam.base-url=https://aam.internal",
                    "test-agent.aam.connect-timeout=2s",
                    "test-agent.aam.request-timeout=4s",
                    "test-agent.aam.max-response-bytes=32768");

    @Test
    void bindsBoundariesAndKeepsAamAndTcdsClientsUnambiguous() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AamLoginTokenVerifier.class);
            assertThat(context).hasBean("aamHttpClient");
            assertThat(context).hasBean("tcdsHttpClient");
            assertThat(context).getBeans(HttpClient.class).hasSize(2);

            AamProperties properties = context.getBean(AamProperties.class);
            assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(4));
            assertThat(properties.getMaxResponseBytes()).isEqualTo(32768);
        });
    }
}
