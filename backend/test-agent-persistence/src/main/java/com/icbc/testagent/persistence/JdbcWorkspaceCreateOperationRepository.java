package com.icbc.testagent.persistence;

import com.icbc.testagent.domain.configuration.ApplicationId;
import com.icbc.testagent.domain.configuration.ApplicationWorkspaceId;
import com.icbc.testagent.domain.configuration.WorkspaceCreateOperation;
import com.icbc.testagent.domain.configuration.WorkspaceCreateOperationRepository;
import com.icbc.testagent.domain.configuration.WorkspaceCreateOperationStatus;
import com.icbc.testagent.domain.configuration.WorkspaceCreateOperationStep;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.icbc.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 工作空间创建进度 JDBC Repository，使用单行快照保存当前步骤。
 */
@Repository
public class JdbcWorkspaceCreateOperationRepository extends JdbcRepositorySupport implements WorkspaceCreateOperationRepository {

    private final JdbcClient jdbcClient;

    private final RowMapper<WorkspaceCreateOperation> mapper = (rs, rowNum) -> new WorkspaceCreateOperation(
            rs.getString("operation_id"),
            new ApplicationId(rs.getString("app_id")),
            new UserId(rs.getString("requested_by_user_id")),
            WorkspaceCreateOperationStatus.valueOf(rs.getString("status")),
            WorkspaceCreateOperationStep.valueOf(rs.getString("current_step")),
            rs.getString("error_code"),
            rs.getString("error_message"),
            optionalApplicationWorkspaceId(rs.getString("workspace_id")),
            optionalApplicationWorkspaceVersionId(rs.getString("version_id")),
            rs.getString("trace_id"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    public JdbcWorkspaceCreateOperationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public WorkspaceCreateOperation start(
            String operationId,
            ApplicationId appId,
            UserId requestedBy,
            String traceId,
            Instant now) {
        if (findById(operationId).isPresent()) {
            jdbcClient.sql("""
                            update workspace_create_operations
                            set app_id = :appId,
                                requested_by_user_id = :requestedBy,
                                status = :status,
                                current_step = :currentStep,
                                error_code = null,
                                error_message = null,
                                workspace_id = null,
                                version_id = null,
                                trace_id = :traceId,
                                updated_at = :updatedAt
                            where operation_id = :operationId
                            """)
                    .param("operationId", operationId)
                    .param("appId", appId.value())
                    .param("requestedBy", requestedBy.value())
                    .param("status", WorkspaceCreateOperationStatus.RUNNING.name())
                    .param("currentStep", WorkspaceCreateOperationStep.VALIDATING_INPUT.name())
                    .param("traceId", traceId)
                    .param("updatedAt", timestamp(now))
                    .update();
            return findById(operationId).orElseThrow();
        }
        jdbcClient.sql("""
                        insert into workspace_create_operations(
                            operation_id, app_id, requested_by_user_id, status, current_step,
                            error_code, error_message, workspace_id, version_id, trace_id, created_at, updated_at)
                        values (
                            :operationId, :appId, :requestedBy, :status, :currentStep,
                            null, null, null, null, :traceId, :createdAt, :updatedAt)
                        """)
                .param("operationId", operationId)
                .param("appId", appId.value())
                .param("requestedBy", requestedBy.value())
                .param("status", WorkspaceCreateOperationStatus.RUNNING.name())
                .param("currentStep", WorkspaceCreateOperationStep.VALIDATING_INPUT.name())
                .param("traceId", traceId)
                .param("createdAt", timestamp(now))
                .param("updatedAt", timestamp(now))
                .update();
        return findById(operationId).orElseThrow();
    }

    @Override
    public WorkspaceCreateOperation markStep(String operationId, WorkspaceCreateOperationStep step, Instant now) {
        jdbcClient.sql("""
                        update workspace_create_operations
                        set status = :status, current_step = :currentStep, updated_at = :updatedAt
                        where operation_id = :operationId
                        """)
                .param("operationId", operationId)
                .param("status", WorkspaceCreateOperationStatus.RUNNING.name())
                .param("currentStep", step.name())
                .param("updatedAt", timestamp(now))
                .update();
        return findById(operationId).orElseThrow();
    }

    @Override
    public WorkspaceCreateOperation markSucceeded(
            String operationId,
            ApplicationWorkspaceId workspaceId,
            ApplicationWorkspaceVersionId versionId,
            Instant now) {
        jdbcClient.sql("""
                        update workspace_create_operations
                        set status = :status,
                            current_step = :currentStep,
                            workspace_id = :workspaceId,
                            version_id = :versionId,
                            updated_at = :updatedAt
                        where operation_id = :operationId
                        """)
                .param("operationId", operationId)
                .param("status", WorkspaceCreateOperationStatus.SUCCEEDED.name())
                .param("currentStep", WorkspaceCreateOperationStep.COMPLETED.name())
                .param("workspaceId", workspaceId.value())
                .param("versionId", versionId.value())
                .param("updatedAt", timestamp(now))
                .update();
        return findById(operationId).orElseThrow();
    }

    @Override
    public WorkspaceCreateOperation markFailed(String operationId, String errorCode, String errorMessage, Instant now) {
        jdbcClient.sql("""
                        update workspace_create_operations
                        set status = :status,
                            error_code = :errorCode,
                            error_message = :errorMessage,
                            updated_at = :updatedAt
                        where operation_id = :operationId
                        """)
                .param("operationId", operationId)
                .param("status", WorkspaceCreateOperationStatus.FAILED.name())
                .param("errorCode", errorCode)
                .param("errorMessage", errorMessage)
                .param("updatedAt", timestamp(now))
                .update();
        return findById(operationId).orElseThrow();
    }

    @Override
    public Optional<WorkspaceCreateOperation> findById(String operationId) {
        return jdbcClient.sql("""
                        select operation_id, app_id, requested_by_user_id, status, current_step,
                               error_code, error_message, workspace_id, version_id, trace_id, created_at, updated_at
                        from workspace_create_operations
                        where operation_id = :operationId
                        """)
                .param("operationId", operationId)
                .query(mapper)
                .optional();
    }

    private static ApplicationWorkspaceId optionalApplicationWorkspaceId(String value) {
        return value == null || value.isBlank() ? null : new ApplicationWorkspaceId(value);
    }

    private static ApplicationWorkspaceVersionId optionalApplicationWorkspaceVersionId(String value) {
        return value == null || value.isBlank() ? null : new ApplicationWorkspaceVersionId(value);
    }
}
