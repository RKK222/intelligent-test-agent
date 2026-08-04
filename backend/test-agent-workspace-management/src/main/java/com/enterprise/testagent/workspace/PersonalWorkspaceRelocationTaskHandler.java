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

/** 全局每分钟扫描一次错配，并广播唤醒所有源服务器认领自己的物理 worktree。 */
@Component
public class PersonalWorkspaceRelocationTaskHandler
        implements ScheduledTaskHandler, ServerBroadcastHandler {

    public static final ScheduledTaskKey TASK_KEY =
            new ScheduledTaskKey("workspace-management.personal-workspace-relocation");
    static final String WAKE_EVENT = "personal-workspace.relocation-requested";

    private final PersonalWorkspaceRelocationWorker worker;
    private final ServerBroadcastPublisher publisher;
    private final WorkspaceServerIdentity serverIdentity;
    private final Clock clock;

    public PersonalWorkspaceRelocationTaskHandler(
            PersonalWorkspaceRelocationWorker worker,
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
        return "个人工作区跨服务器自动搬迁";
    }

    @Override
    public String cronExpression() {
        return "0 0/1 * * * ? *";
    }

    @Override
    public Duration lockTtl() {
        return Duration.ofMinutes(30);
    }

    @Override
    public ScheduledTaskResult run(ScheduledTaskContext context) {
        context.throwIfStopRequested();
        try {
            publisher.publish(new ServerBroadcastEvent(
                    RuntimeIdGenerator.serverBroadcastEventId(),
                    WAKE_EVENT,
                    publisher.instanceId(),
                    serverIdentity.linuxServerId(),
                    context.traceId(),
                    clock.instant(),
                    Map.of()));
        } catch (RuntimeException ignored) {
            // 广播只负责低延迟唤醒；入口 Java 本地执行和下一分钟 XXL 调度仍会补偿。
        }
        return ScheduledTaskResult.of(worker.runDue(context.traceId()).asDetails());
    }

    @Override
    public boolean supports(String type) {
        return WAKE_EVENT.equals(type);
    }

    @Override
    public void handle(ServerBroadcastEvent event) {
        if (supports(event.type())) {
            worker.runDue(event.traceId());
        }
    }
}
