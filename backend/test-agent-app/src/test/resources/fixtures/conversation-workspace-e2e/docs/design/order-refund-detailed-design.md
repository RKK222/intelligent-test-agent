# 订单退款详细设计

## 1. 模块职责

- `RefundController`：接收请求、校验认证主体并转换统一响应。
- `RefundApplicationService`：执行幂等检查、金额规则和状态编排。
- `RefundRepository`：通过 MyBatis XML 持久化退款单和幂等键。
- `PaymentGateway`：调用外部支付渠道，设置 3 秒超时，不在同步链路内无限重试。
- `RefundCompensationJob`：扫描“退款处理中”的记录并查询渠道最终状态。

## 2. 接口

`POST /api/refunds`

```json
{
  "orderId": "order-10001",
  "amount": 88.00,
  "reason": "重复收费",
  "idempotencyKey": "refund-order-10001-001"
}
```

成功返回退款单号、当前状态和 traceId。参数错误返回 `VALIDATION_ERROR`，越权返回 `FORBIDDEN`，
累计金额超限返回 `REFUND_AMOUNT_EXCEEDED`。

## 3. 状态机

```text
CREATED -> APPROVED -> PROCESSING -> SUCCEEDED
                |             |  -> FAILED
                -> REVIEWING -|
```

终态不可回退。支付渠道超时只进入 `PROCESSING`，不能直接标记 `FAILED`。

## 4. 数据一致性

退款单包含 `refund_id/order_id/amount/status/idempotency_key/version/trace_id`。
`idempotency_key` 建唯一索引；更新状态时使用版本号执行乐观锁。订单累计退款金额与退款单创建在同一事务内完成。

## 5. 安全与可观测性

- 订单所属人校验在应用服务执行，Controller 不直接访问 Repository。
- 支付 token 只从密钥管理读取，不进入请求 DTO、数据库或日志。
- 渠道调用记录耗时、结果分类和 traceId，不记录敏感报文。

## 6. 测试关注点

- 金额 0、0.01、100、100.01 和累计金额临界值。
- 同一幂等键串行/并发重复提交。
- 人工审核通过、拒绝和重复审核。
- 渠道成功、业务失败、超时、回调乱序与补偿恢复。
- 本人、其他用户、管理员三类访问边界。
