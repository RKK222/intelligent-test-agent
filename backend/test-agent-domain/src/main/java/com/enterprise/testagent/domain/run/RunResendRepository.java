package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 原生撤销重发的关系型控制面仓储。 */
public interface RunResendRepository {

    RunResend save(RunResend resend);

    boolean saveIfStatus(RunResend resend, RunResendStatus expectedStatus);

    boolean saveIfStatusAndLease(
            RunResend resend,
            RunResendStatus expectedStatus,
            String expectedLeaseToken);

    Optional<RunResend> findById(RunResendId resendId);

    Optional<RunResend> findByOwnerAndClientRequestId(UserId owner, String clientRequestId);

    Optional<RunResend> findBySourceRunId(RunId sourceRunId);

    Optional<RunResend> findByReplacementRunId(RunId replacementRunId);

    Optional<RunResend> findActiveBySession(SessionId sessionId);

    List<RunResend> findDue(Instant now, int limit);

    boolean insertSessionLock(SessionId sessionId, RunResendId resendId, UserId owner, Instant now);

    void deleteSessionLock(SessionId sessionId, RunResendId resendId);

    boolean hasSessionLock(SessionId sessionId);

    /** 历史读取防御性过滤：即使异步清理延迟，也不能让已提交源回复重新出现。 */
    List<RunId> findDispatchedSourceRunIds(SessionId sessionId);
}
