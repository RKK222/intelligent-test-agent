export type WorkbenchCenterMode = "editor" | "diff" | "system" | "hub" | "toolbox" | "memories";
export type RoutedCenterMode = Extract<WorkbenchCenterMode, "system" | "hub" | "toolbox" | "memories">;
export type NonRoutedCenterMode = Exclude<WorkbenchCenterMode, RoutedCenterMode>;

const ROUTED_CENTER_MODES: readonly RoutedCenterMode[] = ["system", "hub", "toolbox", "memories"];

/** 活动栏沉浸式页面使用同名路由，集中校验避免组件内散落字符串分支。 */
export function routedCenterModeFromRouteName(routeName: unknown): RoutedCenterMode | null {
  return typeof routeName === "string" && ROUTED_CENTER_MODES.includes(routeName as RoutedCenterMode)
    ? routeName as RoutedCenterMode
    : null;
}

export function isRoutedCenterMode(mode: WorkbenchCenterMode): mode is RoutedCenterMode {
  return ROUTED_CENTER_MODES.includes(mode as RoutedCenterMode);
}

export type ImmersivePanelSnapshot = {
  leftOpen: boolean;
  rightOpen: boolean;
  bottomOpen: boolean;
  savedLeftOpen: boolean;
  savedRightOpen: boolean;
  savedBottomOpen: boolean;
};

export function isImmersiveCenterMode(mode: WorkbenchCenterMode): boolean {
  return mode === "system" || mode === "hub" || mode === "toolbox" || mode === "memories";
}

/** 深链接首屏没有 editor -> immersive 的 watch 过渡，需要按当前路由主动建立同一份面板快照。 */
export function initialImmersivePanels(
  state: ImmersivePanelSnapshot,
  routeMode: RoutedCenterMode | null
): ImmersivePanelSnapshot {
  if (!routeMode) return state;
  return {
    leftOpen: false,
    rightOpen: false,
    bottomOpen: false,
    savedLeftOpen: state.leftOpen,
    savedRightOpen: state.rightOpen,
    savedBottomOpen: state.bottomOpen
  };
}

/** 沉浸式中心视图共用一次快照，互相切换时不覆盖用户进入前的面板状态。 */
export function transitionImmersivePanels(
  state: ImmersivePanelSnapshot,
  newMode: WorkbenchCenterMode,
  oldMode: WorkbenchCenterMode
): ImmersivePanelSnapshot {
  const nextImmersive = isImmersiveCenterMode(newMode);
  const previousImmersive = isImmersiveCenterMode(oldMode);
  if (nextImmersive && !previousImmersive) {
    return {
      leftOpen: false,
      rightOpen: false,
      bottomOpen: false,
      savedLeftOpen: state.leftOpen,
      savedRightOpen: state.rightOpen,
      savedBottomOpen: state.bottomOpen
    };
  }
  if (!nextImmersive && previousImmersive) {
    return {
      ...state,
      leftOpen: state.savedLeftOpen,
      rightOpen: state.savedRightOpen,
      bottomOpen: state.savedBottomOpen
    };
  }
  return state;
}

/** 将命名路由状态映射到中心视图，并保留离开全部沉浸式路由后的工作台模式。 */
export function routeCenterTransition(
  routeMode: RoutedCenterMode | null,
  currentMode: WorkbenchCenterMode,
  beforeRoute: NonRoutedCenterMode
): { mode: WorkbenchCenterMode; beforeRoute: NonRoutedCenterMode } {
  if (routeMode) {
    return {
      mode: routeMode,
      beforeRoute: isRoutedCenterMode(currentMode) ? beforeRoute : currentMode
    };
  }
  if (isRoutedCenterMode(currentMode)) {
    return { mode: beforeRoute, beforeRoute };
  }
  return { mode: currentMode, beforeRoute };
}
