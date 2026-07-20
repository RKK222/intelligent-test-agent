package com.enterprise.testagent.xxljob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class XxlJobAdminHealthIndicatorTest {

    @Test
    void reportsDedicatedDownAndUpStatesWithoutExposingCredentials() {
        XxlJobProperties properties = new XxlJobProperties();
        properties.setEnabled(true);
        XxlJobAdminState state = new XxlJobAdminState();
        XxlJobAdminHealthIndicator indicator = new XxlJobAdminHealthIndicator(properties, state);

        assertThatCode(indicator::health).doesNotThrowAnyException();
        Health initialHealth = indicator.health();
        assertThat(initialHealth.getStatus()).isEqualTo(Status.DOWN);
        assertThat(initialHealth.getDetails())
                .containsEntry("reason", "XXL-JOB Admin 尚未启动")
                .doesNotContainKey("checkedAt");
        state.down("XXL-JOB Admin 启动失败");
        assertThat(indicator.health().getDetails())
                .containsEntry("reason", "XXL-JOB Admin 启动失败")
                .doesNotContainValue("mysql-secret");

        state.up();
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails()).containsEntry("port", 18080);
    }
}
