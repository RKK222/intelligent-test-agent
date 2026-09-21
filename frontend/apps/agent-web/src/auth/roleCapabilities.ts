/** 平台前端唯一的角色能力层级定义，与后端 RoleCapabilities 保持一致。 */
const ROLE_LEVELS: Readonly<Record<string, number>> = Object.freeze({
  USER: 0,
  APP_ADMIN: 1,
  SYSTEM_ADMIN: 2,
  SUPER_ADMIN: 3
});

/** 未知扩展角色只支持精确匹配，避免超级管理员无意继承未声明能力。 */
export function hasRoleCapability(roles: readonly string[] | null | undefined, requiredRole: string): boolean {
  if (!roles) return false;
  if (roles.includes(requiredRole)) return true;
  const requiredLevel = ROLE_LEVELS[requiredRole];
  if (requiredLevel === undefined) return false;
  return roles.some((role) => {
    const level = ROLE_LEVELS[role];
    return level !== undefined && level >= requiredLevel;
  });
}

export const hasAppAdminCapability = (roles: readonly string[] | null | undefined): boolean =>
  hasRoleCapability(roles, "APP_ADMIN");

export const hasSystemAdminCapability = (roles: readonly string[] | null | undefined): boolean =>
  hasRoleCapability(roles, "SYSTEM_ADMIN");

export const hasSuperAdminCapability = (roles: readonly string[] | null | undefined): boolean =>
  hasRoleCapability(roles, "SUPER_ADMIN");
