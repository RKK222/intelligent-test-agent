# OpenCode 可观测性验收记录

本文只记录可重复执行的 Markdown 验收结果，不生成 Word、证据卡或阅读路线。目标分支为 `release`，方案复用现有 Java、OpenCode worker、本地客户端、ClickHouse 和服务器持久化卷，不新增部署节点。服务端与本地客户端的 OpenCode 版本均固定为 `1.18.4`。

## 原运营页面指标清单

基线取插件化 Trace 引入前提交 `473d48dfc`，当前实现使用同一份 `AnalyticsQueryServiceTest.originalOperationsMetricsMatchTheFixedDatasetBaseline` 固定数据。测试覆盖页面实际调用的全部查询和全部 CSV 导出；先移除新增的 `coverageStartAt/completeThrough/source/rolloutCompleteness/cancelledCount` 向后兼容字段，再对旧字段规范化 JSON 计算 SHA-256。基线与当前摘要均为 `ff77d42df433913e460f2bc281084b50530cfb1dc8bc8f18e2a2e3482b06e012`。

共同筛选维度为 `startTime/endTime/granularity/organization/rdDepartment/department/user`。页面默认最近 30 天，趋势支持 hour/day/week/month，小时热力最多 90 天；所有时间桶按 `Asia/Shanghai` 自然日口径生成。下表的“现数据源”中，`活动事实`表示现有业务事件进入 `analytics_activity_facts` 后的 rollup，`插件能力事实`表示 `analytics_capability_facts.source=OPENCODE_PLUGIN`。Trace 正文归档不参与任何运营查询。

