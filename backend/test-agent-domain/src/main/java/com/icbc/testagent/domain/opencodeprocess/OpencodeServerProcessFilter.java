package com.icbc.testagent.domain.opencodeprocess;

import com.icbc.testagent.domain.user.UserId;

/**
 * opencode server 进程管理页筛选条件；字段为 null 表示不按该维度过滤。
 */
public record OpencodeServerProcessFilter(
        OpencodeServerProcessStatus status,
        LinuxServerId linuxServerId,
        OpencodeContainerId containerId,
        UserId userId) {

    /**
     * 返回无筛选条件，用于管理页默认查询。
     */
    public static OpencodeServerProcessFilter empty() {
        return new OpencodeServerProcessFilter(null, null, null, null);
    }
}
