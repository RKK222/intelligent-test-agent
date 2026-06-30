package com.icbc.testagent.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.ContainerManagerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.icbc.testagent.domain.support.DomainValidation;
import com.icbc.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * opencode 用户进程管理 JDBC Repository，保存服务器、容器、管理进程、用户进程和绑定快照。
 */
@Repository
public class JdbcOpencodeProcessManagementRepository extends JdbcRepositorySupport
        implements OpencodeProcessManagementRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    private final RowMapper<LinuxServer> linuxServerRowMapper = (rs, rowNum) -> new LinuxServer(
            new LinuxServerId(rs.getString("linux_server_id")),
            rs.getString("name"),
            LinuxServerStatus.valueOf(rs.getString("status")),
            readMap(rs.getString("capacity_summary_json")),
            instant(rs, "last_heartbeat_at"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            rs.getString("trace_id"));

    private final RowMapper<BackendJavaProcess> backendProcessRowMapper = (rs, rowNum) -> new BackendJavaProcess(
            new BackendProcessId(rs.getString("backend_process_id")),
            new LinuxServerId(rs.getString("linux_server_id")),
            rs.getString("listen_url"),
            BackendJavaProcessStatus.valueOf(rs.getString("status")),
            instant(rs, "started_at"),
            instant(rs, "last_heartbeat_at"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            rs.getString("trace_id"));

    private final RowMapper<OpencodeContainer> containerRowMapper = (rs, rowNum) -> new OpencodeContainer(
            new OpencodeContainerId(rs.getString("container_id")),
            new LinuxServerId(rs.getString("linux_server_id")),
            rs.getString("container_name"),
            rs.getInt("port_start"),
            rs.getInt("port_end"),
            rs.getInt("max_processes"),
            rs.getInt("current_processes"),
            OpencodeContainerStatus.valueOf(rs.getString("status")),
            instant(rs, "last_heartbeat_at"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            rs.getString("trace_id"));

    private final RowMapper<OpencodeContainerManager> managerRowMapper = (rs, rowNum) -> new OpencodeContainerManager(
            new ContainerManagerId(rs.getString("manager_id")),
            new OpencodeContainerId(rs.getString("container_id")),
            new LinuxServerId(rs.getString("linux_server_id")),
            rs.getString("protocol_version"),
            ManagerConnectionStatus.valueOf(rs.getString("connection_status")),
            readMap(rs.getString("capabilities_json")),
            instant(rs, "last_heartbeat_at"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            rs.getString("trace_id"));

    private final RowMapper<OpencodeManagerBackendConnection> connectionRowMapper =
            (rs, rowNum) -> new OpencodeManagerBackendConnection(
                    new ContainerManagerId(rs.getString("manager_id")),
                    new BackendProcessId(rs.getString("backend_process_id")),
                    ManagerConnectionStatus.valueOf(rs.getString("status")),
                    instant(rs, "connected_at"),
                    instant(rs, "last_heartbeat_at"),
                    instant(rs, "updated_at"),
                    rs.getString("trace_id"));

    private final RowMapper<OpencodeServerProcess> processRowMapper = (rs, rowNum) -> {
        Instant createdAt = instant(rs, "created_at");
        Instant updatedAt = instant(rs, "updated_at");
        if (updatedAt != null && createdAt != null && updatedAt.isBefore(createdAt)) {
            // 兼容历史脏数据，避免旧进程记录阻断用户重新初始化 opencode。
            updatedAt = createdAt;
        }
        return new OpencodeServerProcess(
                new OpencodeProcessId(rs.getString("process_id")),
                new UserId(rs.getString("user_id")),
                new LinuxServerId(rs.getString("linux_server_id")),
                new OpencodeContainerId(rs.getString("container_id")),
                rs.getInt("port"),
                longObject(rs.getObject("pid")),
                rs.getString("base_url"),
                OpencodeServerProcessStatus.valueOf(rs.getString("status")),
                rs.getString("session_path"),
                rs.getString("config_path"),
                instant(rs, "started_at"),
                instant(rs, "last_health_check_at"),
                rs.getString("health_message"),
                createdAt,
                updatedAt,
                rs.getString("trace_id"));
    };

    private final RowMapper<UserOpencodeProcessBinding> bindingRowMapper =
            (rs, rowNum) -> new UserOpencodeProcessBinding(
                    new UserId(rs.getString("user_id")),
                    rs.getString("agent_id"),
                    new OpencodeProcessId(rs.getString("process_id")),
                    new LinuxServerId(rs.getString("linux_server_id")),
                    rs.getInt("port"),
                    UserOpencodeProcessBindingStatus.valueOf(rs.getString("status")),
                    instant(rs, "created_at"),
                    instant(rs, "updated_at"),
                    rs.getString("trace_id"));

    /**
     * 注入 JdbcClient 和 ObjectMapper，JSON 字段统一以文本列保存。
     */
    public JdbcOpencodeProcessManagementRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public LinuxServer saveLinuxServer(LinuxServer linuxServer) {
        // 使用 INSERT ... ON CONFLICT DO UPDATE 替代 check-then-act，避免并发竞态。
        jdbcClient.sql("""
                        insert into linux_servers(
                            linux_server_id, name, status, capacity_summary_json,
                            last_heartbeat_at, trace_id, created_at, updated_at
                        )
                        values (
                            :linuxServerId, :name, :status, :capacitySummaryJson,
                            :lastHeartbeatAt, :traceId, :createdAt, :updatedAt
                        )
                        on conflict (linux_server_id) do update set
                            name = excluded.name,
                            status = excluded.status,
                            capacity_summary_json = excluded.capacity_summary_json,
                            last_heartbeat_at = excluded.last_heartbeat_at,
                            trace_id = excluded.trace_id,
                            updated_at = excluded.updated_at
                        """)
                .param("linuxServerId", linuxServer.linuxServerId().value())
                .param("name", linuxServer.name())
                .param("status", linuxServer.status().name())
                .param("capacitySummaryJson", writeMap(linuxServer.capacitySummary()))
                .param("lastHeartbeatAt", timestamp(linuxServer.lastHeartbeatAt()))
                .param("traceId", linuxServer.traceId())
                .param("createdAt", timestamp(linuxServer.createdAt()))
                .param("updatedAt", timestamp(linuxServer.updatedAt()))
                .update();
        return linuxServer;
    }

    @Override
    public Optional<LinuxServer> findLinuxServerById(LinuxServerId linuxServerId) {
        return jdbcClient.sql("""
                        select linux_server_id, name, status, capacity_summary_json,
                               last_heartbeat_at, trace_id, created_at, updated_at
                        from linux_servers
                        where linux_server_id = :linuxServerId
                        """)
                .param("linuxServerId", linuxServerId.value())
                .query(linuxServerRowMapper)
                .optional();
    }

    @Override
    public BackendJavaProcess saveBackendJavaProcess(BackendJavaProcess backendJavaProcess) {
        // 使用 INSERT ... ON CONFLICT DO UPDATE 替代 check-then-act，
        // 避免并发心跳注册时的 DuplicateKeyException。
        jdbcClient.sql("""
                        insert into backend_java_processes(
                            backend_process_id, linux_server_id, listen_url, status,
                            started_at, last_heartbeat_at, trace_id, created_at, updated_at
                        )
                        values (
                            :backendProcessId, :linuxServerId, :listenUrl, :status,
                            :startedAt, :lastHeartbeatAt, :traceId, :createdAt, :updatedAt
                        )
                        on conflict (backend_process_id) do update set
                            linux_server_id = excluded.linux_server_id,
                            listen_url = excluded.listen_url,
                            status = excluded.status,
                            started_at = excluded.started_at,
                            last_heartbeat_at = excluded.last_heartbeat_at,
                            trace_id = excluded.trace_id,
                            updated_at = excluded.updated_at
                        """)
                .param("backendProcessId", backendJavaProcess.backendProcessId().value())
                .param("linuxServerId", backendJavaProcess.linuxServerId().value())
                .param("listenUrl", backendJavaProcess.listenUrl())
                .param("status", backendJavaProcess.status().name())
                .param("startedAt", timestamp(backendJavaProcess.startedAt()))
                .param("lastHeartbeatAt", timestamp(backendJavaProcess.lastHeartbeatAt()))
                .param("traceId", backendJavaProcess.traceId())
                .param("createdAt", timestamp(backendJavaProcess.createdAt()))
                .param("updatedAt", timestamp(backendJavaProcess.updatedAt()))
                .update();
        return backendJavaProcess;
    }

    @Override
    public Optional<BackendJavaProcess> findBackendJavaProcessById(BackendProcessId backendProcessId) {
        return jdbcClient.sql("""
                        select backend_process_id, linux_server_id, listen_url, status,
                               started_at, last_heartbeat_at, trace_id, created_at, updated_at
                        from backend_java_processes
                        where backend_process_id = :backendProcessId
                        """)
                .param("backendProcessId", backendProcessId.value())
                .query(backendProcessRowMapper)
                .optional();
    }

    @Override
    public List<BackendJavaProcess> findReadyBackendJavaProcesses(Instant minHeartbeatAt, int limit) {
        validateLimit(limit);
        return jdbcClient.sql("""
                        select backend_process_id, linux_server_id, listen_url, status,
                               started_at, last_heartbeat_at, trace_id, created_at, updated_at
                        from backend_java_processes
                        where status = :status and last_heartbeat_at >= :minHeartbeatAt
                        order by last_heartbeat_at desc, backend_process_id asc
                        limit :limit
                        """)
                .param("status", BackendJavaProcessStatus.READY.name())
                .param("minHeartbeatAt", timestamp(minHeartbeatAt))
                .param("limit", limit)
                .query(backendProcessRowMapper)
                .list();
    }

    @Override
    public OpencodeContainer saveContainer(OpencodeContainer container) {
        // 使用 INSERT ... ON CONFLICT DO UPDATE 替代 check-then-act，避免并发竞态。
        jdbcClient.sql("""
                        insert into opencode_containers(
                            container_id, linux_server_id, container_name, port_start, port_end,
                            max_processes, current_processes, status, last_heartbeat_at,
                            trace_id, created_at, updated_at
                        )
                        values (
                            :containerId, :linuxServerId, :containerName, :portStart, :portEnd,
                            :maxProcesses, :currentProcesses, :status, :lastHeartbeatAt,
                            :traceId, :createdAt, :updatedAt
                        )
                        on conflict (container_id) do update set
                            linux_server_id = excluded.linux_server_id,
                            container_name = excluded.container_name,
                            port_start = excluded.port_start,
                            port_end = excluded.port_end,
                            max_processes = excluded.max_processes,
                            current_processes = excluded.current_processes,
                            status = excluded.status,
                            last_heartbeat_at = excluded.last_heartbeat_at,
                            trace_id = excluded.trace_id,
                            updated_at = excluded.updated_at
                        """)
                .param("containerId", container.containerId().value())
                .param("linuxServerId", container.linuxServerId().value())
                .param("containerName", container.containerName())
                .param("portStart", container.portStart())
                .param("portEnd", container.portEnd())
                .param("maxProcesses", container.maxProcesses())
                .param("currentProcesses", container.currentProcesses())
                .param("status", container.status().name())
                .param("lastHeartbeatAt", timestamp(container.lastHeartbeatAt()))
                .param("traceId", container.traceId())
                .param("createdAt", timestamp(container.createdAt()))
                .param("updatedAt", timestamp(container.updatedAt()))
                .update();
        return container;
    }

    @Override
    public Optional<OpencodeContainer> findContainerById(OpencodeContainerId containerId) {
        return jdbcClient.sql("""
                        select container_id, linux_server_id, container_name, port_start, port_end,
                               max_processes, current_processes, status, last_heartbeat_at,
                               trace_id, created_at, updated_at
                        from opencode_containers
                        where container_id = :containerId
                        """)
                .param("containerId", containerId.value())
                .query(containerRowMapper)
                .optional();
    }

    @Override
    public List<OpencodeContainer> findHealthyContainers(int limit) {
        validateLimit(limit);
        return jdbcClient.sql("""
                        select container_id, linux_server_id, container_name, port_start, port_end,
                               max_processes, current_processes, status, last_heartbeat_at,
                               trace_id, created_at, updated_at
                        from opencode_containers
                        where status = :status and current_processes < max_processes
                        order by current_processes asc, updated_at asc, container_id asc
                        limit :limit
                        """)
                .param("status", OpencodeContainerStatus.READY.name())
                .param("limit", limit)
                .query(containerRowMapper)
                .list();
    }

    @Override
    public List<OpencodeContainer> findHealthyContainersByLinuxServer(LinuxServerId linuxServerId, int limit) {
        validateLimit(limit);
        return jdbcClient.sql("""
                        select container_id, linux_server_id, container_name, port_start, port_end,
                               max_processes, current_processes, status, last_heartbeat_at,
                               trace_id, created_at, updated_at
                        from opencode_containers
                        where linux_server_id = :linuxServerId
                          and status = :status
                          and current_processes < max_processes
                        order by current_processes asc, updated_at asc, container_id asc
                        limit :limit
                        """)
                .param("linuxServerId", linuxServerId.value())
                .param("status", OpencodeContainerStatus.READY.name())
                .param("limit", limit)
                .query(containerRowMapper)
                .list();
    }

    @Override
    public List<OpencodeContainer> findHealthyContainersConnectedToBackend(BackendProcessId backendProcessId, int limit) {
        validateLimit(limit);
        return jdbcClient.sql("""
                        select c.container_id, c.linux_server_id, c.container_name, c.port_start, c.port_end,
                               c.max_processes, c.current_processes, c.status, c.last_heartbeat_at,
                               c.trace_id, c.created_at, c.updated_at
                        from opencode_containers c
                        join opencode_container_managers m on m.container_id = c.container_id
                        join opencode_manager_backend_connections mbc on mbc.manager_id = m.manager_id
                        where mbc.backend_process_id = :backendProcessId
                          and mbc.status = :connectionStatus
                          and m.connection_status = :connectionStatus
                          and c.status = :containerStatus
                          and c.current_processes < c.max_processes
                        order by c.current_processes asc, c.updated_at asc, c.container_id asc
                        limit :limit
                        """)
                .param("backendProcessId", backendProcessId.value())
                .param("connectionStatus", ManagerConnectionStatus.CONNECTED.name())
                .param("containerStatus", OpencodeContainerStatus.READY.name())
                .param("limit", limit)
                .query(containerRowMapper)
                .list();
    }

    @Override
    public List<OpencodeContainer> findHealthyContainersConnectedToBackendByLinuxServer(
            BackendProcessId backendProcessId,
            LinuxServerId linuxServerId,
            int limit) {
        validateLimit(limit);
        return jdbcClient.sql("""
                        select c.container_id, c.linux_server_id, c.container_name, c.port_start, c.port_end,
                               c.max_processes, c.current_processes, c.status, c.last_heartbeat_at,
                               c.trace_id, c.created_at, c.updated_at
                        from opencode_containers c
                        join opencode_container_managers m on m.container_id = c.container_id
                        join opencode_manager_backend_connections mbc on mbc.manager_id = m.manager_id
                        where mbc.backend_process_id = :backendProcessId
                          and c.linux_server_id = :linuxServerId
                          and mbc.status = :connectionStatus
                          and m.connection_status = :connectionStatus
                          and c.status = :containerStatus
                          and c.current_processes < c.max_processes
                        order by c.current_processes asc, c.updated_at asc, c.container_id asc
                        limit :limit
                        """)
                .param("backendProcessId", backendProcessId.value())
                .param("linuxServerId", linuxServerId.value())
                .param("connectionStatus", ManagerConnectionStatus.CONNECTED.name())
                .param("containerStatus", OpencodeContainerStatus.READY.name())
                .param("limit", limit)
                .query(containerRowMapper)
                .list();
    }

    @Override
    public OpencodeContainerManager saveContainerManager(OpencodeContainerManager manager) {
        // 使用 INSERT ... ON CONFLICT DO UPDATE 替代 check-then-act，避免并发竞态。
        jdbcClient.sql("""
                        insert into opencode_container_managers(
                            manager_id, container_id, linux_server_id, protocol_version,
                            connection_status, capabilities_json, last_heartbeat_at,
                            trace_id, created_at, updated_at
                        )
                        values (
                            :managerId, :containerId, :linuxServerId, :protocolVersion,
                            :connectionStatus, :capabilitiesJson, :lastHeartbeatAt,
                            :traceId, :createdAt, :updatedAt
                        )
                        on conflict (manager_id) do update set
                            container_id = excluded.container_id,
                            linux_server_id = excluded.linux_server_id,
                            protocol_version = excluded.protocol_version,
                            connection_status = excluded.connection_status,
                            capabilities_json = excluded.capabilities_json,
                            last_heartbeat_at = excluded.last_heartbeat_at,
                            trace_id = excluded.trace_id,
                            updated_at = excluded.updated_at
                        """)
                .param("managerId", manager.managerId().value())
                .param("containerId", manager.containerId().value())
                .param("linuxServerId", manager.linuxServerId().value())
                .param("protocolVersion", manager.protocolVersion())
                .param("connectionStatus", manager.connectionStatus().name())
                .param("capabilitiesJson", writeMap(manager.capabilities()))
                .param("lastHeartbeatAt", timestamp(manager.lastHeartbeatAt()))
                .param("traceId", manager.traceId())
                .param("createdAt", timestamp(manager.createdAt()))
                .param("updatedAt", timestamp(manager.updatedAt()))
                .update();
        return manager;
    }

    @Override
    public Optional<OpencodeContainerManager> findContainerManagerById(ContainerManagerId managerId) {
        return jdbcClient.sql("""
                        select manager_id, container_id, linux_server_id, protocol_version,
                               connection_status, capabilities_json, last_heartbeat_at,
                               trace_id, created_at, updated_at
                        from opencode_container_managers
                        where manager_id = :managerId
                        """)
                .param("managerId", managerId.value())
                .query(managerRowMapper)
                .optional();
    }

    @Override
    public OpencodeManagerBackendConnection saveManagerBackendConnection(
            OpencodeManagerBackendConnection connection) {
        // 使用 INSERT ... ON CONFLICT DO UPDATE 替代 check-then-act，避免并发竞态。
        jdbcClient.sql("""
                        insert into opencode_manager_backend_connections(
                            manager_id, backend_process_id, status, connected_at,
                            last_heartbeat_at, trace_id, updated_at
                        )
                        values (
                            :managerId, :backendProcessId, :status, :connectedAt,
                            :lastHeartbeatAt, :traceId, :updatedAt
                        )
                        on conflict (manager_id, backend_process_id) do update set
                            status = excluded.status,
                            connected_at = excluded.connected_at,
                            last_heartbeat_at = excluded.last_heartbeat_at,
                            trace_id = excluded.trace_id,
                            updated_at = excluded.updated_at
                        """)
                .param("managerId", connection.managerId().value())
                .param("backendProcessId", connection.backendProcessId().value())
                .param("status", connection.status().name())
                .param("connectedAt", timestamp(connection.connectedAt()))
                .param("lastHeartbeatAt", timestamp(connection.lastHeartbeatAt()))
                .param("traceId", connection.traceId())
                .param("updatedAt", timestamp(connection.updatedAt()))
                .update();
        return connection;
    }

    @Override
    public Optional<OpencodeManagerBackendConnection> findManagerBackendConnection(
            ContainerManagerId managerId,
            BackendProcessId backendProcessId) {
        return jdbcClient.sql("""
                        select manager_id, backend_process_id, status, connected_at,
                               last_heartbeat_at, trace_id, updated_at
                        from opencode_manager_backend_connections
                        where manager_id = :managerId and backend_process_id = :backendProcessId
                        """)
                .param("managerId", managerId.value())
                .param("backendProcessId", backendProcessId.value())
                .query(connectionRowMapper)
                .optional();
    }

    @Override
    public OpencodeServerProcess saveOpencodeServerProcess(OpencodeServerProcess process) {
        if (findOpencodeServerProcessById(process.processId()).isPresent()) {
            jdbcClient.sql("""
                            update opencode_server_processes
                            set user_id = :userId, linux_server_id = :linuxServerId, container_id = :containerId,
                                port = :port, pid = :pid, base_url = :baseUrl, status = :status,
                                session_path = :sessionPath, config_path = :configPath,
                                started_at = :startedAt, last_health_check_at = :lastHealthCheckAt,
                                health_message = :healthMessage, trace_id = :traceId,
                                created_at = :createdAt, updated_at = :updatedAt
                            where process_id = :processId
                            """)
                    .param("processId", process.processId().value())
                    .param("userId", process.userId().value())
                    .param("linuxServerId", process.linuxServerId().value())
                    .param("containerId", process.containerId().value())
                    .param("port", process.port())
                    .param("pid", process.pid())
                    .param("baseUrl", process.baseUrl())
                    .param("status", process.status().name())
                    .param("sessionPath", process.sessionPath())
                    .param("configPath", process.configPath())
                    .param("startedAt", timestamp(process.startedAt()))
                    .param("lastHealthCheckAt", timestamp(process.lastHealthCheckAt()))
                    .param("healthMessage", process.healthMessage())
                    .param("traceId", process.traceId())
                    .param("createdAt", timestamp(process.createdAt()))
                    .param("updatedAt", timestamp(process.updatedAt()))
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into opencode_server_processes(
                                process_id, user_id, linux_server_id, container_id, port, pid, base_url,
                                status, session_path, config_path, started_at, last_health_check_at,
                                health_message, trace_id, created_at, updated_at
                            )
                            values (
                                :processId, :userId, :linuxServerId, :containerId, :port, :pid, :baseUrl,
                                :status, :sessionPath, :configPath, :startedAt, :lastHealthCheckAt,
                                :healthMessage, :traceId, :createdAt, :updatedAt
                            )
                            """)
                    .param("processId", process.processId().value())
                    .param("userId", process.userId().value())
                    .param("linuxServerId", process.linuxServerId().value())
                    .param("containerId", process.containerId().value())
                    .param("port", process.port())
                    .param("pid", process.pid())
                    .param("baseUrl", process.baseUrl())
                    .param("status", process.status().name())
                    .param("sessionPath", process.sessionPath())
                    .param("configPath", process.configPath())
                    .param("startedAt", timestamp(process.startedAt()))
                    .param("lastHealthCheckAt", timestamp(process.lastHealthCheckAt()))
                    .param("healthMessage", process.healthMessage())
                    .param("traceId", process.traceId())
                    .param("createdAt", timestamp(process.createdAt()))
                    .param("updatedAt", timestamp(process.updatedAt()))
                    .update();
        }
        return process;
    }

    @Override
    public Optional<OpencodeServerProcess> findOpencodeServerProcessById(OpencodeProcessId processId) {
        return jdbcClient.sql("""
                        select process_id, user_id, linux_server_id, container_id, port, pid, base_url,
                               status, session_path, config_path, started_at, last_health_check_at,
                               health_message, trace_id, created_at, updated_at
                        from opencode_server_processes
                        where process_id = :processId
                        """)
                .param("processId", processId.value())
                .query(processRowMapper)
                .optional();
    }

    @Override
    public List<Integer> findOccupiedPorts(LinuxServerId linuxServerId, OpencodeContainerId containerId) {
        return jdbcClient.sql("""
                        select port
                        from opencode_server_processes
                        where linux_server_id = :linuxServerId
                        order by port asc
                        """)
                .param("linuxServerId", linuxServerId.value())
                .query(Integer.class)
                .list();
    }

    @Override
    public UserOpencodeProcessBinding saveUserBinding(UserOpencodeProcessBinding binding) {
        if (findUserBinding(binding.userId(), binding.agentId()).isPresent()) {
            jdbcClient.sql("""
                            update user_opencode_process_bindings
                            set process_id = :processId, linux_server_id = :linuxServerId, port = :port,
                                status = :status, trace_id = :traceId, updated_at = :updatedAt
                            where user_id = :userId and agent_id = :agentId
                            """)
                    .param("userId", binding.userId().value())
                    .param("agentId", binding.agentId())
                    .param("processId", binding.processId().value())
                    .param("linuxServerId", binding.linuxServerId().value())
                    .param("port", binding.port())
                    .param("status", binding.status().name())
                    .param("traceId", binding.traceId())
                    .param("updatedAt", timestamp(binding.updatedAt()))
                    .update();
        } else {
            jdbcClient.sql("""
                            insert into user_opencode_process_bindings(
                                user_id, agent_id, process_id, linux_server_id, port,
                                status, trace_id, created_at, updated_at
                            )
                            values (
                                :userId, :agentId, :processId, :linuxServerId, :port,
                                :status, :traceId, :createdAt, :updatedAt
                            )
                            """)
                    .param("userId", binding.userId().value())
                    .param("agentId", binding.agentId())
                    .param("processId", binding.processId().value())
                    .param("linuxServerId", binding.linuxServerId().value())
                    .param("port", binding.port())
                    .param("status", binding.status().name())
                    .param("traceId", binding.traceId())
                    .param("createdAt", timestamp(binding.createdAt()))
                    .param("updatedAt", timestamp(binding.updatedAt()))
                    .update();
        }
        return binding;
    }

    @Override
    public Optional<UserOpencodeProcessBinding> findUserBinding(UserId userId, String agentId) {
        return jdbcClient.sql("""
                        select user_id, agent_id, process_id, linux_server_id, port,
                               status, trace_id, created_at, updated_at
                        from user_opencode_process_bindings
                        where user_id = :userId and agent_id = :agentId
                        """)
                .param("userId", userId.value())
                .param("agentId", normalizeAgentId(agentId))
                .query(bindingRowMapper)
                .optional();
    }

    @Override
    public List<OpencodeServerProcess> findOpencodeServerProcesses(int limit) {
        validateLimit(limit);
        return jdbcClient.sql("""
                        select process_id, user_id, linux_server_id, container_id, port, pid, base_url,
                               status, session_path, config_path, started_at, last_health_check_at,
                               health_message, trace_id, created_at, updated_at
                        from opencode_server_processes
                        order by updated_at desc, process_id asc
                        limit :limit
                        """)
                .param("limit", limit)
                .query(processRowMapper)
                .list();
    }

    @Override
    public List<LinuxServer> findLinuxServers(int limit) {
        validateLimit(limit);
        return jdbcClient.sql("""
                        select linux_server_id, name, status, capacity_summary_json,
                               last_heartbeat_at, trace_id, created_at, updated_at
                        from linux_servers
                        order by updated_at desc, linux_server_id asc
                        limit :limit
                        """)
                .param("limit", limit)
                .query(linuxServerRowMapper)
                .list();
    }

    @Override
    public List<BackendJavaProcess> findBackendJavaProcesses(int limit) {
        validateLimit(limit);
        return jdbcClient.sql("""
                        select backend_process_id, linux_server_id, listen_url, status,
                               started_at, last_heartbeat_at, trace_id, created_at, updated_at
                        from backend_java_processes
                        order by updated_at desc, backend_process_id asc
                        limit :limit
                        """)
                .param("limit", limit)
                .query(backendProcessRowMapper)
                .list();
    }

    @Override
    public List<OpencodeContainer> findContainers(int limit) {
        validateLimit(limit);
        return jdbcClient.sql("""
                        select container_id, linux_server_id, container_name, port_start, port_end,
                               max_processes, current_processes, status, last_heartbeat_at,
                               trace_id, created_at, updated_at
                        from opencode_containers
                        order by linux_server_id asc, current_processes desc, container_id asc
                        limit :limit
                        """)
                .param("limit", limit)
                .query(containerRowMapper)
                .list();
    }

    @Override
    public List<OpencodeContainerManager> findContainerManagers(int limit) {
        validateLimit(limit);
        return jdbcClient.sql("""
                        select manager_id, container_id, linux_server_id, protocol_version,
                               connection_status, capabilities_json, last_heartbeat_at,
                               trace_id, created_at, updated_at
                        from opencode_container_managers
                        order by updated_at desc, manager_id asc
                        limit :limit
                        """)
                .param("limit", limit)
                .query(managerRowMapper)
                .list();
    }

    @Override
    public List<OpencodeManagerBackendConnection> findManagerBackendConnections(int limit) {
        validateLimit(limit);
        return jdbcClient.sql("""
                        select manager_id, backend_process_id, status, connected_at,
                               last_heartbeat_at, trace_id, updated_at
                        from opencode_manager_backend_connections
                        order by updated_at desc, manager_id asc, backend_process_id asc
                        limit :limit
                        """)
                .param("limit", limit)
                .query(connectionRowMapper)
                .list();
    }

    @Override
    public PageResponse<OpencodeServerProcess> findOpencodeServerProcesses(
            OpencodeServerProcessFilter filter,
            PageRequest pageRequest) {
        String whereClause = processWhereClause(filter);
        Map<String, Object> params = processFilterParams(filter);
        params.put("limit", pageRequest.size());
        params.put("offset", pageRequest.offset());
        List<OpencodeServerProcess> items = jdbcClient.sql("""
                        select process_id, user_id, linux_server_id, container_id, port, pid, base_url,
                               status, session_path, config_path, started_at, last_health_check_at,
                               health_message, trace_id, created_at, updated_at
                        from opencode_server_processes
                        %s
                        order by updated_at desc, process_id asc
                        limit :limit offset :offset
                        """.formatted(whereClause))
                .params(params)
                .query(processRowMapper)
                .list();
        long total = jdbcClient.sql("""
                        select count(*)
                        from opencode_server_processes
                        %s
                        """.formatted(whereClause))
                .params(processFilterParams(filter))
                .query(Long.class)
                .single();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), total);
    }

    @Override
    public long countOpencodeServerProcesses(OpencodeServerProcessFilter filter) {
        return jdbcClient.sql("""
                        select count(*)
                        from opencode_server_processes
                        %s
                        """.formatted(processWhereClause(filter)))
                .params(processFilterParams(filter))
                .query(Long.class)
                .single();
    }

    @Override
    public Map<OpencodeProcessId, UserOpencodeProcessBinding> findUserBindingsByProcessIds(
            List<OpencodeProcessId> processIds) {
        if (processIds == null || processIds.isEmpty()) {
            return Map.of();
        }
        StringBuilder placeholders = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < processIds.size(); i++) {
            if (i > 0) {
                placeholders.append(", ");
            }
            String name = "processId" + i;
            placeholders.append(':').append(name);
            params.put(name, processIds.get(i).value());
        }
        List<UserOpencodeProcessBinding> rows = jdbcClient.sql("""
                        select user_id, agent_id, process_id, linux_server_id, port,
                               status, trace_id, created_at, updated_at
                        from user_opencode_process_bindings
                        where process_id in (%s)
                        order by updated_at desc, process_id asc
                        """.formatted(placeholders))
                .params(params)
                .query(bindingRowMapper)
                .list();
        Map<OpencodeProcessId, UserOpencodeProcessBinding> result = new LinkedHashMap<>();
        for (UserOpencodeProcessBinding binding : rows) {
            result.put(binding.processId(), binding);
        }
        return result;
    }

    @Override
    public long countUserBindings() {
        return jdbcClient.sql("select count(*) from user_opencode_process_bindings")
                .query(Long.class)
                .single();
    }

    /**
     * agentId 与 URL 标志保持一致，统一小写并去除首尾空白。
     */
    private String normalizeAgentId(String agentId) {
        return DomainValidation.requireText(agentId, "agentId").trim().toLowerCase(Locale.ROOT);
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be between 1 and 500");
        }
    }

    /**
     * 为进程分页查询构造可复用 where 条件；所有入参仍通过命名参数绑定。
     */
    private String processWhereClause(OpencodeServerProcessFilter filter) {
        StringBuilder where = new StringBuilder("where 1 = 1");
        if (filter != null && filter.status() != null) {
            where.append(" and status = :status");
        }
        if (filter != null && filter.linuxServerId() != null) {
            where.append(" and linux_server_id = :linuxServerId");
        }
        if (filter != null && filter.containerId() != null) {
            where.append(" and container_id = :containerId");
        }
        if (filter != null && filter.userId() != null) {
            where.append(" and user_id = :userId");
        }
        return where.toString();
    }

    /**
     * 将进程筛选对象转换为 JDBC 命名参数。
     */
    private Map<String, Object> processFilterParams(OpencodeServerProcessFilter filter) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (filter != null && filter.status() != null) {
            params.put("status", filter.status().name());
        }
        if (filter != null && filter.linuxServerId() != null) {
            params.put("linuxServerId", filter.linuxServerId().value());
        }
        if (filter != null && filter.containerId() != null) {
            params.put("containerId", filter.containerId().value());
        }
        if (filter != null && filter.userId() != null) {
            params.put("userId", filter.userId().value());
        }
        return params;
    }

    /**
     * 将 JSON 文本恢复为不可变 Map。
     */
    private Map<String, Object> readMap(String json) {
        try {
            return Map.copyOf(objectMapper.readValue(json, MAP_TYPE));
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "opencode 进程管理 JSON 反序列化失败", Map.of(), exception);
        }
    }

    /**
     * 将 Map 序列化为 JSON 文本。
     */
    private String writeMap(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "opencode 进程管理 JSON 序列化失败", Map.of(), exception);
        }
    }

    /**
     * JDBC 读取 bigint 可空列时统一转换为 Long。
     */
    private Long longObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }
}
