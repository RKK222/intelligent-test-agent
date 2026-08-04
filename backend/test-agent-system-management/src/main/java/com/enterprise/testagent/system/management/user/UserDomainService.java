package com.enterprise.testagent.system.management.user;

import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRole;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class UserDomainService {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(UserDomainService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ThirdPartyUserApiClient thirdPartyUserApiClient;
    private final UserRoleRepository userRoleRepository;
    private final DictionaryRepository dictionaryRepository;
    private TransactionTemplate transactionTemplate;

    public UserDomainService(
            UserRepository userRepository,
            ThirdPartyUserApiClient thirdPartyUserApiClient,
            UserRoleRepository userRoleRepository,
            DictionaryRepository dictionaryRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.thirdPartyUserApiClient = Objects.requireNonNull(
                thirdPartyUserApiClient, "thirdPartyUserApiClient must not be null");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null");
        this.dictionaryRepository = Objects.requireNonNull(dictionaryRepository, "dictionaryRepository must not be null");
    }

    /**
     * 统一认证建号时让 users 与 user_roles 在同一短事务内提交，避免产生无角色账号。
     */
    @Autowired(required = false)
    void setPlatformTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 注册新用户，用户名和统一认证号不能重复。
     *
     * @throws PlatformException 当用户名或统一认证号已存在时
     */
    public User registerUser(
            String unifiedAuthId,
            String username,
            String rawPassword,
            String organization,
            String rdDepartment,
            String department) {
        if (userRepository.existsByUsername(username)) {
            throw new PlatformException(ErrorCode.CONFLICT, "用户名已存在");
        }
        if (userRepository.existsByUnifiedAuthId(unifiedAuthId)) {
            throw new PlatformException(ErrorCode.CONFLICT, "统一认证号已存在");
        }
        String passwordHash = passwordEncoder.encode(rawPassword);
        String userId = RuntimeIdGenerator.userId();
        User user = User.createNew(
                userId, unifiedAuthId, username,
                passwordHash, organization, rdDepartment, department);
        userRepository.save(user);
        return user;
    }

    /**
     * 验证用户密码是否正确。
     */
    public boolean verifyPassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.passwordHash());
    }

    /**
     * 根据用户名查找用户。
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new PlatformException(ErrorCode.UNAUTHENTICATED, "用户名或密码错误"));
    }

    /**
     * 按统一认证号读取用户；首次登录时先查询 TCDS，再以普通用户角色原子建号。
     *
     * <p>已存在用户（包括历史空角色用户）保持原授权，不在登录链路静默改权。
     */
    public User findOrCreateByUnifiedAuthId(String unifiedAuthId) {
        Optional<User> existing = userRepository.findByUnifiedAuthId(unifiedAuthId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // TCDS 是外部网络调用，先完成资料查询，再进入只包含数据库写入的短事务。
        Optional<UserManagementResponses.ThirdPartyUserInfoResponse> thirdPartyInfo =
                thirdPartyUserApiClient.getUserByLoginName(unifiedAuthId);
        User newUser = createUnifiedAuthUser(unifiedAuthId, thirdPartyInfo);
        if (transactionTemplate == null) {
            return persistUnifiedAuthUserWithDefaultRole(unifiedAuthId, newUser);
        }
        return transactionTemplate.execute(status -> persistUnifiedAuthUserWithDefaultRole(unifiedAuthId, newUser));
    }

    /** 根据 TCDS 资料构造待持久化用户，外部资料不可用时保留既有降级建号语义。 */
    private User createUnifiedAuthUser(
            String unifiedAuthId,
            Optional<UserManagementResponses.ThirdPartyUserInfoResponse> thirdPartyInfo) {
        String passwordHash = passwordEncoder.encode(UUID.randomUUID().toString());
        if (thirdPartyInfo.isPresent()) {
            UserManagementResponses.ThirdPartyUserInfoResponse profile = thirdPartyInfo.get();
            return User.createNew(
                    profile.loginname(),
                    unifiedAuthId,
                    profile.fullname(),
                    passwordHash,
                    null,
                    profile.basement(),
                    profile.departname());
        }
        // 第三方接口调用失败或超时，仍允许登录，但必须同时授予最小权限的普通用户角色。
        LOGGER.warn(
                "Third party user info API failed, fallback to default user creation for unifiedAuthId: {}",
                unifiedAuthId);
        return User.createNew(
                unifiedAuthId, unifiedAuthId, unifiedAuthId,
                passwordHash, null, null, null);
    }

    /** 在当前事务内再次检查并同时保存用户与最小权限角色。 */
    private User persistUnifiedAuthUserWithDefaultRole(String unifiedAuthId, User newUser) {
        // 事务内再次检查，尽量避免同一认证号并发首次登录时重复建号。
        Optional<User> existing = userRepository.findByUnifiedAuthId(unifiedAuthId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Dictionary defaultRole = dictionaryRepository
                .findByDictKeyAndValue(Dictionary.DICT_KEY_ROLE, Dictionary.ROLE_USER)
                .orElseThrow(() -> new PlatformException(ErrorCode.INTERNAL_ERROR, "默认普通用户角色未配置"));
        userRepository.save(newUser);
        userRoleRepository.save(UserRole.create(newUser.userId(), defaultRole.dictId()));
        return newUser;
    }
}
