package com.enterprise.testagent.xxljob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

class XxlJobAdminLifecycleTest {

    @Test
    void adminStartupFailureOnlyMarksDedicatedHealthDown() {
        XxlJobProperties properties = enabledProperties();
        XxlJobAdminState state = new XxlJobAdminState();
        XxlJobAdminContextLauncher launcher = bridge -> {
            throw new IllegalStateException("mysql password must stay hidden");
        };
        XxlJobAdminLifecycle lifecycle = new XxlJobAdminLifecycle(properties, state, launcher, new NoopBridge());

        assertThatCode(lifecycle::attemptStart).doesNotThrowAnyException();

        assertThat(state.isUp()).isFalse();
        assertThat(state.failureReason()).isEqualTo("XXL-JOB Admin 启动失败");
        assertThat(lifecycle.nextRetryDelay()).isEqualTo(Duration.ofSeconds(5));

        lifecycle.attemptStart();

        assertThat(lifecycle.nextRetryDelay()).isEqualTo(Duration.ofSeconds(10));
        lifecycle.attemptStart();
        assertThat(lifecycle.nextRetryDelay()).isEqualTo(Duration.ofSeconds(20));
        lifecycle.attemptStart();
        assertThat(lifecycle.nextRetryDelay()).isEqualTo(Duration.ofSeconds(40));
        lifecycle.attemptStart();
        assertThat(lifecycle.nextRetryDelay()).isEqualTo(Duration.ofSeconds(60));
        lifecycle.attemptStart();
        assertThat(lifecycle.nextRetryDelay()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void successfulRetryPublishesUpWithoutRestartingMainContext() {
        XxlJobProperties properties = enabledProperties();
        XxlJobAdminState state = new XxlJobAdminState();
        AtomicInteger attempts = new AtomicInteger();
        ConfigurableApplicationContext child = org.mockito.Mockito.mock(ConfigurableApplicationContext.class);
        XxlJobAdminContextLauncher launcher = bridge -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("mysql unavailable");
            }
            return child;
        };
        XxlJobAdminLifecycle lifecycle = new XxlJobAdminLifecycle(properties, state, launcher, new NoopBridge());

        lifecycle.attemptStart();
        lifecycle.attemptStart();

        assertThat(state.isUp()).isTrue();
        assertThat(attempts).hasValue(2);
        assertThat(lifecycle.nextRetryDelay()).isEqualTo(Duration.ofSeconds(5));
    }

    private static XxlJobProperties enabledProperties() {
        XxlJobProperties properties = new XxlJobProperties();
        properties.setEnabled(true);
        properties.getAdmin().setRetryInitialDelay(Duration.ofSeconds(5));
        properties.getAdmin().setRetryMaxDelay(Duration.ofSeconds(60));
        return properties;
    }

    private static final class NoopBridge implements XxlJobAdminBridge {
        @Override
        public java.util.Optional<XxlJobSsoIdentity> consumeTicket(String ticket) {
            return java.util.Optional.empty();
        }

        @Override
        public boolean isPlatformSessionActive(String sessionDigest) {
            return false;
        }
    }
}
