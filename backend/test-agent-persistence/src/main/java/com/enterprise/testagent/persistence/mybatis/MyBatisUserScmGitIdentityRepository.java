package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserScmGitIdentity;
import com.enterprise.testagent.domain.user.UserScmGitIdentityCandidate;
import com.enterprise.testagent.domain.user.UserScmGitIdentityRepository;
import com.enterprise.testagent.domain.user.UserScmGitIdentitySource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 用户 SCM Git 姓名端口的 MyBatis XML 实现。 */
@Repository
public class MyBatisUserScmGitIdentityRepository implements UserScmGitIdentityRepository {

    private static final int MAX_PAGE_SIZE = 1_000;
    private final UserScmGitIdentityMapper mapper;

    public MyBatisUserScmGitIdentityRepository(UserScmGitIdentityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<UserScmGitIdentity> findByUserId(UserId userId) {
        return Optional.ofNullable(mapper.findByUserId(userId.value())).map(this::toDomain);
    }

    @Override
    public List<UserScmGitIdentityCandidate> findSyncCandidatesAfter(String afterUserId, int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        return mapper.findSyncCandidatesAfter(afterUserId, limit).stream()
                .map(row -> new UserScmGitIdentityCandidate(
                        new UserId(row.userId()),
                        row.unifiedAuthId(),
                        row.gitName() == null ? null : toDomain(new UserScmGitIdentityRow(
                                row.userId(), row.gitName(), row.source(), row.evidenceCommit(),
                                row.evidenceAt(), row.verifiedAt()))))
                .toList();
    }

    @Override
    public int upsertAcceptedCommitIdentities(List<UserScmGitIdentity> identities) {
        if (identities == null || identities.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        return mapper.upsertAccepted(identities.stream().map(this::toRow).toList(), now);
    }

    @Override
    public void upsertRemoteRejection(UserScmGitIdentity identity) {
        mapper.upsertRemoteRejection(toRow(identity), Instant.now());
    }

    private UserScmGitIdentity toDomain(UserScmGitIdentityRow row) {
        return new UserScmGitIdentity(
                new UserId(row.userId()), row.gitName(), UserScmGitIdentitySource.valueOf(row.source()),
                row.evidenceCommit(), row.evidenceAt(), row.verifiedAt());
    }

    private UserScmGitIdentityRow toRow(UserScmGitIdentity identity) {
        return new UserScmGitIdentityRow(
                identity.userId().value(), identity.gitName(), identity.source().name(),
                identity.evidenceCommit(), identity.evidenceAt(), identity.verifiedAt());
    }
}
