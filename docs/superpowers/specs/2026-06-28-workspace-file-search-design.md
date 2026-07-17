# 工作空间文件搜索设计

## 概述

为工作空间增加模糊搜索文件的能力，支持在整个工作空间范围内递归搜索文件名，并在搜索结果中高亮显示匹配的关键字。

## 需求确认

| 决策项 | 选择 |
|--------|------|
| 搜索范围 | 整个工作空间（后端递归搜索） |
| 模糊匹配规则 | 子串匹配（不区分大小写） |
| 匹配目标 | 仅文件名，不匹配路径 |
| 结果展示 | 文件名高亮关键字 + 父目录路径灰色显示 |
| 传输层 | WebSocket RPC（与现有文件操作一致） |
| 目录过滤 | 硬编码黑名单（`.git`, `node_modules` 等），后续扩展配置 |

## 架构与数据流

```
[前端 FileExplorer 搜索 Tab]
    → emit("search", keyword) → [AgentWorkbench]
    → api.searchFiles(workspaceId, keyword)
    → WebSocket RPC {op: "workspace.search", params: {workspaceId, query}}
    → [WorkspaceFileWebSocketHandler] → workspaceService.searchFiles(...)
    → [WorkspaceFileService.searchFiles] 递归扫描，返回匹配结果
    → 前端接收结果 → 传入 FileExplorer searchResults prop
    → 渲染：文件名高亮关键字 + 父目录灰色路径
```

## 后端设计

### 新增 DTO

```java
// FileSearchResultResponse.java
package com.enterprise.testagent.workspace;

public record FileSearchResultResponse(
    String path,           // 相对路径，如 "src/components/AgentConfig.vue"
    String name,           // 文件名，如 "AgentConfig.vue"
    String directory,      // 父目录相对路径，如 "src/components"
    long size,
    Instant lastModifiedAt
) {}
```

### WorkspaceFileService 新增方法

```java
/**
 * 在 rootPath 下递归搜索文件名包含 query（不区分大小写）的文件。
 * 忽略黑名单目录，结果按文件名排序，最多返回 maxResults 条。
 */
public List<FileSearchResultResponse> searchFiles(String rootPath, String query, int maxResults);
```

**匹配规则：**
- 仅匹配文件名（不含路径），子串匹配，不区分大小写。
- 仅返回文件，不返回目录。
- 结果按文件名字母排序，最多返回 `maxResults` 条（默认 200）。

**安全与性能：**
- 复用 `resolveInsideRoot` 确保递归不越界。
- 深度优先遍历，深度上限 20。
- 超时保护 5 秒（使用 `CompletableFuture` + `orTimeout`）。
- 黑名单目录（硬编码）：`.git`, `node_modules`, `.idea`, `target`, `build`, `.gradle`, `__pycache__`, `.venv`, `venv`。

### WorkspaceApplicationService 新增方法

```java
public List<FileSearchResultResponse> searchFiles(WorkspaceId workspaceId, String query);
```

### WorkspaceFileWebSocketHandler 新增操作

在 `handleMessage` 的 switch 中新增：

```java
case "workspace.search" -> workspaceService.searchFiles(workspaceId(ticket, params), text(params, "query"));
```

请求格式：`{id, op: "workspace.search", params: {workspaceId?, query}}`

响应格式：`{id, type: "result", data: [...results], traceId}`

## 前端设计

### shared-types 新增类型

```ts
// FileSearchResult
export type FileSearchResult = {
  path: string;         // 相对路径
  name: string;         // 文件名
  directory: string;    // 父目录路径
  size: number;
  modifiedAt?: string;
};
```

### backend-api 新增方法

```ts
searchFiles: async (workspaceId: string, query: string) => {
  const results = await workspaceFileRpc<BackendFileSearchResult[]>(workspaceId, "workspace.search", { query });
  return results.map(toFileSearchResult);
}
```

### file-explorer 包改造

**新增 `highlightKeyword.ts`：**

```ts
// 输入 (text, keyword)，输出分段数组 [{text, match: boolean}]
// 用于渲染高亮 span，大小写不敏感
export function highlightKeyword(text: string, keyword: string): Array<{text: string; match: boolean}>;
```

**FileExplorer.vue 搜索 Tab 改造：**

- 不再用 `filterLoadedFiles` 过滤已加载文件。
- 新增 props：`searchResults`, `searchLoading`, `searchError`。
- 新增 emit：`search(keyword: string)`。
- 结果渲染：
  - 文件名用分段渲染，匹配片段用 `<mark>` 高亮。
  - 下方灰色小字显示父目录路径。
- 状态：loading 时显示加载提示，空结果时显示"无匹配文件"。

### AgentWorkbench.vue 改造

- 新增 ref：`searchKeyword`, `searchResults`, `searchLoading`, `searchError`。
- 监听 `search` emit，用防抖（250ms）调用 `api.searchFiles`。
- 处理 loading、空结果、错误状态。
- 切换到搜索 Tab 或清空关键字时清空结果。

## 文件改动清单

| 文件 | 改动类型 |
|------|----------|
| `backend/test-agent-workspace-management/src/main/java/com/enterprise/testagent/workspace/FileSearchResultResponse.java` | 新增 |
| `backend/test-agent-workspace-management/src/main/java/com/enterprise/testagent/workspace/WorkspaceFileService.java` | 修改 |
| `backend/test-agent-workspace-management/src/main/java/com/enterprise/testagent/workspace/WorkspaceApplicationService.java` | 修改 |
| `backend/test-agent-api/src/main/java/com/enterprise/testagent/api/web/platform/WorkspaceFileWebSocketHandler.java` | 修改 |
| `frontend/packages/shared-types/src/index.ts` | 修改 |
| `frontend/packages/backend-api/src/index.ts` | 修改 |
| `frontend/packages/file-explorer/src/highlightKeyword.ts` | 新增 |
| `frontend/packages/file-explorer/src/FileExplorer.vue` | 修改 |
| `frontend/apps/agent-web/src/components/AgentWorkbench.vue` | 修改 |
| `docs/api/event-stream.md` | 修改（补充 workspace.search RPC） |
| `frontend/packages/file-explorer/README.md` | 修改 |

## 测试要点

- 后端单测：匹配规则、黑名单过滤、深度限制、结果上限。
- 前端单测：`highlightKeyword` 分段逻辑。
- 集成测试：搜索流程端到端验证。

## 风险与后续

- 大仓库搜索性能：当前硬编码黑名单 + 超时保护，后续可引入可配置过滤规则。
- 黑名单配置化：后续可通过配置项扩展。
