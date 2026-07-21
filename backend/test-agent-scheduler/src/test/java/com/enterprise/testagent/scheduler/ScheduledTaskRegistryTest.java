package com.enterprise.testagent.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 验证 XXL handler 注册表只维护进程内映射，不再同步 PostgreSQL 任务定义。 */
class ScheduledTaskRegistryTest {

    private static final ScheduledTaskKey TASK_KEY = new ScheduledTaskKey("daily.cleanup");

    @Test
    void resolvesRegisteredHandler() {
        ScheduledTaskHandler handler = handler(TASK_KEY);
        ScheduledTaskRegistry registry = new ScheduledTaskRegistry(List.of(handler));

        assertThat(registry.handlerFor(TASK_KEY)).contains(handler);
    }

    @Test
    void rejectsDuplicateTaskKey() {
        assertThatThrownBy(() -> new ScheduledTaskRegistry(List.of(handler(TASK_KEY), handler(TASK_KEY))))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("taskKey 重复");
    }

    private ScheduledTaskHandler handler(ScheduledTaskKey key) {
        return new ScheduledTaskHandler() {
            @Override public ScheduledTaskKey taskKey() { return key; }
            @Override public String name() { return "cleanup"; }
            @Override public String cronExpression() { return "0 0 2 * * *"; }
            @Override public ScheduledTaskResult run(ScheduledTaskContext context) {
                return ScheduledTaskResult.empty();
            }
        };
    }
}
