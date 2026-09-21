# 内部结构与正式案例映射

先在 Agent 内部上下文形成 JSON 数组，每个元素遵循以下结构；该 JSON 不落盘、不直接展示：

```json
[
  {
    "case": "混沌案例关注点描述",
    "case_id": "案例编号",
    "fault_type": "故障类型",
    "fault_category": "故障分类（如 网络/计算/存储/进程/服务/数据/消息/配置/安全）",
    "kws": ["故障类型关键词1", "故障类型关键词2"],
    "injection_instance": {
      "fault_scenario": "结合需求的故障注入场景描述",
      "injection_steps": ["步骤1", "步骤2", "步骤3"],
      "monitoring_checkpoints": ["监控指标1", "监控指标2"],
      "expected_system_behavior": "故障注入后系统的预期行为"
    }
  }
]
```

正式文件映射为四列表 Markdown：

| 正式列 | 来源 |
| --- | --- |
| 案例名称 | `case` + `injection_instance.fault_scenario` |
| 测试步骤 | `injection_instance.injection_steps`，按序号完整展开 |
| 测试数据 | `fault_type`、`fault_category`、注入目标和 `monitoring_checkpoints` |
| 预期结果 | `injection_instance.expected_system_behavior` |

## 注入实例生成规则

为每个推荐案例生成针对性的注入实例，结合需求上下文实例化。

### fault_scenario（故障场景）

将通用混沌案例映射到需求的具体依赖点：
- 识别需求中的关键服务、中间件、接口依赖
- 将通用故障类型转换为针对该依赖的具体注入场景
- 说明故障范围（单实例/多实例、部分流量/全流量）

### injection_steps（注入步骤）

生成 3-6 个可执行步骤：
1. 准备注入环境和目标确认
2. 执行故障注入操作
3. 观察系统行为和监控指标
4. （可选）验证降级/熔断/兜底逻辑
5. （可选）恢复故障并验证自愈
6. 清理注入残留

### monitoring_checkpoints（监控检查点）

列出需要关注的关键指标：
- 基础设施指标（CPU、内存、网络、磁盘）
- 应用指标（请求延迟、错误率、吞吐量）
- 中间件指标（连接池、队列长度、缓存命中率）
- 业务指标（交易成功率、响应时间）

### expected_system_behavior（预期系统行为）

描述故障注入后系统的预期表现：
- 降级策略是否生效
- 熔断是否触发（熔断阈值与恢复时间）
- 是否自动恢复（自愈时间窗口）
- 是否存在级联故障或刚性依赖
