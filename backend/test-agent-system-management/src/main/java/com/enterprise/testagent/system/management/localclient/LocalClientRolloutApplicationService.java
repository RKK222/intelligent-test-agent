package com.enterprise.testagent.system.management.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.localclient.LocalClientRolloutEntry;
import com.enterprise.testagent.domain.localclient.LocalClientRolloutRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 超级管理员维护本地客户端下载灰度用户，普通查询只返回当前用户是否被放开。 */
@Service
public class LocalClientRolloutApplicationService {

    private final LocalClientRolloutRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Autowired
    public LocalClientRolloutApplicationService(
            LocalClientRolloutRepository repository,
            UserRepository userRepository) {
        this(repository, userRepository, Clock.systemUTC());
    }

    LocalClientRolloutApplicationService(
            LocalClientRolloutRepository repository,
            UserRepository userRepository,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** 名单为空或查询不到记录时严格返回 false，避免部署后意外向全员展示。 */
    @Transactional(readOnly = true)
    public boolean isDownloadAllowed(UserId userId) {
        return repository.isEnabled(Objects.requireNonNull(userId, "userId must not be null"));
    }

    @Transactional(readOnly = true)
    public PageResponse<LocalClientRolloutResponses.RolloutUserView> list(PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        return new PageResponse<>(
                repository.findEnabledPage(pageRequest.offset(), pageRequest.size()).stream()
                        .map(LocalClientRolloutResponses.RolloutUserView::from)
                        .toList(),
                pageRequest.page(),
                pageRequest.size(),
                repository.countEnabled());
    }

    @Transactional
    public LocalClientRolloutResponses.RolloutUserView enable(UserId targetUserId, UserId adminUserId) {
        User target = userRepository.findByUserId(Objects.requireNonNull(targetUserId, "targetUserId must not be null"))
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.NOT_FOUND,
                        "用户不存在",
                        Map.of("userId", targetUserId.value())));
        if (!target.canLogin()) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "停用用户不能加入本地客户端灰度名单",
                    Map.of("userId", targetUserId.value()));
        }
        UserId operator = Objects.requireNonNull(adminUserId, "adminUserId must not be null");
        Instant now = clock.instant();
        Instant createdAt = repository.findByUserId(targetUserId)
                .map(LocalClientRolloutEntry::createdAt)
                .orElse(now);
        repository.save(new LocalClientRolloutEntry(targetUserId, true, operator, createdAt, now));
        return repository.findByUserId(targetUserId)
                .map(LocalClientRolloutResponses.RolloutUserView::from)
                .orElseThrow(() -> new PlatformException(ErrorCode.INTERNAL_ERROR, "灰度用户保存失败"));
    }

    @Transactional
    public void disable(UserId targetUserId, UserId adminUserId) {
        UserId target = Objects.requireNonNull(targetUserId, "targetUserId must not be null");
        UserId operator = Objects.requireNonNull(adminUserId, "adminUserId must not be null");
        if (!repository.disable(target, operator, clock.instant())) {
            throw new PlatformException(
                    ErrorCode.NOT_FOUND,
                    "本地客户端灰度用户不存在",
                    Map.of("userId", target.value()));
        }
    }
}
