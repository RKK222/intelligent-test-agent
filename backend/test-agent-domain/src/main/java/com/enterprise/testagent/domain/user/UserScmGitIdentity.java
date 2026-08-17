package com.enterprise.testagent.domain.user;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** 平台用户在企业 SCM 中登记的 Git 提交姓名及其校验证据。 */
public record UserScmGitIdentity(
        UserId userId,
        String gitName,
        UserScmGitIdentitySource source,
        String evidenceCommit,
        Instant evidenceAt,
        Instant verifiedAt) {

    private static final Pattern COMMIT_HASH = Pattern.compile("[0-9a-fA-F]{7,64}");

    public UserScmGitIdentity {
        Objects.requireNonNull(userId, "userId must not be null");
        gitName = requireText(gitName, "gitName");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(evidenceAt, "evidenceAt must not be null");
        Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
        if (gitName.length() > 128 || gitName.indexOf('\r') >= 0 || gitName.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("gitName is invalid");
        }
        evidenceCommit = evidenceCommit == null || evidenceCommit.isBlank() ? null : evidenceCommit.trim();
        if (evidenceCommit != null && !COMMIT_HASH.matcher(evidenceCommit).matches()) {
            throw new IllegalArgumentException("evidenceCommit is invalid");
        }
        evidenceCommit = evidenceCommit == null ? null : evidenceCommit.toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
