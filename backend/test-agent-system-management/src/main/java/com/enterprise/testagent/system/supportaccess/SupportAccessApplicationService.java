package com.enterprise.testagent.system.supportaccess;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.auth.TokenSessionMarkerStore;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.dictionary.UserRoleRepository;
import com.enterprise.testagent.domain.supportaccess.SupportAccessAuditEvent;
import com.enterprise.testagent.domain.supportaccess.SupportAccessAuditQuery;
import com.enterprise.testagent.domain.supportaccess.SupportAccessGrant;
import com.enterprise.testagent.domain.supportaccess.SupportAccessGrantSession;
import com.enterprise.testagent.domain.supportaccess.SupportAccessGrantStore;
import com.enterprise.testagent.domain.supportaccess.SupportAccessRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 超级管理员问题排查只读授权服务。共享暗号不属于安全边界；授权绑定当前登录会话和实时角色。
 */
@Service
public class SupportAccessApplicationService {

    public static final String HEADER_NAME = "X-Support-Access-Grant";
    private static final int MIN_DURATION_MINUTES = 5;
    private static final int MAX_DURATION_MINUTES = 240;
    private static final Duration AUDIT_RETENTION = Duration.ofDays(365);

    private final SupportAccessRepository repository;
    private final SupportAccessGrantStore grantStore;
    private final TokenSessionMarkerStore markerStore;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final DictionaryRepository dictionaryRepository;
    private final Clock clock;
    private final Supplier<String> tokenFactory;

    @Autowired
    public SupportAccessApplicationService(
            SupportAccessRepository repository,
            SupportAccessGrantStore grantStore,
            TokenSessionMarkerStore markerStore,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            DictionaryRepository dictionaryRepository) {
        this(
                repository,
                grantStore,
                markerStore,
                userRepository,
                userRoleRepository,
                dictionaryRepository,
                Clock.systemUTC(),
                SupportAccessApplicationService::newGrantToken);
    }

    /** 测试构造器允许固定时钟与 Token 工厂。 */
    public SupportAccessApplicationService(
            SupportAccessRepository repository,
            SupportAccessGrantStore grantStore,
            TokenSessionMarkerStore markerStore,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            DictionaryRepository dictionaryRepository,
            Clock clock,
            Supplier<String> tokenFactory) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.grantStore = Objects.requireNonNull(grantStore, "grantStore must not be null");
        this.markerStore = Objects.requireNonNull(markerStore, "markerStore must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null");
        this.dictionaryRepository = Objects.requireNonNull(dictionaryRepository, "dictionaryRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.tokenFactory = Objects.requireNonNull(tokenFactory, "tokenFactory must not be null");
    }

