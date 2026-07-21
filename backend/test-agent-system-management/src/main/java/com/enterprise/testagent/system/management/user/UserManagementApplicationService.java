package com.enterprise.testagent.system.management.user;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.auth.TokenStore;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRole;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.run.ConversationContextUserMutation;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserDeletionRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.system.management.user.UserManagementResponses.DeleteUsersCommand;
import com.enterprise.testagent.system.management.user.UserManagementResponses.DeleteUsersResponse;
import com.enterprise.testagent.system.management.user.UserManagementResponses.CreateUserCommand;
import com.enterprise.testagent.system.management.user.UserManagementResponses.RoleOption;
import com.enterprise.testagent.system.management.user.UserManagementResponses.SyncUsersFromTcdsCommand;
import com.enterprise.testagent.system.management.user.UserManagementResponses.SyncUsersFromTcdsResponse;
import com.enterprise.testagent.system.management.user.UserManagementResponses.UpdateUserRoleCommand;
import com.enterprise.testagent.system.management.user.UserManagementResponses.UserResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户管理应用服务，封装查询、测试造号、角色调整、存量账号删除和 TCDS 信息同步。
 *
 * <p>仅用于研发测试：创建用户时使用固定默认密码，并为其授予单个角色。业务层不依赖 API 模块，
 * 通过领域仓储接口操作用户与角色数据。
 */
