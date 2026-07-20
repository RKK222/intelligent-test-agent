package com.enterprise.testagent.xxljob;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** 独立记录 XXL Admin 子上下文状态，不参与平台 readiness 判定。 */
@Component
public class XxlJobAdminState {

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(
            new Snapshot(false, "XXL-JOB Admin 尚未启动", null));

    public void up() {
        snapshot.set(new Snapshot(true, null, Instant.now()));
    }

    public void down(String safeReason) {
        snapshot.set(new Snapshot(false, safeReason, Instant.now()));
    }

    public boolean isUp() {
        return snapshot.get().up();
    }

    public String failureReason() {
        return snapshot.get().failureReason();
    }

    public Instant checkedAt() {
        return snapshot.get().checkedAt();
    }

    private record Snapshot(boolean up, String failureReason, Instant checkedAt) {
    }
}
