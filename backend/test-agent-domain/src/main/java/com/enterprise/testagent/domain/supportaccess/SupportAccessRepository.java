package com.enterprise.testagent.domain.supportaccess;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import java.time.Instant;
import java.util.Optional;

/** 排查授权与审计的关系型持久化端口。 */
public interface SupportAccessRepository {

    SupportAccessGrant saveGrant(SupportAccessGrant grant);

    Optional<SupportAccessGrant> findGrant(String grantId);

    void revokeGrant(String grantId, Instant revokedAt, String reason);

    void appendAuditEvent(SupportAccessAuditEvent event);

    PageResponse<SupportAccessAuditEvent> findAuditEvents(
            SupportAccessAuditQuery query,
            PageRequest pageRequest);

    /** 删除一年前的审计和已结束授权，返回删除总行数。 */
    int deleteExpiredBefore(Instant cutoff);
}
