import { describe, expect, it } from "vitest";
import nextConfig from "../next.config";

describe("agent-web next config", () => {
  it("allows 127.0.0.1 as a development origin", () => {
    expect(nextConfig.allowedDevOrigins).toContain("127.0.0.1");
  });
});
