package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 验证 360 启动器切换真实浏览器内核后的进程身份 fencing。 */
class LocalBrowserSupervisorTest {

    @Test
    void acceptsLauncherExecTransitionWhenPidAndStartInstantStayStable() {
        Instant startedAt = Instant.parse("2026-09-01T08:17:21Z");
        ProcessHandle process = process(3795036L, startedAt, true);
        when(process.info().command()).thenReturn(Optional.of("/opt/browser360ent/browser360ent"));

        assertThat(LocalBrowserSupervisor.sameProcessIdentity(process, 3795036L, startedAt)).isTrue();
    }

    @Test
    void rejectsExitedOrReusedProcessIdentity() {
        Instant startedAt = Instant.parse("2026-09-01T08:17:21Z");

        assertThat(LocalBrowserSupervisor.sameProcessIdentity(
                process(3795036L, startedAt, false), 3795036L, startedAt)).isFalse();
        assertThat(LocalBrowserSupervisor.sameProcessIdentity(
                process(3795037L, startedAt, true), 3795036L, startedAt)).isFalse();
        assertThat(LocalBrowserSupervisor.sameProcessIdentity(
                process(3795036L, startedAt.plusSeconds(1), true), 3795036L, startedAt)).isFalse();
    }

    private static ProcessHandle process(long pid, Instant startedAt, boolean alive) {
        ProcessHandle process = mock(ProcessHandle.class);
        ProcessHandle.Info info = mock(ProcessHandle.Info.class);
        when(process.pid()).thenReturn(pid);
        when(process.isAlive()).thenReturn(alive);
        when(process.info()).thenReturn(info);
        when(info.startInstant()).thenReturn(Optional.of(startedAt));
        return process;
    }
}