    /**
     * 签发限时只读授权；同一平台登录会话再次签发会让旧授权立即失效。
     */
    @Transactional
    public SupportAccessGrantIssue issue(
            AuthPrincipal principal,
            String incidentId,
            String reason,
            int durationMinutes,
            boolean readOnlyAcknowledged,
            SupportAccessRequestContext requestContext) {
        User actor = requireLiveSuperAdmin(principal);
        String normalizedIncidentId = requireBounded(incidentId, "incidentId", 128);
        String normalizedReason = requireBounded(reason, "reason", 1000);
        if (durationMinutes < MIN_DURATION_MINUTES || durationMinutes > MAX_DURATION_MINUTES) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "排查授权时长必须在 5 到 240 分钟之间",
                    Map.of("minMinutes", MIN_DURATION_MINUTES, "maxMinutes", MAX_DURATION_MINUTES));
        }
        if (!readOnlyAcknowledged) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "必须确认本次访问仅用于只读问题排查");
        }
        String sessionDigest = markerStore.digest(principal.token());
        if (!markerStore.isActiveForUser(sessionDigest, actor.userId())) {
            throw unauthenticated("平台登录会话已失效");
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofMinutes(durationMinutes));
        String rawToken = tokenFactory.get();
        if (rawToken == null || !rawToken.startsWith("sat_") || rawToken.length() < 40) {
            throw new IllegalStateException("support access token factory returned an invalid token");
        }
        String grantId = RuntimeIdGenerator.supportAccessGrantId();
        String tokenDigest = TokenSessionMarkerStore.sha256(rawToken);
        SupportAccessGrant grant = new SupportAccessGrant(
                grantId,
                actor.userId(),
                actor.username(),
                normalizedIncidentId,
                normalizedReason,
                sessionDigest,
                now,
                expiresAt,
                null,
                null,
                requestContext.traceId());
        repository.saveGrant(grant);
        SupportAccessGrantSession payload = new SupportAccessGrantSession(
                grantId, actor.userId().value(), sessionDigest, tokenDigest, expiresAt);
        grantStore.rotate(payload, Duration.between(now, expiresAt)).ifPresent(previous ->
                repository.revokeGrant(previous.grantId(), now, "REPLACED_BY_NEW_GRANT"));
        appendAudit(
                grant,
                actor,
                null,
                "GRANT_ISSUED",
                "GRANT",
                grantId,
                null,
                "SUCCESS",
                null,
                requestContext);
        return new SupportAccessGrantIssue(grantId, rawToken, expiresAt);
    }

    /**
     * 生成本次排查使用的新单号。当前工程没有权威工单数据源，禁止从历史授权循环回填旧号码。
     */
    public String generateIncidentId(AuthPrincipal principal) {
        requireLiveSuperAdmin(principal);
        return RuntimeIdGenerator.supportAccessIncidentId();
    }

    /** 显式撤销当前授权。 */
    @Transactional
    public void revoke(
            AuthPrincipal principal,
            String rawGrantToken,
            String grantId,
            SupportAccessRequestContext requestContext) {
        SupportAccessAuthorization authorization = authorize(
                principal, rawGrantToken, null, "GRANT_REVOKE", "GRANT", grantId, null, requestContext, false);
        if (!authorization.grant().grantId().equals(grantId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "排查授权范围不匹配");
        }
        grantStore.revoke(authorization.session());
        repository.revokeGrant(grantId, clock.instant(), "REVOKED_BY_ACTOR");
        appendAudit(
                authorization.grant(), authorization.actor(), null, "GRANT_REVOKED", "GRANT", grantId,
                null, "SUCCESS", null, requestContext);
    }

    /**
     * 校验 HTTP 排查授权。validateTarget=true 时目标用户必须存在；身份主体始终保持 actor。
     */
    public SupportAccessAuthorization authorize(
            AuthPrincipal principal,
            String rawGrantToken,
            UserId targetUserId,
            String action,
            String resourceType,
            String resourceId,
            String path,
            SupportAccessRequestContext requestContext,
            boolean validateTarget) {
        User actor = requireLiveSuperAdmin(principal);
        String sessionDigest = markerStore.digest(principal.token());
        SupportAccessGrantSession payload = requirePayload(TokenSessionMarkerStore.sha256(requireRawGrantToken(rawGrantToken)));
        if (!payload.actorUserId().equals(actor.userId().value())
                || !payload.sessionDigest().equals(sessionDigest)
                || !markerStore.isActiveForUser(payload.sessionDigest(), actor.userId())) {
            throw unauthenticated("排查授权已失效");
        }
        SupportAccessGrant grant = requireActiveGrant(payload);
        User target = targetUserId == null ? null : requireTarget(targetUserId, validateTarget);
        return new SupportAccessAuthorization(grant, payload, actor, target);
    }

    /** WebSocket 每个 RPC 使用摘要重新校验，不信任连接建立时的角色或授权状态。 */
    public SupportAccessAuthorization authorizeByDigest(
            String actorUserId,
            String actorSessionDigest,
            String grantTokenDigest,
            UserId targetUserId) {
        SupportAccessGrantSession payload = requirePayload(grantTokenDigest);
        if (!payload.actorUserId().equals(actorUserId)
                || !payload.sessionDigest().equals(actorSessionDigest)
                || !markerStore.isActiveForUser(actorSessionDigest, new UserId(actorUserId))) {
            throw unauthenticated("排查授权已失效");
        }
        User actor = requireLiveSuperAdmin(new UserId(actorUserId));
        SupportAccessGrant grant = requireActiveGrant(payload);
        User target = requireTarget(targetUserId, true);
        return new SupportAccessAuthorization(grant, payload, actor, target);
    }

    /** 执行只读访问，并保证成功响应之前已经落库审计；审计失败时正文不返回。 */
    public <T> T executeRead(
            SupportAccessAuthorization authorization,
            String action,
            String resourceType,
            String resourceId,
            String path,
            SupportAccessRequestContext requestContext,
            Supplier<T> reader) {
        try {
            T result = reader.get();
            appendAudit(
                    authorization.grant(), authorization.actor(), authorization.target(), action, resourceType,
                    resourceId, path, "SUCCESS", null, requestContext);
            return result;
        } catch (RuntimeException exception) {
            appendAudit(
                    authorization.grant(), authorization.actor(), authorization.target(), action, resourceType,
                    resourceId, path, "FAILED", errorCode(exception), requestContext);
            throw exception;
        }
    }

    /** 记录目标切换；目标内容尚未读取也必须留下显式审计。 */
    public void recordTargetSelection(
            SupportAccessAuthorization authorization,
            SupportAccessRequestContext requestContext) {
        appendAudit(
                authorization.grant(), authorization.actor(), authorization.target(), "TARGET_SELECTED", "USER",
                authorization.target().userId().value(), null, "SUCCESS", null, requestContext);
    }

    /** 文件 WebSocket 在每个 RPC 完成后记录结果；成功审计落库前不得向浏览器发送正文。 */
    public void recordReadOutcome(
            SupportAccessAuthorization authorization,
            String action,
            String resourceType,
            String resourceId,
            String path,
            String outcome,
            String errorCode,
            SupportAccessRequestContext requestContext) {
        appendAudit(
                authorization.grant(), authorization.actor(), authorization.target(), action, resourceType,
                resourceId, path, outcome, errorCode, requestContext);
    }

    /** 所有实时超级管理员均可查询一年期审计，不要求先开启排查授权。 */
    public PageResponse<SupportAccessAuditEvent> listAuditEvents(
            AuthPrincipal principal,
            SupportAccessAuditQuery query,
            PageRequest pageRequest,
            SupportAccessRequestContext requestContext) {
        User actor = requireLiveSuperAdmin(principal);
        try {
            PageResponse<SupportAccessAuditEvent> result = repository.findAuditEvents(query, pageRequest);
            appendStandaloneAudit(actor, "AUDIT_LIST", "AUDIT", null, "SUCCESS", null, requestContext);
            return result;
        } catch (RuntimeException exception) {
            appendStandaloneAudit(actor, "AUDIT_LIST", "AUDIT", null, "FAILED", errorCode(exception), requestContext);
            throw exception;
        }
    }

    /** 每日清理超过一年保留期的排查审计与已结束授权。 */
    @Scheduled(cron = "${test-agent.support-access.retention-cron:0 35 3 * * *}")
    @Transactional
    public void purgeExpiredAudit() {
        repository.deleteExpiredBefore(clock.instant().minus(AUDIT_RETENTION));
    }

    private SupportAccessGrantSession requirePayload(String grantTokenDigest) {
        SupportAccessGrantSession payload = grantStore.findByTokenDigest(grantTokenDigest)
                .orElseThrow(() -> unauthenticated("排查授权无效或已失效"));
        if (!payload.expiresAt().isAfter(clock.instant())) {
            throw unauthenticated("排查授权已过期");
        }
        return payload;
    }

    private SupportAccessGrant requireActiveGrant(SupportAccessGrantSession payload) {
        SupportAccessGrant grant = repository.findGrant(payload.grantId())
                .orElseThrow(() -> unauthenticated("排查授权无效或已失效"));
        if (grant.actorUserId() == null
                || !grant.actorUserId().value().equals(payload.actorUserId())
                || !grant.sessionDigest().equals(payload.sessionDigest())
                || !grant.expiresAt().equals(payload.expiresAt())
                || !grant.isActiveAt(clock.instant())) {
            throw unauthenticated("排查授权无效或已失效");
        }
        return grant;
    }

    private User requireLiveSuperAdmin(AuthPrincipal principal) {
        if (principal == null || principal.isExpired()) {
            throw unauthenticated("平台登录会话已失效");
        }
        User user = requireLiveSuperAdmin(principal.userId());
        if (!Objects.equals(user.unifiedAuthId(), principal.unifiedAuthId())) {
            throw unauthenticated("平台登录会话已失效");
        }
        return user;
    }

    private User requireLiveSuperAdmin(UserId userId) {
        User user = userRepository.findByUserId(userId)
                .filter(User::canLogin)
                .orElseThrow(() -> unauthenticated("平台用户已失效"));
        boolean superAdmin = userRoleRepository.findByUserId(userId).stream()
                .map(role -> dictionaryRepository.findByDictId(role.dictId()))
                .flatMap(java.util.Optional::stream)
                .anyMatch(dictionary -> Dictionary.DICT_KEY_ROLE.equals(dictionary.dictKey())
                        && Dictionary.ROLE_SUPER_ADMIN.equals(dictionary.dictValue()));
        if (!superAdmin) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "仅超级管理员可使用问题排查入口");
        }
        return user;
    }

    private User requireTarget(UserId userId, boolean required) {
        User target = userRepository.findByUserId(userId).orElse(null);
        if (target == null && required) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "目标用户不存在", Map.of("userId", userId.value()));
        }
        return target;
    }

    private void appendAudit(
            SupportAccessGrant grant,
            User actor,
            User target,
            String action,
            String resourceType,
            String resourceId,
            String path,
            String outcome,
            String errorCode,
            SupportAccessRequestContext requestContext) {
        repository.appendAuditEvent(new SupportAccessAuditEvent(
                RuntimeIdGenerator.supportAccessAuditEventId(),
                grant.grantId(),
                actor.userId().value(),
                actor.username(),
                target == null ? null : target.userId().value(),
                target == null ? null : target.username(),
                grant.incidentId(),
                grant.reason(),
                action,
                resourceType,
                normalize(resourceId),
                digestOptional(path),
                outcome,
                errorCode,
                requestContext.traceId(),
                requestContext.ipAddress(),
                digestOptional(requestContext.userAgent()),
                clock.instant()));
    }

    private void appendStandaloneAudit(
            User actor,
            String action,
            String resourceType,
            String resourceId,
            String outcome,
            String errorCode,
            SupportAccessRequestContext requestContext) {
        repository.appendAuditEvent(new SupportAccessAuditEvent(
                RuntimeIdGenerator.supportAccessAuditEventId(), null, actor.userId().value(), actor.username(),
                null, null, null, null, action, resourceType, resourceId, null, outcome, errorCode,
                requestContext.traceId(), requestContext.ipAddress(), digestOptional(requestContext.userAgent()), clock.instant()));
    }

    private String errorCode(RuntimeException exception) {
        return exception instanceof PlatformException platformException
                ? platformException.errorCode().name()
                : ErrorCode.INTERNAL_ERROR.name();
    }

    private String requireRawGrantToken(String token) {
        if (token == null || !token.startsWith("sat_") || token.length() < 40) {
            throw unauthenticated("排查授权无效或已失效");
        }
        return token;
    }

    private String requireBounded(String value, String field, int maxLength) {
        String normalized = normalize(value);
        if (normalized == null || normalized.length() > maxLength) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    field + " 不能为空且长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private String digestOptional(String value) {
        return value == null ? null : TokenSessionMarkerStore.sha256(value);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PlatformException unauthenticated(String message) {
        return new PlatformException(ErrorCode.UNAUTHENTICATED, message);
    }

    private static String newGrantToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return "sat_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
