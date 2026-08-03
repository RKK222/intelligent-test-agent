import { describe, expect, it } from "vitest";
import { createWorkflowDevProxyOptions } from "../workflow-dev-proxy";

describe("workflow dev proxy", () => {
  it("routes the workflow prefix directly to the local Python service", () => {
    const proxies = createWorkflowDevProxyOptions("http://127.0.0.1:8090");

    expect(proxies["^/workflow-api(?:/|$)"]).toMatchObject({
      target: "http://127.0.0.1:8090",
      changeOrigin: true,
    });
  });
});
