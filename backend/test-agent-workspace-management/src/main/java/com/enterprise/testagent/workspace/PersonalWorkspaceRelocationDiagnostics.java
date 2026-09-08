package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.PlatformException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/** 搬迁专用安全诊断：输出受控阶段、原因及日志专用相对路径，禁止透传异常消息、任意 details 或 stderr。 */
final class PersonalWorkspaceRelocationDiagnostics {
    enum Stage {
        SOURCE_PATHS, CREATE_ARCHIVE, EXPORT_SNAPSHOT, CAPTURE_SOURCE_STATE,
        INSPECT_UNTRACKED, CREATE_BUNDLE, WRITE_ARCHIVE, VERIFY_SOURCE_STATE,
        HASH_ARCHIVE, REGISTER_SNAPSHOT, TRANSFER, CLEANUP_SOURCE
    }

    private static final Map<String, String> HINTS = Map.ofEntries(
            Map.entry("UNMERGED_WORKTREE", "源仓库存在未完成合并，需保留现场后处理"),
            Map.entry("UNSUPPORTED_GIT_SUBMODULE", "源仓库包含暂不支持搬迁的Git子模块"),
            Map.entry("UNSUPPORTED_UNTRACKED_ENTRY", "未跟踪文件包含符号链接或特殊文件，需检查源仓库"),
            Map.entry("SOURCE_CHANGED_DURING_SNAPSHOT", "快照期间源文件发生变化，需检查持续写入"),
            Map.entry("SOURCE_FILE_CHANGED_DURING_ARCHIVE", "归档期间源文件发生变化，需检查持续写入"),
            Map.entry("SOURCE_SERVER_CHANGED", "搬迁源服务器身份已变化"),
            Map.entry("SOURCE_REPLICA_MISSING", "源服务器缺少应用版本副本记录"),
            Map.entry("SOURCE_DIRECTORY_INVALID", "源个人仓库目录不满足安全校验"),
            Map.entry("RELOCATION_IDENTITY_CHANGED", "搬迁记录与个人工作区事实已变化"),
            Map.entry("TARGET_BRANCH_IN_USE", "目标分支已被工作区占用"),
            Map.entry("INVALID_RELOCATION_ARCHIVE", "快照内容或路径未通过安全校验"),
            Map.entry("SNAPSHOT_RESTORE_FAILED", "目标快照恢复异常"),
            Map.entry("SNAPSHOT_EXPORT_FAILED", "源快照操作异常，请结合阶段和原因类型排查"),
            Map.entry("RESTORED_STATE_MISMATCH", "目标恢复状态校验不一致"));

    private PersonalWorkspaceRelocationDiagnostics() {
    }

    /** 保留原业务错误码与详情，只添加本次失败阶段，避免改变重试与保护语义。 */
    static PlatformException atStage(PlatformException error, Stage stage) {
        Map<String, Object> details = new HashMap<>(error.details());
        details.put("relocationStage", stage.name());
        return new PlatformException(error.errorCode(), error.getMessage(), details, error);
    }

    /** 原始相对路径只留在内部异常字段，不进入 API details 或数据库安全消息。 */
    static PlatformException withFilePath(PlatformException error, String relative) {
        return new FilePathFailure(error, relative);
    }

    /** 只读取本模块创建的路径上下文，并转义日志分隔符和控制字符，限制单字段长度。 */
    static String logFilePath(Throwable error) {
        Throwable current = error;
        for (int i = 0; i < 8 && current != null; i++, current = current.getCause()) {
            if (current instanceof FilePathFailure failure) {
                StringBuilder result = new StringBuilder();
                String path = failure.relative;
                for (int j = 0; j < path.length(); j++) {
                    char ch = path.charAt(j);
                    String escaped = ch == '\\' ? "\\\\" : ch == '"' ? "\\\""
                            : Character.isISOControl(ch) || Character.getType(ch) == Character.FORMAT
                            || ch == '\u2028' || ch == '\u2029'
                            ? String.format("\\u%04x", (int) ch) : String.valueOf(ch);
                    if (result.length() + escaped.length() > 2048) {
                        return result + "...[truncated]";
                    }
                    result.append(escaped);
                }
                return result.toString();
            }
        }
        return "NONE";
    }

    /** 私有上下文不新增公开 getter，统一错误响应仍只消费 PlatformException.details。 */
    private static final class FilePathFailure extends PlatformException {
        private final String relative;

        private FilePathFailure(PlatformException error, String relative) {
            super(error.errorCode(), error.getMessage(), error.details(), error);
            this.relative = relative;
        }
    }

    /** 路径只保存相对路径的 SHA-256；不输出用户名、文件名、链接目标或绝对目录。 */
    static String pathRef(String relative) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(relative.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /** 只消费白名单诊断字段，旧异常或外部未知值安全退回当前阶段。 */
    static Failure describe(RuntimeException error, Stage fallback) {
        Map<String, Object> details = error instanceof PlatformException platform ? platform.details() : Map.of();
        Stage stage = fallback;
        Object rawStage = details.get("relocationStage");
        if (rawStage instanceof String value) {
            try {
                stage = Stage.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                // 未知阶段不透传，使用调用方记录的受控阶段。
            }
        }
        String reason = knownReason(details.get("reason"));
        if (reason == null) {
            reason = knownReason(details.get("gitFailureType"));
        }
        if (reason == null) {
            reason = "UNCLASSIFIED";
        }
        Object rawPath = details.get("pathRef");
        String path = rawPath instanceof String value && value.matches("[0-9a-f]{64}") ? value : "NONE";
        return new Failure(stage, reason, path);
    }

    /** 有界遍历原因链，只记录类型，不输出可能含凭据或路径的消息与堆栈。 */
    static String causeType(Throwable error) {
        Throwable current = error;
        for (int i = 0; i < 8 && current.getCause() != null && current.getCause() != current; i++) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

    private static String knownReason(Object value) {
        return value instanceof String text && HINTS.containsKey(text) ? text : null;
    }

    record Failure(Stage stage, String reason, String pathRef) {
        String safeMessage() {
            return "个人工作区自动搬迁失败 [stage=" + stage + "; reason=" + reason + "; pathRef=" + pathRef
                    + "] " + HINTS.getOrDefault(reason, "请按搬迁ID和本次traceId检查对应阶段")
                    + "；等待下一轮安全重试";
        }
    }
}
