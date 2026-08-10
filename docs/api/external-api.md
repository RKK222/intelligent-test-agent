# 外部 API Key 与 SSH Key 查询

本文档是 `/api/external/v1/**` 的调用方契约。该命名空间面向受信内网中的服务端工具，不开放浏览器跨域调用；网关负责来源隔离和频率限制。

## 凭据与请求认证

超级管理员在“系统管理 → API Key 管理”登记工具后，由平台生成一次 `taak_v1_` 加 32 字节安全随机数无填充 Base64URL 的 API Key。调用方在每次请求中以明文 Header 传入：

```http
X-Test-Agent-Tool-Code: deploy.bot
X-Test-Agent-Api-Key: taak_v1_<opaque-value>
```

Header 值不再做二次加密，安全性依赖受信内网、网关隔离和链路保护。任何能够截获 API Key 的主体也能冒用请求并解密 SSH 响应，因此不得把该接口暴露到公网、浏览器或不可信代理；API Key 不得进入 URL、日志、错误、监控标签或客户端持久化。

缺失 Header、未知工具、停用工具或 Key 错误统一返回 `401 UNAUTHENTICATED`；scope 不足返回 `403 FORBIDDEN`。用户 Bearer Token 和静态 `TEST_AGENT_API_TOKEN` 不能代替这两个 Header。

## 查询用户 SSH Key

```http
GET /api/external/v1/users/{unifiedAuthId}/ssh-key
```

要求 scope `USER_SSH_KEY_READ`。只查询状态正常且保存了新版混合加密 SSH Key 的用户：用户不存在、已停用或未配置 Key 均返回同一 `404 NOT_FOUND`；旧 SSH 加密格式返回 `409 CONFLICT`。注册表在启动后不可用时返回 `503 EXTERNAL_API_UNAVAILABLE`。错误正文不加密，但不会包含 API Key、SSH 内容或数据库密文。

成功响应继续使用统一外层格式，`traceId` 同时参与响应密文认证：

```json
{
  "success": true,
  "traceId": "trace_1234567890abcdef",
  "data": {
    "version": "TAEK1",
    "keyDerivation": "HKDF-SHA256",
    "cipher": "AES-256-GCM",
    "salt": "<base64url-no-padding>",
    "nonce": "<base64url-no-padding>",
    "ciphertext": "<base64url-no-padding>"
  }
}
```

解密后的 UTF-8 JSON 固定包含：

```json
{
  "schemaVersion": 1,
  "unifiedAuthId": "AUTH_001",
  "sshKeyId": "ssh_001",
  "name": "company-git",
  "fingerprint": "<sha256-hex>",
  "privateKey": "-----BEGIN ... PRIVATE KEY-----\n...",
  "issuedAt": "2026-08-09T08:00:00Z"
}
```

## TAEK1 解密

- 输入密钥材料：API Key 的 UTF-8 字节。
- salt：响应中的随机 16 字节值。
- HKDF：HKDF-SHA256，输出 32 字节，info 固定为 `test-agent/external-api/ssh-key/v1`。
- AES：AES-256-GCM，随机 12 字节 nonce，128 位认证标签；`ciphertext` 已包含尾部认证标签。
- AAD：UTF-8 字节 `TAEK1\n{toolCode}\n{unifiedAuthId}\n{traceId}`。
- `salt/nonce/ciphertext` 均使用无填充 Base64URL。

Python 调用方可使用 `cryptography`：

```python
import base64
import json
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

def b64url(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))

def decrypt_taek1(api_key: str, tool_code: str, unified_auth_id: str,
                  trace_id: str, data: dict) -> dict:
    if (data["version"], data["keyDerivation"], data["cipher"]) != (
        "TAEK1", "HKDF-SHA256", "AES-256-GCM"
    ):
        raise ValueError("unsupported SSH key envelope")
    key = HKDF(
        algorithm=hashes.SHA256(), length=32, salt=b64url(data["salt"]),
        info=b"test-agent/external-api/ssh-key/v1"
    ).derive(api_key.encode("utf-8"))
    aad = f"TAEK1\n{tool_code}\n{unified_auth_id}\n{trace_id}".encode("utf-8")
    plaintext = AESGCM(key).decrypt(
        b64url(data["nonce"]), b64url(data["ciphertext"]), aad
    )
    return json.loads(plaintext.decode("utf-8"))
```

解密失败必须视为认证失败或报文篡改，不能尝试忽略 AAD、认证标签或回退到非认证加密。私钥使用完毕后应尽快释放进程内引用，不写入磁盘、日志、异常或遥测。

## 管理 API

基础路径 `/api/internal/platform/system-management/api-keys`，全部要求平台用户 JWT 和实时 `SUPER_ADMIN` 角色。

| 方法 | 路径 | 请求/用途 | 响应要点 |
|---|---|---|---|
| `GET` | `/scopes` | 查询支持的 scope | 首版仅 `USER_SSH_KEY_READ` |
| `GET` | `?keyword=&enabled=&page=&size=` | 分页查询；默认 `1/20`，size 最大 `100` | 列表仅返回 `keyHint` |
| `POST` | `/` | `{toolCode,toolName,scopes,enabled}` | 平台生成 Key，返回 credential 与完整 `apiKey` |
| `PATCH` | `/{credentialId}` | `{toolName,scopes,enabled}` | `toolCode` 不可修改 |
| `POST` | `/{credentialId}/reveal` | 查看当前完整 Key | 返回 `credentialId/toolCode/apiKey` |
| `POST` | `/{credentialId}/rotate` | 立即轮换 | 返回新 Key，旧 Key 在所有 Java 刷新后失效 |
| `DELETE` | `/{credentialId}` | 永久删除 | 同时级联删除 scope |

`toolCode` 匹配 `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`；`toolName` 去首尾空白后为 1–128 字符；scope 必须非空且全部受支持。重复工具编码返回 `409 CONFLICT`。新建、查看和轮换响应携带 `Cache-Control: no-store` 与 `Pragma: no-cache`。

平台数据库只保存 API Key 的 RSA-OAEP/SHA-256 密文、SHA-256 指纹和掩码提示。管理员页面只在弹窗组件内存中短暂持有明文，不写浏览器存储、TanStack Query 缓存或原始交换观察记录。

## 刷新与发布

Java 在 Flyway 完成后严格整表解密并构建不可变注册表，失败会阻止实例启动就绪。管理事务提交后，本机立即整表刷新，并通过 Redis 内部广播 `external-api-credential.refresh-requested` 通知其它 Java；广播 payload 为空，不携带凭据。每个实例另有 60 秒补偿刷新，先构建完整快照再原子替换，运行期刷新失败保留上一份有效快照。

多 Java 环境必须共享 PostgreSQL/Redis、启用 `TEST_AGENT_SERVER_BROADCAST_ENABLED=true`，并使用包含同一 `classpath:rsa-private.key` 的相同交付 JAR。发布顺序固定为：数据库 migration、全部 Java 升级并验证注册表加载、前端升级、最后开放网关外部路由；旧 Java 全部退出前不得启用外部调用。
