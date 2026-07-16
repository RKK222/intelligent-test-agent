package com.enterprise.testagent.system.management.maintenance;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.maintenance.DatabaseIdentityMaintenancePort;
import com.enterprise.testagent.domain.maintenance.IdentityManagedTable;
import com.enterprise.testagent.domain.maintenance.IdentityStatus;
import com.enterprise.testagent.system.management.maintenance.DatabaseIdentityResponses.IdentityStatusDto;
import com.enterprise.testagent.system.management.maintenance.DatabaseIdentityResponses.RestartIdentityCommand;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 数据库 identity 运维应用服务，封装白名单校验、状态查询、对齐与手动重启及审计日志。
 *
 * <p>表名只接受 {@link IdentityManagedTable} 枚举，目标值必须为正整数且大于当前 max(id)，
 * 杜绝任意表名注入与往回滚造成新冲突。持久化通过 domain 端口
 * {@link DatabaseIdentityMaintenancePort} 访问，不直接依赖 MyBatis。
 */
@Service
public class DatabaseIdentityMaintenanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseIdentityMaintenanceService.class);

    private final DatabaseIdentityMaintenancePort port;

    /**
     * 注入 identity 运维持久化端口。
     */
    public DatabaseIdentityMaintenanceService(DatabaseIdentityMaintenancePort port) {
        this.port = Objects.requireNonNull(port, "port must not be null");
    }

    /**
     * 查询白名单内全部表的 identity 状态。
     */
    public List<IdentityStatusDto> listIdentityStatuses() {
        return Arrays.stream(IdentityManagedTable.values())
                .map(this::statusOf)
                .toList();
    }

    /**
     * 把指定表 identity 对齐到 max(id)+1。
     *
     * @return 对齐后的最新状态
     */
    public IdentityStatusDto alignIdentity(IdentityManagedTable table) {
        IdentityStatusDto before = statusOf(table);
        long maxId = before.maxId() == null ? 0L : before.maxId();
        long target = maxId + 1;
        restart(table, target, "ALIGN", before);
        return statusOf(table);
    }

    /**
     * 手动把指定表 identity 重启到目标值，目标值必须为正整数且大于当前 max(id)。
     *
     * @throws PlatformException 当目标值非正整数或小于等于当前 max(id) 时
     */
    public IdentityStatusDto restartIdentity(RestartIdentityCommand command) {
        IdentityManagedTable table = command.table();
        if (command.targetValue() <= 0) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "目标值必须为正整数");
        }
        IdentityStatusDto before = statusOf(table);
        Long maxId = before.maxId();
        if (maxId != null && command.targetValue() <= maxId) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR,
                    "目标值必须大于当前最大主键 " + maxId);
        }
        restart(table, command.targetValue(), "RESTART", before);
        return statusOf(table);
    }

    /**
     * 执行重启并记录审计日志（操作人/表/动作/旧值/最大值/新值）。
     */
    private void restart(IdentityManagedTable table, long target, String action, IdentityStatusDto before) {
        port.restartIdentity(table, target);
        LOGGER.info(
                "event=identity_maintain table={} action={} oldValue={} maxValue={} newValue={}",
                table.tableName(), action,
                before.currentValue(), before.maxId(), target);
    }

    /**
     * 查询单张表状态并装配对外 DTO（补查询时间）。
     */
    private IdentityStatusDto statusOf(IdentityManagedTable table) {
        IdentityStatus status = port.queryIdentityStatus(table);
        return new IdentityStatusDto(
                status.table(),
                status.tableName(),
                status.currentValue(),
                status.maxId(),
                status.conflict(),
                Instant.now());
    }

    /**
     * 把字符串表码解析为白名单枚举，非法则抛 VALIDATION_ERROR。
     *
     * @param code 前端传入的表码
     * @return 白名单枚举
     * @throws PlatformException 表码不在白名单时
     */
    public static IdentityManagedTable requireTable(String code) {
        return IdentityManagedTable.fromCode(code)
                .orElseThrow(() -> new PlatformException(ErrorCode.VALIDATION_ERROR, "不支持的数据表"));
    }
}
