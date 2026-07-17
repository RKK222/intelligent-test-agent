package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessage;
import com.enterprise.testagent.domain.session.SessionMessageRepository;
import com.enterprise.testagent.domain.session.SessionMessageRole;

/** 从平台 USER 消息中解析 Run 的稳定远端 dispatch ID，冲突时禁止选择任一候选。 */
final class RunDispatchMessageAnchorResolver {

    private static final int PAGE_SIZE = 200;

    private RunDispatchMessageAnchorResolver() {
    }

    static Anchor resolve(
            SessionMessageRepository repository,
            SessionId sessionId,
            RunId runId) {
        if (repository == null) {
            return Anchor.missing();
        }
        int page = 1;
        String resolved = null;
        while (true) {
            var response = repository.findBySessionId(sessionId, new PageRequest(page, PAGE_SIZE));
            for (SessionMessage message : response.items()) {
                if (message.role() != SessionMessageRole.USER
                        || !runId.equals(message.runId())
                        || message.remoteMessageId() == null
                        || message.remoteMessageId().isBlank()) {
                    continue;
                }
                if (resolved != null && !resolved.equals(message.remoteMessageId())) {
                    return Anchor.conflictedAnchor();
                }
                resolved = message.remoteMessageId();
            }
            if (response.items().isEmpty() || (long) page * PAGE_SIZE >= response.total()) {
                return new Anchor(false, resolved);
            }
            page++;
        }
    }

    /** 合并 scope 等其它可信锚点；候选不一致时显式标记冲突，禁止猜测。 */
    static Anchor merge(Anchor anchor, String candidate) {
        if (anchor.conflicted()) {
            return anchor;
        }
        String normalized = candidate == null || candidate.isBlank() ? null : candidate;
        if (normalized == null) {
            return anchor;
        }
        if (anchor.dispatchMessageId() != null
                && !anchor.dispatchMessageId().equals(normalized)) {
            return Anchor.conflictedAnchor();
        }
        return new Anchor(false, normalized);
    }

    record Anchor(boolean conflicted, String dispatchMessageId) {

        private static Anchor missing() {
            return new Anchor(false, null);
        }

        private static Anchor conflictedAnchor() {
            return new Anchor(true, null);
        }
    }
}
