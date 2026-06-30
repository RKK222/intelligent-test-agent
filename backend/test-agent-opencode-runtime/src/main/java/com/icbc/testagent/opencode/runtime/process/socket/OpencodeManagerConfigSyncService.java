package com.icbc.testagent.opencode.runtime.process.socket;

import com.icbc.testagent.domain.configuration.CommonParameterReloadedEvent;
import com.icbc.testagent.domain.configuration.CommonParameterValues;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.observability.TraceIdSupport;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 把通用参数表中的 opencode manager 运行配置下发给已连接的 manager。
 *
 * <p>manager 发送 configRequest 时下发完整权威值；
 * 后续只允许最大进程数运行时刷新，经 {@link ManagerConnectionRegistry#broadcast}
 * 推给当前实例持有的本服务器 manager 连接。路径类参数属于部署参数，不做热刷新。
 *
 * <p>任一必需参数缺失或非法时不会下发 configUpdate，manager 必须保持未 ready 并拒绝启动用户进程。
 */
@Service
public class OpencodeManagerConfigSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpencodeManagerConfigSyncService.class);

    /** 通用参数英文名：opencode manager 最大并发进程数，全局 platform=all。 */
    public static final String MAX_PROCESSES_PARAM_ENGLISH = "OPENCODE_MANAGER_MAX_PROCESSES";
    /** 通用参数英文名：opencode server XDG_DATA_HOME 根目录，按当前平台读取。 */
    public static final String SESSION_DIR_PARAM_ENGLISH = "OPENCODE_SESSION_DIR";
    /** 通用参数英文名：opencode 公共配置目录，按当前平台读取。 */
    public static final String PUBLIC_CONFIG_DIR_PARAM_ENGLISH = "OPENCODE_PUBLIC_CONFIG_DIR";

    private final CommonParameterValues commonParameterValues;
    private final ManagerConnectionRegistry connections;

    public OpencodeManagerConfigSyncService(
            CommonParameterValues commonParameterValues, ManagerConnectionRegistry connections) {
        this.commonParameterValues = Objects.requireNonNull(
                commonParameterValues, "commonParameterValues must not be null");
        this.connections = Objects.requireNonNull(connections, "connections must not be null");
    }

    /**
     * 读取通用参数表中配置的最大进程数；缺失或非数字返回 empty，调用方应跳过下发。
     */
    public OptionalInt currentConfiguredMax() {
        return readMaxProcesses().map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    /**
     * 构造当前完整 configUpdate；必需公共参数缺失或非法时返回 empty。
     */
    public Optional<ManagerControlMessage> configUpdateMessage(String traceId) {
        return currentRuntimeConfig().map(config -> ManagerControlMessage.configUpdate(
                config.maxProcesses(), config.sessionRoot(), config.configDir(), traceId));
    }

    /**
     * 向单个容器下发当前最大进程数；无配置值时发送安全错误，避免 manager 沿用过期并发限制。
     */
    public void pushCurrentMaxTo(OpencodeContainerId containerId) {
        Objects.requireNonNull(containerId, "containerId must not be null");
        String traceId = TraceIdSupport.generate();
        try {
            ManagerControlMessage message = maxProcessesUpdateMessage(traceId)
                    .orElseGet(() -> ManagerControlMessage.error(
                            "OPENCODE_UNAVAILABLE", "manager 最大进程数参数未配置", traceId));
            connections.send(containerId, message);
            LOGGER.info("已向 manager 下发最大进程数配置 containerId={} type={} traceId={}",
                    containerId, message.type(), traceId);
        } catch (RuntimeException exception) {
            // 连接刚断开等情况下发送失败不应影响注册主流程。
            LOGGER.warn("下发 manager 最大进程数配置失败 containerId={} traceId={}", containerId, traceId, exception);
        }
    }

    /**
     * 向当前实例持有的所有 manager 连接广播最大进程数；无配置值时跳过。
     */
    public int broadcastCurrentMax() {
        String traceId = TraceIdSupport.generate();
        Optional<ManagerControlMessage> message = maxProcessesUpdateMessage(traceId);
        if (message.isEmpty()) {
            LOGGER.warn("跳过广播 manager 最大进程数配置：通用参数缺失或非法 traceId={}", traceId);
            return 0;
        }
        int sent = connections.broadcast(message.get());
        LOGGER.info("已广播 manager 最大进程数配置 maxProcesses={} sent={} traceId={}",
                message.get().maxProcesses(), sent, traceId);
        return sent;
    }

    /**
     * 通用参数更新事件触发；仅最大进程数变化时广播给本实例持有的 manager。
     * 监听方收到事件后直接从数据库读取最新值，确保每台实例都向本服务器 manager 下发最新并发限制。
     */
    @EventListener
    public void onCommonParameterReloaded(CommonParameterReloadedEvent event) {
        if (!MAX_PROCESSES_PARAM_ENGLISH.equals(event.englishName())) {
            return;
        }
        LOGGER.info("收到 manager 最大进程数更新事件，开始广播 englishName={} traceId={}", event.englishName(), event.traceId());
        broadcastCurrentMax();
    }

    private Optional<ManagerRuntimeConfig> currentRuntimeConfig() {
        Optional<Integer> maxProcesses = readMaxProcesses();
        Optional<String> sessionRoot = readPathParameter(SESSION_DIR_PARAM_ENGLISH);
        Optional<String> configDir = readPathParameter(PUBLIC_CONFIG_DIR_PARAM_ENGLISH);
        if (maxProcesses.isEmpty() || sessionRoot.isEmpty() || configDir.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ManagerRuntimeConfig(maxProcesses.get(), sessionRoot.get(), configDir.get()));
    }

    private Optional<Integer> readMaxProcesses() {
        return commonParameterValues.resolvedValue(MAX_PROCESSES_PARAM_ENGLISH, ParameterPlatform.ALL)
                .flatMap(OpencodeManagerConfigSyncService::tryParsePositiveInt);
    }

    private Optional<ManagerControlMessage> maxProcessesUpdateMessage(String traceId) {
        return readMaxProcesses()
                .map(maxProcesses -> ManagerControlMessage.configUpdate(maxProcesses, null, null, traceId));
    }

    private Optional<String> readPathParameter(String englishName) {
        return commonParameterValues.resolvedValue(englishName)
                .map(String::trim)
                .filter(value -> !value.isBlank());
    }

    private static Optional<Integer> tryParsePositiveInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private record ManagerRuntimeConfig(int maxProcesses, String sessionRoot, String configDir) {
    }
}
