# AIPerf & 生成式 AI 模型性能指标英文缩写指南 (Metrics Reference Guide)

本文档参考官方规范 [NVIDIA AIPerf Metrics Reference](https://docs.nvidia.com/aiperf/reference/ai-perf-metrics-reference) 标准，说明企业内部模型调用可观测性面板中的各项性能指标定义、英文缩写、计算状态及离线查看方法。

## 1. 离线查看说明

- **前端界面离线直接查看**：在系统管理 → **内部模型调用可观测** 页面页首，点击“参照 NVIDIA AIPerf 性能指标规范定义 ↗”链接或展开“AIPerf & 业界指标英文缩写指南 (Glossary)”卡片，可在网络隔离环境下直接离线阅读。
- **本地源码离线查看**：本文档落盘于 `docs/standards/metrics-glossary.md`，离线服务器或个人工作区均可随时通过 Markdown 查看器阅读。

## 2. 核心性能指标对照表

| 缩写 | 全称 (Full Name) | 中文名称 | 定义与计算逻辑 (Definition & Logic) | 计算状态 / 参考标准 (AIPerf Ref) |
|---|---|---|---|---|
| **TTFT** | Time to First Token | 首 Token 延迟 | 从客户端向 API 发起请求，到接收到模型返回的第一个 Output Token 之间的等待耗时。反映模型响应的启动速度；看板按模型厂商分别用最小值、P25、中位数、P75、最大值展示选定范围内的分布。 | 已统计 (`time_to_first_token`) |
| **ITL / TPOT** | Inter-Token Latency / Time Per Output Token | Token 输出间隔 / 单 Token 耗时 | 模型开始回答后，后续每个输出 Token 平均要等多久。按 `(最后一个输出到达时间 - 第一个输出到达时间) / (输出 Token 数 - 1)` 计算。至少输出 2 个 Token 且供应商返回准确用量时才统计；不会用数据块数量代替 Token 数。Overview 展示全量可靠样本的平均值和最大值，箱线图按模型厂商分别展示分布。 | 已统计 (`inter_token_latency`) |
| **SCT** | Stream Completion Time | 流式完成时间 | 从发起请求到流式回答接收到结束标记（`[DONE]` 或 `finish_reason`）的完整传输耗时。 | 已统计 (`stream_completion_time`) |
| **E2E** | End-to-End Latency | 端到端总延迟 | 从客户端发送 HTTP 请求开始，到接收完全部响应或确认异常终止的总经历时间。 | 已统计 (`request_latency` / `end_to_end_latency`) |
| **RPS** | Requests Per Second | 每秒请求数 (吞吐量) | 在当前统计窗口内，系统平均每秒处理的请求数量。反映模型代理/推理服务的并发承载压力。 | 已统计 (`throughput` / `request_throughput`) |
| **REQ** | Request Count / Requests | 请求总数 | 在筛选时间内发起的调用总次数，包含成功、失败及调用方中途断开的全部记录。 | 已统计 (`request_count`) |
| **SR / SR %** | Success Rate | 请求成功率 | 成功完成的调用次数占总调用次数的百分比：`SR = (成功次数 / REQ) × 100%`。 | 已统计 (Standard SLA Metric) |
| **FR / FR %** | Failure Rate | 请求错误率 | 异常或中途断开的调用次数占总调用次数的百分比：`FR = (失败次数 / REQ) × 100% = 100% - SR`。 | 已统计 (Standard SLA Metric) |

> **ITL / TPOT 样本说明**：没有准确输出 Token 数或只输出 1 个 Token 的调用显示为空，也不进入箱线图。`[DONE]`、`finish_reason` 等收尾信号晚于最后一个输出，它们的等待时间不计入 Token 输出节奏。

> **看板时长单位**：E2E、TTFT、SCT 和总耗时使用秒（s）；ITL / TPOT 使用毫秒（ms）。后端接口继续返回毫秒原始字段，页面按指标统一换算。

> **全量口径**：明细列表可以分页查看，但 Overview、供应商卡片和所有图表始终使用当前筛选范围内的全量统计，不会拿当前 20/50/100 条明细计算。

## 3. 图表中文表头与对应度量

看板中所有聚合图表均采用标准中文表头：

1. **请求数与成功率趋势**（REQ & SR Trend）
   - 左 Y 轴：每小时请求数 `REQ` (Line)。
   - 右 Y 轴：每小时请求成功率 `SR %` (Dashed Line)。
2. **调用结果分布**（Outcome Distribution）
   - 环形图：展示成功与各类异常在总调用量中的占比。
3. **失败原因分类**（Failure Breakdown）
   - 横向条形图：按“请求或配置问题”、“上游服务异常”、“调用方中断”等归并分类展示失败次数。
4. **供应商请求量对比**（Provider REQ Volume）
   - 横向条形图：按 Provider ID 汇总请求承载量。
5. **TTFT 与 ITL / TPOT 厂商对比**（Provider Latency Box Plot）
   - 每个模型厂商一个竖向箱体，用于直接比较不同厂商的时延分布；TTFT 使用秒，ITL / TPOT 使用毫秒。

## 4. 参考资料 (References)

- NVIDIA GenAI Perf Official Documentation: [NVIDIA GenAI Perf / AI Perf Metrics Reference](https://docs.nvidia.com/aiperf/dev/reference/ai-perf-metrics-reference)
