package com.enterprise.testagent.agent.runtime;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * AgentRuntime 注册表，负责规范化 agentId、查找实现并套用统一观测包装。
 */
@Service
public class AgentRuntimeRegistry {

    public static final String DEFAULT_AGENT_ID = "opencode";

    private final Map<String, AgentRuntime> runtimes;

    /**
     * 创建生产注册表，并用可选 MeterRegistry 为每个运行时增加统一指标。
     */
    @Autowired
    public AgentRuntimeRegistry(List<AgentRuntime> runtimes, ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(runtimes, meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable());
    }

    /**
     * 创建测试注册表，不依赖 Spring ObjectProvider。
     */
    public AgentRuntimeRegistry(List<AgentRuntime> runtimes) {
        this(runtimes, (MeterRegistry) null);
    }

    private AgentRuntimeRegistry(List<AgentRuntime> runtimes, MeterRegistry meterRegistry) {
        Objects.requireNonNull(runtimes, "runtimes must not be null");
        Map<String, AgentRuntime> mapped = new LinkedHashMap<>();
        for (AgentRuntime runtime : runtimes) {
            String agentId = normalize(runtime.agentId());
            mapped.put(agentId, new ObservedAgentRuntime(runtime, meterRegistry));
        }
        this.runtimes = Map.copyOf(mapped);
    }

    /**
     * 返回默认 agent 标志；旧平台 URL 会复用该默认值。
     */
    public String defaultAgentId() {
        return DEFAULT_AGENT_ID;
    }

    /**
     * 根据 URL 中的 agentId 查找运行时，未知 agent 返回统一 NOT_FOUND。
     */
    public AgentRuntime require(String agentId) {
        String normalized = normalize(agentId);
        AgentRuntime runtime = runtimes.get(normalized);
        if (runtime == null) {
            throw new PlatformException(
                    ErrorCode.NOT_FOUND,
                    "Agent 不存在",
                    Map.of("agentId", normalized));
        }
        return runtime;
    }

    /**
     * 规范化 URL agent 标志，空值回退到默认 opencode。
     */
    public String normalize(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return DEFAULT_AGENT_ID;
        }
        return agentId.trim().toLowerCase(Locale.ROOT);
    }
}
