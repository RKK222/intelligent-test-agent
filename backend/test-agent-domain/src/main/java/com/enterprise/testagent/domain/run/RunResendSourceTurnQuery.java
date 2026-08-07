package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.session.SessionId;
import java.util.Optional;

/** 通过 MyBatis XML 查找会话最后一个可恢复用户边界。 */
public interface RunResendSourceTurnQuery {

    Optional<RunResendSourceTurn> findLatestUserTurn(SessionId sessionId);
}
