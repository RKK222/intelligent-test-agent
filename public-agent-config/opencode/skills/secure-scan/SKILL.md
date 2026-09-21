---
name: secure-scan
description: Secure Scan（安全测试）。根据用户提供的 HTTP 请求报文和已授权的扫描端点，拼装规范请求并执行常见安全漏洞扫描；需要安全扫描、红线检测、Top 10 漏洞检查时使用。
compatibility: opencode
metadata:
  display-name: Secure Scan
  display-name-zh: 安全测试
  source: test-agent
  version: '1.0.2'
---

# 安全测试

根据用户明确授权的测试报文，对已授权目标执行常见安全扫描。该技能只负责请求拼装和结果解释，除原公共包的扫描引擎地址外，不内置任何目标企业域名、IP、Cookie、CSRF Token、Authorization 或其他会话凭据。

## 执行前置条件

1. 只对用户明确授权的目标执行扫描；没有目标范围、授权依据或扫描端点时停止并说明缺口。
2. 扫描引擎默认使用 `POST http://122.244.74.57:8888/api/v1/jungle_happy_scan`；运行环境已提供 `SECURE_SCAN_ENDPOINT` 或平台受控连接时可覆盖该默认值。
3. 扫描引擎需要的认证通过运行环境变量 `SECURE_SCAN_AUTH`、平台连接或用户在安全渠道注入；禁止把 Cookie、Token、密码写入文件、日志或最终回复。
4. 用户提供的目标 URL、Host、Method、Body 和请求头只能用于当前授权扫描；对话中出现的凭据按敏感数据处理。

## 请求拼装

将用户提供的参数拼装为 HTTP/1.1 原始报文，放入扫描引擎的 `http` 字段：

```json
{
  "http": "POST /path HTTP/1.1\\nHost: target.example\\nContent-Type: application/json\\n\\n{\"key\":\"value\"}",
  "scheme": "auto",
  "scan_type": ["sqli", "file_read", "file_upload", "xxe", "unauthorized", "reflected_xss", "sensitive_data", "sms_abuse"]
}
```

- 报文换行使用 `\\n` 表示；保留用户提供的合法请求头和请求体，不自行补造身份信息。
- 用户未指定 `scan_type` 时使用示例中的默认集合；用户明确指定时只使用其授权范围。
- 通过受控平台 Tool 调用 `SECURE_SCAN_ENDPOINT` 或上述默认地址，认证从受控环境读取；不要在回复中输出可执行的完整带凭据请求。
- 扫描失败、端点缺失、授权缺失或响应无法解析时返回 `INCOMPLETE`，不得宣称“无漏洞”。

## 结果输出

扫描完成后仅输出脱敏摘要：漏洞名称、等级、检测证据的最小必要片段、扫描类型和是否需要人工复核。隐藏 Cookie、Token、完整请求报文、内部地址和用户数据；没有发现漏洞时明确写“未检测到漏洞”，并附扫描是否完整。

## 示例占位符

以下仅用于说明格式，不得替换成真实凭据：

```text
POST /api/example HTTP/1.1
Host: target.example
Content-Type: application/json
Cookie: <从安全渠道注入>
X-CSRF-Token: <从安全渠道注入>

{"field":"value"}
```
