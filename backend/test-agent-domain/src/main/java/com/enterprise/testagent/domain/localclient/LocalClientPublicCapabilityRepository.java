package com.enterprise.testagent.domain.localclient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 公共客户端能力包的关系型持久化端口。 */
public interface LocalClientPublicCapabilityRepository {

    void insertRelease(LocalClientPublicCapabilityModels.Release release);

    Optional<LocalClientPublicCapabilityModels.Release> findReleaseByDigest(String bundleDigest);

    Optional<LocalClientPublicCapabilityModels.Release> findReleaseBySourceCommit(String sourceCommit);

    Optional<LocalClientPublicCapabilityModels.Release> findLatestRelease();

    Optional<LocalClientPublicCapabilityModels.Release> findLatestAvailableRelease();

    Optional<LocalClientPublicCapabilityModels.InstanceState> findInstanceState(LocalClientInstanceId instanceId);

    List<LocalClientPublicCapabilityModels.InstanceState> findUpdateAvailableStates(int limit);

    void saveInstanceState(LocalClientPublicCapabilityModels.InstanceState state);

    void markUpdateAvailableForCapableInstances(
            String sourceCommit,
            String bundleDigest,
            Instant observedAt,
            String capabilityName);

    void insertAttempt(LocalClientPublicCapabilityModels.Attempt attempt);

    Optional<LocalClientPublicCapabilityModels.Attempt> findAttempt(String commandId);

    Optional<LocalClientPublicCapabilityModels.Attempt> findAttempt(
            LocalClientInstanceId clientInstanceId,
            String targetDigest);

    List<LocalClientPublicCapabilityModels.Attempt> findDispatchableAttempts(int limit);

    boolean bindAttemptGeneration(String commandId, long expectedGeneration, long targetGeneration, Instant observedAt);

    boolean transitionAttempt(
            String commandId,
            String expectedStatus,
            String targetStatus,
            String errorCode,
            Instant observedAt);
}
