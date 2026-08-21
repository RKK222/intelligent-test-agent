/// <reference path="../simple-mind-map.d.ts" />

import type { SimpleMindMapTreeData } from "./simple-mind-map-adapter";

export interface SimpleMindMapNodeInstance {
  isRoot: boolean;
  layerIndex: number;
  getData(key?: string): unknown;
}

export interface SimpleMindMapInstance {
  renderer: {
    activeNodeList: SimpleMindMapNodeInstance[];
  };
  view: {
    enlarge(): void;
    narrow(): void;
    fit(): void;
  };
  on(event: string, handler: (...args: unknown[]) => void): void;
  off(event: string, handler?: (...args: unknown[]) => void): void;
  execCommand(command: string, ...args: unknown[]): void;
  getData(): SimpleMindMapTreeData;
  setData(data: SimpleMindMapTreeData): void;
  setMode(mode: "readonly" | "edit"): void;
  resize(): void;
  destroy(): void;
}

export interface SimpleMindMapConstructor {
  usePlugin(plugin: unknown): SimpleMindMapConstructor;
  new(options: Record<string, unknown>): SimpleMindMapInstance;
}

export interface SimpleMindMapRuntime {
  MindMap: SimpleMindMapConstructor;
}

let runtimePromise: Promise<SimpleMindMapRuntime> | undefined;

/** core 与两个必要插件共享同一懒加载 Promise，失败后允许下一次打开重试。 */
export function loadSimpleMindMapRuntime(): Promise<SimpleMindMapRuntime> {
  if (!runtimePromise) {
    runtimePromise = Promise.all([
      import("simple-mind-map"),
      import("simple-mind-map/src/plugins/Drag.js"),
      import("simple-mind-map/src/plugins/KeyboardNavigation.js")
    ]).then(([core, drag, keyboard]) => {
      const MindMap = core.default as unknown as SimpleMindMapConstructor;
      MindMap.usePlugin(drag.default).usePlugin(keyboard.default);
      return { MindMap };
    }).catch((error) => {
      runtimePromise = undefined;
      throw error;
    });
  }
  return runtimePromise;
}
