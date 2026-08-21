package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.localclient.LocalClientCredential;
import com.enterprise.testagent.domain.localclient.LocalClientCredentialRepository;
import com.enterprise.testagent.domain.localclient.LocalClientCredentialStatus;
import com.enterprise.testagent.domain.user.UserId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** 本地客户端凭据 MyBatis 仓储实现。 */
@Repository
public class MyBatisLocalClientCredentialRepository implements LocalClientCredentialRepository {

    private final LocalClientMapper mapper;

    public MyBatisLocalClientCredentialRepository(LocalClientMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void lockUser(UserId userId) {
        if (mapper.lockCredentialUser(userId.value()) == null) {
            throw new IllegalStateException("local client credential owner does not exist");
        }
    }

    @Override
    public Optional<LocalClientCredential> findByUserId(UserId userId) {
        return Optional.ofNullable(mapper.findCredentialByUserId(userId.value())).map(this::toDomain);
    }

    @Override
    public Optional<LocalClientCredential> findByUserIdForUpdate(UserId userId) {
        return Optional.ofNullable(mapper.findCredentialByUserIdForUpdate(userId.value())).map(this::toDomain);
    }

    @Override
    public Optional<LocalClientCredential> findActiveByFingerprint(String fingerprint) {
        return Optional.ofNullable(mapper.findActiveCredentialByFingerprint(fingerprint)).map(this::toDomain);
    }

    @Override
    public void save(LocalClientCredential credential) {
        int updated = mapper.upsertCredential(new LocalClientCredentialRow(
                credential.userId().value(),
                credential.encryptedClientKey(),
                credential.clientKeyFingerprint(),
                credential.keyHint(),
                credential.version(),
                credential.status().name(),
                credential.createdAt(),
                credential.updatedAt(),
                credential.revealedAt(),
                credential.revokedAt()));
        if (updated != 1) {
            throw new IllegalStateException("local client credential upsert did not affect one row");
        }
    }

    private LocalClientCredential toDomain(LocalClientCredentialRow row) {
        return new LocalClientCredential(
                new UserId(row.userId()),
                row.encryptedClientKey(),
                row.clientKeyFingerprint(),
                row.keyHint(),
                row.version(),
                LocalClientCredentialStatus.valueOf(row.status()),
                row.createdAt(),
                row.updatedAt(),
                row.revealedAt(),
                row.revokedAt());
    }
}
