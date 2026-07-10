package com.icbc.testagent.event;

import com.icbc.testagent.domain.event.RunEvent;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventRepository;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.domain.run.RunStorageMode;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * RunEvent 追加服务，负责把待追加事件交给持久化端口分配 eventId 和 seq。
 */
@Service
public class RunEventAppender {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunEventAppender.class);

    private final RunEventRepository runEventRepository;
    private final RunEventLiveBus liveBus;
    private final RunRuntimeStore runRuntimeStore;

    /**
     * 构造仅持久化的追加服务，适用于不需要实时推送的测试或降级场景。
     */
    public RunEventAppender(RunEventRepository runEventRepository) {
        this(runEventRepository, null, null);
    }

    /**
     * 构造追加服务；liveBus 可为空，为空时只写入 durable Repository，不做进程内实时广播。
     */
    public RunEventAppender(RunEventRepository runEventRepository, RunEventLiveBus liveBus) {
        this(runEventRepository, liveBus, null);
    }

    /** 生产构造器显式注入 Redis 运行态端口；event 模块不依赖具体 persistence 实现。 */
    @Autowired
    public RunEventAppender(
            RunEventRepository runEventRepository,
            RunEventLiveBus liveBus,
            RunRuntimeStore runRuntimeStore) {
        this.runEventRepository = Objects.requireNonNull(runEventRepository, "runEventRepository must not be null");
        this.liveBus = liveBus;
        this.runRuntimeStore = runRuntimeStore;
    }

    /**
     * 追加一条 durable RunEvent，持久化层负责分配 eventId/seq，成功后再发布到实时通道。
     */
    public RunEvent append(RunEventDraft draft) {
        Objects.requireNonNull(draft, "draft must not be null");
        return append(draft, storageMode(draft.runId()));
    }

    /** 按 Run 启动时已经固定的 storageMode 追加，避免每条事件再次探测 Redis 来猜测分流。 */
    public RunEvent append(RunEventDraft draft, RunStorageMode storageMode) {
        Objects.requireNonNull(draft, "draft must not be null");
        Objects.requireNonNull(storageMode, "storageMode must not be null");
        if (storageMode == RunStorageMode.REDIS_SUMMARY) {
            var result = requireRuntimeStore().appendDurable(draft);
            RunEvent event = result.event();
            if (result.visible() && liveBus != null) {
                liveBus.publishDurable(event);
            }
            return event;
        }
        RunEvent event = runEventRepository.append(draft);
        appendLegacyHotTail(draft);
        if (liveBus != null) {
            liveBus.publishDurable(event);
        }
        return event;
    }

    /**
     * 发布 transient 事件。新模式先更新 Redis 物化快照再进 live bus；legacy 无 manifest 时保持原行为。
     */
    public boolean publishTransient(RunEventDraft draft) {
        Objects.requireNonNull(draft, "draft must not be null");
        return publishTransient(draft, storageMode(draft.runId()));
    }

    /** 按固定 storageMode 投影 transient；新模式 Redis 失败必须向上返回 503，禁止写入数据库。 */
    public boolean publishTransient(RunEventDraft draft, RunStorageMode storageMode) {
        Objects.requireNonNull(draft, "draft must not be null");
        Objects.requireNonNull(storageMode, "storageMode must not be null");
        boolean visible = true;
        if (storageMode == RunStorageMode.REDIS_SUMMARY) {
            visible = requireRuntimeStore().projectTransient(draft);
        } else if (runRuntimeStore != null) {
            try {
                if (runRuntimeStore.findManifest(draft.runId()).isPresent()) {
                    visible = runRuntimeStore.projectTransient(draft);
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Legacy Run Redis hot snapshot unavailable, runId={}", draft.runId().value(), exception);
            }
        }
        if (visible && liveBus != null) {
            liveBus.publishTransient(draft);
            return true;
        }
        return false;
    }

    /** manifest 缺失表示旧数据，必须继续走 LEGACY_FULL。 */
    public RunStorageMode storageMode(RunId runId) {
        return runRuntimeStore == null ? RunStorageMode.LEGACY_FULL : runRuntimeStore.storageMode(runId);
    }

    private void appendLegacyHotTail(RunEventDraft draft) {
        if (runRuntimeStore == null) {
            return;
        }
        try {
            if (runRuntimeStore.findManifest(draft.runId()).isPresent()) {
                runRuntimeStore.appendDurable(draft);
            }
        } catch (RuntimeException exception) {
            // legacy DB 已是事实源，shadow Redis 失败不得改变既有请求结果。
            LOGGER.warn("Legacy Run Redis hot tail unavailable, runId={}", draft.runId().value(), exception);
        }
    }

    private RunRuntimeStore requireRuntimeStore() {
        if (runRuntimeStore == null) {
            throw new IllegalStateException("REDIS_SUMMARY requires RunRuntimeStore");
        }
        return runRuntimeStore;
    }
}
