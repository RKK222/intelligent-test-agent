# 对话面板回退到 976a798211 行为基线设计

## 目标

以 `976a7982115efd90251c9f7210ad8e8289dc903d` 为对话行为基线，撤销之后专门针对对话活动入口、专注阅读、Timeline 外观、时间戳和最终文本标记的提交，恢复该基线的 OpenCode 原生消息投影、ask/permission 交互和运行状态展示。

## 回退边界

按逆序反向还原以下提交：

- `89fe791e`：撤销专注阅读保留全部事件、最终输出容器和复制覆盖。
- `b0723df4`：撤销最终文本 part 标记链路。
- `12fa3b16`：撤销对话时间戳重写。
- `9cd1af7c`：撤销 Timeline 细节样式与智能体命名改造。
- `a26f354b`：撤销活动摘要入口、活动浮层和统一弹框视觉覆盖。
- `32607785`：删除已经失效的“对话专注模式”设计说明。

不回退进程状态卡、宠物旁路问答、文件树、运行后端、OpenCode client/event mapper、数据库或其他模块改动。发生冲突时以保留无关功能、恢复上述对话提交前内容为原则逐块解决，禁止整仓 reset。目标提交对 `.agents/session-log.md` 的历史记录不做反向删除；保留原记录并新增本次回退记录。

## 行为要求

1. 历史会话从 session-tree 恢复出的 `question.asked`、`permission.asked` 必须显示可操作区域，回复后继续调用原有 reply/reject API。
2. 新会话继续使用同一套 `OpencodeTimeline`、RunEvent reducer 和 OpenCode 原生 message part；text、reasoning、tool、file、task/subagent、retry、diff、todo、question、permission 不得被宿主模式过滤。
3. Run 状态以平台 Run 与原生 session status 的既有链路实时更新；收到成功、失败或取消终态后不得继续显示思考中、进行中或运行计时。
4. 移除“专注阅读”按钮和只读活动浮层，不新增第二套消息或状态聚合。

## 验证

- 先运行基线相关的 reducer、Timeline、FigmaChatPanel、workbench-utils 和 Playwright 工作台测试；对 text、reasoning、tool、file、task/subagent、retry、diff、todo 分别断言仍存在且原折叠/打开交互可用。
- 运行进程状态卡、宠物旁路问答和文件树定向回归，证明冲突解决没有误伤相邻存量功能。
- 使用 `.env.test` / `test` profile 重启真实服务。
- 真实新建会话，要求 OpenCode 调用 question 工具；确认弹出问题、提交答案后工具完成、模型继续输出并最终结束。
- 真实触发 permission；确认一次允许/始终允许/拒绝入口至少完成一条实际回复路径。
- 构造并切换到仍有 pending ask 与 permission 的历史会话，确认控件仍可点击并能继续。
- 分别验证成功、失败、取消三种终态；检查 Run、Timeline 过程行、计时、输入区和历史列表状态均实时收敛，完成会话不再显示进行中。

## 兼容性

本次不新增或修改 API、RunEvent wire name、数据库字段、Flyway migration、generated SDK 或安全策略。回退后继续复用 Huangzhenren 基线中的事件、回复和终态判定链路。
