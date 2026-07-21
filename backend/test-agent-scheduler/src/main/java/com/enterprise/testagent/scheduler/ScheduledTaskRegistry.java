package com.enterprise.testagent.scheduler;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * XXL adapter 使用的进程内 handler 注册表；任务定义由 XXL MySQL Flyway 管理。
 */
@Component
public class ScheduledTaskRegistry {

    private final Map<ScheduledTaskKey, ScheduledTaskHandler> handlers;

    /**
     * 注入所有 handler Bean，并在构造阶段检查 taskKey 是否重复。
     */
    public ScheduledTaskRegistry(
            List<ScheduledTaskHandler> handlers) {
        this.handlers = indexHandlers(handlers == null ? List.of() : handlers);
    }

    /**
     * 根据 taskKey 查找对应 handler。
     */
    public Optional<ScheduledTaskHandler> handlerFor(ScheduledTaskKey taskKey) {
        return Optional.ofNullable(handlers.get(taskKey));
    }

    /** 校验注册 handler 是否接受指定触发类型。 */
    public boolean supports(ScheduledTaskKey taskKey, ScheduledTaskTriggerType triggerType) {
        return handlerFor(taskKey).map(handler -> handler.supportedTriggerTypes().contains(triggerType)).orElse(false);
    }

    private Map<ScheduledTaskKey, ScheduledTaskHandler> indexHandlers(List<ScheduledTaskHandler> handlers) {
        Map<ScheduledTaskKey, ScheduledTaskHandler> indexed = new LinkedHashMap<>();
        for (ScheduledTaskHandler handler : handlers) {
            ScheduledTaskKey taskKey = handler.taskKey();
            ScheduledTaskHandler previous = indexed.putIfAbsent(taskKey, handler);
            if (previous != null) {
                throw new PlatformException(
                        ErrorCode.CONFLICT,
                        "定时任务 taskKey 重复",
                        Map.of("taskKey", taskKey.value()));
            }
        }
        return Map.copyOf(indexed);
    }
}
