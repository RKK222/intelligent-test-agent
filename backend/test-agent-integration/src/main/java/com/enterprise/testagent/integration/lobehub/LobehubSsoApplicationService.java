package com.enterprise.testagent.integration.lobehub;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.lobehub.LobehubGrantPayload;
import com.enterprise.testagent.domain.lobehub.LobehubSsoStore;
import com.enterprise.testagent.domain.lobehub.LobehubTicketPayload;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 平台与 LobeHub 之间的一次性登录交接及模型委托服务。 */
@Service
public class LobehubSsoApplicationService implements LobehubSsoService {

    private static final String MODEL_GATEWAY_SCOPE = "model-gateway";
    private static final String PARAM_ENABLED = "LOBEHUB_ENABLED";
    private static final String PARAM_BASE_URL = "LOBEHUB_BASE_URL";
    private static final String PARAM_EMAIL_DOMAIN = "LOBEHUB_SSO_EMAIL_DOMAIN";
    private static final String PARAM_OWNER = "LOBEHUB_INITIAL_OWNER_UNIFIED_AUTH_ID";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final DictionaryRepository dictionaryRepository;
    private final CommonParameterValues commonParameterValues;
    private final LobehubSsoStore store;
    private final LobehubIntegrationProperties properties;
    private final Clock clock;
    private final LobehubTokenFactory tokenFactory;

