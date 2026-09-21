package com.enterprise.testagent.system.management.team;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.auth.TokenSessionMarkerStore;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.RoleCapabilities;
import com.enterprise.testagent.domain.team.SystemAdminTeamMember;
import com.enterprise.testagent.domain.team.SystemAdminTeamMemberView;
import com.enterprise.testagent.domain.team.SystemAdminTeamRepository;
import com.enterprise.testagent.domain.team.TeamOversightAuditEvent;
import com.enterprise.testagent.domain.team.TeamOversightAuditRepository;
import com.enterprise.testagent.domain.team.TeamScopeMode;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserManagementQuery;
import com.enterprise.testagent.domain.user.UserManagementQueryRepository;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.system.management.auth.LiveRoleCapabilityService;
import com.enterprise.testagent.system.management.auth.LiveRoleCapabilityService.LiveActor;
import com.enterprise.testagent.system.management.team.SystemAdminTeamResponses.TeamMutationResponse;
import com.enterprise.testagent.system.management.team.SystemAdminTeamResponses.TeamScope;
import com.enterprise.testagent.system.management.team.SystemAdminTeamResponses.TeamUserResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统管理员团队名单、范围解析、实时授权和团队审计的唯一业务入口。 */
@Service
public class SystemAdminTeamApplicationService {

    private final SystemAdminTeamRepository teams;
    private final TeamOversightAuditRepository auditRepository;
    private final UserRepository users;
    private final UserManagementQueryRepository userQueries;
    private final LiveRoleCapabilityService liveRoles;
    private final Clock clock;

    public SystemAdminTeamApplicationService(
            SystemAdminTeamRepository teams,
            TeamOversightAuditRepository auditRepository,
            UserRepository users,
            UserManagementQueryRepository userQueries,
            LiveRoleCapabilityService liveRoles,
            Clock clock) {
        this.teams = Objects.requireNonNull(teams, "teams must not be null");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository must not be null");
        this.users = Objects.requireNonNull(users, "users must not be null");
        this.userQueries = Objects.requireNonNull(userQueries, "userQueries must not be null");
        this.liveRoles = Objects.requireNonNull(liveRoles, "liveRoles must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** 超级管理员分页选择一个实际持有 SYSTEM_ADMIN 角色的团队负责人。 */
    public PageResponse<TeamUserResponse> listSystemAdmins(
            AuthPrincipal principal, String keyword, PageRequest pageRequest, TeamOversightRequestContext context) {
        LiveActor actor = liveRoles.require(principal, Dictionary.ROLE_SUPER_ADMIN);
        PageResponse<User> page = userQueries.findPage(
                new UserManagementQuery(keyword, Dictionary.ROLE_SYSTEM_ADMIN, null, null, null), pageRequest);
        PageResponse<TeamUserResponse> response = mapUsers(page, null);
        audit(actor.user(), null, "SYSTEM_ADMINS_LIST", "TEAM_OWNER", null, null, "SUCCESS", null, context);
        return response;
    }

    public PageResponse<TeamUserResponse> listMembers(
            AuthPrincipal principal,
            TeamScopeMode mode,
            String ownerUserId,
            String keyword,
            PageRequest pageRequest,
            TeamOversightRequestContext context) {
        AuthorizedScope authorized = authorizeScope(principal, mode, ownerUserId);
        if (authorized.scope().global()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "全平台范围不维护团队名单");
        }
        PageResponse<SystemAdminTeamMemberView> page = teams.findMembers(
                new UserId(authorized.scope().ownerUserId()), keyword, pageRequest);
        List<TeamUserResponse> items = page.items().stream()
                .map(item -> response(item.user(), item.addedAt()))
                .toList();
        PageResponse<TeamUserResponse> response = new PageResponse<>(items, page.page(), page.size(), page.total());
        audit(authorized.actor().user(), null, "TEAM_MEMBERS_LIST", "TEAM",
                authorized.scope().ownerUserId(), null, "SUCCESS", null, context);
        return response;
    }

    public PageResponse<TeamUserResponse> listCandidates(
            AuthPrincipal principal,
            TeamScopeMode mode,
            String ownerUserId,
            String keyword,
            PageRequest pageRequest,
            TeamOversightRequestContext context) {
        AuthorizedScope authorized = authorizeScope(principal, mode, ownerUserId);
        if (authorized.scope().global()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "全平台范围不维护团队名单");
        }
        PageResponse<User> page = teams.findCandidates(
                new UserId(authorized.scope().ownerUserId()), keyword, pageRequest);
        PageResponse<TeamUserResponse> response = mapUsers(page, null);
        audit(authorized.actor().user(), null, "TEAM_CANDIDATES_LIST", "TEAM",
                authorized.scope().ownerUserId(), null, "SUCCESS", null, context);
        return response;
    }

