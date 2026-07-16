-- 为数据库表和字段添加中文注释
-- 核心表：workspaces
comment on table workspaces is '平台工作区表，包含业务ID、名称、根路径、服务器归属、状态等信息';
comment on column workspaces.id is '数据库自增主键';
comment on column workspaces.workspace_id is '工作区业务ID，格式如：wks_xxx';
comment on column workspaces.name is '工作区名称';
comment on column workspaces.root_path is '工作区根路径，如：/data/workspace/my-project';
comment on column workspaces.status is '工作区状态：ACTIVE/ARCHIVED';
comment on column workspaces.trace_id is '创建工作区的链路traceId';
comment on column workspaces.created_at is '创建时间';
comment on column workspaces.updated_at is '更新时间';
comment on column workspaces.linux_server_id is '工作空间所在Linux服务器ID，与opencode进程管理中的linux_servers.linux_server_id一致';

-- 核心表：sessions
comment on table sessions is '智能体会话表，关联workspace，包含标题、状态、来源等信息';
comment on column sessions.id is '数据库自增主键';
comment on column sessions.session_id is '会话业务ID，格式如：ses_xxx';
comment on column sessions.workspace_id is '关联工作区ID';
comment on column sessions.title is '会话标题';
comment on column sessions.status is '会话状态：ACTIVE/ARCHIVED';
comment on column sessions.pinned is '是否置顶';
comment on column sessions.trace_id is '创建会话的链路traceId';
comment on column sessions.opencode_session_id is '远端opencode会话ID，首次Run成功后写入';
comment on column sessions.opencode_execution_node_id is '远端会话所在执行节点ID';
comment on column sessions.source_type is '会话来源类型：MANUAL/SCHEDULED_TASK，默认MANUAL';
comment on column sessions.source_ref_id is '来源关联ID，如定时任务ID';
comment on column sessions.created_by_user_id is '创建会话的用户ID';
comment on column sessions.created_at is '创建时间';
comment on column sessions.updated_at is '更新时间';

-- 核心表：runs
comment on table runs is '运行记录表，关联session/workspace，记录Run状态、token消耗等信息';
comment on column runs.id is '数据库自增主键';
comment on column runs.run_id is '运行记录业务ID，格式如：run_xxx';
comment on column runs.session_id is '关联会话ID';
comment on column runs.workspace_id is '关联工作区ID';
comment on column runs.status is '运行状态：PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED';
comment on column runs.trace_id is '创建运行的链路traceId';
comment on column runs.tokens_input is '输入token消耗';
comment on column runs.tokens_output is '输出token消耗';
comment on column runs.tokens_reasoning is '推理token消耗';
comment on column runs.tokens_cache_read is '缓存读取token消耗';
comment on column runs.tokens_cache_write is '缓存写入token消耗';
comment on column runs.cost_usd is '本次运行成本美元快照';
comment on column runs.source_type is '运行来源类型：MANUAL/SCHEDULED_TASK，默认MANUAL';
comment on column runs.source_ref_id is '来源关联ID';
comment on column runs.triggered_by_user_id is '触发运行的用户ID';
comment on column runs.created_at is '创建时间';
comment on column runs.updated_at is '更新时间';

-- 核心表：run_events
comment on table run_events is 'RunEvent事件流表，append-only，按(run_id, seq)唯一并支持增量回放';
comment on column run_events.id is '数据库自增主键';
comment on column run_events.event_id is '事件业务ID，格式如：evt_xxx';
comment on column run_events.run_id is '关联运行记录ID';
comment on column run_events.seq is '事件序号，同一run内递增';
comment on column run_events.type is '事件类型，如：MESSAGE/TOOL_USE/ERROR';
comment on column run_events.trace_id is '事件链路traceId';
comment on column run_events.occurred_at is '事件发生时间';
comment on column run_events.payload_json is '事件负载JSON文本';

-- 核心表：execution_nodes
comment on table execution_nodes is 'opencode执行节点表，包含baseUrl、健康状态、运行容量、权重、心跳和能力标签';
comment on column execution_nodes.id is '数据库自增主键';
comment on column execution_nodes.execution_node_id is '执行节点业务ID';
comment on column execution_nodes.base_url is '节点基础URL，如：http://127.0.0.1:4096';
comment on column execution_nodes.status is '节点状态：READY/UNAVAILABLE';
comment on column execution_nodes.running_runs is '当前运行中的Run数量';
comment on column execution_nodes.max_runs is '最大Run容量';
comment on column execution_nodes.weight is '路由权重';
comment on column execution_nodes.last_heartbeat_at is '最近心跳时间';
comment on column execution_nodes.capabilities_json is '节点能力标签JSON，如：{"tools": ["git", "docker"]}';
comment on column execution_nodes.trace_id is '创建节点的链路traceId';
comment on column execution_nodes.created_at is '创建时间';
comment on column execution_nodes.updated_at is '更新时间';

