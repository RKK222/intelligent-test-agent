package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastHandler;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskHandler;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 每分钟全局协调一次清理，并广播唤醒每台在线 Java 仅处理自己的持久化任务。 */
@Component
public class AppSourceCleanupTaskHandler implements ScheduledTaskHandler, ServerBroadcastHandler {

    public static final ScheduledTaskKey TASK_KEY =
            new ScheduledTaskKey("workspace-management.app-source-cleanup");
    static final String WAKE_EVENT = "app-source.cleanup-requested";
    private final AppSourceCleanupWorker worker;
    private final ServerBroadcastPublisher publisher;
    private final WorkspaceServerIdentity serverIdentity;
    private final Clock clock;

    public AppSourceCleanupTaskHandler(
            AppSourceCleanupWorker worker,
            ServerBroadcastPublisher publisher,
            WorkspaceServerIdentity serverIdentity,
            Clock clock) {
        this.worker = Objects.requireNonNull(worker);
        this.publisher = Objects.requireNonNull(publisher);
        this.serverIdentity = Objects.requireNonNull(serverIdentity);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ScheduledTaskKey taskKey() {
        return TASK_KEY;
    }

    @Override
    public String name() {
        return "应用源码到期清理";
    }

    @Override
    public String cronExpression() {
        return "0 0/1 * * * ? *";
    }

    @Override
    public Duration lockTtl() {
        return Duration.ofMinutes(1);
    }

    @Override
    public ScheduledTaskResult run(ScheduledTaskContext context) {
        context.throwIfStopRequested();
        try {
            publisher.publish(new ServerBroadcastEvent(
                    RuntimeIdGenerator.serverBroadcastEventId(), WAKE_EVENT, publisher.instanceId(),
                    serverIdentity.linuxServerId(), context.traceId(), clock.instant(), Map.of()));
        } catch (RuntimeException ignored) {
            // 广播仅低延迟唤醒，数据库任务和下一分钟调度仍会补偿。
        }
        int localCompleted = worker.runDue();
        return ScheduledTaskResult.of(Map.of("localCompleted", localCompleted));
    }

    @Override
    public boolean supports(String type) {
        return WAKE_EVENT.equals(type);
    }

    @Override
    public void handle(ServerBroadcastEvent event) {
        if (supports(event.type())) {
            worker.runDue();
        }
    }
}
