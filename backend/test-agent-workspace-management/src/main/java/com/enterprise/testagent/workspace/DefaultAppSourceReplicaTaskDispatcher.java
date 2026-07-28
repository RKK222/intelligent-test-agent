package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastHandler;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** 本机有界副本 dispatcher，并以安全广播载荷低延迟唤醒目标 Java 实例。 */
@Component
public class DefaultAppSourceReplicaTaskDispatcher
        implements AppSourceReplicaTaskDispatcher, ServerBroadcastHandler, SmartLifecycle {

    static final String WAKE_EVENT = "app-source.replica-requested";
    static final String RECOVERY_TRACE_ID = "app-source-recovery";
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAppSourceReplicaTaskDispatcher.class);

    private final AppSourceReplicaWorker worker;
    private final AppSourceRepository appSources;
    private final ServerBroadcastPublisher publisher;
    private final WorkspaceServerIdentity serverIdentity;
    private final int workerCount;
    private final int maxPendingTasks;
    private final Clock clock;
    private final Duration recoveryInterval;
    private final int recoveryBatchSize;
    private final Set<TaskKey> enqueued = ConcurrentHashMap.newKeySet();
    private volatile ThreadPoolExecutor executor;
    private volatile ScheduledExecutorService recoveryExecutor;
    private volatile boolean running;

    @Autowired
    public DefaultAppSourceReplicaTaskDispatcher(
            AppSourceReplicaWorker worker,
            AppSourceRepository appSources,
            ServerBroadcastPublisher publisher,
            WorkspaceServerIdentity serverIdentity,
            @Value("${test-agent.app-source.replica-worker-count:2}") int workerCount,
            @Value("${test-agent.app-source.replica-max-pending:256}") int maxPendingTasks,
            @Value("${test-agent.app-source.replica-recovery-interval-millis:5000}") long recoveryIntervalMillis,
            @Value("${test-agent.app-source.replica-recovery-batch-size:64}") int recoveryBatchSize) {
        this(worker, appSources, publisher, serverIdentity, workerCount, maxPendingTasks, Clock.systemUTC(),
                Duration.ofMillis(recoveryIntervalMillis), recoveryBatchSize);
    }

    DefaultAppSourceReplicaTaskDispatcher(
            AppSourceReplicaWorker worker,
            AppSourceRepository appSources,
            ServerBroadcastPublisher publisher,
            WorkspaceServerIdentity serverIdentity,
            int workerCount,
            int maxPendingTasks,
            Clock clock,
            Duration recoveryInterval,
            int recoveryBatchSize) {
        if (workerCount < 1 || maxPendingTasks < 1 || recoveryBatchSize < 1
                || recoveryInterval.isZero() || recoveryInterval.isNegative()) {
            throw new IllegalArgumentException("worker, queue and recovery settings must be positive");
        }
        this.worker = Objects.requireNonNull(worker);
        this.appSources = Objects.requireNonNull(appSources);
        this.publisher = Objects.requireNonNull(publisher);
        this.serverIdentity = Objects.requireNonNull(serverIdentity);
        this.workerCount = workerCount;
        this.maxPendingTasks = maxPendingTasks;
        this.clock = Objects.requireNonNull(clock);
        this.recoveryInterval = Objects.requireNonNull(recoveryInterval);
        this.recoveryBatchSize = recoveryBatchSize;
    }

    /** 发布冻结目标集合，并显式提交本机任务；广播传输失败不改变数据库受理事实。 */
    @Override
    public void wake(AppSourceOperation operation, Set<LinuxServerId> targetServerIds) {
        Objects.requireNonNull(operation, "operation must not be null");
        Set<LinuxServerId> targets = Set.copyOf(Objects.requireNonNull(targetServerIds));
        try {
            publisher.publish(new ServerBroadcastEvent(
                    RuntimeIdGenerator.serverBroadcastEventId(), WAKE_EVENT, publisher.instanceId(),
                    serverIdentity.linuxServerId(), operation.traceId(), clock.instant(),
                    Map.of(
                            "repositoryId", operation.repositoryId().value(),
                            "generation", operation.targetGeneration(),
                            "targetServerIds", targets.stream()
                                    .map(LinuxServerId::value)
                                    .sorted(Comparator.naturalOrder())
                                    .toList())));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "event=app_source_replica_wake_publish_failed repositoryId={} generation={} traceId={}",
                    operation.repositoryId().value(), operation.targetGeneration(), operation.traceId());
        }
        LinuxServerId local = new LinuxServerId(serverIdentity.linuxServerId());
        if (targets.contains(local)) {
            dispatch(operation.repositoryId(), operation.targetGeneration(), local, operation.traceId());
        }
    }

    @Override
    public boolean supports(String type) {
        return WAKE_EVENT.equals(type);
    }

    /** 广播只是唤醒；只有载荷明确包含本服务器时才进入本机有界队列。 */
    @Override
    public void handle(ServerBroadcastEvent event) {
        if (!supports(event.type())) {
            return;
        }
        Object repositoryId = event.payload().get("repositoryId");
        Object generation = event.payload().get("generation");
        Object targetServerIds = event.payload().get("targetServerIds");
        String localId = serverIdentity.linuxServerId();
        if (!(repositoryId instanceof String id)
                || !(generation instanceof Number number)
                || !(targetServerIds instanceof Collection<?> targets)
                || targets.stream().noneMatch(localId::equals)) {
            return;
        }
        dispatch(new CodeRepositoryId(id), number.longValue(), new LinuxServerId(localId), event.traceId());
    }

    private boolean dispatch(
            CodeRepositoryId repositoryId,
            long generation,
            LinuxServerId linuxServerId,
            String traceId) {
        TaskKey key = new TaskKey(repositoryId, generation, linuxServerId);
        ThreadPoolExecutor current = executor;
        synchronized (enqueued) {
            if (!running || current == null || enqueued.contains(key)
                    || enqueued.size() >= maxPendingTasks) {
                return false;
            }
            enqueued.add(key);
        }
        try {
            current.execute(() -> {
                try {
                    worker.run(repositoryId, generation, linuxServerId, traceId);
                } finally {
                    synchronized (enqueued) {
                        enqueued.remove(key);
                    }
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            synchronized (enqueued) {
                enqueued.remove(key);
            }
            LOGGER.warn(
                    "event=app_source_replica_queue_full repositoryId={} generation={} linuxServerId={} traceId={}",
                    repositoryId.value(), generation, linuxServerId.value(), traceId);
            return false;
        }
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        executor = new ThreadPoolExecutor(
                workerCount, workerCount, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxPendingTasks), runnable -> {
                    Thread thread = new Thread(runnable, "app-source-replica-worker");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        recoveryExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "app-source-replica-recovery");
            thread.setDaemon(true);
            return thread;
        });
        running = true;
        recoveryExecutor.scheduleWithFixedDelay(
                this::recoverClaimableSafely, 0L, recoveryInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** 数据库是副本执行事实；启动及短周期扫描补偿广播、队列和 JVM 生命周期造成的瞬时信号丢失。 */
    private void recoverClaimableSafely() {
        try {
            LinuxServerId local = new LinuxServerId(serverIdentity.linuxServerId());
            for (var replica : appSources.findClaimableReplicas(local, clock.instant(), recoveryBatchSize)) {
                dispatch(replica.repositoryId(), replica.generation(), local, RECOVERY_TRACE_ID);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "event=app_source_replica_recovery_scan_failed linuxServerId={}",
                    serverIdentity.linuxServerId());
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        ThreadPoolExecutor current = executor;
        ScheduledExecutorService currentRecovery = recoveryExecutor;
        executor = null;
        recoveryExecutor = null;
        enqueued.clear();
        if (currentRecovery != null) {
            currentRecovery.shutdownNow();
        }
        if (current != null) {
            current.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private record TaskKey(CodeRepositoryId repositoryId, long generation, LinuxServerId linuxServerId) {
    }
}
