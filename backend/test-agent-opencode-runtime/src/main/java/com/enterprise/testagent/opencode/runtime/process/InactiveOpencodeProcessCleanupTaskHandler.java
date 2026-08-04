package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastHandler;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskHandler;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 每天由 XXL 全局协调一次，并广播每台 Java 清理自己持有的闲置用户进程。 */
@Component
public class InactiveOpencodeProcessCleanupTaskHandler
        implements ScheduledTaskHandler, ServerBroadcastHandler {

    public static final ScheduledTaskKey TASK_KEY =
            new ScheduledTaskKey("opencode-runtime.inactive-user-process-cleanup");
    static final String WAKE_EVENT = "opencode-runtime.inactive-user-process-cleanup-requested";
    static final String CRON = "0 0 2 * * ? *";
    static final Duration LOCK_TTL = Duration.ofHours(2);

    private final InactiveOpencodeProcessCleanupService cleanupService;
    private final ServerBroadcastPublisher publisher;
    private final BackendInstanceIdentity backendIdentity;
    private final Clock clock;

    public InactiveOpencodeProcessCleanupTaskHandler(
            InactiveOpencodeProcessCleanupService cleanupService,
            ServerBroadcastPublisher publisher,
            BackendInstanceIdentity backendIdentity,
            Clock clock) {
        this.cleanupService = Objects.requireNonNull(cleanupService, "cleanupService must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.backendIdentity = Objects.requireNonNull(backendIdentity, "backendIdentity must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public ScheduledTaskKey taskKey() {
        return TASK_KEY;
    }

    @Override
    public String name() {
        return "十五天未使用用户 OpenCode 进程关闭";
    }

    @Override
    public String cronExpression() {
        return CRON;
    }

    @Override
    public Duration lockTtl() {
        return LOCK_TTL;
    }

    @Override
    public ScheduledTaskResult run(ScheduledTaskContext context) {
        context.throwIfStopRequested();
        try {
            publisher.publish(new ServerBroadcastEvent(
                    RuntimeIdGenerator.serverBroadcastEventId(),
                    WAKE_EVENT,
                    publisher.instanceId(),
                    backendIdentity.linuxServerId(),
                    context.traceId(),
                    clock.instant(),
                    Map.of()));
        } catch (RuntimeException ignored) {
            // 广播只负责低延迟唤醒；当前实例仍执行本机清理，失败节点留待下一次每日任务。
        }
        return ScheduledTaskResult.of(
                cleanupService.cleanupCurrentServer(context.traceId(), context::stopRequested).toMap());
    }

    @Override
    public boolean supports(String type) {
        return WAKE_EVENT.equals(type);
    }

    @Override
    public void handle(ServerBroadcastEvent event) {
        if (supports(event.type())) {
            cleanupService.cleanupCurrentServer(event.traceId(), () -> false);
        }
    }
}
