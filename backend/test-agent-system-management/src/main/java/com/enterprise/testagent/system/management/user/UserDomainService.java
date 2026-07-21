package com.enterprise.testagent.system.management.user;

import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserRepository;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class UserDomainService {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(UserDomainService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ThirdPartyUserApiClient thirdPartyUserApiClient;

    public UserDomainService(UserRepository userRepository, ThirdPartyUserApiClient thirdPartyUserApiClient) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.thirdPartyUserApiClient = thirdPartyUserApiClient;
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

    public User findOrCreateByUnifiedAuthId(String unifiedAuthId) {
        return userRepository.findByUnifiedAuthId(unifiedAuthId)
                .orElseGet(() -> {
                    Optional<UserManagementResponses.ThirdPartyUserInfoResponse> thirdPartyInfoOpt =
                            thirdPartyUserApiClient.getUserByLoginName(unifiedAuthId);
                    String passwordHash = passwordEncoder.encode(UUID.randomUUID().toString());
                    User user;
                    if (thirdPartyInfoOpt.isPresent()) {
                        UserManagementResponses.ThirdPartyUserInfoResponse thirdPartyInfo = thirdPartyInfoOpt.get();
                        user = User.createNew(
                                thirdPartyInfo.loginname(),
                                unifiedAuthId,
                                thirdPartyInfo.fullname(),
                                passwordHash,
                                null,
                                thirdPartyInfo.basement(),
                                thirdPartyInfo.departname());
                    } else {
                        // 第三方接口调用失败或超时，降级到原逻辑
                        LOGGER.warn("Third party user info API failed, fallback to default user creation for unifiedAuthId: {}", unifiedAuthId);
                        user = User.createNew(
                                unifiedAuthId, unifiedAuthId, unifiedAuthId,
                                passwordHash, null, null, null);
                    }
                    userRepository.save(user);
                    return user;
                });
    }
}