| 页面或接口指标 | 原始字段 | 计算公式或原值 | 额外筛选或时间口径 | 现数据源 | 固定集对比 |
| --- | --- | --- | --- | --- | --- |
| 筛选项-机构 | `organizations` | 最新用户维度中的机构去重 | 无 | 活动事实用户维度 | 一致 |
| 筛选项-研发部 | `rdDepartments` | 选定机构下研发部去重 | `organization` | 活动事实用户维度 | 一致 |
| 筛选项-部门 | `departments` | 选定机构、研发部下部门去重 | `organization/rdDepartment` | 活动事实用户维度 | 一致 |
| 数据生成时间 | `freshness.generatedAt` | 最近成功 rollup 水位更新时间 | 全局任务水位 | 活动事实 | 一致 |
| 数据状态 | `freshness.status/message` | FRESH/STALE/FAILED 与说明 | 五分钟陈旧阈值 | 活动事实 | 一致 |
| 数据覆盖区间 | `freshness.coverageStart/coverageEnd/attributionMode` | rollup 已覆盖区间及归属模式 | 全局任务水位 | 活动事实 | 一致 |
| 注册用户 | `registeredUsers` | 最新用户维度记录数 | 公共筛选 | 活动事实用户维度 | 一致 |
| 启用用户 | `enabledUsers` | 最新用户维度中启用记录数 | 公共筛选 | 活动事实用户维度 | 一致 |
| 登录用户 | `loginUsers` | `loginCount>0` 的唯一用户 | 公共筛选 | 活动事实 | 一致 |
| 活跃用户 | `activeUsers` | `userMessageCount>0` 的唯一用户 | 公共筛选 | 活动事实 | 一致 |
| 有效用户 | `validUsers` | `validInteractionCount>0` 的唯一用户 | 公共筛选 | 活动事实 | 一致 |
| 深度用户 | `deepUsers` | 至少 2 个自然日且累计至少 5 条用户消息 | 上海自然日 | 活动事实 | 一致 |
| 活跃率 | `activeRate` | `activeUsers/enabledUsers` | 公共筛选 | 活动事实 | 一致 |
| 登录转活跃率 | `loginToActiveRate` | `activeUsers/loginUsers` | 公共筛选 | 活动事实 | 一致 |
| 活跃转有效率 | `activeToValidRate` | `validUsers/activeUsers` | 公共筛选 | 活动事实 | 一致 |
| 有效转深度率 | `validToDeepRate` | `deepUsers/validUsers` | 公共筛选 | 活动事实 | 一致 |
| 会话数 | `sessionCount` | `sum(sessionCount)` | 公共筛选 | 活动事实 | 一致 |
| 活跃会话数 | `activeSessionCount` | `sum(activeSessionCount)` | 公共筛选 | 活动事实 | 一致 |
| 空会话数 | `emptySessionCount` | `sum(emptySessionCount)` | 公共筛选 | 活动事实 | 一致 |
| 连续会话数 | `continuousSessionCount` | `sum(continuousSessionCount)` | 公共筛选 | 活动事实 | 一致 |
| 用户消息 | `userMessageCount` | `sum(userMessageCount)` | 公共筛选 | 活动事实 | 一致 |
| Assistant 消息 | `assistantMessageCount` | `sum(assistantMessageCount)` | 公共筛选 | 活动事实 | 一致 |
| Run 数 | `runCount` | `sum(runCount)` | 公共筛选 | 活动事实；插件同步保留结构化元数据 | 一致 |
| 人均 Run | `runsPerUser` | `runCount/activeUsers` | 公共筛选 | 活动事实 | 一致 |
| 人均消息 | `messagesPerUser` | `userMessageCount/activeUsers` | 公共筛选 | 活动事实 | 一致 |
| 会话均消息 | `messagesPerSession` | `userMessageCount/activeSessionCount` | 公共筛选 | 活动事实 | 一致 |
| 连续对话率 | `continuousConversationRate` | `continuousSessionCount/activeSessionCount` | 公共筛选 | 活动事实 | 一致 |
| 有效交互数 | `validInteractionCount` | `sum(validInteractionCount)` | 公共筛选 | 活动事实 | 一致 |
| 持续用户 | `sustainedUsers` | 用户维度有效交互达到持续使用判据的唯一用户 | 公共筛选 | 活动事实 | 一致 |
| 成功 Run | `succeededRuns` | `sum(succeededRunCount)` | 公共筛选 | 活动事实；插件记录对应状态 | 一致 |
| 失败 Run | `failedRuns` | `sum(failedRunCount)` | 公共筛选 | 活动事实；插件记录对应状态 | 一致 |
| 取消 Run | `cancelledRuns` | `sum(cancelledRunCount)` | 公共筛选 | 活动事实；插件记录对应状态 | 一致 |
| 主动终止 | `activeTerminations` | `sum(activeTerminationCount)` | 公共筛选 | 活动事实 | 一致 |
| Run 成功率 | `successRate` | `succeededRuns/runCount` | 公共筛选 | 活动事实 | 一致 |
| Run 失败率 | `failureRate` | `failedRuns/runCount` | 公共筛选 | 活动事实 | 一致 |
| Run 取消率 | `cancellationRate` | `cancelledRuns/runCount` | 公共筛选 | 活动事实 | 一致 |
| 平均耗时 | `averageDurationMs` | `sum(durationMs)/count(durationMs>0)` | 公共筛选 | 活动事实；插件记录 LLM/Tool 耗时 | 一致 |
| p95 耗时 | `p95DurationMs` | `quantileExact(0.95)(durationMs>0)` | 公共筛选 | 活动事实 | 一致 |
| 满意反馈 | `positiveFeedbackCount` | `sum(positiveFeedbackCount)` | 公共筛选 | 原反馈链路 | 一致 |
| 不满意反馈 | `negativeFeedbackCount` | `sum(negativeFeedbackCount)` | 公共筛选 | 原反馈链路 | 一致 |
| 满意率 | `satisfactionRate` | `positive/(positive+negative)` | 公共筛选 | 原反馈链路 | 一致 |
| 反馈覆盖率 | `feedbackCoverageRate` | `(positive+negative)/assistantMessageCount` | 公共筛选 | 原反馈链路 | 一致 |
| Diff 生成数 | `diffProposedCount` | `sum(diffProposedCount)` | 公共筛选 | 原业务链路 | 一致 |
| Diff 采纳数 | `diffAcceptedCount` | `sum(diffAcceptedCount)` | 公共筛选 | 原业务链路 | 一致 |
| Diff 拒绝数 | `diffRejectedCount` | `sum(diffRejectedCount)` | 公共筛选 | 原业务链路 | 一致 |
| Diff 采纳率 | `diffAcceptanceRate` | `diffAccepted/diffProposed` | 公共筛选 | 原业务链路 | 一致 |
| Diff 拒绝率 | `diffRejectionRate` | `diffRejected/diffProposed` | 公共筛选 | 原业务链路 | 一致 |
| 输入 Token | `inputTokens` | `sum(tokensInput)` | 公共筛选 | 活动事实；插件记录同名 token | 一致 |
| 输出 Token | `outputTokens` | `sum(tokensOutput)` | 公共筛选 | 活动事实；插件记录同名 token | 一致 |
| Reasoning Token | `reasoningTokens` | `sum(tokensReasoning)` | 公共筛选 | 活动事实；插件记录同名 token | 一致 |
| 主 Token 总量 | `totalTokens` | `input+output+reasoning`，不含缓存 | 公共筛选 | 活动事实；插件记录同名 token | 一致 |
| 人均 Token | `tokensPerUser` | `totalTokens/activeUsers` | 公共筛选 | 活动事实 | 一致 |
| Run 均 Token | `tokensPerRun` | `totalTokens/runCount` | 公共筛选 | 活动事实 | 一致 |
| 漏斗总用户 | `funnel.totalUsers` | 最新注册用户数 | 公共筛选 | 活动事实用户维度 | 一致 |
| 漏斗活跃用户 | `funnel.activeUsers` | 至少 1 条用户消息 | 公共筛选 | 活动事实 | 一致 |
| 漏斗深度用户 | `funnel.deepUsers` | 至少 2 日且累计至少 5 条用户消息 | 上海自然日 | 活动事实 | 一致 |
| 漏斗活跃率 | `funnel.activeRate` | `activeUsers/totalUsers` | 公共筛选 | 活动事实 | 一致 |
| 漏斗深度率 | `funnel.deepRate` | `deepUsers/activeUsers` | 公共筛选 | 活动事实 | 一致 |
| 漏斗定义 | `activeDefinition/deepDefinition` | 固定中文口径说明 | 无 | 服务层常量 | 一致 |
| 趋势桶时间 | `timeseries[].bucketStart` | 按粒度补齐空桶 | 上海时区 | 活动事实 | 一致 |
| 趋势登录用户 | `timeseries[].loginUsers` | 桶内唯一登录用户 | 公共筛选、粒度 | 活动事实 | 一致 |
| 趋势活跃用户 | `timeseries[].activeUsers` | 桶内唯一消息用户 | 公共筛选、粒度 | 活动事实 | 一致 |
| 趋势会话 | `sessionCount/activeSessionCount` | 桶内求和 | 公共筛选、粒度 | 活动事实 | 一致 |
| 趋势消息 | `userMessageCount/assistantMessageCount` | 桶内求和 | 公共筛选、粒度 | 活动事实 | 一致 |
| 趋势 Run 状态 | `runCount/succeededRuns/failedRuns/cancelledRuns` | 桶内求和 | 公共筛选、粒度 | 活动事实 | 一致 |
| 趋势反馈 | `positiveFeedbackCount/negativeFeedbackCount` | 桶内求和 | 公共筛选、粒度 | 原反馈链路 | 一致 |
| 趋势 Diff | `diffAcceptedCount/diffRejectedCount` | 桶内求和 | 公共筛选、粒度 | 原业务链路 | 一致 |
| 趋势 Token | `totalTokens` | 桶内主 Token 求和 | 公共筛选、粒度 | 活动事实 | 一致 |
| 趋势满意率 | `satisfactionRate` | 桶内 `positive/(positive+negative)` | 公共筛选、粒度 | 原反馈链路 | 一致 |
| 趋势 Diff 采纳率 | `diffAcceptanceRate` | 桶内 `accepted/(accepted+rejected)` | 公共筛选、粒度 | 原业务链路 | 一致 |
| 趋势取消率 | `cancellationRate` | 桶内 `cancelled/runCount` | 公共筛选、粒度 | 活动事实 | 一致 |
| 小时热力-用户消息 | `metric=USER_MESSAGES/points[].value` | 小时桶 `sum(userMessageCount)` 并补零 | 最多 90 天、上海小时 | 活动事实 | 一致 |
| 小时热力-主 Token | `metric=PRIMARY_TOKENS/points[].value` | 小时桶 `sum(tokensTotal)` 并补零 | 最多 90 天、上海小时 | 活动事实 | 一致 |
| 小时热力-缓存 Token | `metric=CACHE_TOKENS/points[].value` | 小时桶 `sum(cacheRead+cacheWrite)` 并补零 | 最多 90 天、上海小时 | 活动事实 | 一致 |
| Token 使用总量 | `tokenOperations.totalTokens` | 主 Token、缓存读、缓存写合计 | 公共筛选 | 活动事实 | 一致 |
| Token 主用量 | `primaryTokens` | `input+output+reasoning` | 公共筛选 | 活动事实 | 一致 |
| 缓存读/写 | `cacheReadTokens/cacheWriteTokens` | 各自求和 | 公共筛选 | 活动事实 | 一致 |
| Token 用户 | `tokenUsers` | 至少产生一种 Token 的唯一用户 | 公共筛选 | 活动事实 | 一致 |
| Token 活跃用户 | `tokenOperations.activeUsers` | 至少 1 条用户消息的唯一用户 | 公共筛选 | 活动事实 | 一致 |
| Token 使用率 | `tokenUserRate` | `tokenUsers/activeUsers` | 公共筛选 | 活动事实 | 一致 |
| Token 活跃人天 | `tokenActivePersonDays` | 用户产生 Token 的上海自然日去重计数 | 上海自然日 | 活动事实 | 一致 |
| 日人均 Token | `dailyTokensPerUser` | `totalTokens/tokenActivePersonDays` | 上海自然日 | 活动事实 | 一致 |
| 重复 Token 用户 | `repeatTokenUsers` | Token 使用日不少于 2 的用户数 | 上海自然日 | 活动事实 | 一致 |
| 重复 Token 使用率 | `repeatTokenUserRate` | `repeatTokenUsers/tokenUsers` | 上海自然日 | 活动事实 | 一致 |
| 每日 Token 明细 | `daily[].date/totalTokens/primaryTokens/cacheReadTokens/cacheWriteTokens/tokenUsers/tokensPerUser` | 日期分组求和；日人均为总量/当日用户 | 上海自然日 | 活动事实 | 一致 |
| 用户 Token 明细 | `users[].userId/username/organization/rdDepartment/department` | 用户维度快照 | 公共筛选、Top N | 活动事实用户维度 | 一致 |
| 用户 Token 量 | `users[].totalTokens/primaryTokens/cacheReadTokens/cacheWriteTokens` | 用户分组求和 | 公共筛选、Top N | 活动事实 | 一致 |
| 用户 Token 日与日均 | `tokenDays/tokensPerTokenDay` | Token 日期数与 `total/tokenDays` | 上海自然日 | 活动事实 | 一致 |
| 用户 Token 强度 | `intensityBand` | 用户 Token 日均四分位分档 | 公共筛选、Top N | 活动事实 | 一致 |
| 能力分母 | `capabilities.activeUsers` | 至少 1 条用户消息的唯一用户 | 公共筛选 | 活动事实 | 一致 |
| 能力类型与名称 | `rows[].type/name` | AGENT/SKILL/TOOL 与插件上报身份 | 公共筛选、类型 | 插件能力事实 | `test-design` 由错误 0 修正为真实值 |
| 能力调用数 | `invocationCount` | 唯一 `callId` 计数；成功、失败、取消各计一次 | `coverageStartAt` 前旧事实，之后仅插件事实 | 插件能力事实 | 旧固定集一致；真实 Skill 修复 |
| 能力用户数 | `userCount` | 能力调用事实中的唯一用户 | 同上 | 插件能力事实 | 旧固定集一致；真实 Skill 修复 |
| 能力使用率 | `usageRate` | `userCount/activeUsers` | 同上 | 插件能力事实 + 活跃用户分母 | 旧固定集一致；真实 Skill 修复 |
| 能力成功/失败 | `succeededCount/failedCount` | 唯一调用按最终状态分类 | 同上 | 插件能力事实 | 旧固定集一致 |
| 能力取消 | `cancelledCount` | 唯一调用状态 CANCELLED | 同上 | 插件能力事实 | 新增兼容字段 |
| 能力未完成 | `incompleteCount` | 唯一调用无终态或 Trace 不完整 | 同上 | 插件能力事实 | 旧固定集一致 |
| 能力来源 | `source` | 有插件覆盖后为 `OPENCODE_PLUGIN` | 全局切换点 | Trace 目录 | 新增兼容字段 |
| 能力覆盖起点 | `coverageStartAt` | 首个插件批次成功持久化时间，后续冻结 | 全局切换点 | Trace 目录 | 新增兼容字段 |
| 能力完整水位 | `completeThrough` | 完整 Trace 的最大更新时间 | 公共筛选 | Trace 目录 | 新增兼容字段 |
| 能力 rollout | `rolloutCompleteness` | 完整 Trace 数/插件 Trace 总数 | 公共筛选 | Trace 目录 | 新增兼容字段 |
| 用户身份与组织 | `users[].userId/username/organization/rdDepartment/department` | 用户分组原值 | 公共筛选、分页、排序 | 活动事实用户维度 | 一致 |
| 用户登录与会话 | `loginCount/sessionCount/activeSessionCount` | 用户分组求和 | 公共筛选、分页、排序 | 活动事实 | 一致 |
| 用户消息与 Run | `userMessageCount/runCount` | 用户分组求和 | 公共筛选、分页、排序 | 活动事实 | 一致 |
| 用户 Run 状态 | `succeededRuns/failedRuns/cancelledRuns` | 用户分组求和 | 公共筛选、分页、排序 | 活动事实 | 一致 |
| 用户反馈 | `positiveFeedbackCount/negativeFeedbackCount` | 用户分组求和 | 公共筛选、分页、排序 | 原反馈链路 | 一致 |
| 用户 Diff | `diffAcceptedCount/diffRejectedCount` | 用户分组求和 | 公共筛选、分页、排序 | 原业务链路 | 一致 |
| 用户 Token | `totalTokens` | 用户主 Token 求和 | 公共筛选、分页、排序 | 活动事实 | 一致 |
| 用户成功/满意/Diff 率 | `successRate/satisfactionRate/diffAcceptanceRate` | 对应用户分子/分母 | 公共筛选、分页、排序 | 活动事实与原反馈链路 | 一致 |
| 用户最后活动 | `lastActivityAt` | 用户最大活动时间 | 公共筛选、分页、排序 | 活动事实 | 一致 |
| 组织维度与名称 | `organizations[].dimension/name` | organization/rdDepartment/department 分组 | `groupBy`、公共筛选、Top N | 活动事实用户维度 | 一致 |
| 组织注册/启用/登录/活跃/深度用户 | `registeredUsers/enabledUsers/loginUsers/activeUsers/deepUsers` | 组织内对应唯一用户数 | `groupBy`、公共筛选、Top N | 活动事实 | 一致 |
| 组织活跃/深度率 | `activeRate/deepRate` | `active/enabled`、`deep/active` | 同上 | 活动事实 | 一致 |
| 组织 Run 与状态 | `runCount/succeededRuns/failedRuns/cancelledRuns` | 组织分组求和 | 同上 | 活动事实 | 一致 |
| 组织反馈与 Diff | `positiveFeedbackCount/negativeFeedbackCount/diffAcceptedCount/diffRejectedCount` | 组织分组求和 | 同上 | 原业务链路 | 一致 |
| 组织 Token | `totalTokens` | 组织主 Token 求和 | 同上 | 活动事实 | 一致 |
| 组织成功/满意/Diff 率 | `successRate/satisfactionRate/diffAcceptanceRate` | 组织内对应分子/分母 | 同上 | 活动事实与原反馈链路 | 一致 |
| 满意度汇总 | `positiveFeedbackCount/negativeFeedbackCount/satisfactionRate/feedbackCoverageRate` | 与总览同口径 | 公共筛选 | 原反馈链路 | 一致 |
| 负向原因 | `negativeReasonCounts` | `reasonCode` 分组计数 | 公共筛选 | 原反馈链路 | 一致 |
| 反馈明细身份 | `feedbackId/userId/username/organization/rdDepartment/department` | 原值 | 公共筛选、分页 | 原反馈链路 | 一致 |
| 反馈关联 | `sessionId/runId/messageId` | 原值 | 公共筛选、分页 | 原反馈链路 | 一致 |
| 反馈正文元数据 | `rating/reasonCode/comment/createdAt/updatedAt` | 原值 | 公共筛选、分页 | 原反馈链路 | 一致 |
| 异常 Run 关联 | `runId/userId/username/organization/rdDepartment/department/workspaceId/agentId/modelId` | 原值 | 公共筛选、分页 | 活动事实 | 一致 |
| 异常 Run 状态与时间 | `status/createdAt/updatedAt` | FAILED/CANCELLED 明细及原时间 | 公共筛选、分页 | 活动事实 | 一致 |
| 高峰时段 | `peaks[].bucketStart/activeUsers/runCount/userMessageCount` | 小时聚合后按活跃用户、Run 排序取 Top N | 上海小时 | 活动事实 | 一致 |
| 高峰满意/取消/Token | `satisfactionRate/cancellationRate/totalTokens` | 小时桶对应公式 | 上海小时 | 活动事实与原反馈链路 | 一致 |
| 周时热力 | `heatmap[].dayOfWeek/hourOfDay/activeUsers/runCount/userMessageCount` | 周一至周日 × 24 小时固定补零 | 上海时区 | 活动事实 | 一致 |
| CSV 导出 | `overview/timeseries/users/organizations/feedback/exceptions/funnel/token-operations/capabilities` | 与对应查询同一字段和公式 | 沿用当前筛选 | 同对应查询 | 九类均一致；能力取消列为新增兼容列 |

