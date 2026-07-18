# 引用资产库选择同步进度 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 点击已初始化引用资产库卡片后立即展示真实同步任务的三阶段与逐服务器进度，同时保持右上角 Git 指针只读核验交互不变。

**Architecture:** 仅在 `ReferenceConfigurationDialog.vue` 内把现有核验专用进度状态抽象为 `SYNCHRONIZE/VERIFY_POINTERS` 两种操作共享的状态机；卡片继续调用 `/synchronize`，刷新按钮继续调用 `/verify`。弹层使用仓库 ID、操作类型、请求序号和后端 generation 共同隔离轮询快照，不改 backend-api 类型或后端接口。

**Tech Stack:** Vue 3 `<script setup>`、TypeScript、Vitest、Vue Test Utils、pnpm。

## Global Constraints

- 不新增或修改 HTTP 路径、请求体、后端状态、RunEvent、内部广播、数据库、manager 协议或环境配置。
- 点击已初始化资产库卡片必须在 `/synchronize` Promise 返回前打开同步进度；未初始化资产库不打开该弹层。
- 同步与核验分别使用真实接口和专用文案，不能在同步后追加 `/verify`。
- 活动期间禁止关闭内外弹层并限制焦点；终态手动关闭后，焦点回到本次触发卡片或刷新按钮。
- 保留现有 2 秒轮询、选择代次、响应序号及 generation fencing。

---

### Task 1: 用失败回归锁定卡片同步进度

**Files:**
- Modify: `frontend/apps/agent-web/tests/reference-configuration-dialog.test.ts`
- Modify: `frontend/apps/agent-web/src/components/ReferenceConfigurationDialog.vue`

**Interfaces:**
- Consumes: `BackendApiClient.synchronizeReferenceRepository(appId, repositoryId)` 返回 `ReferenceRepositoryStatus`，其 `operation` 为 `SYNCHRONIZE`。
- Produces: `RepositoryOperationProgress`、`acceptedOperationRepository`、`operationStepState()` 以及动态同步/核验弹层。

- [x] **Step 1: 写同步请求返回前即展示弹层的失败测试**

在现有核验弹层测试之前加入：

```ts
it("opens synchronization progress before selecting an initialized repository finishes", async () => {
  const synchronization = deferred<ReferenceRepositoryStatus>();
  const mockApi = api({
    synchronizeReferenceRepository: vi.fn().mockReturnValue(synchronization.promise),
    verifyReferenceRepositoryPointers: vi.fn()
  });
  const wrapper = render(mockApi);
  await flushPromises();

  await wrapper.get('button[aria-label="选择需求资产库"]').trigger("click");
  await wrapper.vm.$nextTick();

  const progress = wrapper.get('[aria-label="资产库同步进度"]');
  expect(progress.text()).toContain("创建同步任务");
  expect(progress.text()).toContain("正在创建");
  expect(wrapper.get('button[aria-label="关闭资产库同步进度"]').attributes()).toHaveProperty("disabled");
  expect(wrapper.get(".reference-dialog-header").attributes()).toHaveProperty("inert");
  expect(wrapper.get(".reference-dialog-body").attributes()).toHaveProperty("inert");
  expect(document.activeElement).toBe(progress.element);
  expect(mockApi.synchronizeReferenceRepository).toHaveBeenCalledWith("app-demo", "repo-assets");
  expect(mockApi.verifyReferenceRepositoryPointers).not.toHaveBeenCalled();

  synchronization.resolve(status({
    generation: 2,
    status: "SYNCHRONIZING",
    operation: "SYNCHRONIZE",
    targetServerCount: 2,
    readyServerCount: 0,
    servers: [
      { linuxServerId: "linux-a", status: "PENDING", online: true },
      { linuxServerId: "linux-b", status: "PROCESSING", online: true }
    ]
  }));
  await flushPromises();

  expect(progress.text()).toContain("各服务器同步");
  expect(progress.text()).toContain("等待同步");
  expect(progress.text()).toContain("同步中");
});
```

