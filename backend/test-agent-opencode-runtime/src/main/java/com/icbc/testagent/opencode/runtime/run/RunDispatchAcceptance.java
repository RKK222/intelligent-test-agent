package com.icbc.testagent.opencode.runtime.run;

/** OpenCode 是否已经接收稳定 dispatchMessageId。UNKNOWN 必须按“不得重发”处理。 */
public enum RunDispatchAcceptance {
    ACCEPTED,
    NOT_ACCEPTED,
    UNKNOWN
}