## 固定数据集运行证据

```text
基线提交: 473d48dfc
当前分支: release
测试: AnalyticsQueryServiceTest
基线结果: 7 tests, 0 failures
当前结果: 7 tests, 0 failures
旧字段规范化 SHA-256: ff77d42df433913e460f2bc281084b50530cfb1dc8bc8f18e2a2e3482b06e012
```

## 2. 新旧采集链路切换

当前实现以首次成功持久化插件批次冻结 `coverageStartAt`。该时间之前保留旧能力事实，之后 Agent、Skill、Tool 查询只读取
`OPENCODE_PLUGIN`；RunEvent 继续服务聊天、SSE 和非运行态指标，但切换后不再生成新的运行态能力事实。查询以
`eventId` 幂等、以 `runId + callId` 反连接旧事实，并把 `source/coverageStartAt/completeThrough/rolloutCompleteness`
作为向后兼容的新增字段返回。

真实 `test-design` Run `run_6d73a62460254293b8fa1847bb3c271f` 的 ClickHouse 核验结果如下：

```text
source=OPENCODE_PLUGIN
coverageStartAt=2026-08-22T12:22:42.764Z
completeThrough=2026-08-22T17:33:46.248Z
non_plugin_rows=0
duplicate_event_ids=0
duplicate_call_status_rows=0
```

