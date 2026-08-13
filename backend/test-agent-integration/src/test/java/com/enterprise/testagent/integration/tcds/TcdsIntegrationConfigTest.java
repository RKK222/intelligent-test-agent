package com.enterprise.testagent.integration.tcds;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.tcds.TcdsGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** TCDS 部署变量装配测试，不依赖数据库或 Redis。 */
class TcdsIntegrationConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TcdsIntegrationConfig.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void missingAddressFailsContextStartupWithSanitizedMessage() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("TEST_AGENT_TCDS_BASE_URL 未配置");
            assertThat(stackTrace(context.getStartupFailure())).doesNotContain("token=");
        });
    }

    @Test
    void illegalAddressFailsContextStartupWithoutEchoingAddress() {
        contextRunner.withPropertyValues("test-agent.third-party-api.base-url=file:///sensitive/path")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("TEST_AGENT_TCDS_BASE_URL 配置无效");
                    assertThat(stackTrace(context.getStartupFailure())).doesNotContain("sensitive/path");
                });
    }

    @Test
    void validHttpAddressCreatesOneSharedGateway() {
        contextRunner.withPropertyValues("test-agent.third-party-api.base-url=https://tcds.internal/gateway/")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TcdsGateway.class);
                    assertThat(context).hasSingleBean(TcdsHttpGateway.class);
                });
    }

    private static String stackTrace(Throwable failure) {
        java.io.StringWriter output = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(output));
        return output.toString();
    }
}
