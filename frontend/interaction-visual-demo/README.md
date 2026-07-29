# interaction-visual-demo

## 工程定位

独立、无构建依赖的前端视觉参考，不纳入 `pnpm-workspace.yaml`，也不进入生产构建。用于在修改正式 Vue 组件前验证页面结构、配色和信息密度。

## 预览文件

- `cloud-workbench.html`：严格保留当前工作台的 36px 顶部栏、48px 活动栏、262px 左栏、中间编辑区和 450px Agent 对话区布局，仅替换页面底色、顶栏、活动栏、左侧外壳、选中态与外层分隔线。中间编辑器和对话样式使用固定 token，不参与主题切换。默认展示工行红推荐方案，并可切换纯雪白和鼠尾草灰进行对照。

## 本地预览

从仓库根目录执行：

```bash
python3 -m http.server 4173 --directory frontend/interaction-visual-demo
```

浏览器打开 `http://127.0.0.1:4173/cloud-workbench.html`。

该页面中的导航、主题切换和消息发送仅用于视觉反馈，不调用后端接口，也不写入浏览器存储。