- [x] **Step 2: 运行定向测试并确认红灯原因正确**

Run: `cd frontend && corepack pnpm test -- apps/agent-web/tests/reference-configuration-dialog.test.ts`

Expected: FAIL，找不到 `aria-label="资产库同步进度"`；现有核验测试仍可执行。

- [x] **Step 3: 抽象操作进度状态并按操作过滤后端快照**

在组件中把核验专用类型和 ref 改为：

```ts
type RepositoryProgressOperation = "SYNCHRONIZE" | "VERIFY_POINTERS";
type RepositoryOperationTrigger = "repository-card" | "verify-button";
type RepositoryOperationRequestState = "REQUESTING" | "ACCEPTED" | "FAILED";
type RepositoryOperationStepState = "waiting" | "running" | "completed" | "failed";
type RepositoryOperationProgress = {
  repositoryId: string;
  requestToken: number;
  operation: RepositoryProgressOperation;
  trigger: RepositoryOperationTrigger;
  requestState: RepositoryOperationRequestState;
  generation: number | null;
  error: Notice | null;
};

const operationProgress = ref<RepositoryOperationProgress | null>(null);
```

把 `acceptedVerificationRepository` 改为 `acceptedOperationRepository`，必须同时验证仓库、代次和真实操作：

```ts
const acceptedOperationRepository = computed(() => {
  const progress = operationProgress.value;
  const repository = operationRepository.value;
  if (!progress || progress.requestState !== "ACCEPTED" || progress.generation === null || !repository) return null;
  if (repository.generation < progress.generation || repository.operation !== progress.operation) return null;
  return repository;
});
```

保留现有 CSS 类名以避免无关样式变更；将步骤、标题、服务器状态和错误缺省文案改为基于 `operationProgress.operation` 返回：同步模式使用“创建同步任务 / 各服务器同步 / 汇总同步结果”“等待同步 / 同步中 / 已同步 / 同步失败”，核验模式保持现有文案。

- [x] **Step 4: 卡片先建进度状态，再发起同步请求**

在 `selectRepository()` 完成 `resetSelectionState()` 和选中仓库后、调用 API 前创建状态，并立即把焦点移入弹层：

```ts
const requestToken = ++operationRequestSequence;
operationProgress.value = {
  repositoryId: repository.repositoryId,
  requestToken,
  operation: "SYNCHRONIZE",
  trigger: "repository-card",
  requestState: "REQUESTING",
  generation: null,
  error: null
};
void nextTick(() => operationDialogElement.value?.focus());
```

请求成功后只有上下文和 `requestToken` 仍匹配时才能写入 `ACCEPTED` 与 `next.generation`；请求失败写入 `FAILED` 和 `notice(error, "同步引用资产库失败")`。`verifyPointers()` 使用同一状态结构，但写入 `operation: "VERIFY_POINTERS"`、`trigger: "verify-button"` 并继续调用只读核验 API。

- [x] **Step 5: 把模板改为动态操作弹层**

给仓库卡片按钮增加稳定焦点标记：

```vue
:data-reference-repository-select="repository.repositoryId"
```

弹层的可访问名称、标题、关闭与重试标签按操作动态生成；核心绑定为：

```vue
<section
  ref="operationDialogElement"
  role="dialog"
  aria-modal="true"
  :aria-label="operationProgress?.operation === 'SYNCHRONIZE' ? '资产库同步进度' : 'Git 指针核验进度'"
  tabindex="-1"
>
  <h3>{{ operationProgress?.operation === "SYNCHRONIZE" ? "同步资产库" : "刷新 Git 指针" }}</h3>
</section>
```

父弹层 header/body 的 `inert`、Escape/Tab 焦点限制以及关闭按钮禁用条件全部改为读取 `operationProgress`，不能只覆盖核验。

- [x] **Step 6: 运行定向测试并确认同步与核验均通过**

Run: `cd frontend && corepack pnpm test -- apps/agent-web/tests/reference-configuration-dialog.test.ts`

