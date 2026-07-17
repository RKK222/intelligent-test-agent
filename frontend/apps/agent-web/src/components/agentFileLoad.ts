/** Agent 配置文件加载请求只携带路由与交互语义，正文统一由工作台父层读取。 */
export type AgentFileLoadRequest = {
  scope: "PUBLIC" | "WORKSPACE";
  path: string;
  workspaceId?: string;
  worktreeId?: string | null;
  linuxServerId?: string | null;
  readonly: boolean;
  activate: boolean;
  closeOnNotFound: boolean;
};
