package com.enterprise.testagent.system.management.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientVersionModels;
import com.enterprise.testagent.domain.localclient.LocalClientVersionRepository;
import com.enterprise.testagent.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理已验签发布版本及“用户覆盖优先于全局”的目标版本策略。 */
@Service
public class LocalClientVersionPolicyApplicationService {

    private final LocalClientVersionRepository repository;
    private final Clock clock;

    @Autowired
    public LocalClientVersionPolicyApplicationService(LocalClientVersionRepository repository) {
        this(repository, Clock.systemUTC());
    }

    LocalClientVersionPolicyApplicationService(LocalClientVersionRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public List<LocalClientVersionModels.Release> releases() {
        return repository.findReleases();
    }

    @Transactional(readOnly = true)
    public LocalClientVersionModels.GlobalPolicy globalPolicy() {
        return repository.findGlobalPolicy().orElse(null);
    }

    @Transactional(readOnly = true)
    public List<LocalClientVersionModels.UserPolicy> userPolicies() {
        return repository.findUserPolicies();
    }

    @Transactional
    public LocalClientVersionModels.GlobalPolicy setGlobalTarget(String targetVersion, UserId actorUserId) {
        requireSelectableRelease(targetVersion);
        Instant now = Instant.now(clock);
        long revision = repository.nextPolicyRevision();
        String previous = repository.findGlobalPolicy()
                .map(LocalClientVersionModels.GlobalPolicy::targetVersion)
                .orElse(null);
        LocalClientVersionModels.GlobalPolicy policy = new LocalClientVersionModels.GlobalPolicy(
                targetVersion, revision, actorUserId, now);
        repository.saveGlobalPolicy(policy);
        audit("GLOBAL", null, previous, targetVersion, revision, "SET_GLOBAL", actorUserId, now);
        return policy;
    }

    @Transactional
    public LocalClientVersionModels.UserPolicy setUserTarget(
            UserId userId,
            String targetVersion,
            UserId actorUserId) {
        requireSelectableRelease(targetVersion);
        Instant now = Instant.now(clock);
        long revision = repository.nextPolicyRevision();
        String previous = repository.findUserPolicy(userId)
                .map(LocalClientVersionModels.UserPolicy::targetVersion)
                .orElse(null);
        LocalClientVersionModels.UserPolicy policy = new LocalClientVersionModels.UserPolicy(
                userId, targetVersion, revision, actorUserId, now);
        repository.saveUserPolicy(policy);
        audit("USER", userId, previous, targetVersion, revision, "SET_USER", actorUserId, now);
        return policy;
    }

    @Transactional
    public LocalClientVersionModels.UserPolicy clearUserTarget(UserId userId, UserId actorUserId) {
        Instant now = Instant.now(clock);
        long revision = repository.nextPolicyRevision();
        String previous = repository.findUserPolicy(userId)
                .map(LocalClientVersionModels.UserPolicy::targetVersion)
                .orElse(null);
        LocalClientVersionModels.UserPolicy tombstone = new LocalClientVersionModels.UserPolicy(
                userId, null, revision, actorUserId, now);
        repository.saveUserPolicy(tombstone);
        audit("USER", userId, previous, null, revision, "CLEAR_USER", actorUserId, now);
        return tombstone;
    }

    @Transactional(readOnly = true)
    public EffectivePolicy effectivePolicy(UserId userId) {
        LocalClientVersionModels.GlobalPolicy global = repository.findGlobalPolicy().orElse(null);
        LocalClientVersionModels.UserPolicy user = repository.findUserPolicy(userId).orElse(null);
        LocalClientVersionModels.EffectivePolicy effective =
                LocalClientVersionModels.resolveEffectivePolicy(global, user);
        return new EffectivePolicy(effective.targetVersion(), effective.policyRevision(), effective.source());
    }

    private LocalClientVersionModels.Release requireSelectableRelease(String targetVersion) {
        LocalClientVersionModels.Release release = repository.findRelease(targetVersion)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "本地客户端发布版本不存在"));
        if (!release.compatible()) {
            throw new PlatformException(ErrorCode.CONFLICT, "本地客户端发布版本与当前平台或启动器不兼容");
        }
        return release;
    }

    private void audit(
            String scope,
            UserId userId,
            String previous,
            String target,
            long revision,
            String action,
            UserId actor,
            Instant now) {
        repository.savePolicyAudit(new LocalClientVersionModels.PolicyAudit(
                "lcpa_" + UUID.randomUUID().toString().replace("-", ""),
                scope,
                userId,
                previous,
                target,
                revision,
                action,
                actor,
                now));
    }

    public record EffectivePolicy(String targetVersion, long policyRevision, String source) {
    }
}
