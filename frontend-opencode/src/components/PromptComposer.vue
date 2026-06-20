<script setup lang="ts">
import { computed, ref } from "vue";
import type { CommandInfo } from "@test-agent/shared-types";
import { AtSign, FileCode2, Folder, ImagePlus, Paperclip, Search, SendHorizontal, Slash, X } from "lucide-vue-next";
import { usePlatformStore } from "@/stores/platform";
import { usePromptStore } from "@/stores/prompt";
import { useWorkspaceStore } from "@/stores/workspace";

type FilePickerMode = "attach" | "mention";
type WorkspaceFileEntry = { path: string; name: string; type: "file" | "directory" | string };

defineProps<{ busy?: boolean }>();
const emit = defineEmits<{ submit: [] }>();
const platform = usePlatformStore();
const prompt = usePromptStore();
const workspace = useWorkspaceStore();
const slashOpen = ref(false);
const slashQuery = ref("");
const filePickerOpen = ref(false);
const filePickerMode = ref<FilePickerMode>("attach");
const fileQuery = ref("");
const fileResults = ref<WorkspaceFileEntry[]>([]);
const fileLoading = ref(false);
const fileError = ref<string>();
const lineCount = computed(() => Math.max(3, Math.min(12, prompt.text.split(/\r?\n/).length + 1)));
const filePickerLabel = computed(() => (filePickerMode.value === "attach" ? "Attach workspace file" : "Mention workspace file"));
const slashCommands = computed(() => {
  const query = slashQuery.value.trim().toLowerCase();
  if (!query) {
    return workspace.commands;
  }
  return workspace.commands.filter((command) =>
    `${command.name} ${command.description ?? ""} ${command.arguments ?? ""}`.toLowerCase().includes(query)
  );
});

function addFile() {
  void openFilePicker("attach");
}

async function toggleSlashCommands() {
  slashOpen.value = !slashOpen.value;
  if (slashOpen.value) {
    filePickerOpen.value = false;
  }
  if (slashOpen.value) {
    await workspace.loadCommands();
  }
}

function selectSlashCommand(command: CommandInfo) {
  prompt.insertSlashCommand(command);
  slashOpen.value = false;
  slashQuery.value = "";
}

async function openFilePicker(mode: FilePickerMode) {
  slashOpen.value = false;
  filePickerMode.value = mode;
  filePickerOpen.value = true;
  fileQuery.value = "";
  await loadFileResults();
}

// 文件和 @ 上下文选择只走平台代理 fs API，避免组件直连 opencode server。
async function loadFileResults() {
  fileLoading.value = true;
  fileError.value = undefined;
  try {
    fileResults.value = normalizeFileEntries(await platform.api.findRuntimeFiles(workspace.selectedWorkspaceId, fileQuery.value.trim()));
  } catch (cause) {
    fileResults.value = [];
    fileError.value = cause instanceof Error ? cause.message : "文件列表加载失败";
  } finally {
    fileLoading.value = false;
  }
}

function selectFileEntry(entry: WorkspaceFileEntry) {
  if (entry.type === "directory") {
    return;
  }
  if (filePickerMode.value === "attach") {
    if (!prompt.files.some((item) => item.path === entry.path)) {
      prompt.files.push({ path: entry.path, name: entry.name });
    }
  } else if (!prompt.references.some((item) => item.id === `file:${entry.path}`)) {
    prompt.references.push({ id: `file:${entry.path}`, label: entry.path, uri: `file://${entry.path}`, metadata: { type: "file" } });
  }
  filePickerOpen.value = false;
}

function normalizeFileEntries(value: unknown): WorkspaceFileEntry[] {
  const source = readRecord(value);
  const raw = Array.isArray(source?.data) ? source.data : Array.isArray(source?.items) ? source.items : Array.isArray(value) ? value : [];
  return raw.flatMap((item) => {
    const record = readRecord(item);
    const path = readString(record?.path) ?? readString(record?.id) ?? readString(record?.name);
    if (!record || !path) {
      return [];
    }
    return [
      {
        path,
        name: readString(record.name) ?? path.split("/").pop() ?? path,
        type: readString(record.type) ?? "file"
      }
    ];
  });
}

function readRecord(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : undefined;
}

function readString(value: unknown) {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}
</script>

