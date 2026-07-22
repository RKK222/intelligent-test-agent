package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.util.Objects;

/**
 * manager 的已知启动拒绝；仅在 Java 内部保留原因，不透传可能包含身份信息的原始消息。
 */
public final class OpencodeProcessStartRejectionException extends PlatformException {

    private final OpencodeProcessStartRejectionReason reason;

    public OpencodeProcessStartRejectionException(OpencodeProcessStartRejectionReason reason) {
        super(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 管理进程拒绝启动");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public OpencodeProcessStartRejectionReason reason() {
        return reason;
    }
}
