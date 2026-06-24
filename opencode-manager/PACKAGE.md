# 包说明：opencode-manager

## 工程定位

与 `backend/` 平级的独立 Go 单二进制工程，运行在 opencode 容器内，负责本容器的 opencode server 进程生命周期。

## 包边界

- `cmd/opencode-manager`：CLI 协议适配、参数解析和 JSON 输出。
- `internal/config`：环境变量解析、端口池和路径校验。
- `internal/state`：本地 state 文件读写，维护端口到 PID 的索引。
- `internal/health`：PID 存活和 opencode HTTP 健康探测。
- `internal/process`：启动、停止、重启、健康检测和列表编排。

## 禁止事项

- 不访问后端数据库。
- 不连接后端 Java socket；批次 4 再接入。
- 不硬编码 token、账号、生产地址或 opencode server 密码。
- 不把用户 prompt、Authorization、Cookie 或 token 写入 manager 自身日志。

## 测试

```bash
go test ./...
go test ./... -race
```
