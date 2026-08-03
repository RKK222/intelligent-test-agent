import type { ProxyOptions } from "vite";

/**
 * 本地开发仍保持浏览器同源访问，由 Vite 把工作流前缀直接转给 Python，禁止落入 SPA fallback 或 Java。
 */
export function createWorkflowDevProxyOptions(
  target: string,
): Record<string, ProxyOptions> {
  return {
    "^/workflow-api(?:/|$)": {
      target,
      changeOrigin: true,
    },
  };
}
