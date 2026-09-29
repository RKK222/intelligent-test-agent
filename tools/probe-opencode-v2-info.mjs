// 本地验收脚本共用 V2 就绪探针；认证信息只从环境读取，不进入命令参数或错误输出。
const password = process.env.TEST_AGENT_OPENCODE_SERVER_PASSWORD ?? process.env.OPENCODE_PASSWORD;
const headers = password
  ? { authorization: `Basic ${Buffer.from(`opencode:${password}`).toString("base64")}` }
  : {};

try {
  const url = new URL("/api/info", process.env.OPENCODE_BASE_URL ?? "http://127.0.0.1:4096");
  const response = await fetch(url, { headers, signal: AbortSignal.timeout(5000) });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
} catch {
  console.error("OpenCode V2 /api/info probe failed.");
  process.exitCode = 1;
}
