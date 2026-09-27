package com.enterprise.testagent.domain.team;

import java.time.Instant;
import java.util.List;

/** 应用审阅的逻辑身份与来源元数据；不保存正文、凭据或服务器物理路径。 */
public final class TeamReviewModels {
    private TeamReviewModels() { }

    public record Source(String userId, String username, String personalWorkspaceId,
                         String workspaceId, String linuxServerId) { }

    public record Scope(String id, String actorUserId, String actorSessionDigest,
                        TeamScopeMode scopeMode, String ownerUserId, String versionId,
                        String selectedUserId, List<Source> sources, Instant expiresAt) { }

    /** FILE_TIME 只是文件系统时间，不代表真实操作者或提交时间。 */
    public record File(String path, String name, boolean directory, long size,
                       Instant fileTime, String contentVersion, String author,
                       Instant changedAt, String timeType, String changeType, boolean deleted) { }

    public record Candidate(Source source, File file) { }

    public record Entry(String path, String name, boolean directory, long size,
                        Candidate selected, boolean latestUncertain, List<Candidate> alternatives) { }

    public static final String EXCLUDED_POLICY = "审阅范围不含 .git、.env*、私钥/证书凭据、符号链接和非普通文件；二进制或非 UTF-8 正文不可预览";
    public record Listing(List<Entry> entries, List<String> unavailableMembers, boolean complete, String excludedPolicy) {
        public Listing(List<Entry> entries, List<String> unavailableMembers, boolean complete) {
            this(entries, unavailableMembers, complete, EXCLUDED_POLICY);
        }
    }
}