Expected: PASS，包括新增同步测试和已有 `Git 指针核验进度` 测试。

- [x] **Step 7: 提交核心交互**

```bash
git add frontend/apps/agent-web/src/components/ReferenceConfigurationDialog.vue frontend/apps/agent-web/tests/reference-configuration-dialog.test.ts
git commit -m "fix: 点击引用资产库时展示同步进度"
```

### Task 2: 覆盖失败重试、活动任务接管和焦点恢复

**Files:**
- Modify: `frontend/apps/agent-web/tests/reference-configuration-dialog.test.ts`
- Modify: `frontend/apps/agent-web/src/components/ReferenceConfigurationDialog.vue`

**Interfaces:**
- Consumes: Task 1 的 `RepositoryOperationProgress` 和 `acceptedOperationRepository`。
- Produces: `retryOperation()`、`closeOperationProgress()`、活动任务接管逻辑。

- [x] **Step 1: 写同步失败重试与卡片焦点恢复测试**

新增测试，让第一次同步抛出带 `traceId` 的 `BackendApiError`，断言弹层显示错误且“重试资产库同步”再次调用 `/synchronize`；第二次返回 `READY/SYNCHRONIZE` 后关闭弹层，断言焦点恢复到：

```ts
wrapper.get('button[data-reference-repository-select="repo-assets"]').element
```

同时断言活动期关闭按钮 disabled，终态后 enabled。

- [x] **Step 2: 写活动任务接管测试**

列表返回以下状态并点击卡片：

```ts
status({
  generation: 4,
  status: "SYNCHRONIZING",
  operation: "SYNCHRONIZE",
  readyServerCount: 0
})
```

断言直接显示 `资产库同步进度`、不调用 `synchronizeReferenceRepository`，2 秒后通过既有 `getReferenceRepositoryStatus` 轮询。未初始化仓库点击后断言不显示操作弹层。

复用既有 `discards stale status responses after switching repositories and stops polling when closed` 两仓库 deferred 用例覆盖切换隔离；把测试状态夹具的默认 operation 设为 `SYNCHRONIZE` 后，迟到的仓库 A 轮询结果仍不能覆盖仓库 B。已有核验测试继续断言 `operation=VERIFY_POINTERS`，共同证明同步与核验的 operation fencing 不互相消费。

- [x] **Step 3: 运行新增边界测试并确认状态机覆盖行为**

Run: `cd frontend && corepack pnpm test -- apps/agent-web/tests/reference-configuration-dialog.test.ts`

Expected: PASS；这些边界由 Task 1 已完成的通用操作状态机直接覆盖，新增断言用于防止后续回归。

- [x] **Step 4: 实现通用重试、关闭和活动任务接管**

`retryOperation()` 按当前操作分派真实请求：

```ts
function retryOperation() {
  const repository = operationRepository.value;
  const progress = operationProgress.value;
  if (!repository || !progress || !operationCanRetry.value) return;
  if (progress.operation === "SYNCHRONIZE") {
    void selectRepository(repository);
    return;
  }
  void verifyPointers(repository);
}
```

`closeOperationProgress()` 在清空状态前保存 `trigger/repositoryId`，终态关闭后使用 `nextTick` 查询对应卡片或 `button[data-reference-verify="true"]` 并聚焦。`selectRepository()` 遇到 `ACTIVE_STATUSES` 时，如果 `repository.operation` 是 `SYNCHRONIZE` 或 `VERIFY_POINTERS`，创建 `ACCEPTED` 状态并绑定当前 generation，不再发起 POST，只启动既有状态轮询。

- [x] **Step 5: 修正受新终态弹层影响的既有测试辅助方法**

新增并复用以下辅助方法，让需要继续操作目录或刷新按钮的旧测试显式关闭已完成同步结果：

```ts
async function closeCompletedSyncProgress(wrapper: ReturnType<typeof render>) {
  const close = wrapper.find('button[aria-label="关闭资产库同步进度"]');
  if (close.exists() && !close.attributes("disabled")) {
    await close.trigger("click");
    await flushPromises();
  }
}
```

