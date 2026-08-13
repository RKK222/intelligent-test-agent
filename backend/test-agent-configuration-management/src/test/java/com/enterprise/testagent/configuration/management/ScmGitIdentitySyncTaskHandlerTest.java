package com.enterprise.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import org.junit.jupiter.api.Test;

class ScmGitIdentitySyncTaskHandlerTest {

    @Test
    void exposesStableScheduleAndSafeAggregateResult() {
        ScmGitIdentitySyncService service = mock(ScmGitIdentitySyncService.class);
        ScheduledTaskContext context = mock(ScheduledTaskContext.class);
        when(service.synchronizeAll(context)).thenReturn(new ScmGitIdentitySyncService.SyncResult(3, 1, 20, 2));
        ScmGitIdentitySyncTaskHandler handler = new ScmGitIdentitySyncTaskHandler(service);

        assertThat(handler.taskKey().value()).isEqualTo("configuration-management.scm-git-name-sync");
        assertThat(handler.cronExpression()).isEqualTo("0 10 4 * * *");
        assertThat(handler.lockTtl()).isEqualTo(java.time.Duration.ofHours(1));
        assertThat(handler.run(context).result()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "repositoryCount", 3,
                "failedRepositoryCount", 1,
                "checkedUserCount", 20,
                "updatedUserCount", 2));
    }
}
