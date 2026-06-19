// 适配层配置：指向平台后端 test-agent-app。
// 默认走 Vite 代理 /api（→ http://127.0.0.1:8080），规避 CORS 与 SSE 鉴权头问题。
// 直连后端时可设 VITE_PLATFORM_API_URL=http://127.0.0.1:8080。

const env = import.meta.env

/** 平台后端 API 基址（默认 /api，经 Vite 代理） */
export const PLATFORM_API_URL: string = env.VITE_PLATFORM_API_URL ?? "/api"

/** 可选 API token；本地 dev 默认空（后端未设 TEST_AGENT_API_TOKEN 时开放） */
export const PLATFORM_API_TOKEN: string | undefined = env.VITE_PLATFORM_API_TOKEN || undefined

/** 工作区根路径：opencode agent 运行目录。默认取当前 location 推断的 frontend-opencode 真实路径占位，可由环境覆盖。 */
export const PLATFORM_WORKSPACE_ROOT_PATH: string =
  env.VITE_PLATFORM_WORKSPACE_ROOT_PATH ?? "/Users/huang/workspace/intelligent-test-agent/frontend-opencode"

export const PLATFORM_WORKSPACE_NAME: string = env.VITE_PLATFORM_WORKSPACE_NAME ?? "frontend-opencode"
