package com.enterprise.testagent.configuration.management;

import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import com.enterprise.testagent.scheduler.ScheduledTaskHandler;
import com.enterprise.testagent.scheduler.ScheduledTaskResult;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 每日补偿全部已配置 SSH Key 用户的 SCM Git 姓名。 */
@Component
public class ScmGitIdentitySyncTaskHandler implements ScheduledTaskHandler {

    public static final ScheduledTaskKey TASK_KEY =
            new ScheduledTaskKey("configuration-management.scm-git-name-sync");
    static final String CRON = "0 10 4 * * *";
    static final Duration LOCK_TTL = Duration.ofHours(1);
    private final ScmGitIdentitySyncService syncService;

    public ScmGitIdentitySyncTaskHandler(ScmGitIdentitySyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    public ScheduledTaskKey taskKey() {
        return TASK_KEY;
    }

    @Override
    public String name() {
        return "SCM Git 姓名补偿校验";
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
        ScmGitIdentitySyncService.SyncResult result = syncService.synchronizeAll(context);
        return ScheduledTaskResult.of(Map.of(
                "repositoryCount", result.repositoryCount(),
                "failedRepositoryCount", result.failedRepositoryCount(),
                "checkedUserCount", result.checkedUserCount(),
                "updatedUserCount", result.updatedUserCount()));
    }
}
