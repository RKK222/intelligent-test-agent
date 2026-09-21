package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.team.SystemAdminTeamMember;
import com.enterprise.testagent.domain.team.SystemAdminTeamMemberView;
import com.enterprise.testagent.domain.team.SystemAdminTeamRepository;
import com.enterprise.testagent.domain.team.TeamOversightAuditEvent;
import com.enterprise.testagent.domain.team.TeamOversightAuditRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/** 系统管理员团队 MyBatis 仓储；所有关系型 SQL 均保留在对应 XML。 */
@Repository
public class MyBatisSystemAdminTeamRepository implements SystemAdminTeamRepository, TeamOversightAuditRepository {

    private final SystemAdminTeamMapper mapper;

    public MyBatisSystemAdminTeamRepository(SystemAdminTeamMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public PageResponse<SystemAdminTeamMemberView> findMembers(
            UserId ownerUserId, String keyword, PageRequest pageRequest) {
        String pattern = pattern(keyword);
        List<SystemAdminTeamMemberView> items = mapper.findMembers(
                        ownerUserId.value(), pattern, pageRequest.size(), pageRequest.offset())
                .stream()
                .map(row -> new SystemAdminTeamMemberView(toUser(row), row.createdAt(), row.updatedAt()))
                .toList();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(),
                mapper.countMembers(ownerUserId.value(), pattern));
    }

    @Override
    public PageResponse<User> findCandidates(UserId ownerUserId, String keyword, PageRequest pageRequest) {
        String pattern = pattern(keyword);
        List<User> items = mapper.findCandidates(
                        ownerUserId.value(), pattern, pageRequest.size(), pageRequest.offset())
                .stream().map(this::toUser).toList();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(),
                mapper.countCandidates(ownerUserId.value(), pattern));
    }

    @Override
    public Optional<SystemAdminTeamMember> find(UserId ownerUserId, UserId memberUserId) {
        return Optional.ofNullable(mapper.findRelation(ownerUserId.value(), memberUserId.value()))
                .map(row -> new SystemAdminTeamMember(
                        new UserId(row.ownerUserId()), new UserId(row.memberUserId()),
                        new UserId(row.addedByUserId()), row.createdAt(), row.updatedAt(), row.deletedAt()));
    }

    @Override
    public List<UserId> findActiveMemberIds(UserId ownerUserId) {
        return mapper.findActiveMemberIds(ownerUserId.value()).stream().map(UserId::new).toList();
    }

    @Override
    public boolean isActiveMember(UserId ownerUserId, UserId memberUserId) {
        return mapper.existsActiveMember(ownerUserId.value(), memberUserId.value());
    }

    @Override
    public void save(SystemAdminTeamMember member) {
        mapper.upsertMember(
                member.ownerUserId().value(), member.memberUserId().value(), member.addedByUserId().value(),
                member.createdAt(), member.updatedAt());
    }

    @Override
    public void deactivate(UserId ownerUserId, UserId memberUserId, Instant deletedAt) {
        mapper.deactivateMember(ownerUserId.value(), memberUserId.value(), deletedAt);
    }

    @Override
    public void append(TeamOversightAuditEvent event) {
        mapper.insertTeamAudit(event);
    }

    private User toUser(SystemAdminTeamMemberRow row) {
        return new User(
                new UserId(row.memberUserId()), row.unifiedAuthId(), row.username(), row.passwordHash(),
                row.organization(), row.rdDepartment(), row.department(), UserStatus.valueOf(row.userStatus()),
                row.userCreatedAt(), row.userUpdatedAt());
    }

    private User toUser(SystemAdminTeamUserRow row) {
        return new User(
                new UserId(row.userId()), row.unifiedAuthId(), row.username(), row.passwordHash(),
                row.organization(), row.rdDepartment(), row.department(), UserStatus.valueOf(row.status()),
                row.createdAt(), row.updatedAt());
    }

    private String pattern(String value) {
        return value == null || value.isBlank() ? null : "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
