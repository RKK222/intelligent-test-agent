import { describe, expect, it } from "vitest";
import { filterLoadedFiles } from "../src";

describe("filterLoadedFiles", () => {
  it("filters only loaded file entries by file name or path", () => {
    const result = filterLoadedFiles(
      {
        "": [
          { type: "directory", path: "tests", name: "tests" },
          { type: "file", path: "package.json", name: "package.json" }
        ],
        tests: [{ type: "file", path: "tests/checkout.spec.ts", name: "checkout.spec.ts" }]
      },
      "checkout"
    );

    expect(result).toEqual([{ type: "file", path: "tests/checkout.spec.ts", name: "checkout.spec.ts" }]);
  });
});
