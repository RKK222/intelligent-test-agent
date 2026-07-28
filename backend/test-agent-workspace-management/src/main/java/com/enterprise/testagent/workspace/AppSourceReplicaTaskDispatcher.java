package com.enterprise.testagent.workspace;

import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import java.util.Set;

/** 应用源码副本任务低延迟唤醒端口；数据库 generation/lease 才是执行事实。 */
public interface AppSourceReplicaTaskDispatcher {

    /** 事务提交后按受理时冻结的服务器集合唤醒本机/广播协调器。 */
    void wake(AppSourceOperation operation, Set<LinuxServerId> targetServerIds);
}