<template>
  <form class="composer" aria-label="Prompt composer" @submit.prevent="emit('submit')">
    <div class="composer-toolbar" aria-label="Prompt tools">
      <button type="button" class="tool-button" title="Attach file" aria-label="Attach file" @click="addFile">
        <Paperclip :size="15" />
      </button>
      <button type="button" class="tool-button" title="Attach image" aria-label="Attach image">
        <ImagePlus :size="15" />
      </button>
      <button
        type="button"
        class="tool-button"
        title="Mention file or symbol"
        aria-label="Mention file or symbol"
        @click="openFilePicker('mention')"
      >
        <AtSign :size="15" />
      </button>
      <div class="composer-tool-wrap">
        <button type="button" class="tool-button" title="Slash command" aria-label="Open slash commands" @click="toggleSlashCommands">
          <Slash :size="15" />
        </button>
        <section v-if="slashOpen" class="slash-menu" role="dialog" aria-label="Slash commands">
          <label class="slash-search">
            <Search :size="14" />
            <input v-model="slashQuery" aria-label="Search slash commands" autocomplete="off" placeholder="Search commands" />
          </label>
          <div class="slash-list">
            <button
              v-for="command in slashCommands"
              :key="command.commandId"
              class="slash-row"
              type="button"
              :aria-label="`/${command.name} ${command.description ?? command.arguments ?? 'opencode command'}`"
              @click="selectSlashCommand(command)"
            >
              <strong>/{{ command.name }}</strong>
              <small>{{ command.description ?? command.arguments ?? "opencode command" }}</small>
            </button>
          </div>
          <div v-if="!slashCommands.length" class="empty-note">No matching commands</div>
        </section>
      </div>
      <label class="shell-toggle">
        <input v-model="prompt.shellMode" type="checkbox" />
        <span>shell</span>
      </label>
    </div>

    <section v-if="filePickerOpen" class="composer-picker" role="dialog" :aria-label="filePickerLabel">
      <header>
        <div>
          <p class="eyebrow">{{ filePickerMode === "attach" ? "Attachment" : "Context" }}</p>
          <h2>{{ filePickerLabel }}</h2>
        </div>
        <button class="icon-button" type="button" aria-label="Close file picker" @click="filePickerOpen = false">
          <X :size="15" />
        </button>
      </header>
      <label class="slash-search">
        <Search :size="14" />
        <input v-model="fileQuery" aria-label="Search workspace files" autocomplete="off" placeholder="Search files" @input="loadFileResults" />
      </label>
      <div class="file-picker-list">
        <button
          v-for="entry in fileResults"
          :key="entry.path"
          class="file-picker-row"
          type="button"
          :disabled="entry.type === 'directory'"
          :aria-label="`${filePickerMode === 'attach' ? 'Attach' : 'Mention'} ${entry.path} ${entry.type}`"
          @click="selectFileEntry(entry)"
        >
          <Folder v-if="entry.type === 'directory'" :size="15" />
          <FileCode2 v-else :size="15" />
          <span>
            <strong>{{ entry.path }}</strong>
            <small>{{ entry.type }}</small>
          </span>
        </button>
      </div>
      <div v-if="fileLoading" class="empty-note">Loading files...</div>
      <div v-else-if="fileError" class="inline-alert">{{ fileError }}</div>
      <div v-else-if="!fileResults.length" class="empty-note">No matching files</div>
    </section>

    <div class="composer-context" v-if="prompt.files.length || prompt.references.length || prompt.agents.length">
      <span v-for="file in prompt.files" :key="file.path ?? file.name" class="context-chip">
        <FileCode2 :size="13" />{{ file.path ?? file.name }}
      </span>
      <span v-for="reference in prompt.references" :key="reference.id" class="context-chip">@{{ reference.label }}</span>
      <span v-for="agent in prompt.agents" :key="agent.agentId" class="context-chip">agent: {{ agent.name ?? agent.agentId }}</span>
    </div>

    <textarea
      v-model="prompt.text"
      :rows="lineCount"
      placeholder="Ask opencode to inspect, edit, test, or explain this workspace..."
      @keydown.meta.enter.prevent="emit('submit')"
      @keydown.ctrl.enter.prevent="emit('submit')"
    />

    <div class="composer-footer">
      <span>{{ prompt.parts.length }} part{{ prompt.parts.length === 1 ? "" : "s" }}</span>
      <button class="send-button" type="submit" :disabled="busy || !prompt.canSubmit">
        <SendHorizontal :size="16" />
        <span>{{ busy ? "Sending" : "Send" }}</span>
      </button>
    </div>
  </form>
</template>
