# 包说明：@test-agent/diff-viewer/src

## 职责

展示 Run/Session/VCS Diff，并暴露 Run 级接受/拒绝动作入口。

## 主要程序清单

- `DiffViewer.vue`：Monaco Diff、来源切换、split/unified、动作栏、文件列表和 hunk 导航。
- `MergeConflictEditor.vue`：Monaco 三方冲突工作台，用响应式结果桥接预设选择、手工编辑和保存解决动作；卸载时标记异步初始化失效并释放 editor/model，迟到的动态 chunk 不再创建资源。
- `monaco-env.ts`：Monaco Web Worker 配置（懒加载）。
- `hunks.ts`：从 unified patch 提取 hunk 范围、循环导航和 hunk 到 `PromptPart` file context 的转换。
- `unifiedPatch.ts`：统一 diff patch 到 original/modified 文本的轻量转换。

## 允许依赖

- Vue 3。
- `monaco-editor`（原生）。
- `@test-agent/shared-types`。
- `@test-agent/ui-kit`。

## 禁止依赖

- 直接调用后端 API。
- per-file 后端回滚实现。
- 直接发送 prompt 或订阅 RunEvent。

## 修改时必须同步更新

- `docs/api/http-api.md`。
- `docs/architecture/module-map.md`。
- 本包 README 和测试。