    @Transactional
    public TeamMutationResponse addMember(
            AuthPrincipal principal,
            TeamScopeMode mode,
            String ownerUserId,
            String memberUserId,
            TeamOversightRequestContext context) {
        AuthorizedScope authorized = authorizeScope(principal, mode, ownerUserId);
        UserId ownerId = requireOwnedTeam(authorized);
        UserId memberId = new UserId(requireText(memberUserId, "memberUserId"));
        if (ownerId.equals(memberId)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "系统管理员不能把自己加入自己的团队");
        }
        User target = users.findByUserId(memberId)
                .filter(User::canLogin)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "候选用户不存在或已停用"));
        Instant now = clock.instant();
        SystemAdminTeamMember existing = teams.find(ownerId, memberId).orElse(null);
        Instant createdAt = existing == null ? now : existing.createdAt();
        teams.save(new SystemAdminTeamMember(
                ownerId, memberId, authorized.actor().user().userId(), createdAt, now, null));
        audit(authorized.actor().user(), target, "TEAM_MEMBER_ADDED", "TEAM",
                ownerId.value(), null, "SUCCESS", null, context);
        return new TeamMutationResponse(ownerId.value(), memberId.value(), true);
    }

    @Transactional
    public TeamMutationResponse removeMember(
            AuthPrincipal principal,
            TeamScopeMode mode,
            String ownerUserId,
            String memberUserId,
            TeamOversightRequestContext context) {
        AuthorizedScope authorized = authorizeScope(principal, mode, ownerUserId);
        UserId ownerId = requireOwnedTeam(authorized);
        UserId memberId = new UserId(requireText(memberUserId, "memberUserId"));
        if (!teams.isActiveMember(ownerId, memberId)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "团队成员不存在");
        }
        User target = users.findByUserId(memberId).orElse(null);
        teams.deactivate(ownerId, memberId, clock.instant());
        audit(authorized.actor().user(), target, "TEAM_MEMBER_REMOVED", "TEAM",
                ownerId.value(), null, "SUCCESS", null, context);
        return new TeamMutationResponse(ownerId.value(), memberId.value(), false);
    }

    /** 解析并实时复核团队代码视图范围。 */
    public AuthorizedScope authorizeScope(AuthPrincipal principal, TeamScopeMode mode, String ownerUserId) {
        TeamScopeMode normalizedMode = mode == null ? TeamScopeMode.MY_TEAM : mode;
        if (normalizedMode == TeamScopeMode.GLOBAL) {
            LiveActor actor = liveRoles.require(principal, Dictionary.ROLE_SUPER_ADMIN);
            return new AuthorizedScope(actor, new TeamScope(normalizedMode, null, true));
        }
        if (normalizedMode == TeamScopeMode.SYSTEM_ADMIN_TEAM) {
            LiveActor actor = liveRoles.require(principal, Dictionary.ROLE_SUPER_ADMIN);
            UserId ownerId = requireSystemAdminOwner(ownerUserId);
            return new AuthorizedScope(actor, new TeamScope(normalizedMode, ownerId.value(), false));
        }
        LiveActor actor = liveRoles.require(principal, Dictionary.ROLE_SYSTEM_ADMIN);
        return new AuthorizedScope(actor, new TeamScope(
                TeamScopeMode.MY_TEAM, actor.user().userId().value(), false));
    }

    /** 长连接每条 RPC 使用数据库实时角色和团队关系重新验证目标成员。 */
    public AuthorizedTarget authorizeTarget(
            UserId actorUserId,
            TeamScopeMode mode,
            String ownerUserId,
            UserId targetUserId) {
        LiveActor actor;
        UserId owner = null;
        if (mode == TeamScopeMode.GLOBAL) {
            actor = liveRoles.require(actorUserId, Dictionary.ROLE_SUPER_ADMIN);
        } else if (mode == TeamScopeMode.SYSTEM_ADMIN_TEAM) {
            actor = liveRoles.require(actorUserId, Dictionary.ROLE_SUPER_ADMIN);
            owner = requireSystemAdminOwner(ownerUserId);
        } else {
            actor = liveRoles.require(actorUserId, Dictionary.ROLE_SYSTEM_ADMIN);
            owner = actor.user().userId();
        }
        User target = users.findByUserId(targetUserId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "目标用户不存在"));
        if (mode != TeamScopeMode.GLOBAL && !teams.isActiveMember(owner, targetUserId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "目标用户已不属于当前团队");
        }
        return new AuthorizedTarget(actor, target, owner);
    }

    /** 由团队代码、文件和导出入口在返回正文前统一写审计。 */
    public void recordOutcome(
            LiveActor actor,
            User target,
            String action,
            String resourceType,
            String resourceId,
            String path,
            String outcome,
            String errorCode,
            TeamOversightRequestContext context) {
        audit(actor.user(), target, action, resourceType, resourceId, path, outcome, errorCode, context);
    }

    private UserId requireOwnedTeam(AuthorizedScope authorized) {
        if (authorized.scope().global() || authorized.scope().ownerUserId() == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "全平台范围不维护团队名单");
        }
        return new UserId(authorized.scope().ownerUserId());
    }

    private UserId requireSystemAdminOwner(String ownerUserId) {
        UserId ownerId = new UserId(requireText(ownerUserId, "ownerUserId"));
        LiveActor owner = liveRoles.require(ownerId, Dictionary.ROLE_SYSTEM_ADMIN);
        if (!RoleCapabilities.hasCapability(owner.roles(), Dictionary.ROLE_SYSTEM_ADMIN)
                || owner.roles().contains(Dictionary.ROLE_SUPER_ADMIN)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "团队负责人必须实际持有系统管理员角色");
        }
        return ownerId;
    }

    private PageResponse<TeamUserResponse> mapUsers(PageResponse<User> page, Instant addedAt) {
        return new PageResponse<>(page.items().stream().map(user -> response(user, addedAt)).toList(),
                page.page(), page.size(), page.total());
    }

    private TeamUserResponse response(User user, Instant addedAt) {
        return new TeamUserResponse(
                user.userId().value(), user.unifiedAuthId(), user.username(), user.organization(),
                user.rdDepartment(), user.department(), user.status().name(),
                liveRoles.roleCodes(user.userId()), addedAt);
    }

    private void audit(
            User actor,
            User target,
            String action,
            String resourceType,
            String resourceId,
            String path,
            String outcome,
            String errorCode,
            TeamOversightRequestContext context) {
        TeamOversightRequestContext safeContext = Objects.requireNonNull(context, "context must not be null");
        auditRepository.append(new TeamOversightAuditEvent(
                RuntimeIdGenerator.teamOversightAuditEventId(), actor.userId().value(), actor.username(),
                target == null ? null : target.userId().value(), target == null ? null : target.username(),
                action, resourceType, normalize(resourceId), digest(path), outcome, errorCode,
                safeContext.traceId(), normalize(safeContext.ipAddress()), digest(safeContext.userAgent()), clock.instant()));
    }

    private String digest(String value) {
        return value == null || value.isBlank() ? null : TokenSessionMarkerStore.sha256(value);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + " 不能为空", Map.of("field", field));
        }
        return normalized;
    }

    public record AuthorizedScope(LiveActor actor, TeamScope scope) {
    }

    public record AuthorizedTarget(LiveActor actor, User target, UserId ownerUserId) {
    }
}