-- 核心表：routing_decisions
comment on table routing_decisions is 'Run到ExecutionNode的路由决策审计记录表';
comment on column routing_decisions.id is '数据库自增主键';
comment on column routing_decisions.run_id is '关联运行记录ID';
comment on column routing_decisions.execution_node_id is '路由目标执行节点ID';
comment on column routing_decisions.reason is '路由原因，如：LEAST_LOAD/RANDOM';
comment on column routing_decisions.decided_at is '决策时间';
comment on column routing_decisions.trace_id is '决策链路traceId';

-- 会话消息表：session_messages
comment on table session_messages is '会话消息表，记录用户与助手的对话内容';
comment on column session_messages.id is '数据库自增主键';
comment on column session_messages.message_id is '消息业务ID，格式如：msg_xxx';
comment on column session_messages.session_id is '关联会话ID';
comment on column session_messages.role is '消息角色：USER/ASSISTANT/SYSTEM';
comment on column session_messages.content is 'UTF-8文本内容，如：帮我分析这段代码';
comment on column session_messages.trace_id is '创建消息的链路traceId';
comment on column session_messages.created_at is '创建时间';
comment on column session_messages.run_id is '归属的Run ID';
comment on column session_messages.agent_id is '归一化后的agent标志，如：opencode';
comment on column session_messages.remote_message_id is '远端agent消息ID';
comment on column session_messages.parts_json is '远端消息parts的JSON文本快照';
comment on column session_messages.tokens_input is '输入token消耗';
comment on column session_messages.tokens_output is '输出token消耗';
comment on column session_messages.tokens_reasoning is '推理token消耗';
comment on column session_messages.tokens_cache_read is '缓存读取token消耗';
comment on column session_messages.tokens_cache_write is '缓存写入token消耗';
comment on column session_messages.cost_usd is '成本美元快照';
comment on column session_messages.updated_at is '快照更新时间';
comment on column session_messages.source_type is '消息来源类型：MANUAL/SCHEDULED_TASK，默认MANUAL';
comment on column session_messages.source_ref_id is '来源关联ID';
comment on column session_messages.sender_user_id is '发送消息的用户ID';

-- Agent会话绑定表：agent_session_bindings
comment on table agent_session_bindings is '通用agent远端会话绑定表，后续新增agent不再扩展sessions专有字段';
comment on column agent_session_bindings.id is '数据库自增主键';
comment on column agent_session_bindings.session_id is '平台session业务ID';
comment on column agent_session_bindings.agent_id is '规范化后的agent标志，如：opencode';
comment on column agent_session_bindings.remote_session_id is '对应agent的远端session ID';
comment on column agent_session_bindings.execution_node_id is '远端session所在执行节点ID';
comment on column agent_session_bindings.created_at is '绑定创建时间';
comment on column agent_session_bindings.updated_at is '绑定更新时间';
comment on column agent_session_bindings.trace_id is '创建或更新绑定的traceId';

-- 用户表：users
comment on table users is '平台用户表，包含统一认证号、用户名、BCrypt密码哈希、所属机构/研发部/部门';
comment on column users.id is '数据库自增主键';
comment on column users.user_id is '用户业务ID，格式如：usr_xxx';
comment on column users.unified_auth_id is '统一认证号，唯一不可空，如：DEV_888888888';
comment on column users.username is '用户名，唯一，如：888888888';
comment on column users.password_hash is 'BCrypt密码哈希值';
comment on column users.organization is '所属机构，可空，如：测试机构';
comment on column users.rd_department is '所属研发部，可空，如：测试研发部';
comment on column users.department is '所属部门，可空，如：测试部门';
comment on column users.status is '用户状态：ACTIVE/INACTIVE，默认ACTIVE';
comment on column users.created_at is '创建时间';
comment on column users.updated_at is '更新时间';

