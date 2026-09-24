package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LocalClientFileOperationExecutorTest {

    @Test
    void shouldReturnBeforeNativeFileCallCanKeepTheCallerBlocked() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (LocalClientFileOperationExecutor executor =
                new LocalClientFileOperationExecutor(1, Duration.ofMillis(50))) {
            assertThatThrownBy(() -> executor.execute(() -> {
                started.countDown();
                while (release.getCount() > 0) {
                    try {
                        release.await();
                    } catch (InterruptedException ignored) {
                        // 模拟 native 调用忽略中断，验证超时仍能结算调用方但不释放底层槽位。
                    }
                }
                return "never-reached";
            }))
                    .isInstanceOf(LocalClientFileOperationException.class)
                    .hasMessage("本地文件操作超时，请检查本机文件访问权限后重试");
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> executor.execute(() -> null))
                    .isInstanceOf(LocalClientFileOperationException.class)
                    .hasMessage("本地文件操作繁忙，请稍后重试");
        } finally {
            release.countDown();
        }
    }

    @Test
    void shouldRejectWhenAllFileOperationSlotsAreOccupied() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (LocalClientFileOperationExecutor executor =
                new LocalClientFileOperationExecutor(1, Duration.ofSeconds(2))) {
            Thread first = Thread.ofPlatform().start(() -> executor.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> executor.execute(() -> null))
                    .isInstanceOf(LocalClientFileOperationException.class)
                    .hasMessage("本地文件操作繁忙，请稍后重试");
            release.countDown();
            first.join(1000);
            assertThat(first.isAlive()).isFalse();
        } finally {
            release.countDown();
        }
    }
}
