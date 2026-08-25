import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import HelpCenterDialog from "../src/components/HelpCenterDialog.vue";
import {
  buildManualQuestionPrompt,
  HELP_TOPICS,
  helpTopicById,
  helpDocumentUrl,
  normalizeHelpTopic,
  stripMarkdownFrontmatter
} from "../src/components/help-center";

// 用户手册只记录工作能力；游戏内容即使已上线，也必须由整本扫描阻止进入。
const userManualDocuments = import.meta.glob("../../user-manual/docs/**/*.md", {
  eager: true,
  import: "default",
  query: "?raw"
}) as Record<string, string>;

const manualOperationImages = import.meta.glob(
  "../../user-manual/docs/guide/images/operations/*.{png,jpg,jpeg,webp}",
  { eager: true, import: "default", query: "?url" }
) as Record<string, string>;

const forbiddenGameContentPatterns = [
  /游戏|游乐舱|桌面弹球|黄金矿工|俄罗斯方块|扫雷|数独|贪吃蛇/,
  /\b(?:game|games|gaming|pinball|tetris|minesweeper|sudoku)\b/i
];

const dialogStub = {
  props: ["modelValue"],
  emits: ["update:modelValue"],
  template: '<section v-if="modelValue"><slot name="header" /><slot /></section>'
};

const buttonStub = {
  props: ["disabled", "loading"],
  emits: ["click"],
  template: '<button type="button" :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>'
};

const inputStub = {
  props: ["modelValue", "disabled"],
  emits: ["update:modelValue"],
  template: '<textarea :value="modelValue" :disabled="disabled" @input="$emit(\'update:modelValue\', $event.target.value)" />'
};

function mountHelpCenter(props: Record<string, unknown> = {}) {
  return mount(HelpCenterDialog, {
    props: {
      open: true,
      initialTopic: "getting-started",
      sideQuestionAvailable: true,
      ...props
    },
    global: {
      stubs: {
        "el-dialog": dialogStub,
        "el-button": buttonStub,
        "el-input": inputStub
      }
    }
  });
}

