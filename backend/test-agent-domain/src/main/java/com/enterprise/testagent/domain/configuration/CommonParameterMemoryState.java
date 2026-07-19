package com.enterprise.testagent.domain.configuration;

import java.time.Instant;
import java.util.Objects;

/**
 * 单个 JVM 内存通用参数的诊断状态；失败刷新保留上次成功值与 loadedAt，仅更新尝试状态。
 */
public record CommonParameterMemoryState(
        CommonParameterMemoryKey key,
        String sourceValue,
        String memoryValue,
        Instant loadedAt,
        Instant lastRefreshAttemptAt,
        CommonParameterMemoryRefreshStatus refreshStatus,
        String errorMessage) {

    public CommonParameterMemoryState {
        key = Objects.requireNonNull(key, "key must not be null");
        refreshStatus = Objects.requireNonNull(refreshStatus, "refreshStatus must not be null");
        if (refreshStatus == CommonParameterMemoryRefreshStatus.SUCCESS) {
            Objects.requireNonNull(sourceValue, "sourceValue must not be null after successful refresh");
            Objects.requireNonNull(memoryValue, "memoryValue must not be null after successful refresh");
            Objects.requireNonNull(loadedAt, "loadedAt must not be null after successful refresh");
            Objects.requireNonNull(lastRefreshAttemptAt, "lastRefreshAttemptAt must not be null after successful refresh");
            errorMessage = null;
        }
    }

    /** 创建尚未执行启动加载的占位状态；该状态不会在应用 ready 后对外暴露。 */
    public static CommonParameterMemoryState unloaded(CommonParameterMemoryKey key) {
        return new CommonParameterMemoryState(
                key,
                null,
                null,
                null,
                null,
                CommonParameterMemoryRefreshStatus.FAILED,
                "尚未加载");
    }
}
