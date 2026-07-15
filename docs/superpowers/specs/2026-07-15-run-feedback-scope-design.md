# Run 整体回复评价设计

## 背景

原反馈入口依赖平台 assistant `messageId`。一次主智能体回答可能由多条或零条 assistant part 组成，导致成功 Run 没有稳定可评价消息时按钮缺失，也让用户误以为评价对象是一条消息。

## 决策

- 反馈事实以 `(userId, runId)` 唯一定位，评价对象是根用户消息触发的完整主 Run。
- 只有 `SUCCEEDED`（前端兼容 `COMPLETED`）主对话 Run 展示与接受反馈；失败、取消、运行中、旁路问答和子 Agent 不提供入口。
- 每轮工作状态携带自己的 `runId/runStatus`。成功后入口位于该轮最后输出之后；没有 assistant part 时紧跟用户消息。新消息不会隐藏历史成功 Run 的入口。
- 历史恢复按最多 100 个 Run 批量读取真实状态和当前用户反馈。读取失败不隐藏入口，只不回显已选状态。
- 旧消息反馈 API 保留：能关联 Run 的请求转入新逻辑，无法关联的历史记录继续按消息查询。

## 数据与安全

- `ai_message_feedbacks.message_id` 变为可空历史来源字段；新增 `(user_id, run_id)` 唯一约束和 `run_id/message_id` 至少一个非空约束。
- 可关联历史记录回填 Run；重复记录保留最后更新的一条。新记录只写 Run ID。
- 后端校验请求用户是 Run 触发人或 Session 创建人，并限制成功主对话 Run；反馈不包含 prompt 或 assistant 原文，不产生 RunEvent。

## 兼容性

不改变 RunEvent、opencode/generated SDK 或环境配置。旧消息接口和旧数据继续可读；运营反馈明细中的 `messageId` 改为可选历史来源。
