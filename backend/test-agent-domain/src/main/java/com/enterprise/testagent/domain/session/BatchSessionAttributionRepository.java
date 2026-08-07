package com.enterprise.testagent.domain.session;

import com.enterprise.testagent.domain.user.UserId;
import java.util.Optional;

/** 批量会话归因持久化端口；幂等锁和标记必须与会话创建处于同一事务。 */
public interface BatchSessionAttributionRepository {

    /** 按用户和批量条目请求查找已经创建的会话。 */
    Optional<SessionId> findSessionId(UserId userId, String itemRequestId);

    /** 串行化同一用户、同一批量条目请求的创建事务。 */
    void lockCreateRequest(UserId userId, String itemRequestId);

    /** 为新会话写入批量归因；仅允许标记属于当前用户的普通会话。 */
    boolean markBatch(
            SessionId sessionId,
            UserId userId,
            String batchId,
            String itemRequestId);
}
