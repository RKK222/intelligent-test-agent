package com.enterprise.testagent.domain.opencodeprocess;

import com.enterprise.testagent.domain.user.UserId;

/**
 * 用户进程端口预留的权威行锁端口。
 *
 * <p>调用方必须始终先锁用户、再锁 Linux 服务器，避免不同分配入口形成锁顺序反转。
 */
public interface OpencodeProcessReservationLockPort {

    /** 锁定既有 users 行；权威行不存在时返回 false。 */
    boolean lockUser(UserId userId);

    /** 锁定既有 linux_servers 行；权威行不存在时返回 false。 */
    boolean lockLinuxServer(LinuxServerId linuxServerId);
}
