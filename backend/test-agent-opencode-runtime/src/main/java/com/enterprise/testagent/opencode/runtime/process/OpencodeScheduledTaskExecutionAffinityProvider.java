package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.scheduler.ScheduledTaskExecutionAffinityProvider;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 让 scheduler 仅认领当前 Linux 服务器归属的 USER_PLAN 运行。 */
@Component
public class OpencodeScheduledTaskExecutionAffinityProvider implements ScheduledTaskExecutionAffinityProvider {

    private final BackendJavaRouteResolver routeResolver;

    public OpencodeScheduledTaskExecutionAffinityProvider(BackendJavaRouteResolver routeResolver) {
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
    }

    @Override
    public String currentAffinity() {
        return routeResolver.currentLinuxServerIdValue();
    }
}
