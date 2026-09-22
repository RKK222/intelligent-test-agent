import { inject, onScopeDispose, shallowRef, type ShallowRef } from "vue";
import {
  teamManagementKey,
  type TeamManagementController,
  type TeamManagementState
} from "./team-management-controller";

/** 左栏、编辑区和审阅栏订阅同一份管理视角快照。 */
export function useTeamManagementView(): {
  controller: TeamManagementController;
  state: ShallowRef<TeamManagementState>;
} {
  const controller = inject(teamManagementKey);
  if (!controller) {
    throw new Error("管理视角尚未挂入工作台");
  }
  const state: ShallowRef<TeamManagementState> = shallowRef(controller.snapshot());
  const stop = controller.subscribe((next) => {
    state.value = next;
  });
  onScopeDispose(stop);
  return { controller, state };
}
