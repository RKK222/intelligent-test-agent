export type ReferenceConfigurationAccessContext = {
  roles: readonly string[] | undefined;
  personalWorkspaceId: string | undefined;
  runtimeWorkspaceId: string | undefined;
  appId: string | undefined;
};

/** APP_ADMIN 能力包含 SUPER_ADMIN；入口还必须拥有完整应用、个人工作区和实际运行时工作区上下文。 */
export function canShowReferenceConfiguration(context: ReferenceConfigurationAccessContext) {
  if (!context.appId || !context.personalWorkspaceId || !context.runtimeWorkspaceId) return false;
  return context.roles?.some((role) => role === "APP_ADMIN" || role === "SUPER_ADMIN") === true;
}
