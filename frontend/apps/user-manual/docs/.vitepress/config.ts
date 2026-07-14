import { defineConfig } from "vitepress";

/**
 * 手册以纯静态资源嵌入 agent-web，搜索索引必须在浏览器本地生成，不能依赖公网服务。
 */
export default defineConfig({
  lang: "zh-CN",
  title: "MIMO 测试智能体用户手册",
  description: "MIMO 测试智能体内置操作手册",
  base: "/help/",
  outDir: "../../agent-web/public/help",
  cleanUrls: false,
  lastUpdated: true,
  themeConfig: {
    logo: false,
    siteTitle: "MIMO 用户手册",
    search: {
      provider: "local",
      options: {
        translations: {
          button: {
            buttonText: "搜索手册",
            buttonAriaLabel: "搜索手册"
          },
          modal: {
            noResultsText: "没有找到相关说明",
            resetButtonTitle: "清除查询",
            footer: {
              selectText: "选择",
              navigateText: "切换",
              closeText: "关闭"
            }
          }
        }
      }
    },
    nav: [
      { text: "开始使用", link: "/guide/getting-started" },
      { text: "常见问题", link: "/guide/faq" }
    ],
    sidebar: [
      {
        text: "使用指南",
        items: [
          { text: "快速开始", link: "/guide/getting-started" },
          { text: "首次使用前准备", link: "/guide/first-time-setup" },
          { text: "初始化 TestAgent 进程", link: "/guide/process-initialization" },
          { text: "应用版本与个人工作区", link: "/guide/workspace" },
          { text: "开发与测试目录设计", link: "/guide/directory-mapping" },
          { text: "对话与上下文", link: "/guide/conversation" },
          { text: "Agent 与 Skill 配置", link: "/guide/agent-config" },
          { text: "常见问题", link: "/guide/faq" }
        ]
      }
    ],
    outline: {
      level: [2, 3],
      label: "本页内容"
    },
    docFooter: {
      prev: "上一页",
      next: "下一页"
    },
    lastUpdated: {
      text: "最后更新"
    }
  }
});