-- 用户登录日志表：user_login_logs
comment on table user_login_logs is '用户登录日志表，记录登录时间、IP、User-Agent和结果';
comment on column user_login_logs.id is '数据库自增主键';
comment on column user_login_logs.log_id is '日志业务ID，格式如：log_xxx';
comment on column user_login_logs.user_id is '用户业务ID';
comment on column user_login_logs.login_at is '登录时间';
comment on column user_login_logs.ip_address is '客户端IP地址，如：192.168.1.100';
comment on column user_login_logs.user_agent is '浏览器User-Agent，如：Mozilla/5.0 (Windows NT 10.0; Win64; x64)';
comment on column user_login_logs.login_result is '登录结果：SUCCESS/FAILURE';

-- 通用字典表：dictionaries
comment on table dictionaries is '通用字典表，存储应用角色等字典数据';
comment on column dictionaries.id is '数据库自增主键';
comment on column dictionaries.dict_id is '字典业务ID，格式如：dict_xxx';
comment on column dictionaries.dict_name is '字典名称，如：应用角色';
comment on column dictionaries.dict_key is '字典键，如：ROLE';
comment on column dictionaries.dict_value is '字典值，如：SUPER_ADMIN';
comment on column dictionaries.dict_label is '显示标签，如：超级管理员';
comment on column dictionaries.sort_order is '排序序号';
comment on column dictionaries.created_at is '创建时间';
comment on column dictionaries.updated_at is '更新时间';

-- 用户角色对照表：user_roles
comment on table user_roles is '用户角色对照关系表，关联用户和角色字典';
comment on column user_roles.id is '数据库自增主键';
comment on column user_roles.user_id is '用户业务ID';
comment on column user_roles.dict_id is '字典业务ID';
comment on column user_roles.created_at is '创建时间';

-- 应用定义表：applications
comment on table applications is '应用定义表，由外部系统同步，本期平台只读消费';
comment on column applications.id is '数据库自增主键';
comment on column applications.app_id is '应用业务ID，格式如：app_xxx';
comment on column applications.app_name is '应用名称，如：F-COSS';
comment on column applications.enabled is '是否启用';
comment on column applications.created_at is '创建时间';
comment on column applications.updated_at is '更新时间';

-- 应用成员关系表：application_members
comment on table application_members is '应用成员关系表，删除使用deleted_at逻辑删除';
comment on column application_members.id is '数据库自增主键';
comment on column application_members.app_id is '关联应用ID';
comment on column application_members.user_id is '关联用户ID';
comment on column application_members.created_at is '创建时间';
comment on column application_members.updated_at is '更新时间';
comment on column application_members.deleted_at is '删除时间，为空表示有效关系';

-- 代码库配置表：code_repositories
comment on table code_repositories is '代码库配置表，Git URL创建后不允许编辑，不提供删除能力';
comment on column code_repositories.id is '数据库自增主键';
comment on column code_repositories.repository_id is '代码库业务ID，格式如：repo_xxx';
comment on column code_repositories.git_url is 'Git仓库URL，全局唯一，如：https://gitee.com/my-org/my-repo.git';
comment on column code_repositories.name is '代码库名称';
comment on column code_repositories.standard is '是否标准库';
comment on column code_repositories.created_at is '创建时间';
comment on column code_repositories.updated_at is '更新时间';

-- 应用与代码库关联表：application_repository_links
comment on table application_repository_links is '应用与代码库多对多关联表';
comment on column application_repository_links.id is '数据库自增主键';
comment on column application_repository_links.app_id is '关联应用ID';
comment on column application_repository_links.repository_id is '关联代码库ID';
comment on column application_repository_links.created_at is '创建时间';

-- 应用级工作空间配置表：application_workspaces
comment on table application_workspaces is '应用级工作空间配置表，与运行态workspaces表相互独立';
comment on column application_workspaces.id is '数据库自增主键';
comment on column application_workspaces.workspace_id is '工作空间模板业务ID，格式如：aws_xxx';
comment on column application_workspaces.app_id is '关联应用ID';
comment on column application_workspaces.repository_id is '关联代码库ID';
comment on column application_workspaces.branch is 'Git分支名，如：main';
comment on column application_workspaces.directory_path is '工作目录路径，如：src/main';
comment on column application_workspaces.workspace_name is '工作空间模板名称';
comment on column application_workspaces.created_at is '创建时间';
comment on column application_workspaces.updated_at is '更新时间';

