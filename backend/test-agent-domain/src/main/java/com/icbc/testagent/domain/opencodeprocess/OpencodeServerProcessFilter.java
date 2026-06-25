package com.icbc.testagent.domain.opencodeprocess;

import com.icbc.testagent.domain.user.UserId;

/**
 * opencode server 进程管理页筛选条件；字段为 null 表示不按该维度过滤。
 */
public record OpencodeServerProcessFilter(
        OpencodeServerProcessStatus status,
        LinuxServerId linuxServerId,
        OpencodeContainerId containerId,
        UserId userId,
        String username) {

    /**
     * 兼容旧调用方：用户名由运行管理应用服务解析后再传入持久化筛选。
     */
    public OpencodeServerProcessFilter(
            OpencodeServerProcessStatus status,
            LinuxServerId linuxServerId,
            OpencodeContainerId containerId,
            UserId userId) {
        this(status, linuxServerId, containerId, userId, null);
    }

    /**
     * 规整用户名筛选；空白字符串等价于不按用户名筛选。
     */
    public OpencodeServerProcessFilter {
        username = username == null || username.isBlank() ? null : username.trim();
    }

    /**
     * 返回无筛选条件，用于管理页默认查询。
     */
    public static OpencodeServerProcessFilter empty() {
        return new OpencodeServerProcessFilter(null, null, null, null);
    }

    /**
     * 按用户名构造筛选条件；业务层会解析成用户 ID 后再访问 Repository。
     */
    public static OpencodeServerProcessFilter byUsername(String username) {
        return new OpencodeServerProcessFilter(null, null, null, null, username);
    }
}
