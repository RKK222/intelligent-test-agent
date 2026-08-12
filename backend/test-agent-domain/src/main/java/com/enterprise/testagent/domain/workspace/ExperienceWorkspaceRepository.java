package com.enterprise.testagent.domain.workspace;

import java.util.Optional;

/**
 * 体验工作区持久化端口；登记 Workspace 与切换服务器当前绑定必须由实现方在同一事务中幂等完成。
 */
public interface ExperienceWorkspaceRepository {

    /** 查询指定后端服务器当前生效的体验绑定。 */
    Optional<ExperienceWorkspaceBinding> findCurrentByLinuxServerId(String linuxServerId);

    /**
     * 仅当服务器当前绑定仍等于调用方读取的快照时，幂等创建 Workspace 并切换绑定。
     *
     * <p>该比较与写入必须在数据库内原子完成，不能依赖单 JVM 锁；返回 {@code false}
     * 表示其它 Java 实例已经抢先更新，调用方应重新读取配置和绑定。历史 Workspace 不覆盖或删除。
     */
    boolean registerCurrentIfUnchanged(
            Workspace workspace,
            String configuredParameterValue,
            String traceId,
            Optional<ExperienceWorkspaceBinding> expectedCurrent);
}
