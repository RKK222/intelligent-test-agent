// Vite ?worker 导入的类型声明（monaco-editor Web Worker）
declare module "*?worker" {
  const workerConstructor: { new (): Worker };
  export default workerConstructor;
}