只在同步 mock 返回 `READY` 且测试随后操作父弹层时调用，进度行为测试不得调用该辅助方法。

- [x] **Step 6: 运行组件测试并确认全部通过**

Run: `cd frontend && corepack pnpm test -- apps/agent-web/tests/reference-configuration-dialog.test.ts`

Expected: PASS，且无未处理 Promise、fake timer 或 Vue warning。

- [x] **Step 7: 提交边界行为**

```bash
git add frontend/apps/agent-web/src/components/ReferenceConfigurationDialog.vue frontend/apps/agent-web/tests/reference-configuration-dialog.test.ts
git commit -m "test: 完善引用资产同步进度边界"
```

### Task 3: 同步稳定文档并完成全量验证

**Files:**
- Modify: `frontend/README.md`
- Modify: `frontend/apps/agent-web/README.md`
- Modify: `frontend/apps/agent-web/src/PACKAGE.md`
- Modify: `frontend/apps/user-manual/docs/guide/reference-config.md`
- Modify: `.agents/session-log.huangzhenren.md`

**Interfaces:**
- Consumes: Task 1、Task 2 已通过测试的最终交互。
- Produces: 面向研发和用户的稳定行为说明，以及本机提交者会话记录。

- [x] **Step 1: 更新工程和包说明**

在三个工程说明中明确：左侧已初始化资产库卡片在同步 POST 返回前打开“创建同步任务 → 各服务器同步 → 汇总同步结果”弹层；右侧按钮仍为只读核验；两种操作都按仓库、operation、generation 和请求序号隔离，活动期锁定，终态手动关闭并恢复各自触发焦点。

- [x] **Step 2: 更新用户手册操作步骤**

在“初始化或同步资产库”第 4 步补充卡片同步进度及逐服务器“等待同步/同步中/已同步/失败/重试/离线延后”说明；保留第 6 步只读核验描述，并明确两者不会互相追加请求。

- [x] **Step 3: 执行前端定向与全量校验**

Run: `cd frontend && corepack pnpm test -- apps/agent-web/tests/reference-configuration-dialog.test.ts`

Expected: PASS。

Run: `cd frontend && corepack pnpm test`

Expected: PASS。

Run: `cd frontend && corepack pnpm typecheck`

Expected: PASS。

Run: `cd frontend && corepack pnpm lint`

Expected: PASS。

Run: `cd frontend && corepack pnpm build`

Expected: PASS；用户手册和 agent-web 构建成功。

Run: `git diff --check`

Expected: 无输出，退出码 0。

- [x] **Step 4: 按本机身份记录会话结果**

在 `.agents/session-log.huangzhenren.md` 顶部近期记录区增加一条 `Why / What / How / Result`，记录卡片同步进度根因、操作感知状态机、执行过的测试以及已知 Playwright 基线情况；不修改冻结的 `.agents/session-log.md`。

- [x] **Step 5: 回顾全部会话日志并检查暂存边界**

Run: `for file in .agents/session-log*.md; do sed -n '1,220p' "$file"; done`

Expected: 无冲突标记；暂存内容不覆盖其它开发者成果。

Run: `git status --short && git diff --stat && git diff --cached --stat`

Expected: 只有本功能代码、测试、稳定文档、计划和本机 session log；已有其它提交保持不变。

- [x] **Step 6: 提交文档与会话记录**

```bash
git add frontend/README.md frontend/apps/agent-web/README.md frontend/apps/agent-web/src/PACKAGE.md frontend/apps/user-manual/docs/guide/reference-config.md .agents/session-log.huangzhenren.md docs/superpowers/plans/2026-07-18-reference-repository-selection-sync-progress.md
git commit -m "docs: 更新引用资产同步进度说明"
```

- [ ] **Step 7: 推送当前 main 分支**

先执行 `git fetch origin main` 和 `git status --short --branch`，确认没有远端分叉后运行：

```bash
git push origin main
```

Expected: 当前 `main` 的新增中文提交全部推送成功；不得 force push。
