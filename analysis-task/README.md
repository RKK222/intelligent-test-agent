# analysis-task

固定 `linux/amd64` 分析任务镜像层。它基于预装 Codex/OpenCode 的 Debian 11 工具镜像，补充 Python 入口 `test-agent-analysis`，并在构建时锁定 glibc 2.31、工具可执行文件和非 root 用户 `10001:10003`。

运行时源码位于只读 `/workspace/repos/{repositoryAlias}`；每个智能体只写 `/workspace/output/{analyzerId}` 下的 HOME、cache、请求、结果和原始工具日志。输出根不可由分析 UID 列举或改名，未执行智能体的目录模式为 `000`，因此同任务智能体也不能读取彼此产物。平台模型 grant 只通过 `docker exec -i` 的 stdin 交给容器内独立 UID `10002` 的回环 relay，不进入 UID `10001` 的分析进程、请求文件、环境变量或命令行。分析入口只读取 `0640` 的一次性本地 relay token，读取后立即删除，Runner 在退出路径再次覆盖擦除；该 token 仅在 relay 存活期间对本容器 `127.0.0.1` 有效。

镜像不包含 Docker Socket，不允许特权运行或公网访问。Codex使用受控`config.toml`固定`responses`接口并将 shell 环境限制为 PATH/HOME；OpenCode使用唯一启用的`test-agent-workflow`兼容provider和显式`--model`，只开放仓库内读取/检索与受控 shell，并强制经`test-agent-safe-shell`的`env -i`白名单启动仓库命令。两者只连接本容器回环 relay；relay只接受固定模型端点和本地token，覆盖上游Authorization后才访问Runner网络白名单中的平台模型网关。公开模型ID由`TEST_AGENT_WORKFLOW_ANALYSIS_MODEL_NAME`指定且必须存在于模型网关目录。不能在真实 Docker 18.09 非特权模式稳定运行，或不能证明 UID `10001` 无法读取 UID `10002` 的grant内存、stdin及`/proc`状态时，均阻断交付。

同任务的复核智能体由Runner顺序调用；每次调用结束后关闭relay并重启同一容器，清除工具可能留下的后台进程，再复核容器运行状态、冻结镜像、精确挂载、受限网络、资源上限和全部非特权约束。源码卷和各智能体输出卷保持不变，因此不会改变冻结提交或丢失会话制品。

镜像内固定携带 `test-agent-clean-output`。分析入口无论成功或失败都会用它恢复当前智能体创建的私有目录权限；主动取消和到期清理则在容器已停止后由 Runner 以 UID `10001:10003` 对整个输出根执行。助手只接受固定输出根或注册智能体的直接子目录、不跟随符号链接，并在遇到未知 owner 或遍历错误时失败，供 Runner 将清理收敛为 `CLEANUP_FAILED`。

测试：

```bash
python -m pytest tests
```
