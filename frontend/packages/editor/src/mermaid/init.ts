let mermaidInstance: any = null;
let mermaidLoadPromise: Promise<void> | null = null;

/**
 * 懒加载并初始化 Mermaid 与 ELK 布局引擎，全局复用单例实例。
 */
export async function ensureMermaid(): Promise<any> {
  if (!mermaidInstance) {
    if (!mermaidLoadPromise) {
      mermaidLoadPromise = (async () => {
        const [mermaidMod, elkLayouts] = await Promise.all([
          import("mermaid"),
          import("@mermaid-js/layout-elk")
        ]);
        const instance = (mermaidMod as any).default ?? mermaidMod;
        mermaidInstance = (instance.initialize && instance.render) ? instance : (instance.default ?? instance);
        const loaders = (elkLayouts as any).default ?? elkLayouts;
        if (mermaidInstance.registerLayoutLoaders && loaders) {
          mermaidInstance.registerLayoutLoaders(loaders);
        }
        mermaidInstance.initialize({
          startOnLoad: false,
          theme: "neutral",
          securityLevel: "loose",
          layout: "elk"
        });
      })();
    }
    await mermaidLoadPromise;
  }
  return mermaidInstance;
}
