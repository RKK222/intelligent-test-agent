<script lang="ts">
import type { CommandInfo, RuntimeResourceInfo } from "@test-agent/shared-types";
import type { ComposerAttachment } from "./prompt-parts";

export type ComposerAreaProps = {
  running?: boolean;
  commands: CommandInfo[];
  resources: RuntimeResourceInfo[];
};
</script>

<script setup lang="ts">
import { computed, ref, shallowRef } from "vue";
import { ImageIcon, Paperclip, X } from "lucide-vue-next";
import { Button, Textarea } from "@test-agent/ui-kit";
import SuggestionPanel from "./SuggestionPanel.vue";
import { fileToPromptAttachment } from "./prompt-parts";
import {
  commandQuery,
  contextQuery,
  formatBytes,
  mergeAttachments,
  replaceCommandQuery,
  replaceContextQuery
} from "./chat-utils";

const props = defineProps<ComposerAreaProps>();
const emit = defineEmits<{
  send: [prompt: string, attachments: ComposerAttachment[]];
  cancel: [];
  retry: [];
}>();

const text = ref("");
const attachments = ref<ComposerAttachment[]>([]);
const attachmentError = ref<string | null>(null);
const readingAttachments = ref(false);
const fileInput = shallowRef<HTMLInputElement | null>(null);
const imageInput = shallowRef<HTMLInputElement | null>(null);

const slashQuery = computed(() => commandQuery(text.value));
const atQuery = computed(() => contextQuery(text.value));
const contextItems = computed(() =>
  props.resources.slice(0, 12).map((item) => ({
    id: item.id,
    label: item.name,
    detail: item.uri ?? item.type ?? item.id
  }))
);

const commandSuggestions = computed(() => {
  if (slashQuery.value == null || !props.commands.length) return [];
  const q = slashQuery.value.toLowerCase();
  return props.commands
    .filter((c) => c.name.toLowerCase().includes(q))
    .slice(0, 6)
    .map((c) => ({
      id: c.commandId,
      label: `/${c.name}`,
      detail: c.description ?? c.arguments ?? ""
    }));
});

const contextSuggestions = computed(() => {
  if (atQuery.value == null || !contextItems.value.length) return [];
  const q = atQuery.value.toLowerCase();
  return contextItems.value.filter((item) => item.label.toLowerCase().includes(q)).slice(0, 6);
});

async function addAttachments(files: FileList | null) {
  if (!files?.length) return;
  readingAttachments.value = true;
  attachmentError.value = null;
  try {
    const next = await Promise.all(Array.from(files).map((file) => fileToPromptAttachment(file)));
    attachments.value = mergeAttachments(attachments.value, next);
  } catch (error) {
    attachmentError.value = error instanceof Error ? error.message : "附件读取失败";
  } finally {
    readingAttachments.value = false;
  }
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  void addAttachments(input.files);
  input.value = "";
}

function submit() {
  const prompt = text.value.trim();
  if (!prompt && attachments.value.length === 0) return;
  emit("send", prompt, attachments.value);
  text.value = "";
  attachments.value = [];
  attachmentError.value = null;
}

function removeAttachment(id: string) {
  attachments.value = attachments.value.filter((item) => item.id !== id);
}
</script>

<template>
  <form
    class="border-t border-[var(--ta-chat-border)] bg-[var(--ta-chat-surface)] p-3"
    @submit.prevent
  >
    <input ref="fileInput" class="hidden" type="file" multiple @change="onFileChange" />
    <input ref="imageInput" class="hidden" type="file" accept="image/*" multiple @change="onFileChange" />
    <Textarea
      v-model="text"
      rows="3"
      class="border-[var(--ta-chat-border)] bg-[var(--ta-chat-answer-bg)] text-[var(--ta-chat-text)] placeholder:text-[var(--ta-chat-muted)] focus:border-[var(--ta-chat-border-strong)]"
      placeholder="描述测试任务，例如：跑 checkout 模块并分析失败原因"
      @keydown.enter.exact.prevent="submit"
    />
    <SuggestionPanel
      v-if="commandSuggestions.length"
      title="Commands"
      :items="commandSuggestions"
      @pick="(item) => (text = replaceCommandQuery(text, item.label.slice(1)))"
    />
    <SuggestionPanel
      v-if="contextSuggestions.length"
      title="Context"
      :items="contextSuggestions"
      @pick="(item) => (text = replaceContextQuery(text, item.label.slice(1)))"
    />
    <div v-if="attachments.length || attachmentError || readingAttachments" class="mt-2 flex min-h-7 flex-wrap items-center gap-2">
      <span
        v-for="attachment in attachments"
        :key="attachment.id"
        class="inline-flex max-w-full items-center gap-1 rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-process-bg)] px-2 py-1 text-[11px] text-[var(--ta-chat-text)]"
        :title="`${attachment.name} ${formatBytes(attachment.size)}`"
      >
        <span class="max-w-[160px] truncate">{{ attachment.name }}</span>
        <span class="text-[var(--ta-chat-muted)]">{{ formatBytes(attachment.size) }}</span>
        <button
          type="button"
          title="移除附件"
          class="rounded p-0.5 text-[var(--ta-chat-muted)] hover:bg-[var(--ta-chat-hover)] hover:text-[var(--ta-chat-text)]"
          @click="removeAttachment(attachment.id)"
        >
          <X class="h-3 w-3" />
        </button>
      </span>
      <span v-if="readingAttachments" class="text-[11px] text-[var(--ta-chat-muted)]">读取中</span>
      <span v-if="attachmentError" class="text-[11px] text-[var(--ta-chat-status-error)]">{{ attachmentError }}</span>
    </div>
    <div class="mt-2 flex items-center justify-between gap-2">
      <div class="text-[11px] text-[var(--ta-chat-muted)]">{{ running ? "Run 正在执行，发送将排队" : "Enter 发送" }}</div>
      <div class="flex gap-2">
        <Button type="button" size="icon" variant="secondary" title="添加文件" @click="fileInput?.click()">
          <Paperclip class="h-4 w-4" />
        </Button>
        <Button type="button" size="icon" variant="secondary" title="添加图片" @click="imageInput?.click()">
          <ImageIcon class="h-4 w-4" />
        </Button>
        <Button type="button" size="sm" variant="secondary" :disabled="!running" @click="emit('cancel')">取消</Button>
        <Button type="button" size="sm" variant="secondary" @click="emit('retry')">重试</Button>
        <Button type="submit" size="sm" variant="primary" :disabled="readingAttachments" @click="submit">
          {{ running ? "排队" : "发送" }}
        </Button>
      </div>
    </div>
  </form>
</template>
