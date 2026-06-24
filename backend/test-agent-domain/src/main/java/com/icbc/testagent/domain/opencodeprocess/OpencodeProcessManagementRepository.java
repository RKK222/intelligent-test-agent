package com.icbc.testagent.domain.opencodeprocess;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * opencode 用户进程管理持久化端口，业务层通过该端口读写拓扑和用户绑定。
 */
public interface OpencodeProcessManagementRepository {

    LinuxServer saveLinuxServer(LinuxServer linuxServer);

    Optional<LinuxServer> findLinuxServerById(LinuxServerId linuxServerId);

    BackendJavaProcess saveBackendJavaProcess(BackendJavaProcess backendJavaProcess);

    Optional<BackendJavaProcess> findBackendJavaProcessById(BackendProcessId backendProcessId);

    List<BackendJavaProcess> findReadyBackendJavaProcesses(Instant minHeartbeatAt, int limit);

    OpencodeContainer saveContainer(OpencodeContainer container);

    Optional<OpencodeContainer> findContainerById(OpencodeContainerId containerId);

    List<OpencodeContainer> findHealthyContainers(int limit);

    List<OpencodeContainer> findHealthyContainersByLinuxServer(LinuxServerId linuxServerId, int limit);

    List<OpencodeContainer> findHealthyContainersConnectedToBackend(BackendProcessId backendProcessId, int limit);

    List<OpencodeContainer> findHealthyContainersConnectedToBackendByLinuxServer(
            BackendProcessId backendProcessId,
            LinuxServerId linuxServerId,
            int limit);

    OpencodeContainerManager saveContainerManager(OpencodeContainerManager manager);

    Optional<OpencodeContainerManager> findContainerManagerById(ContainerManagerId managerId);

    OpencodeManagerBackendConnection saveManagerBackendConnection(OpencodeManagerBackendConnection connection);

    Optional<OpencodeManagerBackendConnection> findManagerBackendConnection(
            ContainerManagerId managerId,
            BackendProcessId backendProcessId);

    OpencodeServerProcess saveOpencodeServerProcess(OpencodeServerProcess process);

    Optional<OpencodeServerProcess> findOpencodeServerProcessById(OpencodeProcessId processId);

    List<Integer> findOccupiedPorts(LinuxServerId linuxServerId, OpencodeContainerId containerId);

    UserOpencodeProcessBinding saveUserBinding(UserOpencodeProcessBinding binding);

    Optional<UserOpencodeProcessBinding> findUserBinding(UserId userId, String agentId);

    List<OpencodeServerProcess> findOpencodeServerProcesses(int limit);

    /**
     * 只读列出 Linux 服务器拓扑快照，供超级管理员运行管理页展示。
     */
    default List<LinuxServer> findLinuxServers(int limit) {
        throw new UnsupportedOperationException("findLinuxServers is not implemented");
    }

    /**
     * 只读列出后端 Java 进程快照，供超级管理员运行管理页展示。
     */
    default List<BackendJavaProcess> findBackendJavaProcesses(int limit) {
        throw new UnsupportedOperationException("findBackendJavaProcesses is not implemented");
    }

    /**
     * 只读列出 opencode 容器快照，供超级管理员运行管理页展示。
     */
    default List<OpencodeContainer> findContainers(int limit) {
        throw new UnsupportedOperationException("findContainers is not implemented");
    }

    /**
     * 只读列出容器管理进程快照，供超级管理员运行管理页展示。
     */
    default List<OpencodeContainerManager> findContainerManagers(int limit) {
        throw new UnsupportedOperationException("findContainerManagers is not implemented");
    }

    /**
     * 只读列出 manager 到后端实例的连接快照。
     */
    default List<OpencodeManagerBackendConnection> findManagerBackendConnections(int limit) {
        throw new UnsupportedOperationException("findManagerBackendConnections is not implemented");
    }

    /**
     * 分页查询用户专属 opencode server 进程，筛选条件为空时返回最新进程。
     */
    default PageResponse<OpencodeServerProcess> findOpencodeServerProcesses(
            OpencodeServerProcessFilter filter,
            PageRequest pageRequest) {
        throw new UnsupportedOperationException("paged findOpencodeServerProcesses is not implemented");
    }

    /**
     * 按筛选条件统计 opencode server 进程数，供运行管理概览避免受分页影响。
     */
    default long countOpencodeServerProcesses(OpencodeServerProcessFilter filter) {
        throw new UnsupportedOperationException("countOpencodeServerProcesses is not implemented");
    }

    /**
     * 按进程 ID 批量查询当前用户绑定，用于管理页把进程与绑定状态合并展示。
     */
    default Map<OpencodeProcessId, UserOpencodeProcessBinding> findUserBindingsByProcessIds(
            List<OpencodeProcessId> processIds) {
        throw new UnsupportedOperationException("findUserBindingsByProcessIds is not implemented");
    }

    /**
     * 统计当前用户进程绑定数，用于运行管理页概览。
     */
    default long countUserBindings() {
        throw new UnsupportedOperationException("countUserBindings is not implemented");
    }
}
