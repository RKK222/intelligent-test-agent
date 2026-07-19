package com.enterprise.testagent.configuration.management;

import com.enterprise.testagent.domain.configuration.CommonParameterMemoryEntry;
import com.enterprise.testagent.domain.configuration.CommonParameterMemoryKey;
import com.enterprise.testagent.domain.configuration.CommonParameterMemoryLoadException;
import com.enterprise.testagent.domain.configuration.CommonParameterMemoryLoadedValue;
import com.enterprise.testagent.domain.configuration.CommonParameterMemoryRefreshStatus;
import com.enterprise.testagent.domain.configuration.CommonParameterMemoryState;
import com.enterprise.testagent.domain.configuration.CommonParameterReloadedEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 本 Java 进程的显式内存通用参数注册表，统一负责启动加载、事件刷新、手工刷新和诊断状态。
 */
@Service
public class CommonParameterMemoryRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommonParameterMemoryRegistry.class);

    private final List<CommonParameterMemoryEntry> orderedEntries;
    private final Clock clock;
    private volatile Map<CommonParameterMemoryKey, CommonParameterMemoryState> states;

    /** Spring 装配所有显式注册项；没有注册项时允许以空注册表启动。 */
    @Autowired
    public CommonParameterMemoryRegistry(List<CommonParameterMemoryEntry> entries) {
        this(entries, Clock.systemUTC());
    }

    /** 测试构造器允许固定时钟。 */
    CommonParameterMemoryRegistry(List<CommonParameterMemoryEntry> entries, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        List<CommonParameterMemoryEntry> sorted = new ArrayList<>(
                entries == null ? List.of() : entries);
        sorted.sort(Comparator
                .comparing((CommonParameterMemoryEntry entry) -> entry.key().englishName())
                .thenComparing(entry -> entry.key().platform().value()));
        Map<CommonParameterMemoryKey, CommonParameterMemoryEntry> resolvedEntries = new LinkedHashMap<>();
        List<CommonParameterMemoryEntry> resolvedOrder = new ArrayList<>();
        Map<CommonParameterMemoryKey, CommonParameterMemoryState> initialStates = new LinkedHashMap<>();
        for (CommonParameterMemoryEntry entry : sorted) {
            CommonParameterMemoryEntry nonNullEntry = Objects.requireNonNull(entry, "entry must not be null");
            CommonParameterMemoryKey key = Objects.requireNonNull(nonNullEntry.key(), "entry key must not be null");
            if (resolvedEntries.putIfAbsent(key, nonNullEntry) != null) {
                throw new IllegalStateException(
                        "JVM 内存通用参数重复注册: " + key.englishName() + "/" + key.platform().value());
            }
            resolvedOrder.add(nonNullEntry);
            initialStates.put(key, CommonParameterMemoryState.unloaded(key));
        }
        this.orderedEntries = List.copyOf(resolvedOrder);
        this.states = Map.copyOf(initialStates);
    }

    /** 应用 ready 前严格加载全部注册项；任一失败都阻止实例进入可用状态。 */
    public void loadOnStartup() {
        List<CommonParameterMemoryState> refreshed = refreshEntries(orderedEntries, "startup");
        List<String> failures = refreshed.stream()
                .filter(state -> state.refreshStatus() == CommonParameterMemoryRefreshStatus.FAILED)
                .map(state -> state.key().englishName() + "/" + state.key().platform().value())
                .toList();
        if (!failures.isEmpty()) {
            throw new IllegalStateException("JVM 内存通用参数启动加载失败: " + String.join(",", failures));
        }
    }

    /** 返回按英文名、平台稳定排序的当前诊断状态。 */
    public List<CommonParameterMemoryState> snapshots() {
        return states.values().stream()
                .sorted(Comparator
                        .comparing((CommonParameterMemoryState state) -> state.key().englishName())
                        .thenComparing(state -> state.key().platform().value()))
                .toList();
    }

    /** 手工按数据库当前值刷新本机全部注册项；单项失败不阻断其它项。 */
    public List<CommonParameterMemoryState> refreshAll(String traceId) {
        return refreshEntries(orderedEntries, safeTraceId(traceId));
    }

    /** 数据库参数更新广播到达后，仅刷新匹配项；空英文名兼容批量刷新。 */
    @EventListener
    public void onCommonParameterReloaded(CommonParameterReloadedEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        List<CommonParameterMemoryEntry> matched = orderedEntries.stream()
                .filter(entry -> matches(entry.key(), event))
                .toList();
        if (!matched.isEmpty()) {
            refreshEntries(matched, safeTraceId(event.traceId()));
        }
    }

    /**
     * 串行更新本机少量注册项并整体替换状态 Map，避免查询观察到一条状态的中间字段。
     * 业务内存的原子替换由每个 SPI 实现自行保证。
     */
    private synchronized List<CommonParameterMemoryState> refreshEntries(
            Iterable<CommonParameterMemoryEntry> selected,
            String traceId) {
        Map<CommonParameterMemoryKey, CommonParameterMemoryState> next = new LinkedHashMap<>(states);
        for (CommonParameterMemoryEntry entry : selected) {
            CommonParameterMemoryKey key = entry.key();
            Instant attemptedAt = Instant.now(clock);
            CommonParameterMemoryState previous = next.getOrDefault(key, CommonParameterMemoryState.unloaded(key));
            try {
                CommonParameterMemoryLoadedValue loaded = Objects.requireNonNull(
                        entry.reloadFromDatabase(), "reload result must not be null");
                next.put(key, new CommonParameterMemoryState(
                        key,
                        loaded.sourceValue(),
                        loaded.memoryValue(),
                        attemptedAt,
                        attemptedAt,
                        CommonParameterMemoryRefreshStatus.SUCCESS,
                        null));
                LOGGER.info(
                        "JVM 内存通用参数已刷新 traceId={} englishName={} platform={}",
                        traceId,
                        key.englishName(),
                        key.platform().value());
            } catch (CommonParameterMemoryLoadException exception) {
                next.put(key, failed(previous, attemptedAt, exception.getMessage()));
                LOGGER.warn(
                        "JVM 内存通用参数刷新失败 traceId={} englishName={} platform={} reason={}",
                        traceId,
                        key.englishName(),
                        key.platform().value(),
                        exception.getMessage());
            } catch (RuntimeException exception) {
                next.put(key, failed(previous, attemptedAt, "读取或应用失败"));
                LOGGER.warn(
                        "JVM 内存通用参数刷新异常 traceId={} englishName={} platform={} exceptionType={}",
                        traceId,
                        key.englishName(),
                        key.platform().value(),
                        exception.getClass().getSimpleName());
            }
        }
        this.states = Map.copyOf(next);
        return snapshots();
    }

    private static CommonParameterMemoryState failed(
            CommonParameterMemoryState previous,
            Instant attemptedAt,
            String safeMessage) {
        return new CommonParameterMemoryState(
                previous.key(),
                previous.sourceValue(),
                previous.memoryValue(),
                previous.loadedAt(),
                attemptedAt,
                CommonParameterMemoryRefreshStatus.FAILED,
                safeMessage);
    }

    private static boolean matches(CommonParameterMemoryKey key, CommonParameterReloadedEvent event) {
        if (event.englishName() == null || event.englishName().isBlank()) {
            return true;
        }
        return key.englishName().equals(event.englishName())
                && (event.platform() == null || key.platform() == event.platform());
    }

    private static String safeTraceId(String traceId) {
        return traceId == null || traceId.isBlank() ? "trace_common_parameter_memory" : traceId;
    }
}
