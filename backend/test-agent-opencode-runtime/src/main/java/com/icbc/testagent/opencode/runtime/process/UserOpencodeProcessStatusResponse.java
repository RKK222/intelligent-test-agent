package com.icbc.testagent.opencode.runtime.process;

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
        String serviceAddress) {

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
                serviceAddress(linuxServerId, port));
    }

    public UserOpencodeProcessStatusResponse {
        serviceStatus = serviceStatus == null ? defaultServiceStatus(status) : serviceStatus;
        if ((serviceAddress == null || serviceAddress.isBlank()) && linuxServerId != null && port != null) {
            serviceAddress = serviceAddress(linuxServerId, port);
        }
    }

    private static UserOpencodeServiceStatus defaultServiceStatus(UserOpencodeProcessAvailability status) {
        return status == UserOpencodeProcessAvailability.READY
                ? UserOpencodeServiceStatus.RUNNING
                : UserOpencodeServiceStatus.UNASSIGNED;
    }

    private static String serviceAddress(String linuxServerId, Integer port) {
        if (linuxServerId == null || linuxServerId.isBlank() || port == null) {
            return null;
        }
        return linuxServerId + ":" + port;
    }
}
