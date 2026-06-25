package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import java.util.Objects;
import java.util.Optional;

/**
 * 管理页中的 opencode server 进程行，合并进程快照和可选当前用户绑定。
 */
public record RuntimeManagementOpencodeProcess(
        OpencodeServerProcess process,
        Optional<UserOpencodeProcessBinding> binding,
        Optional<String> username) {

    /**
     * 兼容只需要绑定信息的调用方；用户名由查询服务补充。
     */
    public RuntimeManagementOpencodeProcess(
            OpencodeServerProcess process,
            Optional<UserOpencodeProcessBinding> binding) {
        this(process, binding, Optional.empty());
    }

    /**
     * 复制 Optional，确保调用方不会传入 null 绑定包装。
     */
    public RuntimeManagementOpencodeProcess {
        Objects.requireNonNull(process, "process must not be null");
        binding = binding == null ? Optional.empty() : binding;
        username = username == null ? Optional.empty() : username;
    }
}
