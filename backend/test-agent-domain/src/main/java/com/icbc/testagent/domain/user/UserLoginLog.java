package com.icbc.testagent.domain.user;

import java.time.Instant;
import java.util.Objects;

/**
 * 用户登录日志实体，记录用户的登录行为和浏览器信息。
 */
public record UserLoginLog(
        String logId,
        UserId userId,
        Instant loginAt,
        String ipAddress,
        String userAgent,
        LoginResult loginResult) {

    /**
     * 登录结果枚举。
     */
    public enum LoginResult {
        /**
         * 登录成功。
         */
        SUCCESS,
        /**
         * 登录失败。
         */
        FAILURE
    }

    /**
     * 校验登录日志的不变量。
     */
    public UserLoginLog {
        Objects.requireNonNull(logId, "logId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(loginAt, "loginAt must not be null");
        Objects.requireNonNull(loginResult, "loginResult must not be null");
        if (logId.isBlank()) {
            throw new IllegalArgumentException("logId must not be blank");
        }
    }

    /**
     * 创建成功登录日志的静态工厂方法。
     */
    public static UserLoginLog success(String logId, UserId userId, String ipAddress, String userAgent) {
        return new UserLoginLog(logId, userId, Instant.now(), ipAddress, userAgent, LoginResult.SUCCESS);
    }

    /**
     * 创建失败登录日志的静态工厂方法。
     */
    public static UserLoginLog failure(String logId, UserId userId, String ipAddress, String userAgent) {
        return new UserLoginLog(logId, userId, Instant.now(), ipAddress, userAgent, LoginResult.FAILURE);
    }
}
