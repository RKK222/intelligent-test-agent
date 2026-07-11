package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class RunPendingAskExpirySchedulerTest {

    @Test
    void startupAndPeriodicTriggersBothRunLocalExpiryScan() {
        RunPendingAskExpiryCoordinator coordinator = mock(RunPendingAskExpiryCoordinator.class);
        RunPendingAskExpiryScheduler scheduler = new RunPendingAskExpiryScheduler(coordinator);

        scheduler.expireOnStartup();
        scheduler.expirePeriodically();

        verify(coordinator, times(2)).expireCurrentServer(
                startsWith("trace_"), any(BooleanSupplier.class));
    }

    @Test
    void periodicTriggerUsesFiveSecondFixedDelay() throws Exception {
        Method method = RunPendingAskExpiryScheduler.class.getMethod("expirePeriodically");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelay()).isEqualTo(5_000L);
    }
}
