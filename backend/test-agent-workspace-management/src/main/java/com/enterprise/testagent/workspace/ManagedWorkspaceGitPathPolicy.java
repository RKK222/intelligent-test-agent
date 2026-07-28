package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 托管个人工作区 Git 路径权限的公共策略。
 *
 * <p>HTTP 按钮和对话 Tool 必须复用同一规则，避免新增入口后绕过
 * {@code .opencode/**} 的应用管理员限制。</p>
 */
public final class ManagedWorkspaceGitPathPolicy {

    private ManagedWorkspaceGitPathPolicy() {
    }

    /** 校验所选文件是否允许由当前角色执行写入型 Git 操作。 */
    public static void requireWriteAccess(List<String> files, Collection<String> roles) {
        List<String> protectedFiles = files == null ? List.of() : files.stream()
                .filter(ManagedWorkspaceGitPathPolicy::isApplicationConfigPath)
                .toList();
        if (!protectedFiles.isEmpty() && !hasApplicationAdminRole(roles)) {
            throw new PlatformException(
                    ErrorCode.FORBIDDEN,
                    "应用 Agent 配置仅允许应用管理员提交或发布",
                    Map.of("files", protectedFiles));
        }
    }

    /** 超级管理员继承应用管理员能力，与平台 HTTP 鉴权规则保持一致。 */
    public static boolean hasApplicationAdminRole(Collection<String> roles) {
        return roles != null && (roles.contains(Dictionary.ROLE_APP_ADMIN)
                || roles.contains(Dictionary.ROLE_SUPER_ADMIN));
    }

    /** 归一化路径后判断是否落入完整的应用 OpenCode 配置命名空间。 */
    public static boolean isApplicationConfigPath(String file) {
        if (file == null || file.isBlank()) {
            return false;
        }
        try {
            String normalized = Path.of(file.replace('\\', '/')).normalize().toString().replace('\\', '/');
            while (normalized.startsWith("./")) {
                normalized = normalized.substring(2);
            }
            return normalized.equals(".opencode") || normalized.startsWith(".opencode/");
        } catch (RuntimeException exception) {
            // 非法路径仍由业务服务按既有参数错误契约处理；权限策略不改变错误类型。
            return false;
        }
    }
}
