package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessAssignmentConflictException;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessAtomicMutationPort;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessReservationLockPort;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.enterprise.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 在短事务内原子预留用户绑定和服务器端口；manager 外部调用必须在本服务返回后执行。
 */
@Service
public class OpencodeProcessReservationService {

    private static final String AGENT_ID = "opencode";
    private static final int LOCAL_LOCK_STRIPES = 128;

    private final OpencodeProcessManagementRepository repository;
    private final OpencodeProcessReservationLockPort lockPort;
    private final OpencodeProcessAtomicMutationPort atomicMutationPort;
    private final Clock clock;
    private final ReentrantLock[] localUserLocks = lockStripes();
    private final ReentrantLock[] localServerLocks = lockStripes();

    @Autowired
    public OpencodeProcessReservationService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessReservationLockPort lockPort,
            OpencodeProcessAtomicMutationPort atomicMutationPort) {
        this(repository, lockPort, atomicMutationPort, Clock.systemUTC());
    }

    /** 兼容无 Spring 装配的既有单测；生产路径使用上面的数据库 CAS 构造器。 */
    public OpencodeProcessReservationService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessReservationLockPort lockPort) {
        this(repository, lockPort, new RepositoryBackedOpencodeProcessAtomicMutationPort(repository), Clock.systemUTC());
    }

    OpencodeProcessReservationService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessReservationLockPort lockPort,
            Clock clock) {
        this(repository, lockPort, new RepositoryBackedOpencodeProcessAtomicMutationPort(repository), clock);
    }

    OpencodeProcessReservationService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessReservationLockPort lockPort,
            OpencodeProcessAtomicMutationPort atomicMutationPort,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.lockPort = Objects.requireNonNull(lockPort, "lockPort must not be null");
        this.atomicMutationPort = Objects.requireNonNull(atomicMutationPort, "atomicMutationPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 首次分配：锁内复查 ACTIVE binding，再用数据库和 manager 端口并集选择首个空闲端口。
     */
    @Transactional
    public Optional<OpencodeProcessReservation> reserveInitial(
            UserId userId,
            OpencodeContainer container,
            IntFunction<String> baseUrlFactory,
            String sessionPath,
            String configPath,
            Set<Integer> managerOccupiedPorts,
            String traceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(container, "container must not be null");
        Objects.requireNonNull(baseUrlFactory, "baseUrlFactory must not be null");
        return withOrderedLocks(userId, container.linuxServerId(), () -> {
            Optional<UserOpencodeProcessBinding> existing = activeBinding(userId);
            if (existing.isPresent()) {
                return Optional.of(authoritativeReservation(existing.get(), true));
            }
            Optional<Integer> port = firstAvailablePort(container, managerOccupiedPorts);
            if (port.isEmpty()) {
                return Optional.empty();
            }
            Instant now = Instant.now(clock);
            OpencodeServerProcess process = new OpencodeServerProcess(
                    new OpencodeProcessId(RuntimeIdGenerator.opencodeProcessId()),
                    userId,
                    container.linuxServerId(),
                    container.containerId(),
                    port.get(),
                    null,
                    baseUrlFactory.apply(port.get()),
                    OpencodeServerProcessStatus.STARTING,
                    sessionPath,
                    configPath,
                    now,
                    now,
                    "端口已预留，等待 manager 启动",
                    now,
                    now,
                    traceId);
            UserOpencodeProcessBinding binding = new UserOpencodeProcessBinding(
                    userId,
                    AGENT_ID,
                    process.processId(),
                    process.linuxServerId(),
                    process.port(),
                    UserOpencodeProcessBindingStatus.ACTIVE,
                    now,
                    now,
                    traceId);
            repository.saveOpencodeServerProcess(process);
            repository.saveUserBinding(binding);
            return Optional.of(new OpencodeProcessReservation(process, binding, false));
        });
    }

    /**
     * 同服务器迁移：锁内比较预期旧分配；若其它请求已迁移则返回胜者，否则保留身份与创建时间。
     */
    @Transactional
    public Optional<OpencodeProcessReservation> reserveMigration(
            UserOpencodeProcessBinding expectedBinding,
            OpencodeServerProcess expectedProcess,
            OpencodeContainer replacement,
            IntFunction<String> baseUrlFactory,
            Set<Integer> managerOccupiedPorts,
            String traceId) {
        Objects.requireNonNull(expectedBinding, "expectedBinding must not be null");
        Objects.requireNonNull(expectedProcess, "expectedProcess must not be null");
        Objects.requireNonNull(replacement, "replacement must not be null");
        if (!expectedProcess.linuxServerId().equals(replacement.linuxServerId())) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "TestAgent 进程不允许跨 Linux 服务器迁移");
        }
        return withOrderedLocks(expectedBinding.userId(), replacement.linuxServerId(), () -> {
            UserOpencodeProcessBinding currentBinding = activeBinding(expectedBinding.userId())
                    .orElseThrow(() -> unavailable("TestAgent 用户绑定已不存在"));
            if (!sameExpectedAssignment(currentBinding, expectedBinding)) {
                return Optional.of(authoritativeReservation(currentBinding, true));
            }
            OpencodeServerProcess currentProcess = repository.findOpencodeServerProcessById(currentBinding.processId())
                    .orElseThrow(() -> unavailable("TestAgent 用户绑定缺少权威进程"));
            Optional<Integer> port = firstAvailablePort(replacement, managerOccupiedPorts);
            if (port.isEmpty()) {
                return Optional.empty();
            }
            Instant now = Instant.now(clock);
            OpencodeServerProcess reserved = new OpencodeServerProcess(
                    currentProcess.processId(),
                    currentProcess.userId(),
                    replacement.linuxServerId(),
                    replacement.containerId(),
                    port.get(),
                    null,
                    baseUrlFactory.apply(port.get()),
                    OpencodeServerProcessStatus.STARTING,
                    currentProcess.sessionPath(),
                    currentProcess.configPath(),
                    now,
                    now,
                    "迁移端口已预留，等待 manager 启动",
                    currentProcess.createdAt(),
                    now,
                    traceId);
            UserOpencodeProcessBinding reservedBinding = new UserOpencodeProcessBinding(
                    currentBinding.userId(),
                    currentBinding.agentId(),
                    reserved.processId(),
                    reserved.linuxServerId(),
                    reserved.port(),
                    UserOpencodeProcessBindingStatus.ACTIVE,
                    currentBinding.createdAt(),
                    now,
                    traceId);
            try {
                atomicMutationPort.compareAndSetAssignment(
                        expectedProcess,
                        expectedBinding,
                        reserved,
                        reservedBinding);
            } catch (OpencodeProcessAssignmentConflictException exception) {
                // CAS 冲突必须使事务回滚，并对调用方 fail closed，不能继续启动未获权威预留的端口。
                throw new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "TestAgent 进程分配已被并发修改",
                        java.util.Map.of(),
                        exception);
            }
            return Optional.of(new OpencodeProcessReservation(reserved, reservedBinding, false));
        });
    }

    private Optional<UserOpencodeProcessBinding> activeBinding(UserId userId) {
        return repository.findUserBinding(userId, AGENT_ID)
                .filter(binding -> binding.status() == UserOpencodeProcessBindingStatus.ACTIVE);
    }

    private OpencodeProcessReservation authoritativeReservation(
            UserOpencodeProcessBinding binding,
            boolean concurrentWinner) {
        OpencodeServerProcess process = repository.findOpencodeServerProcessById(binding.processId())
                .orElseThrow(() -> unavailable("TestAgent 用户绑定缺少权威进程"));
        return new OpencodeProcessReservation(process, binding, concurrentWinner);
    }

    private Optional<Integer> firstAvailablePort(OpencodeContainer container, Set<Integer> managerOccupiedPorts) {
        Set<Integer> occupied = new HashSet<>(repository.findOccupiedPorts(
                container.linuxServerId(), container.containerId()));
        if (managerOccupiedPorts != null) {
            occupied.addAll(managerOccupiedPorts);
        }
        for (int port = container.portStart(); port <= container.portEnd(); port++) {
            if (!occupied.contains(port)) {
                return Optional.of(port);
            }
        }
        return Optional.empty();
    }

    private boolean sameExpectedAssignment(
            UserOpencodeProcessBinding current,
            UserOpencodeProcessBinding expected) {
        return current.processId().equals(expected.processId())
                && current.linuxServerId().equals(expected.linuxServerId())
                && current.port() == expected.port();
    }

    private <T> T withOrderedLocks(
            UserId userId,
            LinuxServerId linuxServerId,
            java.util.function.Supplier<T> action) {
        ReentrantLock userLock = stripedLock(localUserLocks, userId.value());
        ReentrantLock serverLock = stripedLock(localServerLocks, linuxServerId.value());
        userLock.lock();
        try {
            serverLock.lock();
            try {
                // 本地锁保证无事务代理的单测也能覆盖并发；生产一致性由下列数据库行锁保证。
                if (!lockPort.lockUser(userId) || !lockPort.lockLinuxServer(linuxServerId)) {
                    throw unavailable("TestAgent 端口预留所需权威记录不存在");
                }
                return action.get();
            } finally {
                serverLock.unlock();
            }
        } finally {
            userLock.unlock();
        }
    }

    /** 固定条带锁避免按用户永久累积锁对象；跨实例/跨节点一致性仍由数据库行锁保证。 */
    private static ReentrantLock[] lockStripes() {
        ReentrantLock[] locks = new ReentrantLock[LOCAL_LOCK_STRIPES];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new ReentrantLock();
        }
        return locks;
    }

    private static ReentrantLock stripedLock(ReentrantLock[] locks, String key) {
        return locks[Math.floorMod(key.hashCode(), locks.length)];
    }

    private PlatformException unavailable(String message) {
        return new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, message);
    }
}
