package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunOwnerLease;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** 本机 owner lease 续租器；Redis 仍是唯一所有权事实源，本机 Map 只承载订阅停止信号。 */
@Service
public class RunOwnerLeaseSupervisor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunOwnerLeaseSupervisor.class);

    private final RunRuntimeStore runtimeStore;
    private final Clock clock;
    private final ConcurrentMap<RunId, OwnershipHandle> owned = new ConcurrentHashMap<>();

    public RunOwnerLeaseSupervisor(RunRuntimeStore runtimeStore, Clock clock) {
        this.runtimeStore = Objects.requireNonNull(runtimeStore, "runtimeStore must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public Optional<OwnershipHandle> adopt(RunOwnerLease lease) {
        OwnershipHandle current = owned.get(lease.runId());
        if (current != null && sameFence(current.lease(), lease)) {
            Optional<RunOwnerLease> renewed;
            try {
                renewed = runtimeStore.renewOwnerLease(lease);
            } catch (RuntimeException failure) {
                // 并发恢复路径复用同一 handle 时，续租异常也必须唤醒已经运行的事件订阅。
                markLost(current, failure);
                throw failure;
            }
            if (renewed.isEmpty()) {
                markLost(current);
                return Optional.empty();
            }
            current.update(renewed.orElseThrow());
            return Optional.of(current);
        }
        Optional<RunOwnerLease> renewed = runtimeStore.renewOwnerLease(lease);
        if (renewed.isEmpty()) {
            return Optional.empty();
        }
        OwnershipHandle handle = new OwnershipHandle(renewed.orElseThrow());
        OwnershipHandle previous = owned.put(lease.runId(), handle);
        if (previous != null) {
            previous.markLost();
        }
        return Optional.of(handle);
    }

    @Scheduled(fixedDelay = 5_000L, scheduler = RunRuntimeSchedulingConfiguration.OWNER_LEASE_SCHEDULER)
    void renewOwnedLeases() {
        for (OwnershipHandle handle : new ArrayList<>(owned.values())) {
            try {
                Optional<RunOwnerLease> renewed = runtimeStore.renewOwnerLease(handle.lease());
                if (renewed.isPresent()) {
                    handle.update(renewed.orElseThrow());
                } else {
                    markLost(handle);
                }
            } catch (RuntimeException exception) {
                markLost(handle, exception);
                LOGGER.warn(
                        "Run owner lease 续租异常，旧订阅将进入运行态丢失收敛，runId={}, errorType={}",
                        handle.lease().runId().value(),
                        exception.getClass().getSimpleName());
            }
        }
    }

    public void requireOwned(OwnershipHandle handle) {
        RunOwnerLease lease = handle.lease();
        if (owned.get(lease.runId()) != handle || !lease.expiresAt().isAfter(clock.instant())) {
            throw new RunOwnershipLostException("Run owner lease 已失效");
        }
    }

    /** 返回本机是否仍持有未到期 handle；仅用于避免周期恢复重复订阅当前执行者。 */
    public boolean isOwned(RunId runId) {
        OwnershipHandle handle = owned.get(runId);
        return handle != null && handle.lease().expiresAt().isAfter(clock.instant());
    }

    public void release(OwnershipHandle handle) {
        RunOwnerLease lease = handle.lease();
        if (!owned.remove(lease.runId(), handle)) {
            handle.markLost();
            return;
        }
        try {
            runtimeStore.releaseOwnerLease(lease);
        } finally {
            handle.markLost();
        }
    }

    private boolean sameFence(RunOwnerLease left, RunOwnerLease right) {
        return left.fencingToken() == right.fencingToken()
                && left.ownerBackendProcessId().equals(right.ownerBackendProcessId());
    }

    private void markLost(OwnershipHandle handle) {
        if (owned.remove(handle.lease().runId(), handle)) {
            handle.markLost();
        }
    }

    /**
     * 续租异常与 fencing 被其它 owner 夺走不是同一种终止：前者必须把原异常送入订阅错误链，
     * 由调用方启动 30 秒运行态丢失收敛；后者仍使用正常完成，避免旧 owner 误取消新 owner 的 Run。
     */
    private void markLost(OwnershipHandle handle, RuntimeException failure) {
        if (owned.remove(handle.lease().runId(), handle)) {
            handle.markLost(failure);
        }
    }

    /** 单个恢复订阅持有的可变租约句柄。 */
    public static final class OwnershipHandle {
        private volatile RunOwnerLease lease;
        private final Sinks.Empty<Void> lost = Sinks.empty();

        private OwnershipHandle(RunOwnerLease lease) {
            this.lease = lease;
        }

        public RunOwnerLease lease() {
            return lease;
        }

        public Mono<Void> lost() {
            return lost.asMono();
        }

        private void update(RunOwnerLease renewed) {
            this.lease = renewed;
        }

        private void markLost() {
            lost.tryEmitEmpty();
        }

        private void markLost(RuntimeException failure) {
            lost.tryEmitError(failure);
        }
    }
}
