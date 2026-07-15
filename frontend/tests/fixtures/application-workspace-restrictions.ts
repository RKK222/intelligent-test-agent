/**
 * 应用 worktree / feature 权限回归的固定数据。
 * ID、角色和路径均保持稳定，便于组件测试与浏览器测试复用同一业务语义。
 */
export const applicationWorkspaceRestrictionsFixture = {
  application: {
    appId: "app_workspace_permissions",
    appName: "应用工作区权限样例",
    versionId: "awv_feature_20260715",
    featureWorkspaceId: "wrk_feature_readonly_20260715",
    personalWorkspaceId: "psw_member_default_20260715",
    featureBranch: "feature_testagent_20260715",
    personalBranch: "feature_testagent_20260715_usr_member_default"
  },
  roles: {
    member: ["USER"],
    appAdmin: ["APP_ADMIN"],
    superAdmin: ["SUPER_ADMIN"]
  },
  files: {
    normal: "src/payment/PaymentService.ts",
    docs: "docs/payment/publish-guide.md",
    spec: "spec/payment/design.md",
    applicationAgent: "agents/payment-test.md",
    applicationSkill: "skills/payment-case-design/SKILL.md",
    publicAgent: "agents/public-review.md"
  },
  tree: {
    root: [
      { type: "directory" as const, path: "docs", name: "docs" },
      { type: "file" as const, path: "README.md", name: "README.md" }
    ]
  }
} as const;
