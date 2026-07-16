import agentConfigManual from "../../../user-manual/docs/guide/agent-config.md?raw";
import conversationManual from "../../../user-manual/docs/guide/conversation.md?raw";
import directoryMappingManual from "../../../user-manual/docs/guide/directory-mapping.md?raw";
import faqManual from "../../../user-manual/docs/guide/faq.md?raw";
import firstTimeSetupManual from "../../../user-manual/docs/guide/first-time-setup.md?raw";
import gettingStartedManual from "../../../user-manual/docs/guide/getting-started.md?raw";
import processInitializationManual from "../../../user-manual/docs/guide/process-initialization.md?raw";
import workspaceManual from "../../../user-manual/docs/guide/workspace.md?raw";

export type HelpTopicId =
  | "getting-started"
  | "first-time-setup"
  | "process-initialization"
  | "workspace"
  | "directory-mapping"
  | "conversation"
  | "agent-config"
  | "faq";

export type HelpTopic = {
  id: HelpTopicId;
  label: string;
  description: string;
  path: string;
  content: string;
};

export const DEFAULT_HELP_TOPIC: HelpTopicId = "getting-started";

/**
 * 手册导航与问答共用同一份 Markdown，避免页面说明和宠物回答使用两套事实来源。
 */
export const HELP_TOPICS: HelpTopic[] = [
  {
    id: "getting-started",
    label: "快速开始",
    description: "首次使用的完整顺序",
    path: "guide/getting-started.html",
    content: gettingStartedManual
  },
  {
    id: "process-initialization",
    label: "初始化进程",
    description: "分配、启动与失败处理",
    path: "guide/process-initialization.html",
    content: processInitializationManual
  },
  {
    id: "first-time-setup",
    label: "首次使用前准备",
    description: "角色、SSH、应用与工作空间",
    path: "guide/first-time-setup.html",
    content: firstTimeSetupManual
  },
  {
    id: "workspace",
    label: "应用与工作区",
    description: "版本、个人 worktree 与 Git",
    path: "guide/workspace.html",
    content: workspaceManual
  },
  {
    id: "directory-mapping",
    label: "开发与测试目录",
    description: "双 Git、目录权限与发布边界",
    path: "guide/directory-mapping.html",
    content: directoryMappingManual
  },
  {
    id: "conversation",
    label: "对话与上下文",
    description: "@、#、附件与旁路问答",
    path: "guide/conversation.html",
    content: conversationManual
  },
  {
    id: "agent-config",
    label: "Agent 配置",
    description: "公共与应用配置边界",
    path: "guide/agent-config.html",
    content: agentConfigManual
  },
  {
    id: "faq",
    label: "常见问题",
    description: "高频疑问快速解答",
    path: "guide/faq.html",
    content: faqManual
  }
];

export function normalizeHelpTopic(topic?: string | null): HelpTopicId {
  return HELP_TOPICS.some((candidate) => candidate.id === topic)
    ? topic as HelpTopicId
    : DEFAULT_HELP_TOPIC;
}

export function helpTopicById(topic?: string | null): HelpTopic {
  const normalized = normalizeHelpTopic(topic);
  return HELP_TOPICS.find((candidate) => candidate.id === normalized) ?? HELP_TOPICS[0]!;
}

/**
 * BASE_URL 可能是根路径或企业部署子路径，手册必须始终与主应用同源加载。
 */
export function helpDocumentUrl(topic?: string | null): string {
  const base = import.meta.env.BASE_URL.endsWith("/")
    ? import.meta.env.BASE_URL
    : `${import.meta.env.BASE_URL}/`;
  return `${base}help/${helpTopicById(topic).path}`;
}

/**
 * Markdown frontmatter 只服务于页面渲染，宠物问答应使用用户实际看到的正文，避免大段结构化数据挤占上下文。
 */
export function stripMarkdownFrontmatter(content: string): string {
  return content.replace(/^---\r?\n[\s\S]*?\r?\n---(?:\r?\n|$)/, "");
}

/**
 * 宠物旁路单次问题有长度边界，只携带当前章节的核心内容，避免整本手册挤占主 Session 上下文。
 */
export function buildManualQuestionPrompt(topic: HelpTopicId, question: string): string {
  const currentTopic = helpTopicById(topic);
  const normalizedQuestion = question.trim().slice(0, 500);
  const manualContext = stripMarkdownFrontmatter(currentTopic.content).trim().slice(0, 2_800);
  return [
    "你正在回答 MIMO 测试智能体用户手册问题。请只依据下方内置手册资料作答；资料没有覆盖时直接说明，并建议用户联系平台管理员，不要编造按钮或操作路径。",
    `【当前章节】${currentTopic.label}`,
    "【内置手册资料】",
    manualContext,
    "【用户问题】",
    normalizedQuestion
  ].join("\n");
}
