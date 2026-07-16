package com.enterprise.testagent.system.management.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 验证BCrypt密码加密和验证逻辑是否正常工作。
 */
class PasswordVerificationTest {

    @Test
    void bcryptPasswordCanBeVerified() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        String rawPassword = "123456";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // 验证加密后的密码可以被验证
        assertThat(passwordEncoder.matches(rawPassword, encodedPassword)).isTrue();

        // 验证错误密码不会通过
        assertThat(passwordEncoder.matches("wrong_password", encodedPassword)).isFalse();

        // 验证相同的密码每次加密后的hash都不同（BCrypt的salt机制）
        String encodedPassword2 = passwordEncoder.encode(rawPassword);
        assertThat(encodedPassword).isNotEqualTo(encodedPassword2);
        assertThat(passwordEncoder.matches(rawPassword, encodedPassword2)).isTrue();
    }

    @Test
    void defaultPasswordMatches() {
        // 验证默认密码常量
        String defaultPassword = UserManagementApplicationService.DEFAULT_PASSWORD;
        assertThat(defaultPassword).isEqualTo("123456");

        // 验证加密和验证
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String encoded = passwordEncoder.encode(defaultPassword);
        assertThat(passwordEncoder.matches("123456", encoded)).isTrue();
    }
}
