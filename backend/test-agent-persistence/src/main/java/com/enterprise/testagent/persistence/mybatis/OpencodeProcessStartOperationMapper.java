package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户 opencode 进程初始化进度 MyBatis mapper；SQL 必须维护在 XML 中。
 */
@Mapper
public interface OpencodeProcessStartOperationMapper {

    void insert(OpencodeProcessStartOperationRow row);

    int updateStep(
            @Param("operationId") String operationId,
            @Param("status") String status,
            @Param("currentStep") String currentStep,
            @Param("updatedAt") Instant updatedAt);

    int updateSucceeded(
            @Param("operationId") String operationId,
            @Param("status") String status,
            @Param("currentStep") String currentStep,
            @Param("processId") String processId,
            @Param("serviceAddress") String serviceAddress,
            @Param("updatedAt") Instant updatedAt);

    int updateFailed(
            @Param("operationId") String operationId,
            @Param("status") String status,
            @Param("currentStep") String currentStep,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("updatedAt") Instant updatedAt);

    OpencodeProcessStartOperationRow findByOperationId(@Param("operationId") String operationId);

    OpencodeProcessStartOperationRow findById(
            @Param("operationId") String operationId,
            @Param("requestedByUserId") String requestedByUserId);
}