-- 用户SSH key表：user_ssh_keys
comment on table user_ssh_keys is '用户个人SSH私钥配置表，一个用户只能保存一把key，私钥密文存储';
comment on column user_ssh_keys.id is '数据库自增主键';
comment on column user_ssh_keys.ssh_key_id is 'SSH key业务ID，格式如：ssh_xxx';
comment on column user_ssh_keys.user_id is '关联用户ID';
comment on column user_ssh_keys.name is 'SSH key名称';
comment on column user_ssh_keys.fingerprint is 'SSH key指纹';
comment on column user_ssh_keys.encrypted_private_key is 'AES-GCM加密后的私钥密文';
comment on column user_ssh_keys.encryption_nonce is '加密nonce';
comment on column user_ssh_keys.created_at is '创建时间';

-- 应用版本工作区表：application_workspace_versions
comment on table application_workspace_versions is '应用工作空间模板的版本实例表';
comment on column application_workspace_versions.id is '数据库自增主键';
comment on column application_workspace_versions.version_id is '版本业务ID，格式如：ver_xxx';
comment on column application_workspace_versions.application_workspace_id is '关联应用工作空间模板ID';
comment on column application_workspace_versions.app_id is '关联应用ID';
comment on column application_workspace_versions.repository_id is '关联代码库ID';
comment on column application_workspace_versions.version is '版本号，格式如：20260620';
comment on column application_workspace_versions.branch is '实际Git分支';
comment on column application_workspace_versions.repo_root_path is '物理仓库目录';
comment on column application_workspace_versions.workspace_root_path is 'opencode工作目录';
comment on column application_workspace_versions.runtime_workspace_id is '关联运行态Workspace ID';
comment on column application_workspace_versions.created_by_user_id is '创建人用户ID';
comment on column application_workspace_versions.status is '版本状态：ACTIVE/ARCHIVED';
comment on column application_workspace_versions.created_at is '创建时间';
comment on column application_workspace_versions.updated_at is '更新时间';

-- 个人工作区表：personal_workspaces
comment on table personal_workspaces is '用户基于应用版本工作区派生的git worktree表';
comment on column personal_workspaces.id is '数据库自增主键';
comment on column personal_workspaces.personal_workspace_id is '个人工作区业务ID，格式如：pw_xxx';
comment on column personal_workspaces.app_workspace_version_id is '关联应用版本工作区ID';
comment on column personal_workspaces.app_id is '关联应用ID';
comment on column personal_workspaces.application_workspace_id is '关联应用工作空间模板ID';
comment on column personal_workspaces.user_id is '所属用户ID';
comment on column personal_workspaces.workspace_name is '展示名称';
comment on column personal_workspaces.branch is '私有分支名';
comment on column personal_workspaces.repo_root_path is '物理目录';
comment on column personal_workspaces.workspace_root_path is 'opencode工作目录';
comment on column personal_workspaces.runtime_workspace_id is '关联运行态Workspace ID';
comment on column personal_workspaces.base_commit is '基础commit hash';
comment on column personal_workspaces.status is '状态：ACTIVE/ARCHIVED';
comment on column personal_workspaces.created_at is '创建时间';
comment on column personal_workspaces.updated_at is '更新时间';

-- 用户全局工作区偏好表：user_global_workspace_preferences
comment on table user_global_workspace_preferences is '用户全局最近使用的托管运行态Workspace表';
comment on column user_global_workspace_preferences.id is '数据库自增主键';
comment on column user_global_workspace_preferences.user_id is '用户ID';
comment on column user_global_workspace_preferences.workspace_id is '最近使用的运行态Workspace ID';
comment on column user_global_workspace_preferences.updated_at is '更新时间';

-- 用户应用工作区偏好表：user_application_workspace_preferences
comment on table user_application_workspace_preferences is '用户在某应用下最近使用的托管运行态Workspace表';
comment on column user_application_workspace_preferences.id is '数据库自增主键';
comment on column user_application_workspace_preferences.user_id is '用户ID';
comment on column user_application_workspace_preferences.app_id is '应用ID';
comment on column user_application_workspace_preferences.workspace_id is '最近使用的运行态Workspace ID';
comment on column user_application_workspace_preferences.updated_at is '更新时间';

