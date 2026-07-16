package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.session.SessionMessageId;
import java.util.Objects;

/** 新模式摘要消息的稳定 ID 规则；Run 启动和终态投影必须复用同一实现。 */
public final class RunSummaryIdentifiers {

    private RunSummaryIdentifiers() {
    }

    public static SessionMessageId user(RunId runId, String dispatchMessageId) {
        Objects.requireNonNull(runId, "runId must not be null");
        if (dispatchMessageId != null && dispatchMessageId.startsWith("msg_")) {
            return new SessionMessageId(dispatchMessageId);
        }
        return new SessionMessageId("msg_summary_user_" + suffix(runId));
    }

    public static SessionMessageId assistant(RunId runId) {
        Objects.requireNonNull(runId, "runId must not be null");
        // 前端反馈契约只接受 msg_ + 32 位十六进制平台 ID；复用 run UUID 可稳定生成且无需数据库回查。
        return new SessionMessageId("msg_" + suffix(runId));
    }

    private static String suffix(RunId runId) {
        String value = runId.value();
        return value.startsWith("run_") ? value.substring(4) : value;
    }
}
