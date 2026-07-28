import { describe, expect, it } from "vitest";
import {
  createToolboxDevProxyOptions,
  toolboxSuiteRootRedirect
} from "../toolbox-dev-proxy";

describe("toolbox dev proxy", () => {
  it("proxies both suites to their local containers and strips the public prefix", () => {
    const proxies = createToolboxDevProxyOptions({
      itToolsTarget: "http://127.0.0.1:18120",
      omniToolsTarget: "http://127.0.0.1:18121"
    });
    const itTools = proxies["^/toolbox/apps/it-tools(?:/|$)"];
    const omniTools = proxies["^/toolbox/apps/omni-tools(?:/|$)"];

    expect(itTools).toMatchObject({ target: "http://127.0.0.1:18120", changeOrigin: true });
    expect(omniTools).toMatchObject({ target: "http://127.0.0.1:18121", changeOrigin: true });
    expect(typeof itTools).toBe("object");
    expect(typeof omniTools).toBe("object");
    if (typeof itTools !== "object" || typeof omniTools !== "object") return;

    expect(itTools.rewrite?.("/toolbox/apps/it-tools/token-generator?length=32")).toBe(
      "/token-generator?length=32"
    );
    expect(omniTools.rewrite?.("/toolbox/apps/omni-tools/audio/change-speed")).toBe(
      "/audio/change-speed"
    );
  });

  it("redirects only suite roots back to the platform toolbox", () => {
    expect(toolboxSuiteRootRedirect("/toolbox/apps/it-tools?from=test")).toBe("/toolbox");
    expect(toolboxSuiteRootRedirect("/toolbox/apps/omni-tools")).toBe("/toolbox");
    expect(toolboxSuiteRootRedirect("/toolbox/apps/it-tools/token-generator")).toBeNull();
  });
});
