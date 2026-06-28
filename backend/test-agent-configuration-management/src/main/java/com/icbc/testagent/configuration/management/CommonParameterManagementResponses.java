package com.icbc.testagent.configuration.management;

import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.CommonParameterChangeLog;
import com.icbc.testagent.domain.configuration.CommonParameterLoadSnapshot;
import com.icbc.testagent.domain.configuration.LoadedParameter;
import java.time.Instant;
import java.util.List;

/**
 * 通用参数管理对 API 层暴露的响应模型；只携带展示所需字段，不暴露数据库代理主键。
 */
public final class CommonParameterManagementResponses {

    private CommonParameterManagementResponses() {
    }

    public record CommonParameterResponse(
            String parameterId,
            String englishName,
            String chineseName,
            String parameterValue,
            String platform,
            Instant createdAt,
            Instant updatedAt) {

        static CommonParameterResponse from(CommonParameter parameter) {
            return new CommonParameterResponse(
                    parameter.parameterId(),
                    parameter.englishName(),
                    parameter.chineseName(),
                    parameter.parameterValue(),
                    parameter.platform().value(),
                    parameter.createdAt(),
                    parameter.updatedAt());
        }
    }

    /**
     * 通用参数修改日志响应，用于展示修改历史。
     */
    public record ChangeLogResponse(
            String logId,
            String parameterId,
            String oldValue,
            String newValue,
            String changedByUserId,
            String changedByUsername,
            String traceId,
            Instant createdAt) {

        static ChangeLogResponse from(CommonParameterChangeLog log) {
            return new ChangeLogResponse(
                    log.logId(),
                    log.parameterId(),
                    log.oldValue(),
                    log.newValue(),
                    log.changedByUserId(),
                    log.changedByUsername(),
                    log.traceId(),
                    log.createdAt());
        }
    }

    /**
     * 单个后端 Java 进程加载的通用参数快照响应。
     */
    public record LoadSnapshotResponse(
            String backendProcessId,
            String linuxServerId,
            String listenUrl,
            String instanceId,
            Instant loadedAt,
            List<LoadedParameterResponse> parameters) {

        public static LoadSnapshotResponse from(CommonParameterLoadSnapshot snapshot) {
            List<LoadedParameterResponse> parameters = snapshot.parameters().stream()
                    .map(LoadedParameterResponse::from)
                    .toList();
            return new LoadSnapshotResponse(
                    snapshot.backendProcessId(),
                    snapshot.linuxServerId(),
                    snapshot.listenUrl(),
                    snapshot.instanceId(),
                    snapshot.loadedAt(),
                    parameters);
        }
    }

    /**
     * 加载快照中的单个参数项响应；同时携带原始值与展开值及解析状态。
     */
    public record LoadedParameterResponse(
            String englishName,
            String platform,
            String rawValue,
            String resolvedValue,
            boolean hasReference,
            String resolutionError) {

        public static LoadedParameterResponse from(LoadedParameter parameter) {
            return new LoadedParameterResponse(
                    parameter.englishName(),
                    parameter.platform(),
                    parameter.rawValue(),
                    parameter.resolvedValue(),
                    parameter.hasReference(),
                    parameter.resolutionError());
        }
    }
}
