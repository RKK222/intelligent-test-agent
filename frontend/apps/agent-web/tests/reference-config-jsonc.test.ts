import { describe, expect, it } from "vitest";
import {
  ReferenceConfigValidationError,
  inspectReferenceConfig,
  patchReferenceConfig,
  type ReferenceConfigTarget
} from "../src/components/reference-config-jsonc";

const target: ReferenceConfigTarget = {
  alias: "docs-requirements",
  path: "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
  folder: "docs"
};

describe("reference config JSONC helper", () => {
  it("writes an exact external directory allow when creating a reference", () => {
    const output = patchReferenceConfig("", {
      ...target,
      merge: true,
      sddFolderName: "docs",
      description: "产品需求与接口约束"
    });

    expect(output).toContain('"permission"');
    expect(output).toContain('"external_directory"');
    expect(output).toContain(`"${target.path}/*": "allow"`);
    expect(inspectReferenceConfig(output, target).permissionNeedsUpdate).toBe(false);
  });

  it("creates the minimal schema object and references object for an empty file", () => {
    const output = patchReferenceConfig("", {
      ...target,
      merge: true,
      sddFolderName: "docs",
      description: "  产品需求与接口约束  "
    });

    expect(output).toContain('"$schema": "https://opencode.ai/config.json"');
    expect(output).toContain('"references"');
    expect(output).toContain('"docs-requirements"');
    expect(output).toContain('"path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs"');
    expect(output).toContain('"merge": true');
    expect(output).toContain('"sdd-folder-name": "docs"');
    expect(output).toContain('"description": "产品需求与接口约束"');
    expect(inspectReferenceConfig(output, target)).toMatchObject({
      mode: "update",
      value: { merge: true, sddFolderName: "docs", description: "产品需求与接口约束" }
    });
  });

  it("adds a missing references object without rewriting comments, trailing commas, or other keys", () => {
    const source = `{
  // 模型选择由应用维护
  "model": "provider/model",
  "unknown": { "keep": 1, },
}`;

    const output = patchReferenceConfig(source, {
      ...target,
      merge: false,
      sddFolderName: "docs",
      description: "需求资料"
    });

    expect(output).toContain("// 模型选择由应用维护");
    expect(output).toContain('"unknown"');
    expect(output).toMatch(/"keep": 1,\s*}/);
    expect(output).toContain('"model": "provider/model"');
    expect(inspectReferenceConfig(output, target).value.merge).toBe(false);
  });

  it.each([
    ["invalid JSONC", '{ "references": {'],
    ["root array", "[]"],
    ["root null", "null"],
    ["references array", '{ "references": [] }'],
    ["references string", '{ "references": "docs" }']
  ])("rejects %s", (_label, source) => {
    expect(() => inspectReferenceConfig(source, target)).toThrow(ReferenceConfigValidationError);
  });

  it("reports jsonc-parser parse details instead of treating malformed content as empty", () => {
    try {
      inspectReferenceConfig('{ "references": {', target);
      throw new Error("expected parse failure");
    } catch (error) {
      expect(error).toBeInstanceOf(ReferenceConfigValidationError);
      expect((error as ReferenceConfigValidationError).code).toBe("INVALID_JSONC");
      expect((error as Error).message).toMatch(/JSONC.*offset/i);
    }
  });

  it.each([
    ["string shorthand", '"../docs"'],
    ["number", "42"],
    ["array", "[]"],
    ["git reference", '{ "repository": "owner/repo", "branch": "main" }']
  ])("does not overwrite an existing alias represented as %s", (_label, value) => {
    const source = `{ "references": { "${target.alias}": ${value} } }`;

    expect(() => patchReferenceConfig(source, {
      ...target,
      merge: true,
      sddFolderName: "docs",
      description: "需求资料"
    })).toThrow(ReferenceConfigValidationError);
  });

  it("does not overwrite the same alias when its local path conflicts", () => {
    const source = `{
  "references": {
    "docs-requirements": { "path": "../someone-else", "description": "保留" }
  }
}`;

    expect(() => inspectReferenceConfig(source, target)).toThrowError(
      expect.objectContaining({ code: "PATH_CONFLICT" })
    );
    expect(() => patchReferenceConfig(source, {
      ...target,
      merge: true,
      sddFolderName: "docs",
      description: "新描述"
    })).toThrowError(expect.objectContaining({ code: "PATH_CONFLICT" }));
  });

  it("normalizes an existing sdd-folder-name to the selected folder and exposes the persisted value as dirty baseline", () => {
    const source = `{
  "references": {
    "docs-requirements": {
      "path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
      "merge": false,
      "sdd-folder-name": "requirements-docs",
      "description": "现有说明",
      "hidden": true
    }
  }
}`;

    expect(inspectReferenceConfig(source, target)).toEqual({
      mode: "update",
      value: {
        path: target.path,
        merge: false,
        sddFolderName: "docs",
        description: "现有说明"
      },
      baseline: {
        path: target.path,
        merge: false,
        sddFolderName: "requirements-docs",
        description: "现有说明"
      },
      permissionNeedsUpdate: true
    });
    expect(inspectReferenceConfig(`{
      "references": {
        "docs-requirements": { "path": "${target.path}" }
      }
    }`, target)).toMatchObject({
      value: { merge: true, sddFolderName: "docs", description: "" },
      baseline: { merge: true, sddFolderName: "", description: "" }
    });

    const output = patchReferenceConfig(source, {
      ...target,
      merge: false,
      sddFolderName: "不得写入",
      description: "现有说明"
    });
    expect(output).toContain('"sdd-folder-name": "docs"');
    expect(output).not.toContain("不得写入");
  });

  it("updates only four managed fields and preserves comments, hidden, unknown fields, and trailing commas", () => {
    const source = `{
  "$schema": "https://opencode.ai/config.json",
  "references": {
    "docs-requirements": {
      // 路径旁注必须保留
      "path": "{env:OPENCODE_REFERENCES_DIR}/requirements/docs",
      "merge": true,
      "sdd-folder-name": "docs",
      "description": "旧说明",
      "hidden": true,
      "custom": { "owner": "qa", },
    },
    "other": "../other",
  },
  "plugin": ["keep"],
}`;

    const output = patchReferenceConfig(source, {
      ...target,
      merge: false,
      sddFolderName: "docs",
      description: "  新说明  "
    });

    expect(output).toContain("// 路径旁注必须保留");
    expect(output).toContain('"hidden": true');
    expect(output).toContain('"custom": { "owner": "qa", }');
    expect(output).toContain('"other": "../other"');
    expect(output).toMatch(/"plugin":\s*\[\s*"keep"\s*\]/);
    expect(output).toContain('"merge": false');
    expect(output).toContain('"description": "新说明"');
    expect(inspectReferenceConfig(output, target).value).toEqual({
      path: target.path,
      merge: false,
      sddFolderName: "docs",
      description: "新说明"
    });
  });

  it("marks a missing permission on an existing reference as drift and repairs it", () => {
    const source = `{
  "references": {
    "docs-requirements": {
      "path": "${target.path}",
      "merge": true,
      "sdd-folder-name": "docs",
      "description": "现有说明"
    }
  }
}`;

    expect(inspectReferenceConfig(source, target).permissionNeedsUpdate).toBe(true);

    const output = patchReferenceConfig(source, {
      ...target,
      merge: true,
      sddFolderName: "docs",
      description: "现有说明"
    });

    expect(inspectReferenceConfig(output, target).permissionNeedsUpdate).toBe(false);
    expect(output).toContain(`"${target.path}/*": "allow"`);
  });

  it.each(["ask", "deny"])("overrides an exact %s permission with allow", (action) => {
    const source = `{
  "permission": {
    "external_directory": {
      "${target.path}/*": "${action}"
    }
  }
}`;

    expect(inspectReferenceConfig(source, target).permissionNeedsUpdate).toBe(true);

    const output = patchReferenceConfig(source, {
      ...target,
      merge: true,
      sddFolderName: "docs",
      description: "需求资料"
    });

    expect(output).toContain(`"${target.path}/*": "allow"`);
    expect(inspectReferenceConfig(output, target).permissionNeedsUpdate).toBe(false);
  });

  it("moves the exact allow after a later broad matching rule so allow wins", () => {
    const exactRule = `"${target.path}/*": "allow"`;
    const source = `{
  "permission": {
    "external_directory": {
      ${exactRule},
      "{env:OPENCODE_REFERENCES_DIR}/requirements/*": "deny",
      "/unrelated/*": "ask"
    }
  }
}`;

    expect(inspectReferenceConfig(source, target).permissionNeedsUpdate).toBe(true);

    const output = patchReferenceConfig(source, {
      ...target,
      merge: true,
      sddFolderName: "docs",
      description: "需求资料"
    });

    expect(output.indexOf(exactRule)).toBeGreaterThan(
      output.indexOf('"{env:OPENCODE_REFERENCES_DIR}/requirements/*": "deny"')
    );
    expect(output).toContain('"/unrelated/*": "ask"');
    expect(inspectReferenceConfig(output, target).permissionNeedsUpdate).toBe(false);
  });

  it("preserves comments when moving an exact rule behind a broad rule", () => {
    const source = `{
  "permission": {
    "external_directory": {
      // 精确规则备注
      "${target.path}/*": "allow",
      // 宽泛规则备注
      "*": "deny"
    }
  }
}`;

    const output = patchReferenceConfig(source, {
      ...target,
      merge: true,
      sddFolderName: "docs",
      description: "需求资料"
    });

    expect(output).toContain("// 精确规则备注");
    expect(output).toContain("// 宽泛规则备注");
    expect(inspectReferenceConfig(output, target).permissionNeedsUpdate).toBe(false);
  });

  it("does not report drift when only unrelated rules follow the exact allow", () => {
    const source = `{
  "permission": {
    "external_directory": {
      "${target.path}/*": "allow",
      "/unrelated/*": "deny"
    }
  }
}`;

    expect(inspectReferenceConfig(source, target).permissionNeedsUpdate).toBe(false);
  });

  it.each(["allow", "ask", "deny"])(
    "expands a root permission %s shorthand and preserves it as the root fallback",
    (action) => {
      const source = `{
  // 原权限兜底必须保留
  "permission": "${action}",
  "unknown": true
}`;

      const output = patchReferenceConfig(source, {
        ...target,
        merge: true,
        sddFolderName: "docs",
        description: "需求资料"
      });

      expect(output).toContain("// 原权限兜底必须保留");
      expect(output).toContain('"*": "' + action + '"');
      expect(output).toContain(`"${target.path}/*": "allow"`);
      expect(output).toContain('"unknown": true');
      expect(inspectReferenceConfig(output, target).permissionNeedsUpdate).toBe(false);
    }
  );

  it.each(["allow", "ask", "deny"])(
    "expands an external_directory %s shorthand and preserves it as the nested fallback",
    (action) => {
      const source = `{
  "permission": {
    // 外部目录兜底必须保留
    "external_directory": "${action}",
    "read": "ask"
  }
}`;

      const output = patchReferenceConfig(source, {
        ...target,
        merge: true,
        sddFolderName: "docs",
        description: "需求资料"
      });

      expect(output).toContain("// 外部目录兜底必须保留");
      expect(output).toContain('"*": "' + action + '"');
      expect(output).toContain(`"${target.path}/*": "allow"`);
      expect(output).toContain('"read": "ask"');
      expect(inspectReferenceConfig(output, target).permissionNeedsUpdate).toBe(false);
    }
  );

  it.each([
    ["permission array", '{ "permission": [] }', "INVALID_PERMISSION"],
    ["permission number", '{ "permission": 42 }', "INVALID_PERMISSION"],
    ["permission null", '{ "permission": null }', "INVALID_PERMISSION"],
    ["permission action", '{ "permission": "sometimes" }', "INVALID_PERMISSION"],
    [
      "external_directory array",
      '{ "permission": { "external_directory": [] } }',
      "INVALID_EXTERNAL_DIRECTORY_PERMISSION"
    ],
    [
      "external_directory number",
      '{ "permission": { "external_directory": 42 } }',
      "INVALID_EXTERNAL_DIRECTORY_PERMISSION"
    ],
    [
      "external_directory action",
      '{ "permission": { "external_directory": "sometimes" } }',
      "INVALID_EXTERNAL_DIRECTORY_PERMISSION"
    ],
    [
      "external_directory rule value",
      '{ "permission": { "external_directory": { "*": 42 } } }',
      "INVALID_EXTERNAL_DIRECTORY_PERMISSION"
    ]
  ])("rejects an invalid %s structure before patching", (_label, source, code) => {
    expect(() => inspectReferenceConfig(source, target)).toThrowError(
      expect.objectContaining({ code })
    );
    expect(() => patchReferenceConfig(source, {
      ...target,
      merge: true,
      sddFolderName: "docs",
      description: "需求资料"
    })).toThrowError(expect.objectContaining({ code }));
  });

  it("preserves permission comments, unknown rules, trailing commas, and CRLF", () => {
    const source = [
      "{",
      '  "permission": {',
      '    "external_directory": {',
      "      // 人工规则必须保留",
      '      "/manual/*": "ask",',
      "    },",
      '    "custom_permission": { "keep": true, },',
      "  },",
      '  "unknown": { "keep": true, },',
      "}"
    ].join("\r\n");

    const output = patchReferenceConfig(source, {
      ...target,
      merge: true,
      sddFolderName: "docs",
      description: "需求资料"
    });

    expect(output).toContain("// 人工规则必须保留");
    expect(output).toContain('"/manual/*": "ask"');
    expect(output).toContain('"custom_permission": { "keep": true, }');
    expect(output).toMatch(/"unknown":\s*{\s*"keep": true,\s*}/);
    expect(output).not.toMatch(/(?<!\r)\n/);
  });

  it("is idempotent and never duplicates the managed permission rule", () => {
    const patch = {
      ...target,
      merge: false,
      sddFolderName: "docs",
      description: "需求资料"
    };
    const first = patchReferenceConfig("", patch);
    const second = patchReferenceConfig(first, patch);

    expect(second).toBe(first);
    expect(second.match(new RegExp(target.path.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "g"))).toHaveLength(2);
  });
});
