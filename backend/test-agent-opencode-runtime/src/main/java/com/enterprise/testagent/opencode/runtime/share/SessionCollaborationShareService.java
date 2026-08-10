package com.enterprise.testagent.opencode.runtime.share;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.sessionshare.SessionShare;
import com.enterprise.testagent.domain.sessionshare.SessionShareAuditEvent;
import com.enterprise.testagent.domain.sessionshare.SessionShareCandidate;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.sessionshare.SessionShareMembership;
import com.enterprise.testagent.domain.sessionshare.SessionShareMembershipStatus;
import com.enterprise.testagent.domain.sessionshare.SessionShareParticipant;
import com.enterprise.testagent.domain.sessionshare.SessionShareRepository;
import com.enterprise.testagent.domain.sessionshare.SessionShareStatus;
import com.enterprise.testagent.domain.sessionshare.SharedSessionListItem;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 会话协作分享管理、列表和访问上下文编排。 */
@Service
public class SessionCollaborationShareService {

    private static final Duration AUDIT_RETENTION = Duration.ofDays(365);

    private final SessionShareRepository shareRepository;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final SecureRandom secureRandom;

    /** 生产环境固定使用 UTC 时钟和 256 位 SecureRandom 分享标识。 */
    @Autowired
    public SessionCollaborationShareService(
            SessionShareRepository shareRepository,
            SessionRepository sessionRepository,
            UserRepository userRepository) {
        this(shareRepository, sessionRepository, userRepository, Clock.systemUTC(), new SecureRandom());
    }

    /** 测试构造器允许固定时间与随机源，生产代码不得传入可预测随机源。 */
    SessionCollaborationShareService(
            SessionShareRepository shareRepository,
            SessionRepository sessionRepository,
            UserRepository userRepository,
            Clock clock,
            SecureRandom secureRandom) {
        this.shareRepository = Objects.requireNonNull(shareRepository);
        this.sessionRepository = Objects.requireNonNull(sessionRepository);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.clock = Objects.requireNonNull(clock);
        this.secureRandom = Objects.requireNonNull(secureRandom);
    }

    /** 查询所属人的当前分享设置；没有分享时返回空。 */
    public SessionShare get(UserId actor, SessionId sessionId) {
        Session session = requireOwnedActiveSession(actor, sessionId);
        return shareRepository.findBySessionId(session.sessionId()).orElse(null);
    }

    /** 首次创建、重新启用或全量更新同一个分享链接。 */
    @Transactional
    public SessionShare put(
            UserId actor,
            SessionId sessionId,
            Long expectedVersion,
            Instant expiresAt,
            List<SessionShareMemberCommand> memberCommands,
            String traceId) {
        Session session = requireOwnedActiveSession(actor, sessionId);
        Instant now = clock.instant();
        List<SessionShareMembership> memberships = resolveMembers(actor, memberCommands, now);
        SessionShare existing = shareRepository.findBySessionId(sessionId).orElse(null);
        SessionShare result;
        String action;
        if (existing == null) {
            if (expectedVersion != null) {
                throw versionConflict(-1, expectedVersion);
            }
            result = SessionShare.create(
                    nextShareId(), sessionId, session.workspaceId(), actor,
                    expiresAt, memberships, now, traceId);
            shareRepository.insert(result);
            action = "SHARE_CREATED";
        } else {
            requireShareOwner(actor, existing);
            if (expectedVersion == null || expectedVersion.longValue() != existing.version()) {
                throw versionConflict(existing.version(), expectedVersion);
            }
            result = existing.status() == SessionShareStatus.REVOKED || !existing.activeAt(now)
                    ? existing.reactivate(expiresAt, memberships, now, traceId)
                    : existing.update(expiresAt, memberships, now, traceId);
            if (!shareRepository.update(result, expectedVersion)) {
                throw versionConflict(existing.version(), expectedVersion);
            }
            action = existing.status() == SessionShareStatus.REVOKED || !existing.activeAt(now)
                    ? "SHARE_REACTIVATED"
                    : "SHARE_UPDATED";
        }
        appendAudit(result, actor, action, "SESSION", sessionId.value(), "SUCCESS", null, traceId, now);
        return result;
    }

