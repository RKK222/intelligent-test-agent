package com.enterprise.testagent.xxljob;

import java.util.Objects;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** XXL Admin 独立健康项；Spring readiness group 不包含该自定义项。 */
@Component("xxlJobAdminHealthIndicator")
public class XxlJobAdminHealthIndicator implements HealthIndicator {

    private final XxlJobProperties properties;
    private final XxlJobAdminState state;

    public XxlJobAdminHealthIndicator(XxlJobProperties properties, XxlJobAdminState state) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
    }

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.unknown().withDetail("enabled", false).build();
        }
        if (state.isUp()) {
            return Health.up().withDetail("port", properties.getAdmin().getPort()).build();
        }
        Health.Builder down = Health.down();
        String reason = state.failureReason();
        if (reason != null) {
            down.withDetail("reason", reason);
        }
        // 子上下文首次启动尝试前尚无检查时间，不能把 null 交给 Spring Boot 4 的 Health.Builder。
        if (state.checkedAt() != null) {
            down.withDetail("checkedAt", state.checkedAt());
        }
        return down.build();
    }
}
