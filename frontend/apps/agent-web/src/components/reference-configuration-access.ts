export type ReferenceConfigurationAccessContext = {
  roles: readonly string[] | undefined;
  personalWorkspaceId: string | undefined;
  runtimeWorkspaceId: string | undefined;
  appId: string | undefined;
};

/** 应用成员可只读查看自动化引用；资产库和自动化变更仍由弹窗及后端按管理员权限收口。 */
export function canShowReferenceConfiguration(context: ReferenceConfigurationAccessContext) {
  if (!context.appId || !context.personalWorkspaceId || !context.runtimeWorkspaceId) return false;
  return (context.roles?.length ?? 0) > 0;
}
