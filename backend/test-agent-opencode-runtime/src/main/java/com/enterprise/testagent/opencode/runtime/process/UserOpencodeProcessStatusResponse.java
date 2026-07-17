package com.enterprise.testagent.opencode.runtime.process;

import java.time.Instant;

/**
 * 当前用户 opencode 进程状态响应模型，API 层可直接转换为 HTTP DTO。
 */
public record UserOpencodeProcessStatusResponse(
        UserOpencodeProcessAvailability status,
        boolean initializable,
        String message,
        String processId,
        String linuxServerId,
        String containerId,
        Integer port,
        String baseUrl,
        Instant checkedAt,
        UserOpencodeServiceStatus serviceStatus,
        String serviceAddress,
        String backendJavaServerIp,
        boolean messageSendAllowed,
        String messageSendBlockedReason,
        String publicConfigRolloutId) {

    /** 兼容既有完整构造器；未传发布状态时默认允许发送。 */
    public UserOpencodeProcessStatusResponse(
            UserOpencodeProcessAvailability status,
            boolean initializable,
            String message,
            String processId,
            String linuxServerId,
            String containerId,
            Integer port,
            String baseUrl,
            Instant checkedAt,
            UserOpencodeServiceStatus serviceStatus,
            String serviceAddress,
            String backendJavaServerIp) {
        this(status, initializable, message, processId, linuxServerId, containerId, port, baseUrl, checkedAt,
                serviceStatus, serviceAddress, backendJavaServerIp, true, null, null);
    }

    /**
     * 兼容旧调用方构造器：未显式传头像菜单状态时，按进程可用性和地址字段推断。
     */
    public UserOpencodeProcessStatusResponse(
            UserOpencodeProcessAvailability status,
            boolean initializable,
            String message,
            String processId,
            String linuxServerId,
            String containerId,
            Integer port,
            String baseUrl,
            Instant checkedAt) {
        this(
                status,
                initializable,
                message,
                processId,
                linuxServerId,
                containerId,
                port,
                baseUrl,
                checkedAt,
                defaultServiceStatus(status),
                null,
                null,
                true,
                null,
                null);
    }

    public UserOpencodeProcessStatusResponse {
        serviceStatus = serviceStatus == null ? defaultServiceStatus(status) : serviceStatus;
        serviceAddress = serviceAddress == null || serviceAddress.isBlank() ? null : serviceAddress.trim();
        backendJavaServerIp = backendJavaServerIp == null || backendJavaServerIp.isBlank() ? null : backendJavaServerIp.trim();
        messageSendBlockedReason = messageSendBlockedReason == null || messageSendBlockedReason.isBlank()
                ? null
                : messageSendBlockedReason.trim();
        publicConfigRolloutId = publicConfigRolloutId == null || publicConfigRolloutId.isBlank()
                ? null
                : publicConfigRolloutId.trim();
    }

    public UserOpencodeProcessStatusResponse withMessageGate(
            boolean allowed,
            String blockedReason,
            String rolloutId) {
        return new UserOpencodeProcessStatusResponse(
                status, initializable, message, processId, linuxServerId, containerId, port, baseUrl, checkedAt,
                serviceStatus, serviceAddress, backendJavaServerIp, allowed, blockedReason, rolloutId);
    }

    private static UserOpencodeServiceStatus defaultServiceStatus(UserOpencodeProcessAvailability status) {
        return status == UserOpencodeProcessAvailability.READY
                ? UserOpencodeServiceStatus.RUNNING
                : UserOpencodeServiceStatus.UNASSIGNED;
    }

}