ClickHouse 中另有 16 条 `occurred_at >= coverageStartAt` 的 `EVENT_SNAPSHOT` 存量行，但其最新 `ingested_at` 为
`2026-08-22T15:39:00.051Z`，早于切换 migration 的安装时间 `2026-08-22T16:11:28.550Z`；查询层始终以
`occurred_at < coverageStartAt` 屏蔽这些遗留行。按 migration 安装时间复核，新产生的旧来源 Agent/Skill/Tool 事实为 0。
因此这里的 `non_plugin_rows=0` 指真实 Run 的有效查询口径，而不是物理删除切换前已写入的存量数据。

结论：切换和幂等口径已由真实插件事实及定向 ClickHouse 测试验证；RunEvent/SSE wire shape 未改变。

## 3. Skill 为 0 修复

插件在 `tool.execute.before` 冻结 `callID/tool/args`，`after` 只闭合状态、结果和耗时；Skill 名只取
`args.name` 或 `metadata.name`。1.18.4 普通 Tool 不触发 after 的 error 分支由
`message.part.updated` 的 `part.state.status=error` 闭合，成功、失败、取消均按唯一 `callID` 计一次。

最终真实测试设计 Agent 使用受保护 `test-design-orchestrator` 执行，结果为：

```text
session=ses_90c4a3fed892450da08d681861d4d92b
run=run_6d73a62460254293b8fa1847bb3c271f
trace=trc_fd13b13f9caa2fcd5b85aedea13407a0
SKILL test-design SUCCEEDED: 14 unique events / 14 unique calls / 1 user
SKILL test-design FAILED:     1 unique event  / 1 unique call  / 1 user
TOOL local_files_list_skill_resources SUCCEEDED: 1 unique call / 1 user
```

