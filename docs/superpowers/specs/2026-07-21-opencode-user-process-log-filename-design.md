# OpenCode 用户进程日志文件名设计

## 背景与目标

当前 `opencode-manager` 把每个用户 OpenCode server 的 stdout/stderr 追加到
`{OPENCODE_MANAGER_STATE_DIR}/logs/{port}.log`。端口来自动态端口池，用户进程停止后再次启动可能分配到其它端口，运维无法仅凭文件名稳定识别日志所属用户；同一端口后续复用给其他用户时还会把不同用户的输出追加到同一个文件。

本次改造目标是让每次真实启动生成独立日志文件，文件名固定为：

```text
logs/{统一认证号}-{启动时间}-{端口}.log
```

示例：

```text
logs/DEV_888888888-20260721T081530.123456789Z-4096.log
```

日志正文、进程生命周期、端口分配和健康检查语义保持不变。

## 已确认决策

1. 同一用户每次真实启动生成一个新文件，不跨启动追加到同一个用户文件。
2. 启动时间使用 UTC、纳秒精度、可按字符串排序的 `yyyyMMdd'T'HHmmss.nnnnnnnnn'Z'` 格式。
3. 文件名同时保留统一认证号和实际端口，既支持按用户检索，也保留单次进程排障上下文。
4. Java 后端通过现有 manager WebSocket `command` 帧显式下发统一认证号；manager 不访问数据库。
5. `unifiedAuthId` 是 `opencode-manager.v1` 的可选新增字段，不提升协议版本。新旧 Java/manager 可滚动升级。
6. 旧端口日志不迁移、不删除；升级后新启动才使用新命名。
7. 本地 CLI 或旧命令无法解析用户身份时继续写 `{port}.log`，不能因缺少新增字段阻断进程启动。

## 方案选择

采用“显式控制字段 + 稳定 session 路径校验”的方案。

- Java 已能从用户仓储取得统一认证号，并负责构造 `{OPENCODE_SESSION_DIR}/users/{unifiedAuthId}`；启动命令新增可选 `unifiedAuthId` 字段。
- manager 收到非空字段时，校验它与显式 `sessionPath` 的 `users/{unifiedAuthId}` 末级目录一致，再生成安全日志文件名。
- manager 收到旧命令或读取旧 state 时，可从符合稳定目录约定的 `sessionPath` 恢复统一认证号；无法可靠恢复时使用旧 `{port}.log`。

不采用以下方案：

- 只解析 `sessionPath`：改动最小，但把日志身份契约隐式绑定到目录结构；显式字段更便于校验、测试和后续演进。
- 读取 `ENTERPRISE_UCID` 子进程环境变量：该变量目前只在内部模型代理配置存在时注入，外部部署和部分测试场景并不稳定；manager 元数据也不应依赖业务子进程环境。
- 为统一认证号创建指向端口日志的软链接：端口变化后历史仍分散，且引入软链接替换与路径攻击风险，不能满足每次启动独立留档的要求。

## 数据流与代码边界

### Java 后端

1. `OpencodeProcessStartupService` 优先通过 `UserRepository` 解析用户统一认证号，并在构造 `OpencodeProcessStartCommand` 时写入规范化后的值；旧调用链无法查到用户时，只允许从已经符合 `users/{unifiedAuthId}` 约定的稳定 `sessionPath` 恢复，不能把平台 `userId` 冒充统一认证号。
2. `UserOpencodeProcessAssignmentService` 的中间启动命令同步携带同一统一认证号，避免命令模型出现语义缺口。
3. `SocketOpencodeProcessManagerGateway` 把 `unifiedAuthId` 放入现有 `ManagerControlMessage.command`。
4. `ManagerControlMessage` 只新增可空 JSON 字段，不改变消息类型、协议版本、超时或结果结构。

Controller 不参与该流程，也不直接调用 manager；公共 `OpencodeProcessStartupService` 仍是唯一启动程序，符合现有依赖和路由规则。

### Go manager

1. 控制协议 `Message`、`process.StartRequest` 和本地 `ProcessRecord` 增加可空统一认证号。
2. `BuildStartSpec` 在真实启动前确定一次 UTC `startedAt`，同一时间同时用于日志文件名和 state 记录，避免文件名时间与进程 state 不一致。
3. 统一认证号安全化后生成：

   ```text
   {stateDir}/logs/{safeUnifiedAuthId}-{startedAtUTC}-{port}.log
   ```

4. Unix 和 Windows 启动器继续把 stdout/stderr 同时指向 `StartSpec.LogPath`，打开模式仍为创建、追加、写入。
5. restart 从旧 state 保留统一认证号；旧 state 缺字段时按稳定 `users/{unifiedAuthId}` session 路径恢复。每次 restart 都生成新的启动时间，因此不会复用上一启动文件。
6. stop、health、list、PID state 文件名仍按端口管理，不改变 manager 对本地进程的索引和幂等语义。

## 文件名安全与长度

统一认证号来自数据库，但 manager 仍把控制帧视为不可信输入：

