import { defineComponent, h, inject, type Slots } from "vue";

export type PanelParams = {
  slot: "left" | "center" | "right" | "bottom";
};

// dockview-vue 把面板 props 包成 { params: { params: PanelParams, api, containerApi, tabLocation } }，
// 用户 params 在 props.params.params 上。
type DockviewPanelProps = {
  params?: PanelParams;
  api?: unknown;
  containerApi?: unknown;
  tabLocation?: unknown;
};

// Dockview 面板渲染器：根据 params.slot 渲染 WorkbenchShell 对应插槽内容
export const DockPanel = defineComponent({
  name: "DockPanel",
  props: {
    params: { type: Object as () => DockviewPanelProps, required: false, default: null }
  },
  setup(props) {
    const slots = inject<Slots | undefined>("workbench-slots");
    return () => {
      // 兼容 dockview-vue 的双层嵌套：用户 params 在 props.params.params
      const userParams = (props.params?.params ?? props.params) as PanelParams | undefined;
      const fn = userParams ? slots?.[userParams.slot] : undefined;
      return h("div", { class: "h-full min-h-0 overflow-hidden" }, fn?.());
    };
  }
});