同一时间窗口的运营 API 返回 `test-design invocationCount=24/userCount=1/succeededCount=21/failedCount=1/
cancelledCount=0/incompleteCount=2`。窗口包含此前插件 Trace，因此 API 总量大于上述单 Run；两种查询的用户数和调用数均不再为 0。

## 4. OpenCode 1.18.4 契约

服务端 manager 与本地受控客户端 `/global/health` 均返回 `1.18.4`。实现只依据只读
`opencode-source/opencode-1.18.4/` 的公开 plugin 类型、schema 和实际发射顺序，覆盖：

- `chat.message`、`experimental.chat.system.transform`、`experimental.chat.messages.transform`、
  `tool.execute.before/after` 和 `event`；
- 从 `output.messages[0].info.sessionID` 关联 messages transform；
- `message.part.updated`、`message.part.delta`、`step-start`、`step-finish`、Tool error/cancel 状态；
- `message.updated.info.time.completed` 作为 assistant 完成边界，不用较早的 step-finish 伪造完成；
- 插件、网络和归档异常 fail-open，不传播到 Hook 或聊天。

`node --test tools/test-opencode-observability-plugin.mjs` 使用真实 1.18.4 fixture 覆盖 16 项，全部通过；只读快照没有修改。

## 5. DSH/Harness 增量可观测能力