    /** 取消分享；链接与成员历史永久保留，后续重新启用仍复用原 shareId。 */
    @Transactional
    public SessionShare revoke(
            UserId actor,
            SessionId sessionId,
            long expectedVersion,
            String traceId) {
        requireOwnedActiveSession(actor, sessionId);
        SessionShare existing = shareRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "会话尚未分享"));
        requireShareOwner(actor, existing);
        if (existing.version() != expectedVersion) {
            throw versionConflict(existing.version(), expectedVersion);
        }
        Instant now = clock.instant();
        SessionShare revoked = existing.revoke(now, traceId);
        if (revoked != existing && !shareRepository.update(revoked, expectedVersion)) {
            throw versionConflict(existing.version(), expectedVersion);
        }
        appendAudit(revoked, actor, "SHARE_REVOKED", "SESSION", sessionId.value(),
                "SUCCESS", null, traceId, now);
        return revoked;
    }

    /** 分页查询当前登录人收到的分享，包含已过期、取消、移除和会话归档历史。 */
    public PageResponse<SharedSessionListItem> listSharedWith(UserId actor, PageRequest pageRequest) {
        requireActiveUser(actor);
        return shareRepository.findSharedWith(actor, pageRequest);
    }

    /** 普通用户目录仅返回可用平台用户的三个安全字段。 */
    public PageResponse<SessionShareCandidate> findCandidates(
            UserId actor,
            String keyword,
            PageRequest pageRequest) {
        requireActiveUser(actor);
        return shareRepository.findCandidates(actor, keyword, pageRequest);
    }

    /**
     * 解析分享访问；真实 actor 不被替换，调用方必须显式使用 executionOwnerUserId 执行 OpenCode 操作。
     */
    public DelegatedOperationContext requireAccess(
            UserId actor,
            SessionShareId shareId,
            boolean requireChat,
            String traceId) {
        return resolveAccess(actor, shareId, requireChat, traceId, true);
    }

    /** 长连接每次刷新都重新鉴权，但不重复写入高频 READ_ACCESS 审计。 */
    public DelegatedOperationContext refreshAccess(
            UserId actor,
            SessionShareId shareId,
            String traceId) {
        return resolveAccess(actor, shareId, false, traceId, false);
    }

    private DelegatedOperationContext resolveAccess(
            UserId actor,
            SessionShareId shareId,
            boolean requireChat,
            String traceId,
            boolean audit) {
        User actorUser = requireActiveUser(actor);
        SessionShare share = shareRepository.findByShareId(shareId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "会话分享不存在"));
        try {
            Session session = sessionRepository.findById(share.sessionId())
                    .orElseThrow(() -> shareExpired("SESSION_MISSING"));
            if (!session.workspaceId().equals(share.workspaceId())) {
                throw shareExpired("SCOPE_CHANGED");
            }

            boolean ownerAccess = actor.equals(share.ownerUserId());
            boolean canChat;
            if (ownerAccess) {
                if (session.status() == SessionStatus.ARCHIVED) {
                    throw shareExpired("SESSION_ARCHIVED");
                }
                canChat = true;
            } else {
                requireActiveUser(share.ownerUserId());
                requireActiveShare(share, session);
                SessionShareMembership membership = share.membership(actor)
                        .orElseThrow(() -> new PlatformException(
                                ErrorCode.FORBIDDEN, "当前用户不在会话分享成员中"));
                if (membership.status() != SessionShareMembershipStatus.ACTIVE) {
                    throw shareExpired("REMOVED");
                }
                canChat = membership.canChat();
            }

            DelegatedOperationContext context = new DelegatedOperationContext(
                    share.shareId(), share.version(), actor, actorUser.unifiedAuthId(), actorUser.username(),
                    share.ownerUserId(), share.sessionId(), share.workspaceId(), canChat,
                    !ownerAccess, ownerAccess, share.expiresAt());
            if (requireChat) {
                context.requireChat();
            }
            if (audit) {
                appendAudit(share, actor, requireChat ? "WRITE_ACCESS_GRANTED" : "READ_ACCESS_GRANTED",
                        "SESSION", share.sessionId().value(), "SUCCESS", null, traceId, clock.instant());
            }
            return context;
        } catch (PlatformException failure) {
            if (audit) {
                appendAudit(share, actor, requireChat ? "WRITE_ACCESS_DENIED" : "READ_ACCESS_DENIED",
                        "SESSION", share.sessionId().value(), "DENIED",
                        failure.errorCode().name(), traceId, clock.instant());
            }
            throw failure;
        }
    }

    /** 返回消息气泡展示所需的所属人和历史成员目录，不暴露密码或其它用户资料。 */
    public List<SessionShareParticipant> participantDirectory(DelegatedOperationContext context) {
        Objects.requireNonNull(context, "context must not be null");
        SessionShare share = shareRepository.findByShareId(context.shareId())
                .filter(item -> item.sessionId().equals(context.sessionId()))
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "会话分享不存在"));
        User owner = requireActiveUser(share.ownerUserId());
        List<SessionShareParticipant> participants = new ArrayList<>();
        participants.add(new SessionShareParticipant(
                owner.userId(), owner.unifiedAuthId(), owner.username(), true, true, null));
        share.memberships().forEach(member -> participants.add(new SessionShareParticipant(
                member.userId(), member.unifiedAuthId(), member.username(), false,
                member.canChat(), member.status())));
        return List.copyOf(participants);
    }

    /** DTO 展示按真实用户 ID 查询当前平台姓名；账号停用不应抹掉既有消息的可选归因。 */
    public String findUsername(UserId userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findByUserId(userId)
                .map(User::username)
                .filter(username -> username != null && !username.isBlank())
                .orElse(null);
    }

    /** 默认每天清理超过 365 天的分享审计；保留天数固定，执行时间可由部署参数调整。 */
    @Scheduled(cron = "${test-agent.session-share.audit-retention-cron:0 45 3 * * *}")
    public void deleteExpiredAuditEvents() {
        shareRepository.deleteAuditEventsBefore(clock.instant().minus(AUDIT_RETENTION));
    }

    /**
     * 记录分享范围内的敏感操作。调用方只可传入资源标识和可选路径，路径在此处立即摘要，禁止明文落库。
     */
    public void recordOperation(
            DelegatedOperationContext context,
            String action,
            String resourceType,
            String resourceId,
            String resourcePath,
            String outcome,
            String errorCode,
            String traceId) {
        Objects.requireNonNull(context, "context must not be null");
        shareRepository.appendAudit(new SessionShareAuditEvent(
                RuntimeIdGenerator.sessionShareAuditEventId(), context.shareId(), context.sessionId(),
                context.workspaceId(), context.actorUserId(), context.executionOwnerUserId(),
                action, resourceType, resourceId, sha256(resourcePath), outcome, errorCode,
                traceId, clock.instant()));
    }

    /**
     * 长连接授权已失效时仍使用票据中的不可变范围快照记录拒绝结果，避免审计因无法刷新权限而丢失。
     */
    public void recordOperationSnapshot(
            SessionShareId shareId,
            SessionId sessionId,
            com.enterprise.testagent.domain.workspace.WorkspaceId workspaceId,
            UserId actor,
            UserId executionOwner,
            String action,
            String resourceType,
            String resourceId,
            String resourcePath,
            String outcome,
            String errorCode,
            String traceId) {
        shareRepository.appendAudit(new SessionShareAuditEvent(
                RuntimeIdGenerator.sessionShareAuditEventId(), shareId, sessionId, workspaceId,
                actor, executionOwner, action, resourceType, resourceId, sha256(resourcePath),
                outcome, errorCode, traceId, clock.instant()));
    }

    private Session requireOwnedActiveSession(UserId actor, SessionId sessionId) {
        requireActiveUser(actor);
        Session session = sessionRepository.findById(sessionId)
                .filter(item -> item.status() == SessionStatus.ACTIVE)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Session 不存在"));
        UserId owner = session.createdByUserId();
        if (owner == null && shareRepository.claimLegacySessionOwner(sessionId, actor)) {
            session = sessionRepository.findById(sessionId)
                    .filter(item -> item.status() == SessionStatus.ACTIVE)
                    .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Session 不存在"));
            owner = session.createdByUserId();
        }
        if (owner == null || !owner.equals(actor)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "仅会话所属人可以管理分享");
        }
        return session;
    }

    private List<SessionShareMembership> resolveMembers(
            UserId owner,
            List<SessionShareMemberCommand> commands,
            Instant now) {
        if (commands == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "分享成员不能为空");
        }
        List<SessionShareMembership> memberships = new ArrayList<>();
        for (SessionShareMemberCommand command : commands) {
            if (owner.equals(command.userId())) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "会话所属人不能作为被分享人");
            }
            User user = userRepository.findByUserId(command.userId())
                    .filter(User::canLogin)
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.VALIDATION_ERROR,
                            "被分享用户不存在或已停用",
                            Map.of("userId", command.userId().value())));
            memberships.add(SessionShareMembership.active(
                    user.userId(), user.unifiedAuthId(), user.username(), command.canChat(), now));
        }
        try {
            // 复用聚合的唯一性与 50 人边界；临时构造不会写入仓储。
            SessionShare.create(nextValidationShareId(), new SessionId("ses_validation_share"),
                    new com.enterprise.testagent.domain.workspace.WorkspaceId("wrk_validation_share"),
                    owner, now.plusSeconds(1), memberships, now, "trace_validation_share");
        } catch (IllegalArgumentException invalid) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, invalid.getMessage());
        }
        return List.copyOf(memberships);
    }

    private void requireActiveShare(SessionShare share, Session session) {
        if (session.status() == SessionStatus.ARCHIVED) {
            throw shareExpired("SESSION_ARCHIVED");
        }
        if (share.status() == SessionShareStatus.REVOKED) {
            throw shareExpired("REVOKED");
        }
        if (!share.expiresAt().isAfter(clock.instant())) {
            throw shareExpired("EXPIRED");
        }
    }

    private User requireActiveUser(UserId userId) {
        return userRepository.findByUserId(userId)
                .filter(User::canLogin)
                .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "用户不可用"));
    }

    private void requireShareOwner(UserId actor, SessionShare share) {
        if (!share.ownerUserId().equals(actor)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "仅会话所属人可以管理分享");
        }
    }

    private SessionShareId nextShareId() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return new SessionShareId("shr_" + HexFormat.of().formatHex(bytes));
    }

    private String sha256(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK 缺少 SHA-256", impossible);
        }
    }

    /** 验证成员聚合时使用固定合法 ID，不能额外消耗生产分享随机数。 */
    private SessionShareId nextValidationShareId() {
        return new SessionShareId(
                "shr_0000000000000000000000000000000000000000000000000000000000000000");
    }

    private PlatformException versionConflict(long currentVersion, Long expectedVersion) {
        return new PlatformException(
                ErrorCode.SESSION_SHARE_VERSION_CONFLICT,
                "分享设置已变化，请刷新后重试",
                Map.of(
                        "currentVersion", currentVersion,
                        "expectedVersion", expectedVersion == null ? "missing" : expectedVersion));
    }

    private PlatformException shareExpired(String reason) {
        return new PlatformException(
                ErrorCode.SESSION_SHARE_EXPIRED,
                "会话分享已失效",
                Map.of("reason", reason));
    }

    private void appendAudit(
            SessionShare share,
            UserId actor,
            String action,
            String resourceType,
            String resourceId,
            String outcome,
            String errorCode,
            String traceId,
            Instant occurredAt) {
        shareRepository.appendAudit(new SessionShareAuditEvent(
                RuntimeIdGenerator.sessionShareAuditEventId(), share.shareId(), share.sessionId(),
                share.workspaceId(), actor, share.ownerUserId(), action, resourceType, resourceId,
                null, outcome, errorCode, traceId, occurredAt));
    }
}