- 去除首尾空白，空值回退兼容路径。
- 显式字段与 `sessionPath` 可解析出的统一认证号不一致时拒绝启动，防止日志错误归属。
- ASCII 字母、数字、下划线和短横线原样保留；其他 UTF-8 字节使用 `%HH` 编码，避免 `/`、`\\`、控制字符、点路径和平台保留字符影响目录边界。
- 数据库字段最大为 255 字符。安全编码过长时保留可读前缀并追加完整值的 SHA-256，确保最终单个文件名低于常见文件系统 255 字节限制，并避免截断碰撞。
- 日志始终位于 `filepath.Join(stateDir, "logs", fileName)`；任何输入都不能改变父目录。

统一认证号会出现在受控服务器文件名中，但不新增到日志正文、HTTP 响应或前端状态。对外收集、截图或传输日志时仍需按安全规范脱敏文件名和正文。

## 兼容性

| 组合 | 行为 |
| --- | --- |
| 新 Java + 新 manager | 使用 `{统一认证号}-{启动时间}-{端口}.log`。 |
| 新 Java + 旧 manager | 旧 manager 忽略未知 JSON 字段，继续使用 `{port}.log`。 |
| 旧 Java + 新 manager | 优先从稳定 `sessionPath` 恢复统一认证号；不能恢复时使用 `{port}.log`。 |
| 新 manager + 旧 state restart | 优先从旧 state 的稳定 `sessionPath` 恢复；不能恢复时使用 `{port}.log`。 |
| 本地 CLI 未携带用户身份 | 保持 `{port}.log`，不改变现有 CLI 契约。 |

旧 `{port}.log` 只保留，不批量改名。批量迁移无法可靠判断一个已复用端口的历史日志分别属于哪些用户，因此禁止猜测归属。

## 错误处理

- 统一认证号与 session 路径身份不一致：manager 返回 `FAILED`，不启动子进程，也不创建错误归属的日志文件。
- 日志目录或文件无法创建：沿用现有启动失败语义，返回 manager 命令失败；不得回退到 console 或其它目录。
- 旧消息缺少统一认证号：走兼容恢复或端口日志，不视为错误。
- 安全化后的文件名生成失败：返回安全错误摘要，不在 manager 日志中输出原始异常认证号。

不新增错误码，Java 继续把 manager 启动失败转换为现有统一 `OPENCODE_BAD_GATEWAY` 或既有公共启动错误。

## 测试设计

### Go 单元测试

- 显式统一认证号生成精确的 `{id}-{UTC纳秒时间}-{port}.log`。
- 同一个用户连续两次启动得到不同文件；同一端口不会继续写旧文件。
- restart 保留统一认证号并生成新的启动时间文件。
- 旧消息/旧 state 可从 `users/{id}` session 路径恢复；普通 CLI session 路径回退 `{port}.log`。
- 显式身份与 session 路径不一致时拒绝启动。
- 空白、Unicode、路径分隔符、控制字符、超长认证号经过安全编码后不能逃逸 `logs` 目录且不会碰撞。
- state JSON 新字段可保存、读取；旧 JSON 缺字段仍能读取。
- supervisor 把协议字段传入 process manager，既有 start/health/stop/restart 行为不变。

### Java 单元测试

- 公共启动服务从用户仓储解析并规范化统一认证号，写入最终 `OpencodeProcessStartCommand`。
- Socket gateway 的 start 帧包含 `unifiedAuthId`，其它命令不要求该字段。
- `ManagerControlMessage` JSON 往返保留新增可空字段，旧构造入口继续兼容。
- 正常初始化链路缺少统一认证号时仍沿用当前失败语义；旧内部调用只有在稳定 session 路径可验证时才允许恢复，不能静默改用平台 `userId`。

### 回归验证

- `cd opencode-manager && go test ./...`
- 后端 opencode-runtime 定向测试。
- `cd backend && mvn -pl test-agent-opencode-runtime -am test`；若全量受任务外既有问题阻断，记录精确阻断并保留定向绿灯。
- `git diff --check`、冲突标记扫描和文档路径核对。

## 文档同步

实现时同步更新：

- `opencode-manager/README.md`：状态/日志路径、协议字段、滚动升级和查看命令。
- `backend/test-agent-opencode-runtime/README.md`：启动命令携带统一认证号及边界。
- `docs/deployment/backend.md`：通用部署与企业纯 Docker worker 的新日志路径示例。
- `docs/standards/security.md`：统一认证号仅作为受控本地日志文件名，外传时必须脱敏。

本次不修改 `docs/api/http-api.md` 或 `docs/api/event-stream.md`，因为没有 HTTP API、DTO、RunEvent 或 SSE 变化；不修改数据库/Flyway、generated SDK、前端或环境配置。

## 验收标准

1. 新 Java 与新 manager 启动用户进程后，stdout/stderr 只写入本次 `{统一认证号}-{UTC启动时间}-{端口}.log`。
2. 用户更换端口后仍能按统一认证号检索全部历史启动日志；同一端口被其他用户复用时不会混写旧文件。
3. restart 产生新文件，state 中的启动时间与文件名时间一致。
4. 新旧 Java/manager/state 的滚动组合不会因新增字段启动失败。
5. 文件名输入不能逃逸 manager `logs` 目录，超长和特殊字符不会造成碰撞或平台非法路径。
6. manager 生命周期日志、Java 日志和用户 OpenCode 日志正文不新增统一认证号输出。
7. 相关 Go/Java 测试通过，稳定 README、部署与安全文档和实现一致。