describe("help center", () => {
  it("keeps manual navigation under the same-origin help base and rejects unknown topics", () => {
    expect(helpDocumentUrl("process-initialization")).toBe("/help/guide/process-initialization.html");
    expect(helpDocumentUrl("settings")).toBe("/help/guide/settings.html");
    expect(helpDocumentUrl("directory-mapping")).toBe("/help/guide/directory-mapping.html");
    expect(helpDocumentUrl("reference-config")).toBe("/help/guide/reference-config.html");
    expect(helpDocumentUrl("memory")).toBe("/help/guide/memory.html");
    expect(helpDocumentUrl("weekly-updates")).toBe("/help/guide/weekly-updates.html");
    expect(normalizeHelpTopic("unknown")).toBe("getting-started");
  });

  it("keeps weekly updates, current features, memory, reference configuration and merged FAQ troubleshooting in embedded Help", async () => {
    const wrapper = mountHelpCenter();
    const topicLabels = wrapper.findAll(".ta-help-center-topic").map((button) => button.text());

    expect(topicLabels.some((label) => label.includes("每周新功能"))).toBe(true);
    expect(topicLabels.some((label) => label.includes("功能总览"))).toBe(true);
    expect(topicLabels.some((label) => label.includes("引用配置"))).toBe(true);
    expect(topicLabels.some((label) => label.includes("长期记忆"))).toBe(true);
    expect(topicLabels.some((label) => label.includes("常见问题与排查"))).toBe(true);
    expect(topicLabels.filter((label) => label.includes("常见问题"))).toHaveLength(1);

    const faqTopic = wrapper.findAll(".ta-help-center-topic")
      .find((button) => button.text().includes("常见问题与排查"));
    await faqTopic!.trigger("click");

    expect(wrapper.get('[data-testid="help-center-frame"]').attributes("src"))
      .toBe("/help/guide/faq.html");
    const prompt = buildManualQuestionPrompt("faq", "为什么输入框不能发送？");
    expect(prompt).toContain("【当前章节】常见问题与排查");
    expect(prompt).toContain("为什么没有主对话");
    expect(prompt).toContain("对话输入框发不出去");
    expect(prompt).toContain("traceId");
    expect(prompt.length).toBeLessThan(6_700);
  });

  it("grounds memory questions in the user-facing governance chapter", () => {
    const prompt = buildManualQuestionPrompt("memory", "为什么没有显示参考了几条记忆？");

    expect(prompt).toContain("【当前章节】长期记忆");
    expect(prompt).toContain("按账号灰度开放");
    expect(prompt).toContain("平台全局记忆配置可用");
    expect(prompt).toContain("记忆灰度");
    expect(prompt).toContain("客户端灰度是另一项独立开关");
    expect(prompt).toContain("参考了 N 条记忆");
    expect(prompt).toContain("团队记忆");
    expect(prompt).toContain("Skill 提案");
    expect(prompt.length).toBeLessThan(3_900);
  });

  it("grounds weekly feature questions in user scenarios and branch-safe boundaries", () => {
    const prompt = buildManualQuestionPrompt("weekly-updates", "这周自动化代码库怎么用？");

    expect(prompt).toContain("【当前章节】每周新功能");
    expect(prompt).toContain("适用场景");
    expect(prompt).toContain("使用前配置");
    expect(prompt).toContain("自动化代码库改为工作区内只读参考");
    expect(prompt).toContain("组合文件树根部会出现虚拟目录“自动化代码库”");
    expect(prompt).toContain("选择应用并进入一个测试工作空间");
    expect(prompt).toContain("主工作空间菜单不再列出自动化仓库");
    expect(prompt).toContain("本地提交，但不提供远程推送或发布");
    expect(prompt).toContain("VITE_CACHE_DATA_URL");
    expect(prompt).not.toMatch(forbiddenGameContentPatterns[0]!);
    expect(prompt).toContain("长期记忆会在新任务中自动复用经验");
    expect(prompt).toContain("本地 OpenCode 客户端（按账号灰度开放）");
    expect(prompt).toContain("客户端灰度");
    expect(prompt).toContain("2026 年 8 月 24 日—8 月 30 日");
    expect(prompt).toContain("从顶部或左下角直接新增版本");
    expect(prompt).toContain("下载本地客户端用户包");
    expect(prompt).toContain("TestAgent-Local-Client");
    expect(prompt).toContain("选择并注册工作区…");
    expect(prompt).toContain("工作空间 → 本地工作区");
    expect(prompt.length).toBeLessThan(8_100);
  });

  it("keeps the SkillMarket upload workflow and its external-material boundary in Help", () => {
    const weekly = helpTopicById("weekly-updates").content;
    const agentConfig = helpTopicById("agent-config").content;

    expect(weekly).toContain("一次提交 SkillMarket 材料并自动刷新目录");
    expect(weekly).toContain("Skill ZIP");
    expect(weekly).toContain("继续查询");
    expect(agentConfig).toContain("向 SkillMarket 上传 Skill");
    expect(agentConfig).toContain("只有超级管理员");
    expect(agentConfig).toContain("不会写入当前应用或个人 worktree");
  });

  it("documents client and memory rollout as separate user-level switches", () => {
    const settings = helpTopicById("settings").content;
    const overview = helpTopicById("feature-overview").content;
    const faq = helpTopicById("faq").content;

    expect(settings).toContain("系统管理 → 用户管理");
    expect(settings).toContain("客户端灰度");
    expect(settings).toContain("记忆灰度");
    expect(settings).toContain("不会互相开启");
    expect(settings).toContain("不停止客户端、不重启本地 OpenCode，也不撤销 client key");
    expect(overview).toContain("平台全局记忆配置可用");
    expect(overview).toContain("本地 OpenCode 客户端");
    expect(overview).toContain("来自 SkillHub 的 Skill 卡片和详情会显示“创建人”");
    expect(faq).toContain("为什么看不到“下载本地客户端”");
    expect(faq).toContain("客户端灰度与记忆灰度相互独立");
    expect(faq).toContain("为什么下载的是压缩包，而不是 DEB 安装包？");
    expect(faq).toContain("客户端注册了本地目录后，怎样在工作台打开？");
    expect(faq).toContain("为什么“新增版本”不可用或创建失败？");
    expect(faq).toContain("为什么看不到“上传 Skill”，或上传后目录还没有出现？");
  });

  it("permanently keeps game content out of every user manual document", () => {
    expect(Object.keys(userManualDocuments).length).toBeGreaterThan(0);
    for (const [documentPath, content] of Object.entries(userManualDocuments)) {
      for (const pattern of forbiddenGameContentPatterns) {
        expect(content, `${documentPath} 不得包含游戏内容：${pattern}`).not.toMatch(pattern);
      }
    }
  });

  it("keeps every embedded manual chapter illustrated with an existing operation image", () => {
    const existingImageSources = new Set(
      Object.keys(manualOperationImages).map((imagePath) =>
        `./images/operations/${imagePath.split("/").at(-1)}`
      )
    );

    for (const topic of HELP_TOPICS) {
      const imageSources = Array.from(
        topic.content.matchAll(/!\[[^\]\r\n]+\]\((\.\/images\/operations\/[^)\s]+\.(?:png|jpe?g|webp))\)/gi),
        (match) => match[1]!
      );
      expect(imageSources.length, `${topic.path} 至少需要一张操作截图`).toBeGreaterThan(0);
      for (const imageSource of imageSources) {
        expect(existingImageSources, `${topic.path} 引用了不存在的截图 ${imageSource}`).toContain(imageSource);
      }
    }
  });

  it("keeps the directory chapter synchronized with the embedded Help navigation", async () => {
    const wrapper = mountHelpCenter();
    const directoryTopic = wrapper.findAll(".ta-help-center-topic")
      .find((button) => button.text().includes("开发与测试目录"));

    expect(directoryTopic).toBeDefined();
    await directoryTopic!.trigger("click");

    expect(wrapper.get('[data-testid="help-center-frame"]').attributes("src"))
      .toBe("/help/guide/directory-mapping.html");
    const prompt = buildManualQuestionPrompt("directory-mapping", "测试目录放什么？");
    expect(prompt).toContain("【当前章节】开发与测试目录");
    expect(prompt).toContain("公共 Git 与应用 Git");
    expect(prompt).toContain("个人 worktree");
    expect(prompt).not.toContain("directoryMapping:");
  });

  it("removes page frontmatter before building the pet manual context", () => {
    expect(stripMarkdownFrontmatter("---\naside: false\ndata:\n  value: 1\n---\n# 正文")).toBe("# 正文");
    expect(stripMarkdownFrontmatter("# 无 frontmatter")).toBe("# 无 frontmatter");
  });

  it("opens the requested chapter and switches the embedded manual", async () => {
    const wrapper = mountHelpCenter({ initialTopic: "process-initialization" });

    expect(wrapper.get('[data-testid="help-center-frame"]').attributes("src"))
      .toBe("/help/guide/process-initialization.html");

    const workspaceTopic = wrapper.findAll(".ta-help-center-topic")
      .find((button) => button.text().includes("应用与工作区"));
    expect(workspaceTopic).toBeDefined();
    await workspaceTopic!.trigger("click");

    expect(wrapper.get('[data-testid="help-center-frame"]').attributes("src"))
      .toBe("/help/guide/workspace.html");
  });

  it("grounds pet questions with the active manual chapter", async () => {
    const wrapper = mountHelpCenter({ initialTopic: "process-initialization" });
    const input = wrapper.get('[data-testid="help-center-question-input"]');
    await input.setValue("为什么初始化按钮不能点击？");
    await wrapper.get('[data-testid="help-center-question-submit"]').trigger("click");

    const emittedPrompt = wrapper.emitted("ask-pet")?.[0]?.[0] as string;
    expect(emittedPrompt).toContain("【当前章节】初始化进程");
    expect(emittedPrompt).toContain("分配专属进程");
    expect(emittedPrompt).toContain("为什么初始化按钮不能点击？");
    expect(emittedPrompt.length).toBeLessThan(3_900);
  });

  it("keeps the manual available when workspace runtime is not ready", () => {
    const wrapper = mountHelpCenter({ sideQuestionAvailable: false });

    expect(wrapper.find('[data-testid="help-center-frame"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="help-center-question-input"]').attributes("disabled")).toBeDefined();
    expect(wrapper.text()).toContain("无需建立主对话也能提问");
  });

  it("offers a replay entry for the first-login guide", async () => {
    const wrapper = mountHelpCenter();

    await wrapper.get('[data-testid="help-center-start-guide"]').trigger("click");

    expect(wrapper.emitted("start-guide")).toHaveLength(1);
  });

  it("builds a bounded manual prompt from the single Markdown source", () => {
    const prompt = buildManualQuestionPrompt("workspace", "个人工作区是什么？");
    expect(prompt).toContain("应用版本与个人工作区");
    expect(prompt).toContain("个人工作区是什么？");
    expect(prompt.length).toBeLessThan(3_900);
  });

  it("documents the implemented two-git permissions and personal HEAD publish flow", () => {
    const workspace = helpTopicById("workspace").content;
    const agentConfig = helpTopicById("agent-config").content;

    expect(workspace).toContain("当前平台使用两套物理 Git");
    expect(workspace).toContain("只把允许发布且已进入个人 `HEAD` 的文件");
    expect(workspace).toContain("`spec/**`");
    expect(workspace).toContain("只保留个人提交");
    expect(workspace).toContain("超级管理员也不能绕过该目录限制");
    expect(workspace).toContain("`docs/**`");
    expect(agentConfig).toContain("只有超级管理员可以创建公共 worktree");
    expect(agentConfig).toContain("不再创建独立的“应用配置 worktree”");
    expect(agentConfig).toContain("个人 `HEAD`");
    expect(agentConfig).toContain("`compatibility: opencode`");
    expect(agentConfig).toContain("公共配置推送成功后，平台会广播公共配置同步");
    expect(agentConfig).toContain("推送成功后更新应用版本 HEAD");
  });

  it("grounds settings questions in the role-aware operations chapter", () => {
    const settings = helpTopicById("settings").content;

    expect(settings).toContain("设置面板怎么用");
    expect(settings).toContain("普通用户：个人设置");
    expect(settings).toContain("用户配置");
    expect(settings).toContain("版本库配置");
    expect(settings).toContain("应用工作区配置");
    expect(settings).toContain("应用人员管理");
    expect(settings).toContain("应用与版本库关联");
    expect(settings).toContain("工作空间管理");
    expect(settings).toContain("08“版本库管理”、09“应用人员管理”、10“应用与版本库关联”、11“工作空间管理”");
    expect(settings).toContain("页面不会把超级管理员专属的用户管理");
  });
});
