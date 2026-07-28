package com.enterprise.testagent.domain.appsource;

import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** 按 repositoryId + generation 标识的不可变源码选择快照。 */
public record AppSourceSnapshot(
        CodeRepositoryId repositoryId,
        long generation,
        String repositoryEnglishName,
        AppSourcePurpose purpose,
        UserId ownerUserId,
        String branch,
        String targetCommit,
        List<AppSourceSelectedPath> selectedPaths,
        String indexSha256,
        Instant acceptedAt,
        Instant expiresAt,
        AppSourceSnapshotStatus status,
        Instant createdAt,
        Instant updatedAt) {

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-fA-F]{64}$");

    public AppSourceSnapshot {
        Objects.requireNonNull(repositoryId, "repositoryId must not be null");
        if (generation < 1L) {
            throw new IllegalArgumentException("generation must be positive");
        }
        repositoryEnglishName = requireText(repositoryEnglishName, "repositoryEnglishName");
        Objects.requireNonNull(purpose, "purpose must not be null");
        if (purpose == AppSourcePurpose.PERSONAL) {
            Objects.requireNonNull(ownerUserId, "personal snapshot ownerUserId must not be null");
        }
        branch = requireText(branch, "branch");
        targetCommit = requireText(targetCommit, "targetCommit");
        selectedPaths = List.copyOf(Objects.requireNonNull(selectedPaths, "selectedPaths must not be null"));
        if (new HashSet<>(selectedPaths.stream().map(AppSourceSelectedPath::path).toList()).size()
                != selectedPaths.size()) {
            throw new IllegalArgumentException("selectedPaths must not contain duplicate paths");
        }
        indexSha256 = optionalText(indexSha256);
        if (indexSha256 != null && !SHA256.matcher(indexSha256).matches()) {
            throw new IllegalArgumentException("indexSha256 must be a SHA-256 hex value");
        }
        acceptedAt = Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        Duration retention = Duration.between(acceptedAt, expiresAt);
        long retentionHours = retention.toHours();
        // snapshot 自身也校验权威时间，避免调用方绕过 AppSourceRetention 构造任意到期时间。
        if (retentionHours < 1L
                || retentionHours > 72L
                || !retention.equals(Duration.ofHours(retentionHours))) {
            throw new IllegalArgumentException("expiresAt must be acceptedAt plus 1 to 72 whole hours");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
