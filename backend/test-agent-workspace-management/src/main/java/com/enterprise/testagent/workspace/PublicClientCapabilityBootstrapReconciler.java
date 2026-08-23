package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.scheduler.ScheduledTaskLock;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 为引入客户端能力包功能前已经存在的公共 Git HEAD 补建首个完整制品。
 *
 * <p>发布流程只会处理后续新 commit；本补偿器不 fetch、不提交、不推送，只读取平台当前已经检出的
 * 公共运行副本，并通过 Redis 锁保证多 Java 节点最多由一个节点生成同一基线。</p>
 */
@Component
public class PublicClientCapabilityBootstrapReconciler implements SmartLifecycle {

    static final String PUBLIC_CONFIG_GIT_ROOT = "OPENCODE_PUBLIC_CONFIG_GIT_ROOT";
    private static final Logger LOGGER = LoggerFactory.getLogger(PublicClientCapabilityBootstrapReconciler.class);
    private static final ScheduledTaskKey TASK_KEY = new ScheduledTaskKey("public-client-capability-bootstrap");
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final CommonParameterValues commonParameterValues;
    private final PublicClientCapabilityPackageService packageService;
    private final ScheduledTaskLock scheduledTaskLock;
    private final GitWorkspaceService gitWorkspaceService;
    private final boolean enabled;
    private final Duration interval;
    private volatile ScheduledExecutorService executor;
    private volatile boolean running;

    @Autowired
    public PublicClientCapabilityBootstrapReconciler(
            CommonParameterValues commonParameterValues,
            PublicClientCapabilityPackageService packageService,
            ScheduledTaskLock scheduledTaskLock,
            @Value("${test-agent.local-client.public-capabilities.bootstrap.enabled:true}") boolean enabled,
            @Value("${test-agent.local-client.public-capabilities.bootstrap.interval-seconds:60}") long intervalSeconds) {
        this(commonParameterValues, packageService, scheduledTaskLock, new GitWorkspaceService(),
                enabled, Duration.ofSeconds(Math.max(10L, intervalSeconds)));
    }

    PublicClientCapabilityBootstrapReconciler(
            CommonParameterValues commonParameterValues,
            PublicClientCapabilityPackageService packageService,
            ScheduledTaskLock scheduledTaskLock,
            GitWorkspaceService gitWorkspaceService,
            boolean enabled,
            Duration interval) {
        this.commonParameterValues = Objects.requireNonNull(commonParameterValues);
        this.packageService = Objects.requireNonNull(packageService);
        this.scheduledTaskLock = Objects.requireNonNull(scheduledTaskLock);
        this.gitWorkspaceService = Objects.requireNonNull(gitWorkspaceService);
        this.enabled = enabled;
        this.interval = Objects.requireNonNull(interval);
    }

    @Override
    public void start() {
        if (running || !enabled) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "public-client-capability-bootstrap");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::safeReconcile, 5L, interval.toSeconds(), TimeUnit.SECONDS);
        running = true;
    }

    @Override
    public void stop() {
        ScheduledExecutorService current = executor;
        if (current != null) {
            current.shutdownNow();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** 包级入口便于用真实 Git 坐标验证一次补偿，不启动后台线程。 */
    void reconcileOnce(String traceId) {
        Path gitRoot = commonParameterValues.resolvedValue(PUBLIC_CONFIG_GIT_ROOT)
                .filter(value -> !value.isBlank() && !"UNCONFIGURED".equalsIgnoreCase(value.trim()))
                .map(String::trim)
                .map(Path::of)
                .map(Path::normalize)
                .orElse(null);
        if (gitRoot == null || !gitWorkspaceService.isGitRepository(gitRoot)) {
            return;
        }
        String sourceCommit = gitWorkspaceService.headCommit(gitRoot).toLowerCase();
        if (packageService.releaseForCommit(sourceCommit) != null) {
            return;
        }
        scheduledTaskLock.acquire(TASK_KEY, LOCK_TTL).ifPresent(lease -> {
            try (lease) {
                // 锁等待期间其它节点可能已经完成；必须再次读取，不能依赖锁前快照。
                if (packageService.releaseForCommit(sourceCommit) == null) {
                    packageService.generateForPublishedCommit(gitRoot, sourceCommit, traceId);
                }
            }
        });
    }

    private void safeReconcile() {
        String traceId = "trace_" + UUID.randomUUID().toString().replace("-", "");
        try {
            reconcileOnce(traceId);
        } catch (RuntimeException exception) {
            LOGGER.warn("event=public_capability_bootstrap_failed traceId={}", traceId, exception);
        }
    }
}
