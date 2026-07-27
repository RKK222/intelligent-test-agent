package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.opencodeprocess.ContainerManagerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * manager 重连并应用完整运行配置后，恢复本容器在重启前仍处于运行意图的用户进程。
 *
 * <p>恢复只选择 {@code RUNNING/STARTING + ACTIVE binding}，显式停止或失败的进程不会被拉起。
 * 实际启动继续委托 {@link UserOpencodeProcessAssignmentService#initialize}，避免复制端口、路由、
 * manager health、权威启动时间和状态回写逻辑。
 */
@Service
public class OpencodeProcessAutoRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(OpencodeProcessAutoRecoveryService.class);
    private static final int RECOVERY_SCAN_LIMIT = PageRequest.MAX_SIZE;
    private static final int MAX_RECOVERY_ATTEMPTS = 2;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
    private static final String OPENCODE_AGENT_ID = "opencode";
    private static final Set<OpencodeServerProcessStatus> RECOVERABLE_STATUSES = Set.of(
            OpencodeServerProcessStatus.RUNNING,
            OpencodeServerProcessStatus.STARTING);

    private final OpencodeProcessManagementRepository repository;
    private final UserOpencodeProcessAssignmentService assignmentService;
    private final Scheduler scheduler;
    private final Consumer<Duration> retrySleeper;
    private final ConcurrentMap<ContainerManagerId, RecoveryPlan> recoveryPlans = new ConcurrentHashMap<>();

    /** 生产恢复任务在 boundedElastic 上执行，避免阻塞 manager WebSocket event-loop。 */
    @Autowired
    public OpencodeProcessAutoRecoveryService(
            OpencodeProcessManagementRepository repository,
            UserOpencodeProcessAssignmentService assignmentService) {
        this(repository, assignmentService, Schedulers.boundedElastic(), OpencodeProcessAutoRecoveryService::sleep);
    }

    /** 测试构造器允许同步调度并跳过真实退避等待。 */
    OpencodeProcessAutoRecoveryService(
            OpencodeProcessManagementRepository repository,
            UserOpencodeProcessAssignmentService assignmentService,
            Scheduler scheduler,
            Consumer<Duration> retrySleeper) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.retrySleeper = Objects.requireNonNull(retrySleeper, "retrySleeper must not be null");
    }

    /**
     * manager 注册时同步冻结本连接的恢复候选，避免 manager 暂未托管进程期间的状态探测
     * 先把数据库 RUNNING 回写为 STOPPED，导致后续恢复丢失原运行意图。
     */
    public void prepareRecovery(
            ContainerManagerId managerId,
            LinuxServerId linuxServerId,
            OpencodeContainerId containerId,
            String traceId) {
        Objects.requireNonNull(managerId, "managerId must not be null");
        Objects.requireNonNull(linuxServerId, "linuxServerId must not be null");
        Objects.requireNonNull(containerId, "containerId must not be null");
        List<OpencodeServerProcess> candidates;
        try {
            candidates = activeRecoveryCandidates(linuxServerId, containerId);
        } catch (RuntimeException exception) {
            // 自动恢复是启动后的补偿能力，候选读取异常不能阻断 manager 控制面和后端 readiness。
            log.warn(
                    "opencode 自动恢复候选冻结失败，本连接跳过恢复 linuxServerId={} containerId={} traceId={}",
                    linuxServerId,
                    containerId,
                    traceId,
                    exception);
            candidates = List.of();
        }
        recoveryPlans.put(managerId, new RecoveryPlan(linuxServerId, containerId, candidates, traceId));
    }

    /**
     * 完整配置已应用且控制连接已可用后，异步执行注册阶段冻结的恢复计划。
     * 周期心跳不会重复执行同一计划。
     */
    public void requestPreparedRecovery(ContainerManagerId managerId, String traceId) {
        Objects.requireNonNull(managerId, "managerId must not be null");
        RecoveryPlan plan = recoveryPlans.remove(managerId);
        if (plan == null) {
            return;
        }
        String effectiveTraceId = traceId == null || traceId.isBlank() ? plan.traceId() : traceId;
        Mono.fromRunnable(() -> recoverContainer(plan, effectiveTraceId))
                .subscribeOn(scheduler)
                .doOnError(exception -> log.error(
                        "opencode 自动恢复任务异常退出 linuxServerId={} containerId={} traceId={}",
                        plan.linuxServerId(),
                        plan.containerId(),
                        effectiveTraceId,
                        exception))
                .onErrorComplete()
                .subscribe();
    }

    /** manager 断线后释放本次连接的恢复标记，使重连能够重新核对运行态。 */
    public void managerDisconnected(ContainerManagerId managerId) {
        if (managerId == null) {
            return;
        }
        recoveryPlans.remove(managerId);
    }

    private void recoverContainer(RecoveryPlan plan, String traceId) {
        LinuxServerId linuxServerId = plan.linuxServerId();
        OpencodeContainerId containerId = plan.containerId();
        List<OpencodeServerProcess> candidates = plan.candidates();
        if (candidates.isEmpty()) {
            log.info(
                    "opencode 自动恢复无需处理 linuxServerId={} containerId={} traceId={}",
                    linuxServerId,
                    containerId,
                    traceId);
            return;
        }

        List<OpencodeServerProcess> pending = candidates;
        int recovered = 0;
        for (int attempt = 1; attempt <= MAX_RECOVERY_ATTEMPTS && !pending.isEmpty(); attempt++) {
            List<OpencodeServerProcess> failed = new ArrayList<>();
            for (OpencodeServerProcess process : pending) {
                try {
                    assignmentService.initialize(process.userId(), OPENCODE_AGENT_ID, traceId);
                    recovered++;
                } catch (RuntimeException exception) {
                    failed.add(process);
                    log.warn(
                            "opencode 自动恢复单进程失败 processId={} containerId={} port={} attempt={} traceId={}",
                            process.processId(),
                            containerId,
                            process.port(),
                            attempt,
                            traceId,
                            exception);
                }
            }
            pending = List.copyOf(failed);
            if (!pending.isEmpty() && attempt < MAX_RECOVERY_ATTEMPTS) {
                retrySleeper.accept(RETRY_DELAY);
            }
        }
        log.info(
                "opencode 自动恢复结束 linuxServerId={} containerId={} candidates={} recovered={} failed={} traceId={}",
                linuxServerId,
                containerId,
                candidates.size(),
                recovered,
                pending.size(),
                traceId);
    }

    private List<OpencodeServerProcess> recoverableProcesses(
            LinuxServerId linuxServerId,
            OpencodeContainerId containerId) {
        Map<OpencodeProcessId, OpencodeServerProcess> processes = new LinkedHashMap<>();
        for (OpencodeServerProcessStatus status : RECOVERABLE_STATUSES) {
            var page = repository.findOpencodeServerProcesses(
                    new OpencodeServerProcessFilter(status, linuxServerId, containerId, null),
                    new PageRequest(1, RECOVERY_SCAN_LIMIT));
            for (OpencodeServerProcess process : page.items()) {
                processes.putIfAbsent(process.processId(), process);
            }
        }
        return List.copyOf(processes.values());
    }

    private List<OpencodeServerProcess> activeRecoveryCandidates(
            LinuxServerId linuxServerId,
            OpencodeContainerId containerId) {
        List<OpencodeServerProcess> candidates = recoverableProcesses(linuxServerId, containerId);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<OpencodeProcessId, UserOpencodeProcessBinding> bindings = repository.findUserBindingsByProcessIds(
                candidates.stream().map(OpencodeServerProcess::processId).toList());
        return candidates.stream()
                .filter(process -> activeBindingMatches(process, bindings.get(process.processId())))
                .toList();
    }

    private boolean activeBindingMatches(
            OpencodeServerProcess process,
            UserOpencodeProcessBinding binding) {
        return binding != null
                && binding.status() == UserOpencodeProcessBindingStatus.ACTIVE
                && binding.processId().equals(process.processId())
                && binding.userId().equals(process.userId())
                && binding.linuxServerId().equals(process.linuxServerId())
                && binding.port() == process.port();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("opencode 自动恢复等待被中断", exception);
        }
    }

    private record RecoveryPlan(
            LinuxServerId linuxServerId,
            OpencodeContainerId containerId,
            List<OpencodeServerProcess> candidates,
            String traceId) {

        private RecoveryPlan {
            candidates = List.copyOf(candidates);
        }
    }
}
