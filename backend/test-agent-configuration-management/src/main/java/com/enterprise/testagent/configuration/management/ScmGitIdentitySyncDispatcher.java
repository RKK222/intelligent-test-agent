package com.enterprise.testagent.configuration.management;

import com.enterprise.testagent.domain.user.UserId;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** SSH Key 保存后的单线程异步校准器，避免仓库历史扫描延长接口响应时间或并发打满磁盘。 */
@Component
public class ScmGitIdentitySyncDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScmGitIdentitySyncDispatcher.class);
    private final ScmGitIdentitySyncService syncService;
    private final Set<UserId> queuedUsers = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(256),
            Thread.ofVirtual().name("scm-git-identity-sync-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    public ScmGitIdentitySyncDispatcher(ScmGitIdentitySyncService syncService) {
        this.syncService = syncService;
    }

    /** 相同用户在队列中只保留一次；失败留给夜间全量任务继续补偿。 */
    public void request(UserId userId) {
        if (!queuedUsers.add(userId)) {
            return;
        }
        try {
            executor.submit(() -> {
                try {
                    syncService.synchronizeUser(userId);
                } catch (RuntimeException exception) {
                    LOGGER.warn(
                            "event=scm_git_identity_async_sync_failed userId={} errorType={}",
                            userId.value(), exception.getClass().getSimpleName());
                } finally {
                    queuedUsers.remove(userId);
                }
            });
        } catch (RejectedExecutionException rejected) {
            queuedUsers.remove(userId);
            // 即时队列有界，过载时交给每日全量任务补偿，避免 SSH Key 接口制造无界后台积压。
            LOGGER.warn("event=scm_git_identity_async_sync_rejected userId={}", userId.value());
        }
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }
}
