import { describe, expect, it } from "vitest";
import {
  isReleaseFeaturePathEnabled,
  resolveReleaseFeatureFlags
} from "../src/release-features";

describe("release feature gates", () => {
  it("fails closed unless a feature is explicitly true", () => {
    expect(resolveReleaseFeatureFlags({} as ImportMetaEnv)).toEqual({
      lobehub: false,
      workflow: false
    });
    expect(resolveReleaseFeatureFlags({
      VITE_TEST_AGENT_LOBEHUB_ENABLED: " TRUE ",
      VITE_TEST_AGENT_WORKFLOW_ENABLED: "1"
    } as ImportMetaEnv)).toEqual({
      lobehub: true,
      workflow: false
    });
  });

  it("blocks only the disabled optional routes", () => {
    const disabled = { lobehub: false, workflow: false };
    expect(isReleaseFeaturePathEnabled("/lobehub/launch", disabled)).toBe(false);
    expect(isReleaseFeaturePathEnabled("/workflow-chat", disabled)).toBe(false);
    expect(isReleaseFeaturePathEnabled("/toolbox", disabled)).toBe(true);
  });
});
