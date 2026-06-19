# 包说明：@test-agent/file-explorer/src

## 职责

实现文件树 UI、基础搜索和变更文件入口，并保持 Phase 11 context 选择场景下的稳定列表布局。

## 主要程序清单

- `FileExplorer.tsx`：三 tab 文件面板、Changed Files 入口、紧凑状态徽标和选择回调。
- `filterLoadedFiles.ts`：已加载文件名过滤。

## 允许依赖

- React。
- `@test-agent/shared-types`。
- `@test-agent/ui-kit`。

## 禁止依赖

- 直接访问后端 API。
- 内容搜索或 Git 操作实现。
- 直接构造 `PromptPart` 或读取文件内容。

## 修改时必须同步更新

- `docs/frontend/frontend-coding-standards.md`。
- 本包 README 和测试。
