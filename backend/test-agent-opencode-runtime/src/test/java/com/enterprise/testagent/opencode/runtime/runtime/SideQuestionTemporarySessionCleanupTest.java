package com.enterprise.testagent.opencode.runtime.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SideQuestionTemporarySessionCleanupTest {

    @Test
    void repeatedCleanupInvokesRemoteDeleteOnlyOnce() {
        AtomicInteger deleteCalls = new AtomicInteger();
        SideQuestionTemporarySessionCleanup cleanup =
                new SideQuestionTemporarySessionCleanup(deleteCalls::incrementAndGet);

        cleanup.run();
        cleanup.run();

        assertThat(deleteCalls).hasValue(1);
    }
}
