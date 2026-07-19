package com.enterprise.testagent.configuration.management;

import com.enterprise.testagent.configuration.management.CommonParameterMemoryResponses.ParameterResponse;
import com.enterprise.testagent.configuration.management.CommonParameterMemoryResponses.ProcessResponse;
import com.enterprise.testagent.configuration.management.CommonParameterMemoryResponses.ProcessStatus;
import com.enterprise.testagent.domain.configuration.CommonParameterMemoryRefreshStatus;
import com.enterprise.testagent.domain.configuration.CommonParameterMemoryState;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 本 Java 进程的内存通用参数查询与手工刷新应用服务。 */
@Service
public class CommonParameterMemoryApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommonParameterMemoryApplicationService.class);

    private final CommonParameterMemoryRegistry registry;
    private final BackendInstanceIdentity identity;
    private final Clock clock;

    /** 生产构造器使用系统 UTC 时钟生成采集时间。 */
    @Autowired
    public CommonParameterMemoryApplicationService(
            CommonParameterMemoryRegistry registry,
            BackendInstanceIdentity identity) {
        this(registry, identity, Clock.systemUTC());
    }

    /** 测试构造器允许固定时钟。 */
    CommonParameterMemoryApplicationService(
            CommonParameterMemoryRegistry registry,
            BackendInstanceIdentity identity,
            Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** 返回当前进程实际内存状态，不触发数据库读取。 */
    public ProcessResponse current() {
        return response(registry.snapshots(), Instant.now(clock));
    }

    /** 按数据库当前值刷新本进程全部注册项并返回逐条结果。 */
    public ProcessResponse refresh(String traceId) {
        List<CommonParameterMemoryState> states = registry.refreshAll(traceId);
        ProcessResponse response = response(states, Instant.now(clock));
        LOGGER.info(
                "本机 JVM 内存通用参数手工刷新结束 traceId={} backendProcessId={} status={}",
                traceId,
                identity.backendProcessId(),
                response.status());
        return response;
    }

    private ProcessResponse response(List<CommonParameterMemoryState> states, Instant capturedAt) {
        List<ParameterResponse> parameters = states.stream()
                .map(CommonParameterMemoryApplicationService::parameterResponse)
                .toList();
        int failed = (int) states.stream()
                .filter(state -> state.refreshStatus() == CommonParameterMemoryRefreshStatus.FAILED)
                .count();
        ProcessStatus status;
        String errorCode = null;
        String errorMessage = null;
        if (failed == 0) {
            status = ProcessStatus.SUCCESS;
        } else if (failed == states.size()) {
            status = ProcessStatus.FAILED;
            errorCode = "REFRESH_FAILED";
            errorMessage = failed + " 个内存参数最近刷新失败，继续使用上一有效值";
        } else {
            status = ProcessStatus.PARTIAL;
            errorCode = "REFRESH_PARTIAL";
            errorMessage = failed + " 个内存参数最近刷新失败，继续使用上一有效值";
        }
        return new ProcessResponse(
                identity.backendProcessId(),
                identity.linuxServerId(),
                identity.listenUrl(),
                identity.instanceId(),
                capturedAt,
                status,
                errorCode,
                errorMessage,
                parameters);
    }

    private static ParameterResponse parameterResponse(CommonParameterMemoryState state) {
        return new ParameterResponse(
                state.key().englishName(),
                state.key().platform().value(),
                state.sourceValue(),
                state.memoryValue(),
                state.loadedAt(),
                state.lastRefreshAttemptAt(),
                state.refreshStatus().name(),
                state.errorMessage());
    }
}
