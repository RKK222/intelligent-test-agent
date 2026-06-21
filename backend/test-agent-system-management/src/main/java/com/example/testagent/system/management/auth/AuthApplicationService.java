package com.example.testagent.system.management.auth;

import com.example.testagent.common.id.RuntimeIdGenerator;
import com.example.testagent.domain.auth.AuthPrincipal;
import com.example.testagent.domain.auth.TokenStore;
import com.example.testagent.domain.user.User;
import com.example.testagent.domain.user.UserLoginLog;
import com.example.testagent.domain.user.UserLoginLogRepository;
import com.example.testagent.system.management.user.UserDomainService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 认证应用服务，处理用户登录、登出和 Token 校验的业务编排。
 */
public class AuthApplicationService {

    /** Token 过期时间：1天 */
    private static final Duration TOKEN_TTL = Duration.ofDays(1);

    private final UserDomainService userDomainService;
    private final TokenStore tokenStore;
    private final UserLoginLogRepository loginLogRepository;

    /**
     * 构造认证服务，注入依赖的领域服务和仓储。
     */
    public AuthApplicationService(
            UserDomainService userDomainService,
            TokenStore tokenStore,
            UserLoginLogRepository loginLogRepository) {
        this.userDomainService = userDomainService;
        this.tokenStore = tokenStore;
        this.loginLogRepository = loginLogRepository;
    }

    /**
     * 用户登录：验证凭据、生成 Token、保存登录日志。
     *
     * @param username    用户名
     * @param rawPassword 原始密码（未加密）
     * @param ipAddress   请求 IP
     * @param userAgent   浏览器 User-Agent
     * @return 认证成功后的 {@link AuthPrincipal}，包含 Token 和用户基本信息
     * @throws com.example.testagent.common.error.PlatformException 用户名或密码错误时
     */
    public AuthPrincipal login(String username, String rawPassword, String ipAddress, String userAgent) {
        User user = userDomainService.findByUsername(username);

        if (!user.canLogin()) {
            throw new com.example.testagent.common.error.PlatformException(
                    com.example.testagent.common.error.ErrorCode.FORBIDDEN, "用户账户已停用");
        }

        if (!userDomainService.verifyPassword(user, rawPassword)) {
            // 登录失败记录日志
            UserLoginLog failureLog = UserLoginLog.failure(
                    RuntimeIdGenerator.logId(), user.userId(), ipAddress, userAgent);
            loginLogRepository.save(failureLog);
            throw new com.example.testagent.common.error.PlatformException(
                    com.example.testagent.common.error.ErrorCode.UNAUTHENTICATED, "用户名或密码错误");
        }

        // 生成 Token
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        AuthPrincipal principal = new AuthPrincipal(
                token, user.userId(), user.username(), user.unifiedAuthId(),
                now, now.plus(TOKEN_TTL));

        // 保存 Token 到 Redis
        tokenStore.save(principal);

        // 记录成功登录日志
        UserLoginLog successLog = UserLoginLog.success(
                RuntimeIdGenerator.logId(), user.userId(), ipAddress, userAgent);
        loginLogRepository.save(successLog);

        return principal;
    }

    /**
     * 用户登出：从存储中删除 Token。
     */
    public void logout(String token) {
        tokenStore.delete(token);
    }

    /**
     * 刷新 Token：根据已有认证主体创建新 Token，旧 Token 失效。
     *
     * @param oldPrincipal 当前有效的认证主体
     * @param ipAddress    请求 IP
     * @param userAgent    浏览器 User-Agent
     * @return 新的 {@link AuthPrincipal}
     */
    public AuthPrincipal refreshToken(AuthPrincipal oldPrincipal, String ipAddress, String userAgent) {
        // 删除旧 Token
        tokenStore.delete(oldPrincipal.token());

        // 生成新 Token
        String newToken = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        AuthPrincipal newPrincipal = new AuthPrincipal(
                newToken, oldPrincipal.userId(), oldPrincipal.username(), oldPrincipal.unifiedAuthId(),
                now, now.plus(TOKEN_TTL));

        // 保存新 Token
        tokenStore.save(newPrincipal);

        return newPrincipal;
    }

    /**
     * 验证 Token 是否有效并返回认证主体。
     *
     * @return 如果 Token 有效返回 {@link AuthPrincipal}，否则返回空
     */
    public AuthPrincipal validateToken(String token) {
        return tokenStore.findByToken(token)
                .filter(principal -> !principal.isExpired())
                .orElse(null);
    }
}
