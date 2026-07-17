package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class ReferenceRepositoryReplicaReconcilerTest {

    @Test
    void startsAndStopsDaemonReconciliationWhenEnabled() {
        ReferenceRepositoryReplicaReconciler reconciler = new ReferenceRepositoryReplicaReconciler(
                mock(ReferenceRepositoryApplicationService.class), true, 60L);

        reconciler.start();

        assertThat(reconciler.isRunning()).isTrue();
        reconciler.stop();
        assertThat(reconciler.isRunning()).isFalse();
    }

    @Test
    void remainsStoppedWhenDisabled() {
        ReferenceRepositoryReplicaReconciler reconciler = new ReferenceRepositoryReplicaReconciler(
                mock(ReferenceRepositoryApplicationService.class), false, 60L);

        reconciler.start();

        assertThat(reconciler.isRunning()).isFalse();
    }
}
