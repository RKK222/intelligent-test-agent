package com.enterprise.testagent.workspace;

import com.enterprise.testagent.domain.appsource.AppSourceOperationStep;
import com.enterprise.testagent.domain.appsource.AppSourceStepScope;
import com.enterprise.testagent.domain.appsource.AppSourceStepStatus;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 应用源码每台目标服务器的稳定步骤目录；摘要只能来自本类固定低敏文案。 */
final class AppSourceReplicaStepCatalog {

    static final String QUEUED = "QUEUED";
    static final String LEASE_CLAIM = "LEASE_CLAIM";
    static final String LOCAL_LOCK = "LOCAL_LOCK";
    static final String STAGING = "STAGING";
    static final String SHALLOW_CLONE = "SHALLOW_CLONE";
    static final String FETCH_FIXED_COMMIT = "FETCH_FIXED_COMMIT";
    static final String SPARSE_CHECKOUT = "SPARSE_CHECKOUT";
    static final String VALIDATE = "VALIDATE";
    static final String REMOVE_GIT_METADATA = "REMOVE_GIT_METADATA";
    static final String WRITE_INDEX = "WRITE_INDEX";
    static final String ATOMIC_REPLACE = "ATOMIC_REPLACE";
    static final String REGISTER_WORKSPACE = "REGISTER_WORKSPACE";
    static final String COMPLETE = "COMPLETE";

    private static final List<String> CODES = List.of(
            QUEUED, LEASE_CLAIM, LOCAL_LOCK, STAGING, SHALLOW_CLONE, FETCH_FIXED_COMMIT,
            SPARSE_CHECKOUT, VALIDATE, REMOVE_GIT_METADATA, WRITE_INDEX, ATOMIC_REPLACE,
            REGISTER_WORKSPACE, COMPLETE);

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry(QUEUED, "目标服务器排队"),
            Map.entry(LEASE_CLAIM, "数据库租约认领"),
            Map.entry(LOCAL_LOCK, "本机目录加锁"),
            Map.entry(STAGING, "准备暂存目录"),
            Map.entry(SHALLOW_CLONE, "浅克隆源码"),
            Map.entry(FETCH_FIXED_COMMIT, "获取固定提交"),
            Map.entry(SPARSE_CHECKOUT, "检出所选源码"),
            Map.entry(VALIDATE, "校验源码快照"),
            Map.entry(REMOVE_GIT_METADATA, "删除 Git 元数据"),
            Map.entry(WRITE_INDEX, "写入源码索引"),
            Map.entry(ATOMIC_REPLACE, "原子发布源码"),
            Map.entry(REGISTER_WORKSPACE, "注册运行工作区"),
            Map.entry(COMPLETE, "完成服务器任务"));

    private AppSourceReplicaStepCatalog() {
    }

    static List<AppSourceOperationStep> pendingSteps(
            String operationId, LinuxServerId serverId, Instant acceptedAt) {
        return java.util.stream.IntStream.range(0, CODES.size())
                .mapToObj(sequence -> pendingStep(operationId, serverId, CODES.get(sequence), sequence, acceptedAt))
                .toList();
    }

    static List<String> codes() {
        return CODES;
    }

    static String summary(String stepCode, AppSourceStepStatus status) {
        String label = LABELS.get(stepCode);
        if (label == null) {
            throw new IllegalArgumentException("unknown app-source step code");
        }
        return switch (status) {
            case PENDING -> "等待：" + label;
            case RUNNING -> "正在执行：" + label;
            case SUCCEEDED -> "已完成：" + label;
            case FAILED -> "执行失败：" + label;
            case SKIPPED -> "已跳过：" + label;
        };
    }

    static String stepId(String operationId, LinuxServerId serverId, String stepCode) {
        return "ass_" + UUID.nameUUIDFromBytes(
                        (operationId + "\n" + serverId.value() + "\n" + stepCode)
                                .getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
    }

    private static AppSourceOperationStep pendingStep(
            String operationId,
            LinuxServerId serverId,
            String stepCode,
            int sequence,
            Instant acceptedAt) {
        return new AppSourceOperationStep(
                stepId(operationId, serverId, stepCode), operationId, AppSourceStepScope.SERVER,
                serverId, stepCode, sequence, AppSourceStepStatus.PENDING,
                summary(stepCode, AppSourceStepStatus.PENDING), null, null, acceptedAt);
    }
}
