package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.run.RunOwnerLease;
import com.enterprise.testagent.domain.run.RunRuntimeManifest;
import com.enterprise.testagent.domain.run.RunStatus;
import org.junit.jupiter.api.Test;

class RedisSummaryInactiveRunExpiryExecutorTest {

    @Test
    void preservesLegacyTwoHourTimeoutAsFailedTerminal() {
        RedisSummaryPendingAskExpiryExecutor termination = mock(RedisSummaryPendingAskExpiryExecutor.class);
        RunRuntimeManifest manifest = mock(RunRuntimeManifest.class);
        RunOwnerLease lease = mock(RunOwnerLease.class);
        when(termination.terminate(
                        manifest,
                        lease,
                        "trace_inactive",
                        RunStatus.FAILED,
                        "STALE_ACTIVE_RUN_TIMEOUT",
                        "STALE_ACTIVE_RUN_TIMEOUT",
                        "运行超过 2 小时无活动，当前对话已安全终止"))
                .thenReturn(true);
        RedisSummaryInactiveRunExpiryExecutor executor =
                new RedisSummaryInactiveRunExpiryExecutor(termination);

        assertThat(executor.expireInactiveRun(manifest, lease, "trace_inactive")).isTrue();

        verify(termination).terminate(
                manifest,
                lease,
                "trace_inactive",
                RunStatus.FAILED,
                "STALE_ACTIVE_RUN_TIMEOUT",
                "STALE_ACTIVE_RUN_TIMEOUT",
                "运行超过 2 小时无活动，当前对话已安全终止");
    }
}
