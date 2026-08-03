# analysis-task

固定 `linux/amd64` 分析任务镜像层。它基于已包含 Python、Codex/OpenCode 的 Debian 11 工具镜像，补充 Python 入口 `test-agent-analysis`，并在构建时锁定 glibc 2.31、工具可执行文件和非 root 用户 `10001:10003`；构建过程不执行包管理器下载。

运行时源码位于只读 `/workspace/repos/{repositoryAlias}`；每个智能体只写 `/workspace/output/{analyzerId}` 下的 HOME、cache、请求、结果和原始工具日志。输出根不可由分析 UID 列举或改名，未执行智能体的目录模式为 `000`，因此同任务智能体也不能读取彼此产物。平台模型 grant 只通过 `docker exec -i` 的 stdin 交给容器内独立 UID `10002` 的回环 relay，不进入 UID `10001` 的分析进程、请求文件、环境变量或命令行。分析入口只读取 `0640` 的一次性本地 relay token，读取后立即删除，Runner 在退出路径再次覆盖擦除；该 token 仅在 relay 存活期间对本容器 `127.0.0.1` 有效。

镜像不包含 Docker Socket，不允许特权运行或公网访问。Codex使用受控`config.toml`固定`responses`接口并将 shell 环境限制为 PATH/HOME；OpenCode使用唯一启用的`test-agent-workflow`兼容provider和显式`--model`，只开放仓库内读取/检索与受控 shell，并强制经`test-agent-safe-shell`的`env -i`白名单启动仓库命令。两者只连接本容器回环 relay；relay只接受固定模型端点和本地token，覆盖上游Authorization后才访问Runner网络白名单中的平台模型网关。公开模型ID由`TEST_AGENT_WORKFLOW_ANALYSIS_MODEL_NAME`指定且必须存在于模型网关目录。不能在真实 Docker 18.09 非特权模式稳定运行，或不能证明 UID `10001` 无法读取 UID `10002` 的grant内存、stdin及`/proc`状态时，均阻断交付。

分析入口在调用模型前会对每个冻结坐标只读执行Git，使用显式`--git-dir/--work-tree`读取不同隔离UID所拥有的仓库，不写入`safe.directory`或全局Git配置；按整次任务的有界预算注入diff stat、变更文件和diff片段，Runner内部绝对路径不会进入提示词。提示词仍要求智能体调用仓库读取工具，对每个冻结坐标执行`git diff --stat mergeBase..targetHead`与`git diff mergeBase..targetHead`，再读取相关实现、调用方和测试。Codex的本地relay会把尚无`function_call_output`的首轮Responses请求收紧为`tool_choice=required`；工具结果回传后不再改写选择策略，避免模型只口头承诺读仓库或陷入无限工具循环。运行时输出Schema进一步要求每条`codeEvidence`使用精确的`repositoryAlias/path`字段，其中仓库别名来自冻结坐标，路径不得翻译或缩写。输出后，入口还会拒绝不存在、越界或不属于冻结仓库的证据；非空差异不得返回空证据。未取得真实证据时必须失败或在`uncertainties`中说明，禁止用示例文件、推测符号或虚构行号补齐结构化输出。

同任务的复核智能体由Runner顺序调用；每次调用结束后关闭relay并重启同一容器，清除工具可能留下的后台进程，再复核容器运行状态、冻结镜像、精确挂载、受限网络、资源上限和全部非特权约束。源码卷和各智能体输出卷保持不变，因此不会改变冻结提交或丢失会话制品。

镜像内固定携带 `test-agent-clean-output`。分析入口无论成功或失败都会用它恢复当前智能体创建的私有目录权限；主动取消和到期清理则在容器已停止后由 Runner 以 UID `10001:10003` 对整个输出根执行。助手只接受固定输出根或注册智能体的直接子目录、不跟随符号链接，并在遇到未知 owner 或遍历错误时失败，供 Runner 将清理收敛为 `CLEANUP_FAILED`。

测试：

```bash
python -m pytest tests
```
