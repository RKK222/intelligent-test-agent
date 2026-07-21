package com.enterprise.testagent.opencode.runtime.night;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** 验证 in-flight 租约只续当前 attempt，旧 handle 不得移除后来认领。 */
class NightExecutionDispatchLeaseGuardTest {

    @Test
    void staleHandleCannotRemoveOrRenewNewAttempt() {
        Instant now = Instant.parse("2026-07-18T13:02:00Z");
        NightExecutionTaskRepository repository = mock(NightExecutionTaskRepository.class);
        NightExecutionDispatchLeaseGuard guard = new NightExecutionDispatchLeaseGuard(
                repository, Clock.fixed(now, ZoneOffset.UTC));
        NightExecutionTaskId taskId = new NightExecutionTaskId("net_lease_guard");
        NightExecutionDispatchLeaseGuard.Handle oldHandle = guard.track(taskId, "nda_old");
        NightExecutionDispatchLeaseGuard.Handle currentHandle = guard.track(taskId, "nda_current");

        oldHandle.close();
        guard.renewInFlightLeases();

        assertThat(guard.isInFlight(taskId, "nda_current")).isTrue();
        verify(repository).renewDispatchLease(
                taskId, "nda_current", now.plus(NightExecutionDispatchLeaseGuard.LEASE_DURATION), now);
        currentHandle.close();
        assertThat(guard.isInFlight(taskId, "nda_current")).isFalse();
    }
}
