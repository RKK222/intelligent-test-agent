package com.enterprise.testagent.localclient;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 将本机文件系统调用与生命周期、HTTP 请求隔离，并限制卡住的 native 调用数量。
 *
 * <p>macOS 的 TCC、文件提供程序或断开的网络盘可能让 {@code opendir(2)} 长时间不返回。
 * 这类调用无法可靠地靠线程中断立即打断，因此使用有界 daemon 平台线程池承载，并在
 * 前端 WebSocket 超时之前返回稳定错误，避免拖垮客户端的通用操作池。</p>
 */
final class LocalClientFileOperationExecutor implements AutoCloseable {

    static final int DEFAULT_MAX_CONCURRENT_OPERATIONS = 4;
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    private final ExecutorService executor;
    private final Semaphore slots;
    private final Duration timeout;

    LocalClientFileOperationExecutor() {
        this(DEFAULT_MAX_CONCURRENT_OPERATIONS, DEFAULT_TIMEOUT);
    }

    LocalClientFileOperationExecutor(int maxConcurrentOperations, Duration timeout) {
        if (maxConcurrentOperations < 1) {
            throw new IllegalArgumentException("maxConcurrentOperations must be positive");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.slots = new Semaphore(maxConcurrentOperations);
        this.timeout = timeout;
        ThreadFactory threadFactory = Thread.ofPlatform()
                .daemon(true)
                .name("local-client-file-", 0)
                .factory();
        this.executor = Executors.newFixedThreadPool(maxConcurrentOperations, threadFactory);
    }

    <T> T execute(Supplier<T> operation) {
        if (!slots.tryAcquire()) {
            throw new LocalClientFileOperationException("本地文件操作繁忙，请稍后重试");
        }
        Future<T> future = null;
        boolean submitted = false;
        try {
            future = executor.submit(() -> {
                try {
                    return operation.get();
                } finally {
                    // native 调用忽略中断时，槽位必须保持占用，避免超时重试不断堆积阻塞线程。
                    slots.release();
                }
            });
            submitted = true;
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            cancel(future);
            throw new LocalClientFileOperationException("本地文件操作超时，请检查本机文件访问权限后重试", exception);
        } catch (InterruptedException exception) {
            cancel(future);
            Thread.currentThread().interrupt();
            throw new LocalClientFileOperationException("本地文件操作已取消", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new LocalClientFileOperationException("本地文件操作失败", cause);
        } catch (RejectedExecutionException exception) {
            throw new LocalClientFileOperationException("本地文件操作当前不可用，请稍后重试", exception);
        } finally {
            if (!submitted) {
                // submit 失败时没有 inner finally 可以释放槽位。
                slots.release();
            }
        }
    }

    private static void cancel(Future<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}

/** 文件系统调用超时、取消或容量受限时使用的稳定客户端错误。 */
final class LocalClientFileOperationException extends IllegalStateException {

    LocalClientFileOperationException(String message) {
        super(message);
    }

    LocalClientFileOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
