import { describe, expect, it } from "vitest";
import {
  isReleaseFeaturePathEnabled,
  resolveReleaseFeatureFlags
} from "../src/release-features";

describe("release feature gates", () => {
  it("fails closed unless a feature is explicitly true", () => {
    expect(resolveReleaseFeatureFlags({} as ImportMetaEnv)).toEqual({
      lobehub: false
    });
    expect(resolveReleaseFeatureFlags({
      VITE_TEST_AGENT_LOBEHUB_ENABLED: " TRUE "
    } as ImportMetaEnv)).toEqual({
      lobehub: true
    });
  });

  it("blocks only the disabled optional routes", () => {
    const disabled = { lobehub: false };
    expect(isReleaseFeaturePathEnabled("/lobehub/launch", disabled)).toBe(false);
    expect(isReleaseFeaturePathEnabled("/toolbox", disabled)).toBe(true);
  });
});
