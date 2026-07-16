package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.agent.AgentSessionBinding;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.support.DomainValidation;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * 一次会话后续 Run 可复用的可信运行上下文。
 *
 * <p>上下文固化签发时的 Session、Workspace、执行节点与可空远端绑定快照，Run 启动不得再用客户端字段重建这些可信对象。
 */
public record ConversationRunContext(
        UserId userId,
        String agentId,
        String processId,
        String linuxServerId,
        OpencodeServerProcess processSnapshot,
        Session sessionSnapshot,
        Workspace workspaceSnapshot,
        ExecutionNode executionNodeSnapshot,
        AgentSessionBinding bindingSnapshot,
        int contextVersion,
        Instant expiresAt) {

    /**
     * 校验各快照之间的稳定绑定，防止把不同用户、服务器、进程或会话的数据拼成一个 token。
     */
    public ConversationRunContext {
        Objects.requireNonNull(userId, "userId must not be null");
        agentId = DomainValidation.requireText(agentId, "agentId").trim().toLowerCase(Locale.ROOT);
        processId = DomainValidation.requireText(processId, "processId");
        linuxServerId = DomainValidation.requireText(linuxServerId, "linuxServerId");
        Objects.requireNonNull(sessionSnapshot, "sessionSnapshot must not be null");
        Objects.requireNonNull(workspaceSnapshot, "workspaceSnapshot must not be null");
        Objects.requireNonNull(executionNodeSnapshot, "executionNodeSnapshot must not be null");
        if (!sessionSnapshot.workspaceId().equals(workspaceSnapshot.workspaceId())) {
            throw new IllegalArgumentException("session and workspace snapshot must match");
        }
        if (workspaceSnapshot.linuxServerId() == null
                || !workspaceSnapshot.linuxServerId().equals(linuxServerId)) {
            throw new IllegalArgumentException("workspace and process linux server must match");
        }
        if (!executionNodeSnapshot.executionNodeId().value().equals("node_" + processId)) {
            throw new IllegalArgumentException("execution node and process snapshot must match");
        }
        if (processSnapshot != null) {
            if (!processSnapshot.processId().value().equals(processId)
                    || !processSnapshot.userId().equals(userId)
                    || !processSnapshot.linuxServerId().value().equals(linuxServerId)) {
                throw new IllegalArgumentException("cached process snapshot must match context ownership");
            }
        }
        if (sessionSnapshot.createdByUserId() != null
                && !sessionSnapshot.createdByUserId().equals(userId)) {
            throw new IllegalArgumentException("session and user snapshot must match");
        }
        if (bindingSnapshot != null) {
            if (!bindingSnapshot.sessionId().equals(sessionSnapshot.sessionId())
                    || !bindingSnapshot.agentId().equals(agentId)
                    || !bindingSnapshot.executionNodeId().equals(executionNodeSnapshot.executionNodeId())) {
                throw new IllegalArgumentException("agent binding snapshot must match session, agent and execution node");
            }
        }
        if (contextVersion < 1) {
            throw new IllegalArgumentException("contextVersion must be greater than or equal to 1");
        }
        expiresAt = DomainValidation.requireInstant(expiresAt, "expiresAt");
    }

    /**
     * 兼容既有测试和升级前序列化形态；新签发上下文必须使用包含完整进程快照的主构造器。
     */
    public ConversationRunContext(
            UserId userId,
            String agentId,
            String processId,
            String linuxServerId,
            Session sessionSnapshot,
            Workspace workspaceSnapshot,
            ExecutionNode executionNodeSnapshot,
            AgentSessionBinding bindingSnapshot,
            int contextVersion,
            Instant expiresAt) {
        this(
                userId,
                agentId,
                processId,
                linuxServerId,
                null,
                sessionSnapshot,
                workspaceSnapshot,
                executionNodeSnapshot,
                bindingSnapshot,
                contextVersion,
                expiresAt);
    }

    public SessionId sessionId() {
        return sessionSnapshot.sessionId();
    }

    public WorkspaceId workspaceId() {
        return workspaceSnapshot.workspaceId();
    }

    public String executionNodeId() {
        return executionNodeSnapshot.executionNodeId().value();
    }

    public String trustedWorkspaceRoot() {
        return workspaceSnapshot.rootPath();
    }

    public String remoteSessionId() {
        return bindingSnapshot == null ? null : bindingSnapshot.remoteSessionId();
    }

    /**
     * 返回仅更新滑动过期时间的副本，其余可信快照保持不变。
     */
    public ConversationRunContext withExpiresAt(Instant expiresAt) {
        return new ConversationRunContext(
                userId,
                agentId,
                processId,
                linuxServerId,
                processSnapshot,
                sessionSnapshot,
                workspaceSnapshot,
                executionNodeSnapshot,
                bindingSnapshot,
                contextVersion,
                expiresAt);
    }
}
