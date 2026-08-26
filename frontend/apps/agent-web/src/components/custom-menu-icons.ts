import type { Component } from "vue";
import {
  BarChart3,
  BookOpen,
  ClipboardCheck,
  Database,
  Gauge,
  Globe2,
  Link2,
  Network
} from "lucide-vue-next";
import type { CustomMenuIconKey } from "./custom-menus";

const CUSTOM_MENU_ICON_COMPONENTS: Record<CustomMenuIconKey, Component> = {
  globe: Globe2,
  link: Link2,
  book: BookOpen,
  chart: BarChart3,
  database: Database,
  checklist: ClipboardCheck,
  gauge: Gauge,
  network: Network
};

export function customMenuIconComponent(icon: CustomMenuIconKey): Component {
  return CUSTOM_MENU_ICON_COMPONENTS[icon] ?? Globe2;
}