@Service
public class UserManagementApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserManagementApplicationService.class);

    /** 测试用户默认密码，仅用于研发测试便捷造号，正式环境不应依赖该入口。 */
    public static final String DEFAULT_PASSWORD = "123456";

    /** 单次删除上限，限制动态 SQL、Redis Token 扫描和事务锁定规模。 */
    public static final int MAX_DELETE_BATCH_SIZE = 100;

    /** TCDS 当前仅提供单用户查询，批量同步限制规模并以最多四路并发调用。 */
    public static final int MAX_TCDS_SYNC_BATCH_SIZE = 20;
    private static final int TCDS_SYNC_CONCURRENCY = 4;

    private final UserDomainService userDomainService;
    private final UserRepository userRepository;
    private final UserDeletionRepository userDeletionRepository;
    private final UserRoleRepository userRoleRepository;
    private final DictionaryRepository dictionaryRepository;
    private final TokenStore tokenStore;
    private final ThirdPartyUserApiClient thirdPartyUserApiClient;
    private ConversationContextStore conversationContextStore;
    private TransactionTemplate transactionTemplate;

    /** 角色调整属于权限边界变化，在关系型写入前后按用户清除运行上下文。 */
    @Autowired(required = false)
    void setConversationContextStore(ConversationContextStore conversationContextStore) {
        this.conversationContextStore = conversationContextStore;
    }

    /** TCDS 网络调用完成后再开启短数据库事务，避免外部等待长期占用数据库连接。 */
    @Autowired(required = false)
    void setPlatformTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 注入用户领域服务与用户、角色、字典仓储。
     */
    public UserManagementApplicationService(
            UserDomainService userDomainService,
            UserRepository userRepository,
            UserDeletionRepository userDeletionRepository,
            UserRoleRepository userRoleRepository,
            DictionaryRepository dictionaryRepository,
            TokenStore tokenStore,
            ThirdPartyUserApiClient thirdPartyUserApiClient) {
        this.userDomainService = Objects.requireNonNull(userDomainService, "userDomainService must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.userDeletionRepository = Objects.requireNonNull(userDeletionRepository, "userDeletionRepository must not be null");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null");
        this.dictionaryRepository = Objects.requireNonNull(dictionaryRepository, "dictionaryRepository must not be null");
        this.tokenStore = Objects.requireNonNull(tokenStore, "tokenStore must not be null");
        this.thirdPartyUserApiClient = Objects.requireNonNull(thirdPartyUserApiClient, "thirdPartyUserApiClient must not be null");
    }

    /**
     * 分页查询用户列表，按关键字匹配用户名/统一认证号，响应剔除密码哈希并装配角色信息。
     */
    public PageResponse<UserResponse> listUsers(String keyword, PageRequest pageRequest) {
        PageResponse<User> page = userRepository.findPage(keyword, pageRequest);
        return new PageResponse<>(
                page.items().stream().map(this::userResponse).toList(),
                page.page(),
                page.size(),
                page.total());
    }

    /**
     * 创建测试用户：使用默认密码注册，并授予指定单个角色。
     *
     * @throws PlatformException 当角色无效、用户名或统一认证号重复时
     */
    @Transactional
    public UserResponse createUser(CreateUserCommand command) {
        String roleCode = command.role();
        if (roleCode == null || roleCode.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "角色不能为空");
        }
        // 校验角色 code 存在于 ROLE 字典，同时拿到 dictId 用于授权
        Dictionary roleDictionary = dictionaryRepository
                .findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, roleCode)
                .orElseThrow(() -> new PlatformException(ErrorCode.VALIDATION_ERROR, "角色无效"));

        // 注册用户（内部完成 BCrypt 加密、唯一性校验、usr_ ID 生成），密码使用测试默认值
        User user = userDomainService.registerUser(
                command.unifiedAuthId(),
                command.username(),
                DEFAULT_PASSWORD,
                command.organization(),
                command.rdDepartment(),
                command.department());

        // 授予选定角色
        userRoleRepository.save(UserRole.create(user.userId(), roleDictionary.dictId()));

        // 重新读取以带回刚授予的角色
        return userResponse(userRepository.findByUserId(user.userId())
                .orElseThrow(() -> new PlatformException(ErrorCode.INTERNAL_ERROR, "用户创建后读取失败")));
    }

    /**
     * 替换指定用户的全局角色。
     *
     * <p>该入口服务于研发测试用户管理页：一次只保留一个角色，先校验用户和目标角色存在，
     * 再在同一事务内删除旧角色并写入新角色，避免前端看到多角色半更新状态。
     *
     * @throws PlatformException 当用户不存在或角色无效时
     */
    @Transactional
    public UserResponse updateUserRole(UpdateUserRoleCommand command) {
        User user = userRepository.findByUserId(new UserId(command.userId()))
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "用户不存在"));
        String roleCode = command.role();
        if (roleCode == null || roleCode.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "角色不能为空");
        }
        Dictionary roleDictionary = dictionaryRepository
                .findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, roleCode)
                .orElseThrow(() -> new PlatformException(ErrorCode.VALIDATION_ERROR, "角色无效"));

        ConversationContextUserMutation mutation = conversationContextStore == null
                ? null
                : conversationContextStore.beginUserMutation(user.userId());
        boolean transactionCompletionRegistered = registerMutationCompletion(mutation);
        try {
            List<UserRole> existingRoles = userRoleRepository.findByUserId(user.userId());
            existingRoles.forEach(userRoleRepository::delete);
            userRoleRepository.save(UserRole.create(user.userId(), roleDictionary.dictId()));
        } catch (RuntimeException exception) {
            if (mutation != null && !transactionCompletionRegistered) {
                abortUserMutation(mutation, exception);
            }
            throw exception;
        }
        if (mutation != null && !transactionCompletionRegistered) {
            conversationContextStore.completeUserMutation(mutation);
        }
        return userResponse(user);
    }

    /**
     * 单个或批量物理删除未承载业务资产的存量用户。
     *
     * <p>操作全有或全无：任一用户不存在、包含当前登录账号或仍被会话/工作区/运行态引用时，
     * 整批拒绝。可安全附属的角色、登录日志、应用成员、SSH key、个人偏好、反馈和用户统计
     * 由持久化端口在同一事务中清理；共享业务资产不做级联删除。
     */
    @Transactional
    public DeleteUsersResponse deleteUsers(DeleteUsersCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        List<UserId> userIds = normalizeUserIds(command.userIds(), MAX_DELETE_BATCH_SIZE, "删除");
        UserId operatorUserId = new UserId(command.operatorUserId());
        if (userIds.contains(operatorUserId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "不能删除当前登录用户");
        }

        List<UserId> existingUserIds = userDeletionRepository.lockExistingUserIds(userIds);
        List<String> missingUserIds = difference(userIds, existingUserIds);
        if (!missingUserIds.isEmpty()) {
            throw new PlatformException(
                    ErrorCode.NOT_FOUND,
                    "部分用户不存在，未执行删除",
                    Map.of("userIds", missingUserIds));
        }

        List<UserId> blockedUserIds = userDeletionRepository.findDeletionBlockedUserIds(userIds);
        if (!blockedUserIds.isEmpty()) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "部分用户仍有关联的会话、工作区、运行进程或业务配置，请改用 TCDS 信息同步",
                    Map.of("userIds", blockedUserIds.stream().map(UserId::value).toList()));
        }

        List<ConversationContextUserMutation> mutations = beginUserMutations(userIds);
        boolean transactionCompletionRegistered = registerDeletionCompletion(mutations, userIds);
        try {
            // 先撤销现有登录态；即使后续关系型事务失败，最多要求目标用户重新登录，不会留下越权 Token。
            tokenStore.deleteByUserIds(userIds);
            int deletedCount = userDeletionRepository.deleteUsers(userIds);
            if (deletedCount != userIds.size()) {
                throw new PlatformException(ErrorCode.CONFLICT, "用户数据已发生变化，未能完成整批删除");
            }
        } catch (RuntimeException exception) {
            if (!transactionCompletionRegistered) {
                abortUserMutations(mutations, exception);
            }
            throw exception;
        }
        if (!transactionCompletionRegistered) {
            completeUserMutations(mutations);
            tokenStore.deleteByUserIds(userIds);
        }
        List<String> deletedUserIds = userIds.stream().map(UserId::value).toList();
        return new DeleteUsersResponse(deletedUserIds, deletedUserIds.size());
    }

    /**
     * 从 TCDS 原位刷新一个或多个存量用户的姓名、研发部门和部门。
     *
     * <p>外部查询在数据库事务之外以有界并发执行；全部用户查询成功后才开启短事务更新，
     * userId、统一认证号、角色和应用成员关系均保持不变。
     */
    public SyncUsersFromTcdsResponse syncUsersFromTcds(SyncUsersFromTcdsCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        List<UserId> userIds = normalizeUserIds(command.userIds(), MAX_TCDS_SYNC_BATCH_SIZE, "TCDS 同步");
        List<User> users = loadExistingUsers(userIds);
        List<TcdsUserProfile> profiles = fetchTcdsProfiles(users);

        Runnable saveAction = () -> saveTcdsProfiles(profiles);
        if (transactionTemplate == null) {
            saveAction.run();
        } else {
            transactionTemplate.executeWithoutResult(status -> saveAction.run());
        }

        List<String> syncedUserIds = userIds.stream().map(UserId::value).toList();
        return new SyncUsersFromTcdsResponse(syncedUserIds, syncedUserIds.size());
    }

    /**
     * 生产事务在真正 commit 后才原子完成 Redis gate；回滚时仅释放当前 gate。
     */
    private boolean registerMutationCompletion(ConversationContextUserMutation mutation) {
        if (mutation == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                conversationContextStore.completeUserMutation(mutation);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                conversationContextStore.abortUserMutation(mutation);
            }
        });
        return true;
    }

    /**
     * 删除提交后再次撤销 Token 以收敛并发登录窗口，并完成用户级上下文失效；回滚只释放闸门。
     */
    private boolean registerDeletionCompletion(
            List<ConversationContextUserMutation> mutations,
            List<UserId> userIds) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                completeUserMutations(mutations);
                try {
                    tokenStore.deleteByUserIds(userIds);
                } catch (RuntimeException exception) {
                    // 首次撤销已在删除前完成；这里仅收敛极小的并发登录窗口，不反转已提交的数据库事实。
                    LOGGER.error("Failed to revoke concurrent tokens after user deletion commit", exception);
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                abortUserMutations(mutations, null);
            }
        });
        return true;
    }

    private void abortUserMutation(ConversationContextUserMutation mutation, RuntimeException original) {
        try {
            conversationContextStore.abortUserMutation(mutation);
        } catch (RuntimeException abortFailure) {
            original.addSuppressed(abortFailure);
        }
    }

    private List<UserId> normalizeUserIds(List<String> rawUserIds, int maxSize, String operationName) {
        if (rawUserIds == null || rawUserIds.isEmpty()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "用户 ID 列表不能为空");
        }
        LinkedHashSet<UserId> normalized = new LinkedHashSet<>();
        for (String rawUserId : rawUserIds) {
            if (rawUserId == null || rawUserId.isBlank()) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "用户 ID 不能为空");
            }
            normalized.add(new UserId(rawUserId.trim()));
        }
        if (normalized.size() > maxSize) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "%s单次最多处理 %d 个用户".formatted(operationName, maxSize));
        }
        return List.copyOf(normalized);
    }

    private List<String> difference(List<UserId> requested, Collection<UserId> actual) {
        return requested.stream()
                .filter(userId -> !actual.contains(userId))
                .map(UserId::value)
                .toList();
    }

    private List<ConversationContextUserMutation> beginUserMutations(List<UserId> userIds) {
        if (conversationContextStore == null) {
            return List.of();
        }
        List<ConversationContextUserMutation> mutations = new ArrayList<>();
        try {
            for (UserId userId : userIds) {
                mutations.add(conversationContextStore.beginUserMutation(userId));
            }
            return List.copyOf(mutations);
        } catch (RuntimeException exception) {
            abortUserMutations(mutations, exception);
            throw exception;
        }
    }

    private void completeUserMutations(List<ConversationContextUserMutation> mutations) {
        for (ConversationContextUserMutation mutation : mutations) {
            conversationContextStore.completeUserMutation(mutation);
        }
    }

    private void abortUserMutations(List<ConversationContextUserMutation> mutations, RuntimeException original) {
        for (ConversationContextUserMutation mutation : mutations) {
            try {
                conversationContextStore.abortUserMutation(mutation);
            } catch (RuntimeException abortFailure) {
                if (original != null) {
                    original.addSuppressed(abortFailure);
                } else {
                    LOGGER.error("Failed to abort user context mutation after transaction rollback", abortFailure);
                }
            }
        }
    }

    private List<User> loadExistingUsers(List<UserId> userIds) {
        List<User> users = new ArrayList<>();
        List<String> missingUserIds = new ArrayList<>();
        for (UserId userId : userIds) {
            userRepository.findByUserId(userId).ifPresentOrElse(
                    users::add,
                    () -> missingUserIds.add(userId.value()));
        }
        if (!missingUserIds.isEmpty()) {
            throw new PlatformException(
                    ErrorCode.NOT_FOUND,
                    "部分用户不存在，未执行 TCDS 同步",
                    Map.of("userIds", missingUserIds));
        }
        return List.copyOf(users);
    }

    private List<TcdsUserProfile> fetchTcdsProfiles(List<User> users) {
        int workerCount = Math.min(TCDS_SYNC_CONCURRENCY, users.size());
        ExecutorService executor = Executors.newFixedThreadPool(
                workerCount,
                Thread.ofVirtual().name("tcds-user-sync-", 0).factory());
        try {
            List<Future<TcdsUserProfile>> futures = users.stream()
                    .map(user -> executor.submit(() -> fetchTcdsProfile(user)))
                    .toList();
            List<TcdsUserProfile> profiles = new ArrayList<>();
            for (Future<TcdsUserProfile> future : futures) {
                try {
                    profiles.add(future.get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new PlatformException(ErrorCode.INTERNAL_ERROR, "TCDS 用户信息同步被中断");
                } catch (ExecutionException exception) {
                    if (exception.getCause() instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new PlatformException(ErrorCode.INTERNAL_ERROR, "TCDS 用户信息同步失败");
                }
            }
            return List.copyOf(profiles);
        } finally {
            executor.shutdownNow();
        }
    }

    private TcdsUserProfile fetchTcdsProfile(User user) {
        UserManagementResponses.ThirdPartyUserInfoResponse response = thirdPartyUserApiClient
                .getUserByLoginName(user.unifiedAuthId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.INTERNAL_ERROR,
                        "未能从 TCDS 获取用户信息，请稍后重试",
                        Map.of("userId", user.userId().value())));
        if (response.fullname() == null || response.fullname().isBlank()) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "TCDS 返回的用户姓名为空，未执行同步",
                    Map.of("userId", user.userId().value()));
        }
        return new TcdsUserProfile(
                user.userId(),
                response.fullname().trim(),
                response.basement(),
                response.departname());
    }

    private void saveTcdsProfiles(List<TcdsUserProfile> profiles) {
        for (TcdsUserProfile profile : profiles) {
            User current = userRepository.findByUserId(profile.userId())
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.CONFLICT,
                            "用户数据已发生变化，未执行 TCDS 同步",
                            Map.of("userId", profile.userId().value())));
            userRepository.save(current.refreshExternalProfile(
                    profile.username(),
                    profile.rdDepartment(),
                    profile.department()));
        }
    }

    /**
     * 查询可选角色列表，来自 ROLE 字典，按 sortOrder 排序。
     */
    public List<RoleOption> listRoles() {
        return dictionaryRepository.findByDictKey(Dictionary.DICT_KEY_ROLE).stream()
                .sorted(java.util.Comparator.comparingInt(Dictionary::sortOrder))
                .map(dictionary -> new RoleOption(dictionary.dictValue(), dictionary.dictLabel()))
                .toList();
    }

    /**
     * 将用户聚合根映射为响应模型，装配角色 code 与中文标签，剔除密码哈希。
     */
    private UserResponse userResponse(User user) {
        List<UserRole> userRoles = userRoleRepository.findByUserId(user.userId());
        List<Dictionary> roleDictionaries = userRoles.stream()
                .map(role -> dictionaryRepository.findByDictId(role.dictId()))
                .flatMap(java.util.Optional::stream)
                .filter(dictionary -> Dictionary.DICT_KEY_ROLE.equals(dictionary.dictKey()))
                .sorted(java.util.Comparator.comparingInt(Dictionary::sortOrder))
                .toList();
        List<String> roles = roleDictionaries.stream().map(Dictionary::dictValue).toList();
        List<String> roleLabels = roleDictionaries.stream().map(Dictionary::dictLabel).toList();
        return new UserResponse(
                user.userId().value(),
                user.username(),
                user.unifiedAuthId(),
                user.organization(),
                user.rdDepartment(),
                user.department(),
                user.status().name(),
                roles,
                roleLabels,
                user.createdAt());
    }

    private record TcdsUserProfile(
            UserId userId,
            String username,
            String rdDepartment,
            String department) {
    }
}
