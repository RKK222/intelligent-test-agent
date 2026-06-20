// Vite ?worker 导入的类型声明（monaco-editor Web Worker）
declare module "*?worker" {
  const workerConstructor: { new (): Worker };
  export default workerConstructor;
}

// 第三方纯 CSS 样式表（github-markdown-css）的侧载导入声明
declare module "*.css";