插件追加记录 system prompt、用户输入、上下文/压缩上下文、assistant 文本与 reasoning 分片、父子 Session、Tool/Skill
参数和结果、Turn/Step/Message/Call 生命周期、五类 token、LLM/Tool/TTFT/decode 指标、cost、dropped、pending、
completeThrough、稳定事件 ID、全局/Session 序号及父子关联。当前真实 Trace
`trc_fd13b13f9caa2fcd5b85aedea13407a0` 为 `COMPLETED/ARCHIVED`、`complete=true`、
`eventCount=3413`、`archivedBytes=696361`、`dropped=0`、`pending=0`、`completeThrough=3462`。

Trace 保持在控制台内：左侧为紧凑基础信息列表，点击后在原位展开 DSH 风格三泳道和右侧检查器，不跳转到独立页面。
Input/Model/Tools 三泳道使用纯色，不使用渐变；基础信息行高 40px，事件行最低 28px。Duration 在等宽块与真实耗时块间切换，
Turns 折叠/展开 Turn，Calls 折叠/展开 Assistant 下工具调用，三者不是三个同义坐标模式。页面还支持搜索、时间区间、父子
Agent、Summary/Payload/Result/Timing/Source 和单条完整下载。

## 6. 本地上传与性能

本地 Java 只保留未 ACK spool，服务器原子校验后才删除；sequence 单调、SHA-256、重启/断网续传、generation fencing、
单在途、空闲 3 秒、1 MiB/s、控制/模型/文件帧抢占、指数退避和预算耗尽标记 `INCOMPLETE` 均有定向测试。真实续传演练中
33 个分片在 ACK 后删除，1 个摘要冲突分片进入持久 `blocked` 且仍计入预算；后续合法分片没有被饿死。

