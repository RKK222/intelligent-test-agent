package com.enterprise.testagent.xxljob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class XxlJobPropertiesTest {

    @Test
    void defaultsKeepIntegrationOptInAndUsePlatformExecutorIdentity() {
        XxlJobProperties properties = new XxlJobProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getAdmin().getPort()).isEqualTo(18080);
        assertThat(properties.getAdmin().getContextPath()).isEqualTo("/xxl-job-admin");
        assertThat(properties.getAdmin().getRetryInitialDelay()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getAdmin().getRetryMaxDelay()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getExecutor().getAppName()).isEqualTo("test-agent-backend");
        assertThat(properties.getExecutor().getLogRetentionDays()).isEqualTo(30);
        assertThat(properties.getSso().getTicketTtl()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void rejectsPortCollisionsAndNonMysqlJdbcUrl() {
        XxlJobProperties properties = new XxlJobProperties();

        assertThatThrownBy(() -> properties.getAdmin().setPort(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.getExecutor().setPort(70000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.getMysql().setUrl("jdbc:postgresql://localhost/xxl_job"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executorPropertiesDoNotExposeManuallyConfiguredAddresses() {
        assertThat(Arrays.stream(XxlJobProperties.Executor.class.getDeclaredFields())
                        .map(java.lang.reflect.Field::getName))
                .doesNotContain("adminAddresses", "address", "ip");
    }
}
