import { describe, expect, it } from "vitest";
import { parseUnifiedPatch } from "../src";

describe("parseUnifiedPatch", () => {
  it("separates deleted and added lines for monaco diff", () => {
    const parsed = parseUnifiedPatch("@@ -1 +1 @@\n-old\n+new\n context");

    expect(parsed.original).toBe("old\ncontext");
    expect(parsed.modified).toBe("new\ncontext");
  });
});