-- 工作区同步记录表：workspace_sync_records
comment on table workspace_sync_records is '个人工作区与应用版本工作区同步审计表';
comment on column workspace_sync_records.id is '数据库自增主键';
comment on column workspace_sync_records.sync_record_id is '同步记录业务ID，格式如：syn_xxx';
comment on column workspace_sync_records.user_id is '操作用户ID';
comment on column workspace_sync_records.source_workspace_id is '源工作区ID';
comment on column workspace_sync_records.target_workspace_id is '目标工作区ID';
comment on column workspace_sync_records.direction is '同步方向：PUSH/PULL';
comment on column workspace_sync_records.files_json is '同步文件列表JSON';
comment on column workspace_sync_records.force is '是否强推';
comment on column workspace_sync_records.status is '同步状态：PENDING/SUCCEEDED/FAILED';
comment on column workspace_sync_records.trace_id is '链路traceId';
comment on column workspace_sync_records.created_at is '创建时间';

-- 用户工作区分支偏好表：user_workspace_branch_preferences
comment on table user_workspace_branch_preferences is '用户工作区分支偏好表，记录用户在某个工作区下最近选择的VCS分支';
comment on column user_workspace_branch_preferences.id is '数据库自增主键';
comment on column user_workspace_branch_preferences.user_id is '用户ID';
comment on column user_workspace_branch_preferences.app_id is '应用ID';
comment on column user_workspace_branch_preferences.workspace_id is '工作区ID';
comment on column user_workspace_branch_preferences.branch is 'VCS分支名';
comment on column user_workspace_branch_preferences.updated_at is '更新时间';

-- AI模型配置表：ai_model_configs
comment on table ai_model_configs is 'AI模型配置表，用于企业内模型目录接口读取和默认模型控制';
comment on column ai_model_configs.id is '数据库自增主键';
comment on column ai_model_configs.provider_id is '模型所属provider，如：enterprise-openai';
comment on column ai_model_configs.model_id is '模型标识，如：DeepSeek-V4-Flash-W8A8';
comment on column ai_model_configs.name is '前端展示名称';
comment on column ai_model_configs.enabled is '是否在模型目录中展示';
comment on column ai_model_configs.default_model is '是否为默认模型';
comment on column ai_model_configs.input_modalities_json is '输入模态JSON，如：["text"]或["text","image"]';
comment on column ai_model_configs.context_limit is '上下文窗口限制';
comment on column ai_model_configs.output_limit is '输出token限制';
comment on column ai_model_configs.sort_order is '模型展示排序';
comment on column ai_model_configs.metadata_json is '模型来源等扩展元数据JSON';
comment on column ai_model_configs.created_at is '创建时间';
comment on column ai_model_configs.updated_at is '更新时间';

-- Linux服务器表：linux_servers
comment on table linux_servers is '后端Linux服务器节点表，记录状态、容量摘要和心跳';
comment on column linux_servers.id is '数据库自增主键';
comment on column linux_servers.linux_server_id is 'Linux服务器ID，当前使用IPv4地址，如：127.0.0.1';
comment on column linux_servers.name is '服务器名称';
comment on column linux_servers.status is '服务器状态：READY/UNAVAILABLE';
comment on column linux_servers.capacity_summary_json is '容量摘要JSON';
comment on column linux_servers.last_heartbeat_at is '最近心跳时间';
comment on column linux_servers.trace_id is '链路traceId';
comment on column linux_servers.created_at is '创建时间';
comment on column linux_servers.updated_at is '更新时间';

-- 后端Java进程表：backend_java_processes
comment on table backend_java_processes is '后端Java进程实例表，记录监听地址、所属Linux服务器和心跳';
comment on column backend_java_processes.id is '数据库自增主键';
comment on column backend_java_processes.backend_process_id is '后端进程业务ID，格式如：bjp_xxx';
comment on column backend_java_processes.linux_server_id is '所属Linux服务器ID';
comment on column backend_java_processes.listen_url is '进程监听地址';
comment on column backend_java_processes.status is '进程状态：RUNNING/STOPPED';
comment on column backend_java_processes.started_at is '启动时间';
comment on column backend_java_processes.last_heartbeat_at is '最近心跳时间';
comment on column backend_java_processes.trace_id is '链路traceId';
comment on column backend_java_processes.created_at is '创建时间';
comment on column backend_java_processes.updated_at is '更新时间';

