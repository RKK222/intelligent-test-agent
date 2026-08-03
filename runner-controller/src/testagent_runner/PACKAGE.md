# testagent_runner 包边界

- `api.py` / `security.py`：Worker 请求 HMAC、SQLite nonce 防重放和统一安全错误。
- `service.py`：每任务工作区状态机；prepare、resume、retain、cancel、cleanup 都按精确 task/run 加锁。
- `git_workspace.py`：冻结检出、LFS、授权 submodule、diff manifest；不把未授权 URL 写入结果。
- `docker_runtime.py`：Docker 18.09 兼容的非特权参数和受限网络复核；镜像不固定`DOCKER_API_VERSION`，由CLI与新旧Engine自动协商。
- `model_relay.py`：以容器内独立 UID 接收真实模型 grant，向分析进程只暴露回环地址和一次性本地 token。
- `analyzer_executor.py`：独立 HOME/cache/output、本地 relay token 临时文件和结构化结果上限；平台 grant 不进入分析进程。
- `tickets.py` / `credentials.py`：Runner 兑换票据与 tmpfs 私钥；`credentials.py`负责`TAEC1`混合加密信封和滚动升级期旧短RSA信封的解封。
- `scope_resolver.py`：程序、模块、目录、文件和符号的唯一匹配/消歧。

本包不得连接 workflow PostgreSQL、读取平台 Redis Token、持有平台用户身份或修改源码。