插件 20,000 次热路径基准结果：

```text
hook p99 incremental = 0.0017 ms  (门槛 2 ms)
additional RSS        = 10,321,920 bytes (门槛 32 MiB)
hot-path file/network = 0
```

Java Trace 内存队列上限锁定为 16 MiB，调度测试证明对话活跃期 Trace 上传为 0。尚未完成真实模型在完全相同负载下的
关闭/开启多轮对照，因此 CPU、磁盘 IOPS、首 token p95 和整轮 p95 回归不超过 3% 仍是发布前未通过门禁。

## 7. 服务端归档与安全

服务器把正文写入持久卷上的不可变 gzip NDJSON 分片，manifest 记录序号、SHA-256、大小、完整度和冻结节点；重复分片返回
相同 ACK，摘要冲突失败关闭。跨节点读取只复用 `BackendJavaRouteResolver` 与 `BackendHttpForwarder`，不扫描其它节点、
不本机降级。ClickHouse 只保存目录/span/能力元数据；运行态 `system.columns` 核验三个新表中 prompt、reasoning、payload、
Tool 正文和物理路径列计数为 0，四个 migration 也没有为这些正文或路径建列，且没有 TTL。

定向后端测试共 28 项通过，覆盖分片幂等、摘要冲突、原子归档、冻结节点、generation fence、未 ACK 保留、ACK 后删除、
SUPER_ADMIN 正文权限和成功/失败审计。真实下载 Trace `trc_fd13b13f9caa2fcd5b85aedea13407a0`：

```text
HTTP=200
gzip bytes=696361
NDJSON lines=3413
SHA-256=fbe2b2e8e44c1a46f51ee21d6b04c24641bff62ebf9814ade1b51ac805767941
audit=VIEW SUCCESS + DOWNLOAD SUCCESS（仅元数据，无正文/物理路径）
```

关闭或重启客户端/后端后，已归档正文不依赖客户端在线。真实归档目录 I/O 故障下的“聊天仍成功、页面显示积压或不完整”
尚未做运行态故障注入；目前只有摘要冲突、归档异常和 fail-open 自动化测试，不能替代该真实故障验收。

