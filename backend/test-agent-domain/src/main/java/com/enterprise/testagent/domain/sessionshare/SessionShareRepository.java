package com.enterprise.testagent.domain.sessionshare;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Optional;

/** 会话协作分享、成员历史、候选用户和安全审计的持久化端口。 */
public interface SessionShareRepository {

    SessionShare insert(SessionShare share);

    /** 按 expectedVersion 原子更新分享和成员，返回是否命中。 */
    boolean update(SessionShare share, long expectedVersion);

    Optional<SessionShare> findBySessionId(SessionId sessionId);

    Optional<SessionShare> findByShareId(SessionShareId shareId);

    PageResponse<SharedSessionListItem> findSharedWith(UserId userId, PageRequest pageRequest);

    PageResponse<SessionShareCandidate> findCandidates(
            UserId excludedUserId,
            String keyword,
            PageRequest pageRequest);

    /**
     * 仅当存量会话全部非代操作 Run/消息归因都唯一指向 actor 时，原子补齐所属人。
     */
    default boolean claimLegacySessionOwner(SessionId sessionId, UserId actor) {
        return false;
    }

    void appendAudit(SessionShareAuditEvent event);

    /** 删除严格早于保留截止时间的分享审计事件。 */
    int deleteAuditEventsBefore(Instant cutoff);
}
