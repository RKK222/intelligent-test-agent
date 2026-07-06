package com.icbc.testagent.system.management.user;

import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.user.User;
import com.icbc.testagent.domain.user.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 用户领域服务，封装用户的创建、查询和密码校验业务逻辑。
 */
public class UserDomainService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 使用用户仓储和 BCrypt 密码编码器创建服务。
     */
    public UserDomainService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
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
     * 根据统一认证号查找用户，不存在时自动创建。
     * user_id、unified_auth_id、username 均使用统一认证号，
     * password_hash 使用统一认证号经 BCrypt 加密。
     */
    public User findOrCreateByUnifiedAuthId(String unifiedAuthId) {
        return userRepository.findByUnifiedAuthId(unifiedAuthId)
                .orElseGet(() -> {
                    String passwordHash = passwordEncoder.encode(unifiedAuthId);
                    User user = User.createNew(
                            unifiedAuthId, unifiedAuthId, unifiedAuthId,
                            passwordHash, null, null, null);
                    userRepository.save(user);
                    return user;
                });
    }
}