-- opencode容器表：opencode_containers
comment on table opencode_containers is 'opencode容器表，记录端口池、容量、当前进程数和状态';
comment on column opencode_containers.id is '数据库自增主键';
comment on column opencode_containers.container_id is '容器业务ID，格式如：ctr_xxx';
comment on column opencode_containers.linux_server_id is '所属Linux服务器ID';
comment on column opencode_containers.container_name is '容器名称';
comment on column opencode_containers.port_start is '端口池起始端口，如：4096';
comment on column opencode_containers.port_end is '端口池结束端口，如：4196';
comment on column opencode_containers.max_processes is '最大进程数';
comment on column opencode_containers.current_processes is '当前进程数';
comment on column opencode_containers.status is '容器状态：READY/FULL/UNAVAILABLE';
comment on column opencode_containers.last_heartbeat_at is '最近心跳时间';
comment on column opencode_containers.trace_id is '链路traceId';
comment on column opencode_containers.created_at is '创建时间';
comment on column opencode_containers.updated_at is '更新时间';

-- opencode容器管理进程表：opencode_container_managers
comment on table opencode_container_managers is 'opencode容器管理进程表，每个容器最多一个manager';
comment on column opencode_container_managers.id is '数据库自增主键';
comment on column opencode_container_managers.manager_id is '管理进程业务ID，格式如：mgr_xxx';
comment on column opencode_container_managers.container_id is '关联容器ID';
comment on column opencode_container_managers.linux_server_id is '所属Linux服务器ID';
comment on column opencode_container_managers.protocol_version is '协议版本';
comment on column opencode_container_managers.connection_status is '连接状态：CONNECTED/DISCONNECTED';
comment on column opencode_container_managers.capabilities_json is '能力JSON';
comment on column opencode_container_managers.last_heartbeat_at is '最近心跳时间';
comment on column opencode_container_managers.trace_id is '链路traceId';
comment on column opencode_container_managers.created_at is '创建时间';
comment on column opencode_container_managers.updated_at is '更新时间';

-- opencode管理进程与后端连接表：opencode_manager_backend_connections
comment on table opencode_manager_backend_connections is '管理进程到后端Java进程的控制面连接状态表';
comment on column opencode_manager_backend_connections.id is '数据库自增主键';
comment on column opencode_manager_backend_connections.manager_id is '管理进程ID';
comment on column opencode_manager_backend_connections.backend_process_id is '后端进程ID';
comment on column opencode_manager_backend_connections.status is '连接状态：CONNECTED/DISCONNECTED';
comment on column opencode_manager_backend_connections.connected_at is '连接时间';
comment on column opencode_manager_backend_connections.last_heartbeat_at is '最近心跳时间';
comment on column opencode_manager_backend_connections.trace_id is '链路traceId';
comment on column opencode_manager_backend_connections.updated_at is '更新时间';

-- opencode用户进程表：opencode_server_processes
comment on table opencode_server_processes is '用户专属opencode server进程表，记录用户、端口、PID、base_url和健康状态';
comment on column opencode_server_processes.id is '数据库自增主键';
comment on column opencode_server_processes.process_id is '进程业务ID，格式如：ocp_xxx';
comment on column opencode_server_processes.user_id is '所属用户ID';
comment on column opencode_server_processes.linux_server_id is '所属Linux服务器ID';
comment on column opencode_server_processes.container_id is '所属容器ID';
comment on column opencode_server_processes.port is '主机直通端口';
comment on column opencode_server_processes.pid is '进程PID';
comment on column opencode_server_processes.base_url is '进程基础URL，如：http://127.0.0.1:4096';
comment on column opencode_server_processes.status is '进程状态：STARTING/RUNNING/STOPPING/STOPPED';
comment on column opencode_server_processes.session_path is 'session路径';
comment on column opencode_server_processes.config_path is 'config路径';
comment on column opencode_server_processes.started_at is '启动时间';
comment on column opencode_server_processes.last_health_check_at is '最近健康检查时间';
comment on column opencode_server_processes.health_message is '健康检查消息';
comment on column opencode_server_processes.trace_id is '链路traceId';
comment on column opencode_server_processes.created_at is '创建时间';
comment on column opencode_server_processes.updated_at is '更新时间';

