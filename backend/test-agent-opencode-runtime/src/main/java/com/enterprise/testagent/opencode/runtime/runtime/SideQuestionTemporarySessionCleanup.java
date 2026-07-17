package com.enterprise.testagent.opencode.runtime.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一次旁路问答执行的临时 Session 清理门闩；无论多少条收尾路径触发，远端 DELETE 最多执行一次。
 */
final class SideQuestionTemporarySessionCleanup implements Runnable {

    private final Runnable deleteAction;
    private final AtomicBoolean started = new AtomicBoolean();

    SideQuestionTemporarySessionCleanup(Runnable deleteAction) {
        this.deleteAction = Objects.requireNonNull(deleteAction, "deleteAction must not be null");
    }

    @Override
    public void run() {
        if (started.compareAndSet(false, true)) {
            deleteAction.run();
        }
    }
}
