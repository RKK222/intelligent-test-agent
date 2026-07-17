package com.enterprise.testagent.system.management.user;

import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 用户密码诊断工具，用于检查用户密码是否正确存储。
 *
 * 使用方法：
 * 1. 确保应用已启动
 * 2. 调用 diagnoseUserPassword 方法，传入用户名和预期的原始密码
 * 3. 查看诊断结果
 */
public class UserPasswordDiagnostic {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserPasswordDiagnostic(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 诊断用户密码问题
     *
     * @param username 用户名
     * @param expectedRawPassword 预期的原始密码（如 "123456"）
     */
    public void diagnoseUserPassword(String username, String expectedRawPassword) {
        System.out.println("=== 用户密码诊断开始 ===");
        System.out.println("用户名: " + username);
        System.out.println("预期密码: " + expectedRawPassword);

        // 查找用户
        var userOpt = userRepository.findByUsername(username);

        if (userOpt.isEmpty()) {
            System.out.println("❌ 错误：用户不存在！");
            return;
        }

        User user = userOpt.get();
        System.out.println("✓ 用户存在");
        System.out.println("用户ID: " + user.userId().value());
        System.out.println("用户名: " + user.username());
        System.out.println("统一认证号: " + user.unifiedAuthId());
        System.out.println("状态: " + user.status());
        System.out.println("密码Hash: " + user.passwordHash());

        // 验证密码
        boolean matches = passwordEncoder.matches(expectedRawPassword, user.passwordHash());

        if (matches) {
            System.out.println("✓ 密码验证成功！");
        } else {
            System.out.println("❌ 密码验证失败！");
            System.out.println();
            System.out.println("可能的原因：");
            System.out.println("1. 用户创建时使用的不是默认密码 123456");
            System.out.println("2. 数据库中的password_hash字段被错误修改");
            System.out.println("3. 用户通过其他方式创建（不是通过设置中的用户管理）");

            // 尝试用默认密码验证
            boolean matchesDefault = passwordEncoder.matches(
                UserManagementApplicationService.DEFAULT_PASSWORD,
                user.passwordHash()
            );

            if (matchesDefault) {
                System.out.println();
                System.out.println("✓ 提示：该用户使用的是默认密码 123456");
            } else {
                System.out.println();
                System.out.println("❌ 提示：该用户的密码不是默认密码 123456");
                System.out.println("建议：删除该用户后重新创建，或修改数据库中的password_hash");
            }
        }

        System.out.println("=== 用户密码诊断结束 ===");
    }
}
