package com.enterprise.testagent.opencode.runtime.process;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserManagementQuery;
import com.enterprise.testagent.domain.user.UserManagementQueryRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import com.enterprise.testagent.notification.UserNotificationApplicationService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 根据 manager 容量心跳向全部有效超级管理员发送站内预警。
 *
 * <p>达到 80% 时预警，回落到 70% 以下后清除，利用滞回避免容量在边界附近波动时反复提醒。
 * 通知失败必须 fail-open，不能影响 manager 心跳或控制连接。</p>
 */
@Service
public class OpencodeCapacityNotificationService {

    static final int WARNING_PERCENT = 80;
    static final int RECOVERY_PERCENT = 70;
    private static final int RECIPIENT_PAGE_SIZE = PageRequest.MAX_SIZE;
    private static final Logger LOGGER = LoggerFactory.getLogger(OpencodeCapacityNotificationService.class);

    private final UserManagementQueryRepository userQueryRepository;
    private final UserNotificationApplicationService notificationService;
    private final ConcurrentMap<String, CapacityBand> observedBands = new ConcurrentHashMap<>();

    /** 复用用户管理角色查询和通用通知服务，不新增收件人或消息持久化旁路。 */
    public OpencodeCapacityNotificationService(
            UserManagementQueryRepository userQueryRepository,
            UserNotificationApplicationService notificationService) {
        this.userQueryRepository = Objects.requireNonNull(userQueryRepository, "userQueryRepository must not be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService must not be null");
    }

    /**
     * 根据当前快照推进通知生命周期；每个 Java 首次观察到高位或恢复态时也会做一次幂等收敛，
     * 避免升级重启时 Redis 中遗留的同档位快照让预警永久漏发或无法失效。
     */
    public void reconcile(
            ManagerRuntimeSnapshot previousSnapshot,
            ManagerRuntimeSnapshot currentSnapshot,
            String traceId) {
        try {
            Objects.requireNonNull(currentSnapshot, "currentSnapshot must not be null");
            OpencodeContainer current = currentSnapshot.container();
            OpencodeContainer previous = sameContainer(previousSnapshot, current)
                    ? previousSnapshot.container()
                    : null;
            String containerKey = current.containerId().value();
            if (atOrAbove(current, WARNING_PERCENT)
                    && observedBands.put(containerKey, CapacityBand.WARNING) != CapacityBand.WARNING) {
                notifySuperAdmins(current, traceId);
                return;
            }
            if (below(current, RECOVERY_PERCENT)
                    && observedBands.put(containerKey, CapacityBand.RECOVERED) != CapacityBand.RECOVERED) {
                notificationService.invalidateOpencodeCapacityWarning(current.containerId(), traceId);
                return;
            }
            if (previous != null && atOrAbove(previous, WARNING_PERCENT) && !below(current, RECOVERY_PERCENT)) {
                // 70%～80% 属于滞回带，保持已记录的 WARNING 状态，不在边界附近反复通知。
                observedBands.putIfAbsent(containerKey, CapacityBand.WARNING);
            }
        } catch (RuntimeException exception) {
            // 容量提醒属于旁路能力；任何查询或通知异常都不得关闭 manager WebSocket。
            LOGGER.warn(
                    "OpenCode 容量通知处理失败，保留 manager 心跳，containerId={} exceptionType={}",
                    currentSnapshot == null ? null : currentSnapshot.container().containerId(),
                    exception.getClass().getSimpleName());
        }
    }

    private void notifySuperAdmins(OpencodeContainer container, String traceId) {
        List<UserId> recipients = activeSuperAdminIds();
        int failed = 0;
        for (UserId recipient : recipients) {
            try {
                notificationService.syncOpencodeCapacityWarning(
                        recipient,
                        container.linuxServerId(),
                        container.containerId(),
                        container.maxProcesses(),
                        WARNING_PERCENT,
                        traceId);
            } catch (RuntimeException exception) {
                failed++;
            }
        }
        if (failed > 0) {
            LOGGER.warn(
                    "OpenCode 容量通知部分接收人写入失败，containerId={} recipientCount={} failedCount={}",
                    container.containerId(),
                    recipients.size(),
                    failed);
        }
    }

    private List<UserId> activeSuperAdminIds() {
        UserManagementQuery query = new UserManagementQuery(
                null, Dictionary.ROLE_SUPER_ADMIN, null, null, null);
        Set<UserId> recipients = new LinkedHashSet<>();
        int page = 1;
        long totalPages;
        do {
            PageResponse<User> result = userQueryRepository.findPage(
                    query, new PageRequest(page, RECIPIENT_PAGE_SIZE));
            result.items().stream()
                    .filter(user -> user.status() == UserStatus.ACTIVE)
                    .map(User::userId)
                    .forEach(recipients::add);
            totalPages = result.totalPages();
            page++;
        } while (page <= totalPages);
        return List.copyOf(recipients);
    }

    private boolean sameContainer(ManagerRuntimeSnapshot previousSnapshot, OpencodeContainer current) {
        return previousSnapshot != null
                && previousSnapshot.container().containerId().equals(current.containerId());
    }

    private boolean atOrAbove(OpencodeContainer container, int percent) {
        return (long) container.currentProcesses() * 100L
                >= (long) container.maxProcesses() * percent;
    }

    private boolean below(OpencodeContainer container, int percent) {
        return (long) container.currentProcesses() * 100L
                < (long) container.maxProcesses() * percent;
    }

    private enum CapacityBand {
        WARNING,
        RECOVERED
    }
}
