import { describe, expect, it } from "vitest";
import {
  hasAppAdminCapability,
  hasRoleCapability,
  hasSystemAdminCapability,
  hasSuperAdminCapability
} from "../src/auth/roleCapabilities";

describe("role capabilities", () => {
  it("applies the platform role hierarchy", () => {
    expect(hasAppAdminCapability(["SYSTEM_ADMIN"])).toBe(true);
    expect(hasSystemAdminCapability(["SUPER_ADMIN"])).toBe(true);
    expect(hasSystemAdminCapability(["APP_ADMIN"])).toBe(false);
    expect(hasSuperAdminCapability(["SYSTEM_ADMIN"])).toBe(false);
  });

  it("requires an exact match for unknown roles", () => {
    expect(hasRoleCapability(["SUPER_ADMIN"], "CUSTOM")).toBe(false);
    expect(hasRoleCapability(["CUSTOM"], "CUSTOM")).toBe(true);
  });
});
