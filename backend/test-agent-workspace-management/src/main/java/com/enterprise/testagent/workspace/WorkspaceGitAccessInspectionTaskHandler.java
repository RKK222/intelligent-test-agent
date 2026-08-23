package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.workspace.WorkspaceGitAccessInspectionEvents;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskHandler;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 每两小时巡检服务器 Git 权限，并广播各 Java 检查自己持有的在线本地客户端。 */
@Component
public class WorkspaceGitAccessInspectionTaskHandler implements ScheduledTaskHandler {

    public static final ScheduledTaskKey TASK_KEY =
            new ScheduledTaskKey("workspace-management.git-access-inspection");
    static final String CRON = "0 0 0/2 * * ? *";
    static final Duration LOCK_TTL = Duration.ofHours(2);

    private final WorkspaceGitAccessInspectionService inspectionService;
    private final ServerBroadcastPublisher publisher;
    private final WorkspaceServerIdentity serverIdentity;
    private final Clock clock;

    public WorkspaceGitAccessInspectionTaskHandler(
            WorkspaceGitAccessInspectionService inspectionService,
            ServerBroadcastPublisher publisher,
            WorkspaceServerIdentity serverIdentity,
            Clock clock) {
        this.inspectionService = Objects.requireNonNull(inspectionService);
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
        return "工作空间 Git 权限巡检";
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
        WorkspaceGitAccessInspectionService.InspectionResult result =
                inspectionService.inspectApplicationWorkspaces(context);
        context.throwIfStopRequested();
        publisher.publish(new ServerBroadcastEvent(
                RuntimeIdGenerator.serverBroadcastEventId(),
                WorkspaceGitAccessInspectionEvents.LOCAL_CLIENT_INSPECTION_REQUESTED,
                publisher.instanceId(),
                serverIdentity.linuxServerId(),
                context.traceId(),
                clock.instant(),
                Map.of()));
        return ScheduledTaskResult.of(Map.of(
                "checked", result.checked(),
                "accessible", result.accessible(),
                "inaccessible", result.inaccessible(),
                "unknown", result.unknown(),
                "localClientInspectionBroadcast", true));
    }
}
