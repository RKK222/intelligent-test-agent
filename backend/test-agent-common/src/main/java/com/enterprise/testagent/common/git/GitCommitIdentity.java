package com.enterprise.testagent.common.git;

import java.util.Objects;

/**
 * 单次 Git 提交使用的作者/提交者身份。
 *
 * <p>身份只作为命令级配置传给 Git，不写入全局或仓库配置，避免共享工作区在多用户操作时串用提交人。</p>
 */
public record GitCommitIdentity(String name, String email) {

    private static final String ENTERPRISE_EMAIL_DOMAIN = "mails.icbc";

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
     * 根据平台用户的展示名和统一认证号生成企业 SCM 可识别的提交身份。
     * User 领域当前没有邮箱字段，企业 SCM 已按统一认证号登记 {@code mails.icbc} 邮箱；
     * 使用同一规则避免远端因 invalid committer 拒绝平台生成的提交，不影响 SSH 鉴权。
     */
    public static GitCommitIdentity forPlatformUser(String username, String unifiedAuthId) {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(unifiedAuthId, "unifiedAuthId must not be null");
        String localPart = unifiedAuthId.trim().replaceAll("[^A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]", "_");
        if (localPart.isBlank()) {
            localPart = "user";
        }
        return new GitCommitIdentity(username.trim(), localPart + "@" + ENTERPRISE_EMAIL_DOMAIN);
    }

    private static boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }
}
