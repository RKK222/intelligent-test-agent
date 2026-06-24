package com.icbc.testagent.domain.opencodeprocess;

import com.icbc.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.List;
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
}
