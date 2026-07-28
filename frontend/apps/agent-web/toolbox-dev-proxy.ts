import type { Plugin, ProxyOptions } from "vite";

const IT_TOOLS_PREFIX = "/toolbox/apps/it-tools";
const OMNI_TOOLS_PREFIX = "/toolbox/apps/omni-tools";
const TOOLBOX_SUITE_ROOTS = new Set([IT_TOOLS_PREFIX, OMNI_TOOLS_PREFIX]);

interface ToolboxDevProxyTargets {
  itToolsTarget: string;
  omniToolsTarget: string;
}

/** 开发服务器只剥离公开前缀，派生应用仍以平台子路径生成浏览器资源地址。 */
const createSuiteProxy = (prefix: string, target: string): ProxyOptions => ({
  target,
  changeOrigin: true,
  rewrite(path) {
    const rewritten = path.slice(prefix.length);
    return rewritten === "" || rewritten.startsWith("?") ? `/${rewritten}` : rewritten;
  }
});

/** 生成 Vite 本地代理；生产环境继续使用受控 Nginx 配置。 */
export const createToolboxDevProxyOptions = ({
  itToolsTarget,
  omniToolsTarget
}: ToolboxDevProxyTargets): Record<string, ProxyOptions> => ({
  "^/toolbox/apps/it-tools(?:/|$)": createSuiteProxy(IT_TOOLS_PREFIX, itToolsTarget),
  "^/toolbox/apps/omni-tools(?:/|$)": createSuiteProxy(OMNI_TOOLS_PREFIX, omniToolsTarget)
});

/** 套件根路径不得展示上游门户，查询参数不影响根路径判定。 */
export const toolboxSuiteRootRedirect = (requestUrl: string | undefined): string | null => {
  const pathname = requestUrl?.split("?", 1)[0];
  return pathname && TOOLBOX_SUITE_ROOTS.has(pathname) ? "/toolbox" : null;
};

/** 在 Vite 内部代理前拦截套件根路径，与生产 Nginx 的 308 行为保持一致。 */
export const toolboxSuiteRootGuard = (): Plugin => ({
  name: "test-agent-toolbox-suite-root-guard",
  configureServer(server) {
    server.middlewares.use((request, response, next) => {
      const location = toolboxSuiteRootRedirect(request.url);
      if (!location) {
        next();
        return;
      }
      response.statusCode = 308;
      response.setHeader("Location", location);
      response.end();
    });
  }
});
