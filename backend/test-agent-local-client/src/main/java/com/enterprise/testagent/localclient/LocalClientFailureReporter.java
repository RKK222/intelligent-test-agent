package com.enterprise.testagent.localclient;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.LoggerFactory;

/** 将双击启动期间的短进程异常安全写入持久日志，避免现场只剩笼统的 exit code。 */
final class LocalClientFailureReporter {

    private LocalClientFailureReporter() {
    }

    /**
     * 日志只记录固定类别、稳定错误码和根异常类型；服务端正文、异常消息、统一认证号和 Client key 均不进入日志。
     */
    static void report(LocalClientMain.Command command, Exception failure, PrintStream error) {
        Objects.requireNonNull(failure, "failure must not be null");
        Objects.requireNonNull(error, "error must not be null");
        Path logFile = prepareLogFile();
        FailureDetails details = details(command, failure);
        try {
            LocalClientDiagnostics.ensureSessionId();
            LoggerFactory.getLogger(LocalClientMain.class).error(
                    "local_client_command_failed command={} failureCategory={} failureCode={} rootFailureType={}",
                    command == null ? "UNKNOWN" : command.name(),
                    details.category(),
                    details.failureCode(),
                    LocalClientDiagnostics.rootFailureType(failure));
        } catch (RuntimeException ignored) {
            // 日志系统自身异常不能覆盖原始故障；终端仍输出安全说明和预期日志位置。
        }
        error.println(details.userMessage());
        error.println("错误详情已写入：" + logFile);
    }

    /** 根据本地异常类型生成不含服务端正文和凭据的现场提示。 */
    static FailureDetails details(LocalClientMain.Command command, Exception failure) {
        if (failure instanceof LocalClientEnrollment.AuthenticationException) {
            return new FailureDetails(
                    "AUTHENTICATION_REJECTED",
                    "UNAUTHENTICATED",
                    "本地客户端认证未通过。请核对统一认证号和 Client key；若已连续尝试多次，请等待 1 分钟后重试。");
        }
        if (failure instanceof LocalClientRegistrationProbe.RegistrationRejectedException rejected) {
            return new FailureDetails(
                    "PLATFORM_REJECTED",
                    rejected.failureCode(),
                    "平台拒绝本地客户端登记（错误码：" + rejected.failureCode()
                            + "）。请管理员按该错误码检查企业后端同一时间段日志及客户端版本。");
        }
        if (failure instanceof LocalClientRegistrationProbe.PlatformConnectionException) {
            return new FailureDetails(
                    "PLATFORM_CONNECTION_FAILED",
                    "PLATFORM_CONNECTION_FAILED",
                    "无法连接企业平台登记地址。请检查用户机到平台入口的内网链路、Nginx WebSocket Upgrade 转发及后端状态。");
        }
        if (failure instanceof LocalClientRegistrationProbe.InvalidRegistrationResponseException) {
            return new FailureDetails(
                    "PLATFORM_PROTOCOL_INVALID",
                    "PLATFORM_PROTOCOL_INVALID",
                    "平台返回的客户端登记响应无效。请确认企业前后端与本地客户端为同一发布版本。");
        }
        String action = command == LocalClientMain.Command.ENROLL ? "接入" : "启动";
        return new FailureDetails(
                "CLIENT_COMMAND_FAILED",
                failure.getClass().getSimpleName(),
                "本地客户端" + action + "失败，请将 client.log 中本次失败时间附近的日志交给管理员。");
    }

    private static Path prepareLogFile() {
        Path logsDirectory = LocalClientPaths.logsDirectory();
        try {
            Files.createDirectories(logsDirectory);
            System.setProperty("testagent.localclient.logDir", logsDirectory.toString());
        } catch (Exception ignored) {
            // 目录不可写时仍返回预期路径，终端提示不会掩盖原始异常。
        }
        return logsDirectory.resolve("client.log");
    }

    record FailureDetails(String category, String failureCode, String userMessage) {
    }
}
