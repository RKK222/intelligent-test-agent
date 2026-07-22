package com.enterprise.testagent.opencode.runtime.session;

import com.enterprise.testagent.domain.session.SessionRuntimeState;
import com.enterprise.testagent.domain.session.SessionRuntimeStateRepository;
import com.enterprise.testagent.domain.session.SessionRuntimeStateSummary;
import com.enterprise.testagent.domain.session.SessionRuntimeAttention;
import com.enterprise.testagent.domain.run.RunRuntimeManifest;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.event.RunEventLiveBus;
import com.enterprise.testagent.event.RunEventLiveEvent;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 用户级会话运行态应用服务，负责把数据库快照和 RunEvent 实时触发合成前端状态流。
 */
@Service
public class SessionRuntimeStateApplicationService {

    private static final Duration DEFAULT_REFRESH_INTERVAL = Duration.ofSeconds(10);
    private static final Set<String> REFRESH_EVENT_TYPES = Set.of(
            "run.created",
            "run.started",
            "run.cancelling",
            "run.succeeded",
            "run.failed",
            "run.cancelled",
            "question.asked",
            "question.replied",
            "question.rejected",
            "permission.asked",
            "permission.replied");

    private final SessionRuntimeStateRepository repository;
    private final RunEventLiveBus runEventLiveBus;
    private final RunRuntimeStore runRuntimeStore;
    private final Duration refreshInterval;

    public SessionRuntimeStateApplicationService(
            SessionRuntimeStateRepository repository,
            RunEventLiveBus runEventLiveBus) {
        this(repository, runEventLiveBus, null, DEFAULT_REFRESH_INTERVAL);
    }

    @Autowired
    public SessionRuntimeStateApplicationService(
            SessionRuntimeStateRepository repository,
            RunEventLiveBus runEventLiveBus,
            RunRuntimeStore runRuntimeStore) {
        this(repository, runEventLiveBus, runRuntimeStore, DEFAULT_REFRESH_INTERVAL);
    }

    /**
     * 允许测试传入较长刷新间隔，避免兜底轮询干扰事件触发断言。
     */
    public SessionRuntimeStateApplicationService(
            SessionRuntimeStateRepository repository,
            RunEventLiveBus runEventLiveBus,
            Duration refreshInterval) {
        this(repository, runEventLiveBus, null, refreshInterval);
    }

    public SessionRuntimeStateApplicationService(
            SessionRuntimeStateRepository repository,
            RunEventLiveBus runEventLiveBus,
            RunRuntimeStore runRuntimeStore,
            Duration refreshInterval) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.runEventLiveBus = Objects.requireNonNull(runEventLiveBus, "runEventLiveBus must not be null");
        this.runRuntimeStore = runRuntimeStore;
        this.refreshInterval = Objects.requireNonNull(refreshInterval, "refreshInterval must not be null");
    }

    /**
     * 查询当前用户运行态快照；仓储是阻塞式实现，调用方在 Reactor 边界需自行 offload。
     */
    public SessionRuntimeStateSummary snapshot(UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (runRuntimeStore != null && runRuntimeStore.hasUserRuntimeState(userId)) {
            return redisSnapshot(runRuntimeStore.findActiveByUser(userId));
        }
        return repository.findUserRuntimeState(userId);
    }

    private SessionRuntimeStateSummary redisSnapshot(java.util.List<RunRuntimeManifest> manifests) {
        java.util.List<SessionRuntimeState> sessions = manifests.stream()
                .filter(RunRuntimeManifest::active)
                .sorted(java.util.Comparator.comparing(RunRuntimeManifest::updatedAt).reversed())
                .map(manifest -> new SessionRuntimeState(
                        manifest.sessionId(),
                        manifest.runId(),
                        manifest.status(),
                        attention(manifest.attention()),
                        manifest.attentionEventId(),
                        manifest.attentionAt(),
                        manifest.updatedAt()))
                .toList();
        int questionCount = (int) sessions.stream()
                .filter(state -> state.attention() == SessionRuntimeAttention.QUESTION)
                .count();
        int permissionCount = (int) sessions.stream()
                .filter(state -> state.attention() == SessionRuntimeAttention.PERMISSION)
                .count();
        java.time.Instant generatedAt = manifests.stream()
                .map(RunRuntimeManifest::updatedAt)
                .max(java.time.Instant::compareTo)
                .orElseGet(java.time.Instant::now);
        return new SessionRuntimeStateSummary(sessions.size(), questionCount, permissionCount, sessions, generatedAt);
    }

    /**
     * Redis manifest 可能来自旧版本或空运行态；未知 attention 保持为空，避免影响历史会话摘要。
     */
    private SessionRuntimeAttention attention(String value) {
        if ("QUESTION".equalsIgnoreCase(value)) {
            return SessionRuntimeAttention.QUESTION;
        }
        if ("PERMISSION".equalsIgnoreCase(value)) {
            return SessionRuntimeAttention.PERMISSION;
        }
        return null;
    }

    /**
     * 返回当前用户运行态 SSE 数据流：首帧立即输出，后续由 run、question、permission 事件和低频轮询刷新。
     */
    public Flux<SessionRuntimeStateSummary> stream(UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Flux<Object> initialTrigger = Flux.just("initial");
        Flux<Object> liveTriggers = runEventLiveBus.streamAll()
                .filter(this::shouldRefresh)
                .map(ignored -> "event");
        Flux<Object> intervalTriggers = Flux.interval(refreshInterval)
                .map(ignored -> "interval");
        return Flux.merge(initialTrigger, liveTriggers, intervalTriggers)
                .concatMap(ignored -> Mono.fromCallable(() -> snapshot(userId))
                        .subscribeOn(Schedulers.boundedElastic()))
                .distinctUntilChanged(summary -> signature(summary));
    }

    private boolean shouldRefresh(RunEventLiveEvent event) {
        return event != null
                && event.payload() != null
                && REFRESH_EVENT_TYPES.contains(event.payload().type());
    }

    private String signature(SessionRuntimeStateSummary summary) {
        StringBuilder builder = new StringBuilder()
                .append(summary.runningCount())
                .append('|')
                .append(summary.questionCount())
                .append('|')
                .append(summary.permissionCount());
        for (SessionRuntimeState state : summary.sessions()) {
            builder.append('|')
                    .append(state.sessionId().value())
                    .append(',')
                    .append(state.runId().value())
                    .append(',')
                    .append(state.runStatus())
                    .append(',')
                    .append(state.attention())
                    .append(',')
                    .append(state.attentionEventId())
                    .append(',')
                    .append(state.attentionAt())
                    .append(',')
                    .append(state.updatedAt());
        }
        return builder.toString();
    }
}