## 8. 企业部署与端到端验收

后端 JAR、前端 dist、本地客户端 ARM64 离线分发物已从正式脚本生成。服务端与本地包均使用同一插件，仓库、已运行
Dev.app 和本地分发物的插件 SHA-256 都为
`70c5204ef254d829b554095ebf93256149f3f822fbece0f35695190572c17b3b`。本地分发 manifest 固定
`opencodeVersion=1.18.4`，JDK、OpenCode、Java 客户端、manifest/catalog/artifact 签名和摘要全部独立验证通过。

```text
后端: /tmp/test-agent-observability-release-final/backend/test-agent-app.jar
前端: /tmp/test-agent-observability-release-final/test-agent-frontend-dist.tar.gz
本地: /tmp/test-agent-observability-release-final/local-opencode-client/
本地版本: 20260823021803
本地 manifest SHA-256: 5f0890f415470660e4d13ce03d9f7c9a63a682ddd337511517d1bd8ae5f5bbbc
Worker: /tmp/test-agent-observability-release-final/test-agent-opencode-worker_internal-linux-amd64.tar
Worker SHA-256: 482868e7b8881f7d03ddab1ce799911888626c5c4e7cc2d1ab20cdd97213def3
Programs SHA-256: 35c6fb2f528aefc40a0d207f3319759abd9fe61126625948785e45deec61e80b
```

后端封包已校验 PostgreSQL 与四条 ClickHouse observability migration 在最终 JAR 中的文件名和 SHA-256；企业文档已覆盖持久卷、
容量/权限/告警、双后端路由、升级/回滚和 Flyway 历史。服务端 worker 已在 ARM64 封包机跨架构构建 `linux/amd64` 镜像，
独立运行 `verify-opencode-node-worker-image.sh` 通过：OpenCode `1.18.4`、glibc `2.31`、离线 Node/Tool 探针均正常；镜像内
Observability 插件 SHA-256 与仓库/本地包一致，均为 `70c5204e...17b3b`。Codex/Python 离线探针也通过；原生
linux/amd64 sandbox 仍按脚本要求必须在每个真实 x86 Worker 节点复验，ARM64 Docker 仿真不能替代这一项。

本机按固定 `.env.test`、规定 PostgreSQL 和 ClickHouse 执行完整重启，backend readiness、frontend、manager、ClickHouse
均正常；服务端与客户端 OpenCode 都为 1.18.4。真实服务端测试设计 Agent、真实本地/服务器普通对话与 Agent 子任务、
真实 Skill 事实、自动归档及关闭客户端后下载均已验证；但受保护测试设计 Agent 当前按平台契约在服务端执行，尚未取得
`runtimeKind=LOCAL_CLIENT` 的真实 `test-design` 调用事实。

实际补测 `run_e0a1d5f3245f4a53ad16a267e5cf1672` 已冻结为 `runtimeKind=LOCAL_CLIENT`、实例
`lci_c8d77417e5a0462db2edbf8d4a433445`，但在提交 prompt 后立即失败。客户端 OpenCode 1.18.4 的 `/agent` 只列出
build/plan/general/explore 等默认 Agent，没有 `test-design-orchestrator`；日志为 `prompt_async failed ... UnknownError`。
这证明阻塞点不是 Trace 路由，而是当前本地离线包没有公共 Agent/Skill 配置分发依赖。服务端受保护 Agent 成功不能替代该门禁。

### 尚未完成的发布门禁

以下项目没有被单元测试或本机打包替代，八组验收目前仍是“部分通过”，不能表述为全部完成：

1. 本地客户端实际执行一次 `test-design` Agent，并得到 `runtimeKind=LOCAL_CLIENT` 的 Skill/Trace 事实；
2. 真实模型关闭/开启采集的同负载 CPU、磁盘 IOPS、首 token 与整轮 p95 对照；
3. 运行态归档服务 I/O 故障注入，并同时证明聊天成功且页面显示积压/不完整；
4. 两台真实企业后端的冻结节点路由、升级与回滚演练；
5. 目标企业历史 PostgreSQL 的 `flyway_schema_history` 基线到 HEAD 升级；
6. 真实麒麟 ARM64 无公网安装、启动、断网、更新与回滚。

因此本文结论为：实现与本机/自动化验证已覆盖主要链路，但尚未达到“八组全部具备真实运行证据”的最终验收标准。
