package com.icbc.testagent.common.git;

import java.util.Objects;

/**
 * 单次 Git 提交使用的作者/提交者身份。
 *
 * <p>身份只作为命令级配置传给 Git，不写入全局或仓库配置，避免共享工作区在多用户操作时串用提交人。</p>
 */
public record GitCommitIdentity(String name, String email) {

    private static final String LOCAL_EMAIL_DOMAIN = "testagent.local";

    public GitCommitIdentity {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(email, "email must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (containsLineBreak(name) || containsLineBreak(email)) {
            throw new IllegalArgumentException("Git identity must not contain line breaks");
        }
    }

    /**
     * 根据平台用户的展示名和统一认证号生成稳定的本地提交身份。
     * User 领域当前没有邮箱字段，因此使用平台保留域名补足 Git 必需的 email；不影响远端 SSH 鉴权。
     */
    public static GitCommitIdentity forPlatformUser(String username, String unifiedAuthId) {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(unifiedAuthId, "unifiedAuthId must not be null");
        String localPart = unifiedAuthId.trim().replaceAll("[^A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]", "_");
        if (localPart.isBlank()) {
            localPart = "user";
        }
        return new GitCommitIdentity(username.trim(), localPart + "@" + LOCAL_EMAIL_DOMAIN);
    }

    private static boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }
}