    /** 生产环境固定使用 UTC 时钟和 256-bit SecureRandom token。 */
    @Autowired
    public LobehubSsoApplicationService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            DictionaryRepository dictionaryRepository,
            CommonParameterValues commonParameterValues,
            LobehubSsoStore store,
            LobehubIntegrationProperties properties) {
        this(
                userRepository,
                userRoleRepository,
                dictionaryRepository,
                commonParameterValues,
                store,
                properties,
                Clock.systemUTC(),
                new SecureLobehubTokenFactory());
    }

    /** 测试构造器允许固定时钟和 token 序列，便于验证过期与轮换边界。 */
    LobehubSsoApplicationService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            DictionaryRepository dictionaryRepository,
            CommonParameterValues commonParameterValues,
            LobehubSsoStore store,
            LobehubIntegrationProperties properties,
            Clock clock,
            LobehubTokenFactory tokenFactory) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository);
        this.dictionaryRepository = Objects.requireNonNull(dictionaryRepository);
        this.commonParameterValues = Objects.requireNonNull(commonParameterValues);
        this.store = Objects.requireNonNull(store);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
        this.tokenFactory = Objects.requireNonNull(tokenFactory);
    }

    /** 校验当前平台用户并签发不超过其现有会话的 60 秒一次性票据。 */
    @Override
    public LobehubSsoTicketIssue issue(AuthPrincipal principal) {
        Objects.requireNonNull(principal, "principal must not be null");
        ensureEnabled();
        // 开关打开后必须一次性校验完整交接配置，不能先落票据再在兑换阶段失败。
        String fixedConsumeUrl = consumeUrl();
        configuredEmailDomain();
        configuredOwner();
        Instant now = clock.instant();
        Instant expiresAt = earlierOf(now.plus(properties.getTicketTtl()), principal.expiresAt());
        if (!expiresAt.isAfter(now)) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "平台会话已过期");
        }
        User user = requireActiveUser(principal.userId());
        normalizeDepartment(user.department());

        String ticket = tokenFactory.newToken();
        store.saveTicket(
                LobehubDigest.sha256(ticket),
                new LobehubTicketPayload(user.userId().value(), expiresAt),
                Duration.between(now, expiresAt));
        return new LobehubSsoTicketIssue(ticket, expiresAt, fixedConsumeUrl);
    }

    /** 原子消费票据，按当前用户、部门和角色签发新委托。 */
    @Override
    public LobehubSsoRedeemResult redeem(String ticket) {
        ensureEnabled();
        String emailDomain = configuredEmailDomain();
        String ownerUnifiedAuthId = configuredOwner();
        if (ticket == null || ticket.isBlank()) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "LobeHub 登录票据无效");
        }
        LobehubTicketPayload payload = store.consumeTicket(LobehubDigest.sha256(ticket))
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.UNAUTHENTICATED,
                        "LobeHub 登录票据无效或已使用"));
        Instant now = clock.instant();
        if (!payload.expiresAt().isAfter(now)) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "LobeHub 登录票据已过期");
        }

        User user = requireActiveUser(new UserId(payload.userId()));
        String department = normalizeDepartment(user.department());
        List<String> roles = currentRoles(user.userId());
        String grant = tokenFactory.newToken();
        Instant grantExpiresAt = now.plus(properties.getGrantTtl());
        store.rotateGrant(
                user.userId().value(),
                LobehubDigest.sha256(grant),
                new LobehubGrantPayload(
                        user.userId().value(),
                        requiredClientId(),
                        MODEL_GATEWAY_SCOPE,
                        grantExpiresAt),
                properties.getGrantTtl());

        return new LobehubSsoRedeemResult(
                user.userId().value(),
                user.unifiedAuthId(),
                user.username(),
                user.unifiedAuthId() + "@" + emailDomain,
                department,
                LobehubDigest.sha256(departmentKeyInput(department)),
                instanceRole(user.unifiedAuthId(), ownerUnifiedAuthId, roles),
                roles,
                grant,
                grantExpiresAt);
    }

    /** 对模型网关的每次请求重新检查委托与平台用户状态。 */
    @Override
    public LobehubModelIdentity authenticateModelGrant(String grant) {
        ensureEnabled();
        if (grant == null || grant.isBlank()) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "模型委托凭证无效");
        }
        LobehubGrantPayload payload = store.findGrant(LobehubDigest.sha256(grant))
                .orElseThrow(() -> new PlatformException(ErrorCode.UNAUTHENTICATED, "模型委托凭证无效"));
        if (!payload.expiresAt().isAfter(clock.instant())
                || !MODEL_GATEWAY_SCOPE.equals(payload.scope())
                || !requiredClientId().equals(payload.clientId())) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, "模型委托凭证已过期或不适用");
        }
        User user = requireActiveUser(new UserId(payload.userId()));
        return new LobehubModelIdentity(user.userId().value(), user.unifiedAuthId(), payload.scope());
    }

    /** LobeHub 显式退出或管理撤销时立即使服务端委托失效。 */
    @Override
    public void revokeModelGrant(String grant) {
        if (grant == null || grant.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "模型委托凭证不能为空");
        }
        store.revokeGrant(LobehubDigest.sha256(grant));
    }

    private User requireActiveUser(UserId userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "平台用户不存在或已删除"));
        if (!user.canLogin()) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "平台用户已停用");
        }
        return user;
    }

    private List<String> currentRoles(UserId userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(role -> dictionaryRepository.findByDictId(role.dictId()).orElse(null))
                .filter(Objects::nonNull)
                .filter(dictionary -> Dictionary.DICT_KEY_ROLE.equals(dictionary.dictKey()))
                .map(Dictionary::dictValue)
                .distinct()
                .sorted()
                .toList();
    }

    private String instanceRole(String unifiedAuthId, String ownerUnifiedAuthId, List<String> roles) {
        if (unifiedAuthId.equals(ownerUnifiedAuthId)) {
            return "owner";
        }
        return roles.contains(Dictionary.ROLE_SUPER_ADMIN) ? "admin" : "member";
    }

    private String configuredEmailDomain() {
        String domain = requiredParameter(PARAM_EMAIL_DOMAIN).toLowerCase(Locale.ROOT);
        if ("disabled.invalid".equals(domain)
                || !domain.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "LobeHub SSO 虚拟邮箱域未正确配置");
        }
        return domain;
    }

    private String configuredOwner() {
        String owner = requiredParameter(PARAM_OWNER);
        if ("NOT_CONFIGURED".equalsIgnoreCase(owner)) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "LobeHub 初始 Owner 未配置");
        }
        return owner;
    }

    private void ensureEnabled() {
        if (!Boolean.parseBoolean(requiredParameter(PARAM_ENABLED))) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "通用问答功能未启用");
        }
    }

    private String consumeUrl() {
        String baseUrl = requiredParameter(PARAM_BASE_URL);
        try {
            URI uri = new URI(baseUrl);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new PlatformException(ErrorCode.INTERNAL_ERROR, "LobeHub 基址配置无效");
            }
            String rawPath = uri.getRawPath();
            if (uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || (rawPath != null && !rawPath.isEmpty() && !"/".equals(rawPath))) {
                throw new PlatformException(ErrorCode.INTERNAL_ERROR, "LobeHub 基址配置无效");
            }
            String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            return normalized + "/api/auth/platform/consume";
        } catch (URISyntaxException exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "LobeHub 基址配置无效");
        }
    }

    private String requiredParameter(String name) {
        return commonParameterValues.resolvedValue(name)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.INTERNAL_ERROR,
                        "LobeHub 公共参数未配置: " + name));
    }

    private String requiredClientId() {
        String clientId = properties.getClientId();
        if (clientId == null || clientId.isBlank()) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "LobeHub clientId 未配置");
        }
        return clientId.trim();
    }

    static String normalizeDepartment(String rawDepartment) {
        if (rawDepartment == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "平台用户未配置部门");
        }
        String normalized = Normalizer.normalize(rawDepartment, Normalizer.Form.NFKC)
                .strip()
                .replaceAll("(?U)\\s+", " ");
        if (normalized.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "平台用户未配置部门");
        }
        return normalized;
    }

    /** 需求只折叠英文字母大小写，其他 Unicode 字符保持原部门名称语义。 */
    static String departmentKeyInput(String normalizedDepartment) {
        StringBuilder key = new StringBuilder(normalizedDepartment.length());
        for (int index = 0; index < normalizedDepartment.length(); index++) {
            char value = normalizedDepartment.charAt(index);
            key.append(value >= 'A' && value <= 'Z' ? (char) (value + ('a' - 'A')) : value);
        }
        return key.toString();
    }

    private static Instant earlierOf(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }
}
