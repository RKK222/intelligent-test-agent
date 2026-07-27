export type WorkbenchCenterMode = "editor" | "diff" | "system" | "hub" | "toolbox";
export type NonToolboxCenterMode = Exclude<WorkbenchCenterMode, "toolbox">;

export type ImmersivePanelSnapshot = {
  leftOpen: boolean;
  rightOpen: boolean;
  bottomOpen: boolean;
  savedLeftOpen: boolean;
  savedRightOpen: boolean;
  savedBottomOpen: boolean;
};

export function isImmersiveCenterMode(mode: WorkbenchCenterMode): boolean {
  return mode === "system" || mode === "hub" || mode === "toolbox";
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

/** 将命名路由状态映射到中心视图，并保留进入工具盒子前的中心模式供后退恢复。 */
export function routeCenterTransition(
  isToolboxRoute: boolean,
  currentMode: WorkbenchCenterMode,
  beforeToolbox: NonToolboxCenterMode
): { mode: WorkbenchCenterMode; beforeToolbox: NonToolboxCenterMode } {
  if (isToolboxRoute) {
    return {
      mode: "toolbox",
      beforeToolbox: currentMode === "toolbox" ? beforeToolbox : currentMode
    };
  }
  if (currentMode === "toolbox") {
    return { mode: beforeToolbox, beforeToolbox };
  }
  return { mode: currentMode, beforeToolbox };
}
