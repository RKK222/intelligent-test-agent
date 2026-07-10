package com.icbc.testagent.domain.run;

import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.time.Duration;
import java.util.Optional;

/**
 * 会话运行上下文存储端口，领域与业务层不感知 Redis key 或序列化实现。
 */
public interface ConversationContextStore {

    /** 上下文默认采用 24 小时滑动有效期。 */
    Duration CONTEXT_TTL = Duration.ofHours(24);

    /**
     * 在权威数据读取前捕获签发代次；后续保存必须携带同一租约。
     */
    ConversationContextIssueLease beginIssue(UserId userId, SessionId sessionId);

    /**
     * 仅当签发期间没有任何相关资源失效时保存上下文；代次已变化时返回 false。
     */
    boolean saveIfCurrent(
            String contextToken,
            ConversationRunContext context,
            ConversationContextIssueLease issueLease);

    /**
     * 按 token 读取上下文；实现负责滑动续期，未命中时返回空。
     */
    Optional<ConversationRunContext> peek(String contextToken);

    /**
     * 在调用方完成身份绑定校验后原子续期；若期间已失效或代次变化则返回空，禁止复活旧值。
     */
    Optional<ConversationRunContext> touch(String contextToken, ConversationRunContext expectedContext);

    /**
     * 为 HTTP 路由只读解析当前有效上下文；校验用户、agent、会话及全部失效代次，但不续期或修改 Redis。
     */
    Optional<ConversationRunContext> resolveForRouting(
            String contextToken,
            UserId userId,
            String agentId,
            SessionId sessionId);

    /**
     * 使指定用户与会话的所有上下文失效。
     */
    void invalidate(UserId userId, SessionId sessionId);

    /**
     * 使指定用户持有的所有上下文失效；用于成员或角色撤权等无法低成本反查应用会话的入口。
     */
    void invalidateUser(UserId userId);

    /**
     * 使指定会话关联的所有用户上下文失效；用于会话归档等不依赖 owner 的生命周期事件。
     */
    void invalidateSession(SessionId sessionId);

    /**
     * 使指定工作区关联的所有上下文失效。
     */
    void invalidateWorkspace(WorkspaceId workspaceId);

    /**
     * 使指定用户进程快照关联的所有上下文失效。
     */
    void invalidateProcess(String processId);

    /**
     * 在用户权限关系型变更前建立临时 gate 并清理旧 token；gate 存在期间禁止该用户签发、续期和路由。
     */
    ConversationContextUserMutation beginUserMutation(UserId userId);

    /**
     * 用户权限写入提交后原子再次失效并释放当前 mutation gate，覆盖关系型写入窗口内的并发签发。
     */
    void completeUserMutation(ConversationContextUserMutation mutation);

    /**
     * 用户权限写入失败时仅释放当前 mutation gate；写前已清理的旧 token 不恢复。
     */
    void abortUserMutation(ConversationContextUserMutation mutation);

    /**
     * 在 Workspace 可信 root/server/status 关系型变更前建立临时 gate 并清理旧 token。
     */
    ConversationContextWorkspaceMutation beginWorkspaceMutation(WorkspaceId workspaceId);

    /**
     * Workspace 写入提交后原子再次失效并释放当前 mutation gate。
     */
    void completeWorkspaceMutation(ConversationContextWorkspaceMutation mutation);

    /**
     * Workspace 写入失败时仅释放当前 mutation gate；写前已清理的旧 token 不恢复。
     */
    void abortWorkspaceMutation(ConversationContextWorkspaceMutation mutation);

    /**
     * 原子建立会话撤销 gate 并清理现有 token；gate 存在时禁止开始或完成新的上下文签发。
     */
    ConversationContextSessionRevocation revokeSession(SessionId sessionId);

    /**
     * 归档持久化失败时按撤销 token 做 CAS 回滚，不得移除其它并发归档建立的 gate。
     */
    void restoreSessionRevocation(ConversationContextSessionRevocation revocation);

    /**
     * O(1) 提升全局代次，使可信路径等全局运行参数变化前签发的上下文全部失效。
     */
    void invalidateAll();
}
