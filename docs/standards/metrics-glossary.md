# AIPerf & 生成式 AI 模型性能指标英文缩写指南 (Metrics Reference Guide)

本文档参考 [NVIDIA GenAI Perf / AI Perf Metrics Reference](https://docs.nvidia.com/aiperf/dev/reference/ai-perf-metrics-reference) 标准，说明企业内部模型调用可观测性面板中的各项性能指标定义、英文缩写及离线查看方法。

## 1. 离线查看说明

- **前端界面离线直接查看**：在系统管理 → **内部模型调用可观测** 页面页首，内置有可展开/收起的“AIPerf & 业界指标英文缩写指南 (Glossary)”卡片（默认展开），离线部署网络隔离环境下可直接阅读。
- **本地源码离线查看**：本文档落盘于 `docs/standards/metrics-glossary.md`，离线服务器或个人工作区均可随时通过 Markdown 查看器阅读。

## 2. 核心性能指标对照表

| 缩写 | 全称 (Full Name) | 中文名称 | 定义与计算逻辑 (Definition & Logic) | 参考标准 (AIPerf Ref) |
|---|---|---|---|---|
| **TTFT** | Time to First Token | 首 Token 延迟 | 从客户端向 API 发起请求，到接收到模型返回的第一个 Output Token 之间的等待耗时。反映模型响应的启动速度。 | NVIDIA GenAI Perf: `time_to_first_token` |
| **ITL / TPOT** | Inter-Token Latency / Time Per Output Token | Token 输出间隔 / 单 Token 耗时 | 生成流式回答过程中，连续两个 Output Token 之间的平均时间间隔。计算公式为 `(E2E - TTFT) / (Output Tokens - 1)`。 | NVIDIA GenAI Perf: `inter_token_latency` |
| **SCT** | Stream Completion Time | 流式完成时间 | 从发起请求到流式回答接收到结束标记（`[DONE]` 或 `finish_reason`）的完整传输耗时。 | NVIDIA GenAI Perf: `stream_completion_time` |
| **E2E** | End-to-End Latency | 端到端总延迟 | 从客户端发送 HTTP 请求开始，到接收完全部响应或确认异常终止的总经历时间。 | NVIDIA GenAI Perf: `request_latency` / `end_to_end_latency` |
| **RPS** | Requests Per Second | 每秒请求数 (吞吐量) | 在当前统计窗口内，系统平均每秒处理的请求数量。反映模型代理/推理服务的并发承载压力。 | NVIDIA GenAI Perf: `throughput` / `request_throughput` |
| **REQ** | Request Count / Requests | 请求总数 | 在筛选时间内发起的调用总次数，包含成功、失败及调用方中途断开的全部记录。 | NVIDIA GenAI Perf: `request_count` |
| **SR / SR %** | Success Rate | 请求成功率 | 成功完成的调用次数占总调用次数的百分比：`SR = (成功次数 / REQ) × 100%`。 | Standard SLA Metric |
| **FR / FR %** | Failure Rate | Failure Rate | 请求错误率 | 异常或中途断开的调用次数占总调用次数的百分比：`FR = (失败次数 / REQ) × 100% = 100% - SR`。 | Standard SLA Metric |

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

## 4. 参考资料 (References)

- NVIDIA GenAI Perf Documentation: [AI Perf Metrics Reference](https://docs.nvidia.com/aiperf/dev/reference/ai-perf-metrics-reference)
