# workflow-chat

独立长程任务对话界面。页面按路由异步加载 TDesign Chat 样式，直接通过 `workflow-api-client` 访问 Python HTTP/AG-UI SSE，不复用 OpenCode 或 LobeHub 对话状态。

切换会话或 `SUPER_ADMIN` 查询的 owner 前必须先关闭旧 SSE，避免迟到事件跨 owner 污染当前视图。

局部重分析进入 `SCOPE_DISAMBIGUATION` 后，每个待消歧项的候选路径可直接选择；候选为空或都不准确时，该项必须保留仓库、范围类型和范围值输入卡。提交单项纠正时要同时保留已解析项及其余待补充项，再恢复原 run，不能让 `WAITING_INPUT` 成为无法继续或静默丢范围的状态。

SSE 状态投影优先于消息 POST 回包。若下一轮 `workflow.input_required` 或运行终态在 POST 完成前到达，组件保留较新的投影；只有没有更新投影事件时才用 POST 结果回填。`RUN_STARTED/RUN_FINISHED/RUN_ERROR` 同步清理旧输入卡，终态后不能再次提交已失效的恢复请求。范围卡使用选择器身份作为渲染键，避免连续消歧时复用上一项的本地输入。

测试：`corepack pnpm vitest run packages/workflow-chat/tests`。