-- 用户opencode进程绑定表：user_opencode_process_bindings
comment on table user_opencode_process_bindings is '用户到agent/opencode进程的当前绑定表';
comment on column user_opencode_process_bindings.id is '数据库自增主键';
comment on column user_opencode_process_bindings.user_id is '用户ID';
comment on column user_opencode_process_bindings.agent_id is 'agent标志，如：opencode';
comment on column user_opencode_process_bindings.process_id is 'opencode进程ID';
comment on column user_opencode_process_bindings.linux_server_id is '进程所在Linux服务器ID';
comment on column user_opencode_process_bindings.port is '进程端口';
comment on column user_opencode_process_bindings.status is '绑定状态：ACTIVE/INACTIVE';
comment on column user_opencode_process_bindings.trace_id is '链路traceId';
comment on column user_opencode_process_bindings.created_at is '创建时间';
comment on column user_opencode_process_bindings.updated_at is '更新时间';

-- 定时任务定义表：scheduled_tasks
comment on table scheduled_tasks is '定时任务定义表，保存任务key、名称、Cron、启停、锁TTL和注册状态';
comment on column scheduled_tasks.id is '数据库自增主键';
comment on column scheduled_tasks.task_key is '任务唯一标识键';
comment on column scheduled_tasks.name is '任务名称';
comment on column scheduled_tasks.cron_expression is 'Cron表达式，如：0 9 * * 1-5（工作日9点）';
comment on column scheduled_tasks.enabled is '是否启用';
comment on column scheduled_tasks.lock_ttl_seconds is 'Redis锁租约秒数';
comment on column scheduled_tasks.next_fire_at is '下次触发时间';
comment on column scheduled_tasks.registration_status is '注册状态：REGISTERED/UNREGISTERED';
comment on column scheduled_tasks.trace_id is '链路traceId';
comment on column scheduled_tasks.created_at is '创建时间';
comment on column scheduled_tasks.updated_at is '更新时间';

-- 定时任务计划表：scheduled_task_plans
comment on table scheduled_task_plans is '用户级Cron计划预留表';
comment on column scheduled_task_plans.id is '数据库自增主键';
comment on column scheduled_task_plans.plan_id is '计划业务ID，格式如：stp_xxx';
comment on column scheduled_task_plans.task_key is '关联任务key';
comment on column scheduled_task_plans.owner_user_id is '计划所有者用户ID';
comment on column scheduled_task_plans.cron_expression is 'Cron表达式';
comment on column scheduled_task_plans.payload_json is '计划负载JSON';
comment on column scheduled_task_plans.enabled is '是否启用';
comment on column scheduled_task_plans.next_fire_at is '下次触发时间';
comment on column scheduled_task_plans.trace_id is '链路traceId';
comment on column scheduled_task_plans.created_at is '创建时间';
comment on column scheduled_task_plans.updated_at is '更新时间';

-- 定时任务运行记录表：scheduled_task_runs
comment on table scheduled_task_runs is '定时任务运行记录表，统一记录Cron、管理员手动触发和用户计划触发的状态';
comment on column scheduled_task_runs.id is '数据库自增主键';
comment on column scheduled_task_runs.task_run_id is '运行记录业务ID，格式如：str_xxx';
comment on column scheduled_task_runs.task_key is '关联任务key';
comment on column scheduled_task_runs.plan_id is '关联计划ID';
comment on column scheduled_task_runs.trigger_type is '触发类型：CRON/MANUAL/USER_PLAN';
comment on column scheduled_task_runs.status is '运行状态：PENDING/RUNNING/STOPPING/SUCCEEDED/FAILED/SKIPPED/MANUALLY_STOPPED';
comment on column scheduled_task_runs.requested_by_user_id is '请求触发用户ID（手动触发时）';
comment on column scheduled_task_runs.scheduled_fire_at is '计划触发时间';
comment on column scheduled_task_runs.started_at is '实际开始时间';
comment on column scheduled_task_runs.ended_at is '结束时间';
comment on column scheduled_task_runs.owner_instance_id is '执行实例ID';
comment on column scheduled_task_runs.skip_reason is '跳过原因';
comment on column scheduled_task_runs.error_code is '错误码';
comment on column scheduled_task_runs.error_message is '错误消息';
comment on column scheduled_task_runs.result_json is '运行结果JSON';
comment on column scheduled_task_runs.trace_id is '链路traceId';
comment on column scheduled_task_runs.created_at is '创建时间';
comment on column scheduled_task_runs.updated_at is '更新时间';

-- 补充V20260625192100扩展字段的注释
comment on column scheduled_task_runs.stop_requested_at is '停止请求时间';
comment on column scheduled_task_runs.stop_requested_by_user_id is '请求停止的用户ID';
comment on column scheduled_task_runs.stop_reason is '停止原因';
-- migration checksum compatibility pad: fpbalE
