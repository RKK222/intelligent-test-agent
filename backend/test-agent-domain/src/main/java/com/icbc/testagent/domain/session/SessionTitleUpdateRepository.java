package com.icbc.testagent.domain.session;

import java.time.Instant;

/**
 * Session 标题条件更新端口，避免异步标题生成覆盖后到的原生标题或用户手动改名。
 */
public interface SessionTitleUpdateRepository {

    /**
     * 仅当当前标题仍等于 expectedTitle 时更新标题，并返回本次是否实际写入。
     */
    boolean updateTitleIfCurrent(
            SessionId sessionId,
            String expectedTitle,
            String title,
            Instant updatedAt,
            String traceId);
}
