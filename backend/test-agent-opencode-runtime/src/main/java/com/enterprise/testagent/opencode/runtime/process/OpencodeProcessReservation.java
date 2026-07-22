package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import java.util.Objects;

/** 一次已提交的进程端口预留；并发胜者表示本请求复用了其它事务刚写入的绑定。 */
public record OpencodeProcessReservation(
        OpencodeServerProcess process,
        UserOpencodeProcessBinding binding,
        boolean concurrentWinner) {

    public OpencodeProcessReservation {
        Objects.requireNonNull(process, "process must not be null");
        Objects.requireNonNull(binding, "binding must not be null");
    }
}
