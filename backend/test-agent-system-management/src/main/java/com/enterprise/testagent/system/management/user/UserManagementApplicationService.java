package com.enterprise.testagent.system.management.user;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRole;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.run.ConversationContextUserMutation;
import org.springframework.beans.factory.annotation.Autowired;
import com.enterprise.testagent.system.management.user.UserManagementResponses.CreateUserCommand;
import com.enterprise.testagent.system.management.user.UserManagementResponses.RoleOption;
import com.enterprise.testagent.system.management.user.UserManagementResponses.UpdateUserRoleCommand;
import com.enterprise.testagent.system.management.user.UserManagementResponses.UserResponse;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户管理应用服务，封装查询用户列表、创建测试用户、调整角色和查询可选角色的业务编排。
 *
 * <p>仅用于研发测试：创建用户时使用固定默认密码，并为其授予单个角色。业务层不依赖 API 模块，
 * 通过领域仓储接口操作用户与角色数据。
 */
@Service
public class UserManagementApplicationService {

    /** 测试用户默认密码，仅用于研发测试便捷造号，正式环境不应依赖该入口。 */
    public static final String DEFAULT_PASSWORD = "123456";

    private final UserDomainService userDomainService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final DictionaryRepository dictionaryRepository;
    private ConversationContextStore conversationContextStore;

    /** 角色调整属于权限边界变化，在关系型写入前后按用户清除运行上下文。 */
    @Autowired(required = false)
    void setConversationContextStore(ConversationContextStore conversationContextStore) {
        this.conversationContextStore = conversationContextStore;
    }

    /**
     * 注入用户领域服务与用户、角色、字典仓储。
     */
    public UserManagementApplicationService(
            UserDomainService userDomainService,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            DictionaryRepository dictionaryRepository) {
        this.userDomainService = Objects.requireNonNull(userDomainService, "userDomainService must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null");
        this.dictionaryRepository = Objects.requireNonNull(dictionaryRepository, "dictionaryRepository must not be null");
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
        User user = userRepository.findByUserId(new com.enterprise.testagent.domain.user.UserId(command.userId()))
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

    private void abortUserMutation(ConversationContextUserMutation mutation, RuntimeException original) {
        try {
            conversationContextStore.abortUserMutation(mutation);
        } catch (RuntimeException abortFailure) {
            original.addSuppressed(abortFailure);
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
}
