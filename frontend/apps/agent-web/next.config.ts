import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  transpilePackages: [
    "@test-agent/agent-chat",
    "@test-agent/backend-api",
    "@test-agent/diff-viewer",
    "@test-agent/editor",
    "@test-agent/event-stream-client",
    "@test-agent/file-explorer",
    "@test-agent/shared-types",
    "@test-agent/test-runner",
    "@test-agent/ui-kit",
    "@test-agent/workbench-shell"
  ]
};

export default nextConfig;
