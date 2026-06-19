# 包说明：@test-agent/shared-types/src

## 职责

提供前端共享类型和稳定字段定义。

## 主要程序清单

- `index.ts`：共享类型出口。

## 允许依赖

- TypeScript 类型系统。

## 禁止依赖

- 运行时业务包、React、后端 SDK。

## 修改时必须同步更新

- `docs/frontend/frontend-backend-contract.md`。
- `docs/api/*`，如果字段来自后端契约。
