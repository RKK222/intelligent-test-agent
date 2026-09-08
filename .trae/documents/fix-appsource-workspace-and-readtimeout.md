# 修复应用代码库两个问题

## 摘要

用户在页面切换工作空间选择"应用代码库"后存在两个问题：

1. **发起会话报错 "Workspace 不存在"**：APP\_SOURCE 工作区首次创建会话时通不过鉴权
2. **物化提交报 readtimeout 但实际克隆成功**：materialize 接口内部同步 git 远端操作（60秒）超过前端 HTTP 超时（30秒）

## 问题1：Workspace 不存在

### 根因

`SessionApplicationService.requireUserWorkspace`（[SessionApplicationService.java:633-648](file:///d:/workspace/intelligent-test-agent/backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/session/SessionApplicationService.java#L633-L648)）直接调用底层 `UserWorkspaceQueryRepository.findUserWorkspace(userId, workspaceId)`，该方法对应的 SQL（[UserWorkspaceQueryMapper.xml:19-85](file:///d:/workspace/intelligent-test-agent/backend/test-agent-persistence/src/main/resources/mybatis/UserWorkspaceQueryMapper.xml#L19-L85)）只认可三类归属：

- `local_client_workspaces`（本地工作区）

- `personal_workspaces`（个人工作区）

- 该工作区下已有该用户的 ACTIVE session

应用代码库（APP\_SOURCE）工作区由 `app_source_replicas.runtime_workspace_id` 映射，**不在这三类里**。首次发起会话时第三类（已有 session）也不成立，SQL 返回 empty → 抛 `Workspace 不存在`。

正确的鉴权在 [UserWorkspaceQueryService.requireUserWorkspace:94-116](file:///d:/workspace/intelligent-test-agent/backend/test-agent-workspace-management/src/main/java/com/enterprise/testagent/workspace/UserWorkspaceQueryService.java#L94-L116)：findUserWorkspace 为空时调 `workspaceAccessAuthorizer.requireClassifiedFileAccess` 判定是否 APP\_SOURCE，是则回退 `workspaceRepository.findById` 兜底。

### 约束

- [dependency-rules.md:62-68](file:///d:/workspace/intelligent-test-agent/docs/architecture/dependency-rules.md#L62-L68) 规定 `test-agent-opencode-runtime` **不允许依赖** **`test-agent-workspace-management`**，所以不能直接注入 `UserWorkspaceQueryService`

- `ConversationWorkspaceAccessAuthorizer` 接口在 domain 层（[ConversationWorkspaceAccessAuthorizer.java](file:///d:/workspace/intelligent-test-agent/backend/test-agent-domain/src/main/java/com/enterprise/testagent/domain/workspace/ConversationWorkspaceAccessAuthorizer.java)），opencode-runtime 已依赖 domain，可直接注入

- AGENTS.md 规则10要求复用公共鉴权逻辑，不自行绕过

### 修复方案

在 `SessionApplicationService` 注入 `ConversationWorkspaceAccessAuthorizer`（domain 接口），在 `requireUserWorkspace` 方法里复用 `UserWorkspaceQueryService` 的 APP\_SOURCE 回退逻辑。

#### 改动文件

**1.** **[SessionApplicationService.java](file:///d:/workspace/intelligent-test-agent/backend/test-agent-opencode-runtime/src/main/java/com/enterprise/testagent/opencode/runtime/session/SessionApplicationService.java)**

- 新增字段 `private ConversationWorkspaceAccessAuthorizer workspaceAccessAuthorizer;`（已有 `experienceWorkspaceAccessAuthorizer`，区分命名）

- 新增 `@Autowired(required = false)` setter 方法 `configureWorkspaceAccessAuthorizer`，注入 `ConversationWorkspaceAccessAuthorizer`

- 修改 `requireUserWorkspace` 方法（第633-648行），复用 APP\_SOURCE 回退逻辑：

```java
private void requireUserWorkspace(UserId userId, WorkspaceId workspaceId) {
    if (userId == null || userWorkspaceQueryRepository == null) {
        return;
    }
    if (experienceWorkspaceAccessAuthorizer != null
            && experienceWorkspaceAccessAuthorizer.isExperienceWorkspace(workspaceId)) {
        experienceWorkspaceAccessAuthorizer.requireAccess(userId, workspaceId);
        return;
    }
    if (userWorkspaceQueryRepository.findUserWorkspace(userId, workspaceId).isPresent()) {
        return;
    }
    // APP_SOURCE 工作区由 app_source_replicas 映射，不在用户归属表内；
    // 复用 ConversationWorkspaceAccessAuthorizer 分类鉴权，仅 APP_SOURCE 回退到主表校验 ACTIVE。
    if (workspaceAccessAuthorizer != null) {
        var kind = workspaceAccessAuthorizer.requireClassifiedFileAccess(userId, workspaceId, true);
        if (kind == ConversationWorkspaceAccessAuthorizer.FileWorkspaceKind.APP_SOURCE) {
            workspaceRepository.findById(workspaceId)
                    .filter(workspace -> workspace.status() == WorkspaceStatus.ACTIVE)
                    .orElseThrow(() -> new PlatformException(
                            ErrorCode.NOT_FOUND,
                            "Workspace 不存在",
                            Map.of("workspaceId", workspaceId.value())));
            return;
        }
    }
    throw new PlatformException(
            ErrorCode.NOT_FOUND,
            "Workspace 不存在",
            Map.of("workspaceId", workspaceId.value()));
}
```

- 需要 import `WorkspaceStatus`（已在 domain 层，opencode-runtime 已依赖）

**2. 测试文件**

- 搜索 `backend/test-agent-opencode-runtime/src/test` 下 SessionApplicationService 的测试

- 新增测试：APP\_SOURCE 工作区首次创建会话应成功（mock findUserWorkspace 返回 empty + requireClassifiedFileAccess 返回 APP\_SOURCE + workspaceRepository.findById 返回 ACTIVE workspace）

- 新增测试：非 APP\_SOURCE 工作区 findUserWorkspace 为空时仍应抛 NOT\_FOUND

## 问题2：物化提交 readtimeout 但克隆成功

### 根因

`AppSourceApplicationService.materialize`（[AppSourceApplicationService.java:377-433](file:///d:/workspace/intelligent-test-agent/backend/test-agent-workspace-management/src/main/java/com/enterprise/testagent/workspace/AppSourceApplicationService.java#L377-L433)）方法内部有**两处同步 git 远端操作**：

- 第414行：`git.resolveRemoteBranchCommit(access.url(), branch, access.privateKey())` — git ls-remote 解析分支 commit

- 第420行：`remote.listTree(access.url(), targetCommit, access.privateKey())` — git archive 验证选中路径

这两个操作调用 `GitRemoteService`（[GitRemoteService.java:18](file:///d:/workspace/intelligent-test-agent/backend/test-agent-common/src/main/java/com/enterprise/testagent/common/git/GitRemoteService.java#L18)），后端超时 `DEFAULT_TIMEOUT = 60秒`。

前端 HTTP 请求默认超时 30 秒（[backend-api/src/index.ts:592](file:///d:/workspace/intelligent-test-agent/frontend/packages/backend-api/src/index.ts#L592) `requestTimeoutMs = 30000`）。

时序：

1. 前端提交物化 → POST /materializations
2. 后端 materialize 方法调 git 远端操作（最多 60 秒）
3. 前端 30 秒超时 → 报 readtimeout
4. 后端 git 操作继续完成，materialize 方法继续执行
5. dispatcher.wake 触发 worker 异步克隆
6. worker 克隆最终成功

### 修复方案

给前端 `materializeAppSource` 调用设置 90 秒超时（大于后端 60 秒 git 超时），让后端 git 操作超时后能正确返回错误，而不是前端先超时断开。

#### 改动文件

**1.** **[backend-api/src/index.ts](file:///d:/workspace/intelligent-test-agent/frontend/packages/backend-api/src/index.ts)**

搜索 `materializeAppSource` 定义（约第2280-2288行），在请求选项中加 `timeoutMs: 90000`：

```typescript
materializeAppSource: (appId, repositoryId, payload) =>
    requestFrom(`/applications/${appId}/app-source-repositories/${repositoryId}/materializations`, {
        method: "POST",
        body: JSON.stringify(payload),
        timeoutMs: 90000
    })
```

同时给 `retryAppSourceReplicas`（retry 也调 materialize 逻辑）设置相同超时。

**2.** **[AgentWorkbench.vue](file:///d:/workspace/intelligent-test-agent/frontend/apps/agent-web/src/components/AgentWorkbench.vue)**

前端 `materializeAppSource`（第6840行）和 `retryAppSourceOperation`（第6858行）的 catch 错误提示优化：当错误为 `REQUEST_TIMEOUT` 时显示"源码物化超时，请稍后查看进度，后端可能仍在执行"，而不是通用失败提示。

## 假设与决策

1. **问题1不改 SQL**：不修改 `UserWorkspaceQueryMapper.xml` 的 SQL 加 APP\_SOURCE 归属，因为 APP\_SOURCE 工作区的归属判断需要应用成员校验（ConversationWorkspaceAccessAuthorizer），SQL 层不适合做这种业务逻辑。复用 domain 接口的分类回退是正确方案。

2. **问题2不改后端 git 超时**：不减小 `GitRemoteService.DEFAULT_TIMEOUT`（60秒），因为大仓库 git ls-remote/archive 可能需要时间；也不改 materialize 为完全异步（改变接口语义，风险大）。只调大前端超时是最小改动。

3. **问题2前端超时设为 90 秒**：后端 git 超时 60 秒 + 网络传输余量，90 秒足够让后端 git 操作超时后返回 `GIT_TIMEOUT` 错误，前端能正确显示错误而不是 readtimeout。

4. **分支**：两个修复都不新增部署节点，使用 `release` 分支。

## 验证步骤

1. **问题1验证**：

   - 编译 `test-agent-opencode-runtime` 模块

   - 运行 SessionApplicationService 单元测试

   - 手动测试：前端切换应用代码库后发起会话，不再报 "Workspace 不存在"

2. **问题2验证**：

   - 前端提交应用代码库物化，观察是否还报 readtimeout

   - 如果后端 git 操作超过 60 秒，前端应显示 "GIT\_TIMEOUT" 错误（90 秒内），而不是 readtimeout

   - 如果后端 git 操作在 60 秒内完成，前端应正常返回 operation

3. **回归验证**：

   - 普通托管工作区发起会话仍正常

   - 体验工作区发起会话仍正常

   - 本地客户端工作区发起会话仍正常

   - 应用代码库物化在正常网络下仍快速完成

