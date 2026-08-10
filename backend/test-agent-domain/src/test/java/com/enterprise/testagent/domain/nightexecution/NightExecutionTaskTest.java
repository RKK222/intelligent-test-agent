package com.enterprise.testagent.domain.nightexecution;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 验证夜间窗口和超级管理员测试定时共享聚合时仍保持各自的容量与窗口语义。 */
class NightExecutionTaskTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-24T04:00:00Z");
    private static final Instant SLOT_START = Instant.parse("2026-07-24T05:01:00Z");

    @Test
    void compatibilityConstructorDefaultsToNightWindow() {
        NightExecutionTask task = compatibilityTask();

        assertThat(task.scheduleMode()).isEqualTo(NightExecutionScheduleMode.NIGHT_WINDOW);
    }

    @Test
    void rescheduleReplacesTheWholeCustomRetryWindow() {
        NightExecutionTask task = customTask();
        Instant nextStart = Instant.parse("2026-07-24T06:05:00Z");

        NightExecutionTask adjusted = task.reschedule(
                nextStart, nextStart.plusSeconds(60), nextStart.plusSeconds(900),
                task.targetLinuxServerId(), CREATED_AT.plusSeconds(30));

        assertThat(adjusted.scheduleMode()).isEqualTo(NightExecutionScheduleMode.ADMIN_CUSTOM);
        assertThat(adjusted.slotStart()).isEqualTo(nextStart);
        assertThat(adjusted.slotEnd()).isEqualTo(nextStart.plusSeconds(60));
        assertThat(adjusted.windowEnd()).isEqualTo(nextStart.plusSeconds(900));
    }

    @Test
    void customTaskTerminalTransitionsDoNotClaimNightCapacityWasReleased() {
        Instant claimedAt = SLOT_START.plusSeconds(1);
        NightExecutionTask dispatching = customTask().startDispatch(
                "nda_custom_domain", "bjp_custom_domain", claimedAt.plusSeconds(300), claimedAt);

        assertThat(dispatching.dispatched(new RunId("run_custom_domain"), claimedAt.plusSeconds(2))
                .reservationReleasedAt()).isNull();
        assertThat(customTask().cancel(claimedAt).reservationReleasedAt()).isNull();
        assertThat(customTask().fail("WINDOW_EXPIRED", "测试定时已过期", claimedAt)
                .reservationReleasedAt()).isNull();
    }

    @Test
    void delegatedCreatorSnapshotSurvivesLifecycleTransitions() {
        NightExecutionTask task = customTask().withCreatorSnapshot(
                new UserId("usr_shared_creator"), "ucid-shared-creator", true,
                new SessionShareId("shr_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                7L, CREATED_AT.plusSeconds(3600), true);

        NightExecutionTask adjusted = task.reschedule(
                SLOT_START.plusSeconds(120), SLOT_START.plusSeconds(180), SLOT_START.plusSeconds(1020),
                task.targetLinuxServerId(), CREATED_AT.plusSeconds(30));

        assertThat(adjusted.creatorUserId()).isEqualTo(new UserId("usr_shared_creator"));
        assertThat(adjusted.creatorUnifiedAuthId()).isEqualTo("ucid-shared-creator");
        assertThat(adjusted.createdBySharedUser()).isTrue();
        assertThat(adjusted.shareIdSnapshot()).isEqualTo(task.shareIdSnapshot());
        assertThat(adjusted.shareVersionSnapshot()).isEqualTo(7L);
        assertThat(adjusted.canChatSnapshot()).isTrue();
    }

    private NightExecutionTask compatibilityTask() {
        return new NightExecutionTask(
                new NightExecutionTaskId("net_domain_night"), new UserId("usr_domain_night"),
                new SessionId("ses_domain_night"), new WorkspaceId("wrk_domain_night"),
                "request-domain-night", "夜间执行", "生成测试", "{}",
                NightExecutionTaskStatus.SCHEDULED, SLOT_START, SLOT_START.plusSeconds(900),
                Instant.parse("2026-07-24T23:00:00Z"), "linux-domain", null, null, 0,
                false, null, null, null, null, null, "trace_domain_night", CREATED_AT, CREATED_AT);
    }

    private NightExecutionTask customTask() {
        return new NightExecutionTask(
                new NightExecutionTaskId("net_domain_custom"), new UserId("usr_domain_custom"),
                new SessionId("ses_domain_custom"), new WorkspaceId("wrk_domain_custom"),
                "request-domain-custom", "测试定时", "生成测试", "{}",
                NightExecutionScheduleMode.ADMIN_CUSTOM, NightExecutionTaskStatus.SCHEDULED,
                SLOT_START, SLOT_START.plusSeconds(60), SLOT_START.plusSeconds(900),
                "linux-domain", null, null, 0, false, null, null, null, null,
                0L, null, null, null, null, "trace_domain_custom", CREATED_AT, CREATED_AT);
    }
}
