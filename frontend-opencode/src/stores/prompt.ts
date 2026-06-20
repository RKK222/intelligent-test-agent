import { defineStore } from "pinia";
import { computed, ref } from "vue";
import type { CommandInfo } from "@test-agent/shared-types";
import { buildPromptParts, type PromptBuildInput, type PromptFileInput } from "@/utils/prompt";

export const usePromptStore = defineStore("prompt", () => {
  const text = ref("");
  const files = ref<PromptFileInput[]>([]);
  const images = ref<PromptFileInput[]>([]);
  const agents = ref<Array<{ agentId: string; name?: string }>>([]);
  const references = ref<Array<{ id: string; label: string; uri?: string; metadata?: Record<string, unknown> }>>([]);
  const runtimeAgent = ref("");
  const runtimeModel = ref("");
  const runtimeVariant = ref("");
  const shellMode = ref(false);
  const history = ref<string[]>([]);

  const parts = computed(() => buildPromptParts(snapshot()));
  const canSubmit = computed(() => parts.value.length > 0);

  function snapshot(): PromptBuildInput {
    return {
      text: text.value,
      files: files.value,
      images: images.value,
      agents: agents.value,
      references: references.value,
      agent: runtimeAgent.value || undefined,
      model: runtimeModel.value || undefined,
      variant: runtimeVariant.value || undefined
    };
  }

  function remember() {
    const value = text.value.trim();
    if (value && history.value[0] !== value) {
      history.value.unshift(value);
      history.value = history.value.slice(0, 40);
    }
  }

  // Slash 命令沿用 opencode 的 /command 文本形态，由 composer 从平台代理命令目录写入。
  function insertSlashCommand(command: Pick<CommandInfo, "name" | "arguments">) {
    const name = command.name.replace(/^\//, "").trim();
    if (!name) {
      return;
    }
    const usage = `/${name}${command.arguments?.trim() ? ` ${command.arguments.trim()}` : ""}`;
    const current = text.value.trim();
    text.value = !current || current === "/" || current.startsWith("/") ? usage : `${usage}\n${current}`;
  }

  // Composer context chip 的移除动作集中在 store，避免组件直接重建 prompt part 状态。
  function removeFile(pathOrName: string) {
    files.value = files.value.filter((item) => (item.path ?? item.name) !== pathOrName);
  }

  function removeReference(id: string) {
    references.value = references.value.filter((item) => item.id !== id);
  }

  function removeAgent(agentId: string) {
    agents.value = agents.value.filter((item) => item.agentId !== agentId);
  }

  function reset() {
    text.value = "";
    files.value = [];
    images.value = [];
    agents.value = [];
    references.value = [];
    shellMode.value = false;
  }

  return {
    text,
    files,
    images,
    agents,
    references,
    runtimeAgent,
    runtimeModel,
    runtimeVariant,
    shellMode,
    history,
    parts,
    canSubmit,
    snapshot,
    remember,
    insertSlashCommand,
    removeFile,
    removeReference,
    removeAgent,
    reset
  };
});
