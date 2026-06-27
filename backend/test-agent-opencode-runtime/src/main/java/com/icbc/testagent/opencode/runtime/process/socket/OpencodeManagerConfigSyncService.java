package com.icbc.testagent.opencode.runtime.process.socket;

import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.CommonParameterRepository;
import com.icbc.testagent.domain.configuration.CommonParameterUpdatedEvent;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.observability.TraceIdSupport;
import java.util.Objects;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 把通用参数表中的 opencode manager 最大进程数下发给已连接的 manager。
 *
 * <p>manager 注册时调用 {@link #pushCurrentMaxTo(OpencodeContainerId)} 立即下发权威值；
 * 前端修改参数后，{@link CommonParameterUpdatedEvent} 在事务提交后触发 {@link #broadcastCurrentMax()},
 * 经 {@link ManagerConnectionRegistry#broadcast} 推给当前实例持有的所有 manager 连接。
 * 全互联拓扑下每台 Java 实例各自广播即可触达全部 manager。
 *
 * <p>参数缺失或非数字时静默跳过，manager 继续使用 env 兜底值，不阻断主流程。
 */
@Service
public class OpencodeManagerConfigSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpencodeManagerConfigSyncService.class);

    /** 通用参数英文名：opencode manager 最大并发进程数，全局 platform=all。 */
    public static final String MAX_PROCESSES_PARAM_ENGLISH = "OPENCODE_MANAGER_MAX_PROCESSES";

    private final CommonParameterRepository commonParameterRepository;
    private final ManagerConnectionRegistry connections;

    public OpencodeManagerConfigSyncService(
            CommonParameterRepository commonParameterRepository, ManagerConnectionRegistry connections) {
        this.commonParameterRepository = Objects.requireNonNull(
                commonParameterRepository, "commonParameterRepository must not be null");
        this.connections = Objects.requireNonNull(connections, "connections must not be null");
    }

    /**
     * 读取通用参数表中配置的最大进程数；缺失或非数字返回 empty，调用方应跳过下发。
     */
    public OptionalInt currentConfiguredMax() {
        return commonParameterRepository
                .findByEnglishNameAndPlatform(MAX_PROCESSES_PARAM_ENGLISH, ParameterPlatform.ALL)
                .map(CommonParameter::parameterValue)
                .flatMap(OpencodeManagerConfigSyncService::tryParseInt)
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty);
    }

    /**
     * 向单个容器下发当前配置的最大进程数；无配置值时跳过。
     */
    public void pushCurrentMaxTo(OpencodeContainerId containerId) {
        Objects.requireNonNull(containerId, "containerId must not be null");
        OptionalInt configured = currentConfiguredMax();
        if (configured.isEmpty()) {
            LOGGER.debug("跳过下发最大进程数：通用参数缺失或非数字 containerId={}", containerId);
            return;
        }
        String traceId = TraceIdSupport.generate();
        try {
            connections.send(containerId, ManagerControlMessage.configUpdate(configured.getAsInt(), traceId));
            LOGGER.info("已向 manager 下发最大进程数 containerId={} maxProcesses={} traceId={}",
                    containerId, configured.getAsInt(), traceId);
        } catch (RuntimeException exception) {
            // 连接刚断开等情况下发送失败不应影响注册主流程。
            LOGGER.warn("下发最大进程数失败 containerId={} traceId={}", containerId, traceId, exception);
        }
    }

    /**
     * 向当前实例持有的所有 manager 连接广播最大进程数；无配置值时跳过。
     */
    public int broadcastCurrentMax() {
        OptionalInt configured = currentConfiguredMax();
        if (configured.isEmpty()) {
            LOGGER.debug("跳过广播最大进程数：通用参数缺失或非数字");
            return 0;
        }
        String traceId = TraceIdSupport.generate();
        int sent = connections.broadcast(ManagerControlMessage.configUpdate(configured.getAsInt(), traceId));
        LOGGER.info("已广播最大进程数 maxProcesses={} sent={} traceId={}", configured.getAsInt(), sent, traceId);
        return sent;
    }

    /**
     * 通用参数更新事件触发；仅最大进程数参数变化时广播给所有 manager。
     * updateValue 当前无事务包裹、单条 UPDATE 自动提交，事件在 DB 写入成功后同步发布，
     * 因此此处直接广播即可；若将来为 updateValue 引入事务，应改为 @TransactionalEventListener(AFTER_COMMIT)。
     */
    @EventListener
    public void onCommonParameterUpdated(CommonParameterUpdatedEvent event) {
        if (!MAX_PROCESSES_PARAM_ENGLISH.equals(event.englishName())) {
            return;
        }
        LOGGER.info("收到最大进程数参数更新事件，开始广播 newValue={} traceId={}", event.newValue(), event.traceId());
        broadcastCurrentMax();
    }

    private static java.util.Optional<Integer> tryParseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException exception) {
            return java.util.Optional.empty();
        }
    }
}
