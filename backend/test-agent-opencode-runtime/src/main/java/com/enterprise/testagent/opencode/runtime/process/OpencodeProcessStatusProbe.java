package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * opencode server 状态查询结果，隔离查询语义、管理页展示状态和可选平台错误。
 */
public record OpencodeProcessStatusProbe(
        OpencodeProcessProbeStatus status,
        Optional<OpencodeServerProcess> process,
        String managerStatus,
        String healthStatus,
        String message,
        Instant checkedAt,
        boolean restartable,
        ErrorCode errorCode) {

    /**
     * 归一 Optional 和空白展示字段，避免调用方重复兜底。
     */
    public OpencodeProcessStatusProbe {
        Objects.requireNonNull(status, "status must not be null");
        process = process == null ? Optional.empty() : process;
        // STALE 状态使用特殊默认值，表示"状态暂无法确认"
        if (status == OpencodeProcessProbeStatus.STALE) {
            managerStatus = blankToDefault(managerStatus, "STALE");
            healthStatus = blankToDefault(healthStatus, "STALE");
        } else {
            managerStatus = blankToDefault(managerStatus, status == OpencodeProcessProbeStatus.RUNNING ? "RUNNING" : "NOT_RUNNING");
            healthStatus = blankToDefault(healthStatus, status == OpencodeProcessProbeStatus.RUNNING ? "HEALTHY" : managerStatus);
        }
        message = blankToDefault(message, "");
        Objects.requireNonNull(checkedAt, "checkedAt must not be null");
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
