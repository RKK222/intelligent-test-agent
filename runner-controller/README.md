# runner-controller

## 工程定位

独立分析节点上的 Python 3.12 Runner 控制器，是长程任务体系中唯一允许挂载 Docker Socket 的组件。workflow API 与 Worker 只通过带 HMAC、时间戳、请求体摘要和持久 nonce 的 `/runner-api/v1/**` 调用它。

## 责任

- 用一次性 checkout ticket 向 Java 兑换 Runner 公钥加密的 Git 凭据。
- 在 tmpfs 中解密个人 SSH 私钥，Git 完成后立即擦除。
- 冻结默认/基线与目标提交，计算 merge-base，完整拉取 Git LFS，并只检出已映射且当前用户有权访问的 submodule。
- 为每个任务创建一个固定镜像 digest 的非特权分析容器；多智能体共享只读源码，使用独立 HOME、cache 和输出目录。
- 生成确定性 diff manifest、解析局部重分析范围、执行 Codex/OpenCode、停止保留、恢复、取消和到期清理。
- 维护 Runner 本地状态；控制面清理循环把成功/失败同步为 PostgreSQL `EXPIRED/CLEANUP_FAILED`。

## 安全基线

- Docker Server 必须不低于 18.09；正式交付必须在真实 18.09 环境验收。
- 任务容器强制非 root、`cap-drop=ALL`、`no-new-privileges`、只读根文件系统、独立 tmpfs、CPU/内存/PID/nofile 限制且无 Docker Socket。
- 源码卷只读；输出卷可写；任务子网只允许访问固定 IPv4/CIDR 与端口的模型网关。
- Runner同时校验请求中的网关URL和公开模型ID格式；真实grant只经stdin交给容器内UID `10002`的回环relay，UID `10001`的Codex/OpenCode只获得生命周期内有效的本地token和`127.0.0.1`地址。relay固定上游IP/base path与模型端点并覆盖Authorization，真实grant不进入分析进程、挂载文件、环境或命令行。Codex shell使用环境白名单，OpenCode仓库命令固定经过`env -i`安全shell。
- `known_hosts` 必须是非符号链接的非空受控文件；Git 禁止交互、禁用全局/系统配置。
- Runner 通过目录 fd 与 `O_NOFOLLOW` 读写请求、本地relay token和结果；可信幂等缓存位于不挂载进容器的 `control/`，不能信任分析进程可写的 output 路径或符号链接。
- Runner 管理 API 只绑定管理网络/loopback，任务子网不能访问。
- 同任务多智能体在Runner内顺序执行；每次工具进程结束后关闭relay并重启同一任务容器、复核非特权约束，清除可能遗留的后台进程，同时保留只读源码卷和各智能体持久输出。
- 不能证明 Docker、网络标签、防火墙首条跳转规则或容器约束时，任务失败关闭；不存在特权降级。

## 测试

```bash
uv sync --frozen
PYTHONPATH=src uv run pytest tests
```

部署与真实 Docker 验收见 `docs/deployment/workflow-offline.md`。
