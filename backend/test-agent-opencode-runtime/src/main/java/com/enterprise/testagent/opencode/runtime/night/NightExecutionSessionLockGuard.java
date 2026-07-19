package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.session.SessionId;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 会话写入口共用的夜间任务锁门禁，防止浏览器之外的调用绕过前端禁用。 */
@Service
public class NightExecutionSessionLockGuard {

    private final NightExecutionTaskRepository repository;

    public NightExecutionSessionLockGuard(NightExecutionTaskRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public void requireUnlocked(SessionId sessionId) {
        if (repository.hasSessionLock(sessionId)) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "当前会话已有待执行夜间任务，取消后可继续对话",
                    Map.of("sessionId", sessionId.value()));
        }
    }
}
