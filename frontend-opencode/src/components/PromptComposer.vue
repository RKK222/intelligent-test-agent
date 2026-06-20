<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import type { CommandInfo } from "@test-agent/shared-types";
import { AtSign, FileCode2, Folder, ImagePlus, Paperclip, Search, SendHorizontal, Slash, X } from "lucide-vue-next";
import { usePlatformStore } from "@/stores/platform";
import { usePromptStore } from "@/stores/prompt";
import { useWorkspaceStore } from "@/stores/workspace";
import {
  buildSlashCommandText,
  createSlashParameterForm,
  initialSlashParameterValues,
  type SlashParameterField,
  type SlashParameterForm,
  type SlashParameterValue
} from "@/utils/slashParameters";

type FilePickerMode = "attach" | "mention";
type WorkspaceFileEntry = { path: string; name: string; type: "file" | "directory" | string };

const props = defineProps<{ busy?: boolean }>();
const emit = defineEmits<{ submit: [] }>();
const platform = usePlatformStore();
const prompt = usePromptStore();
const workspace = useWorkspaceStore();
const slashOpen = ref(false);
const slashQuery = ref("");
const slashActiveIndex = ref(0);
const slashParameterForm = ref<SlashParameterForm>();
const slashParameterValues = ref<Record<string, SlashParameterValue>>({});
const historyIndex = ref(-1);
const historyDraft = ref("");
const filePickerOpen = ref(false);
const filePickerMode = ref<FilePickerMode>("attach");
const imageInput = ref<HTMLInputElement>();
const imageError = ref<string>();
const draggingImages = ref(false);
const fileQuery = ref("");
const fileResults = ref<WorkspaceFileEntry[]>([]);
const fileLoading = ref(false);
const fileError = ref<string>();
const imageSequence = ref(0);
const lineCount = computed(() => Math.max(3, Math.min(12, prompt.text.split(/\r?\n/).length + 1)));
const filePickerLabel = computed(() => (filePickerMode.value === "attach" ? "Attach workspace file" : "Mention workspace file"));
const runtimeModels = computed(() => workspace.models.filter((model) => model.providerId));
const selectedRuntimeModel = computed(() =>
  runtimeModels.value.find((model) => `${model.providerId}/${model.id}` === prompt.runtimeModel)
);
const runtimeVariants = computed(() => (selectedRuntimeModel.value?.variants ?? []).filter((variant) => variant.trim()));
const slashCommands = computed(() => {
  const query = slashQuery.value.trim().toLowerCase();
  if (!query) {
    return workspace.commands;
  }
  return workspace.commands.filter((command) =>
    `${command.name} ${command.description ?? ""} ${command.arguments ?? ""}`.toLowerCase().includes(query)
  );
});

watch(
  () => [prompt.runtimeModel, runtimeVariants.value.join("\u0000")],
  () => {
    // Variant 绑定在具体模型上，切换模型后必须丢弃不再可用的旧运行态选择。
    if (prompt.runtimeVariant && !runtimeVariants.value.includes(prompt.runtimeVariant)) {
      prompt.runtimeVariant = "";
    }
  },
  { immediate: true }
);

watch(slashQuery, () => {
  slashActiveIndex.value = 0;
});

watch(
  slashCommands,
  (commands) => {
    if (!commands.length) {
      slashActiveIndex.value = 0;
      return;
    }
    if (slashActiveIndex.value >= commands.length) {
      slashActiveIndex.value = commands.length - 1;
    }
  },
  { immediate: true }
);

watch(
  () => prompt.text,
  (value) => {
    const form = slashParameterForm.value;
    if (form && !value.startsWith(`/${form.commandName}`)) {
      closeSlashParameters();
    }
  }
);

function addFile() {
  void openFilePicker("attach");
}

function openImagePicker() {
  imageInput.value?.click();
}

async function handleImageInput(event: Event) {
  const target = event.currentTarget instanceof HTMLInputElement ? event.currentTarget : undefined;
  await addImageFiles(Array.from(target?.files ?? []));
  if (target) {
    target.value = "";
  }
}

async function toggleSlashCommands() {
  slashOpen.value = !slashOpen.value;
  if (slashOpen.value) {
    filePickerOpen.value = false;
  }
  if (slashOpen.value) {
    await workspace.loadCommands();
    slashActiveIndex.value = 0;
  } else {
    slashQuery.value = "";
  }
}

function selectSlashCommand(command: CommandInfo) {
  const form = createSlashParameterForm(command);
  if (form) {
    slashParameterForm.value = form;
    slashParameterValues.value = initialSlashParameterValues(form);
    applySlashParameters();
  } else {
    slashParameterForm.value = undefined;
    slashParameterValues.value = {};
    prompt.insertSlashCommand(command);
  }
  slashOpen.value = false;
  slashQuery.value = "";
  slashActiveIndex.value = 0;
}

function setSlashParameterValue(field: SlashParameterField, value: SlashParameterValue) {
  slashParameterValues.value = { ...slashParameterValues.value, [field.id]: value };
  applySlashParameters();
}

function slashParameterText(field: SlashParameterField) {
  const value = slashParameterValues.value[field.id];
  return typeof value === "string" ? value : "";
}

function slashParameterChecked(field: SlashParameterField) {
  return slashParameterValues.value[field.id] === true;
}

function applySlashParameters() {
  const form = slashParameterForm.value;
  if (!form) {
    return;
  }
  prompt.text = buildSlashCommandText(form, slashParameterValues.value);
}

function closeSlashParameters() {
  slashParameterForm.value = undefined;
  slashParameterValues.value = {};
}

function handleSlashKeydown(event: KeyboardEvent) {
  if (event.key === "Escape") {
    event.preventDefault();
    slashOpen.value = false;
    slashQuery.value = "";
    slashActiveIndex.value = 0;
    return;
  }
  if (event.key === "ArrowDown" || event.key === "ArrowUp") {
    event.preventDefault();
    moveSlashActive(event.key === "ArrowDown" ? 1 : -1);
    return;
  }
  if (event.key === "Enter") {
    event.preventDefault();
    const command = slashCommands.value[slashActiveIndex.value] ?? slashCommands.value[0];
    if (command) {
      selectSlashCommand(command);
    }
  }
}

function handleTextKeydown(event: KeyboardEvent) {
  if (handleHistoryKeydown(event)) {
    return;
  }
  if (event.key !== "Enter" || event.shiftKey || event.altKey || event.isComposing) {
    if (event.key.length === 1) {
      historyIndex.value = -1;
    }
    return;
  }
  event.preventDefault();
  // opencode composer 的主路径是 Enter 提交；Shift+Enter 交给 textarea 保留换行。
  if (!props.busy && prompt.canSubmit) {
    emit("submit");
  }
}

function handleTextInput(event: Event) {
  const target = event.currentTarget instanceof HTMLTextAreaElement ? event.currentTarget : undefined;
  const value = target?.value ?? prompt.text;
  const slashMatch = value.match(/^\/([^\s/]*)$/);
  if (!slashMatch) {
    if (slashOpen.value && !value.startsWith("/")) {
      slashOpen.value = false;
      slashQuery.value = "";
      slashActiveIndex.value = 0;
    }
    return;
  }
  filePickerOpen.value = false;
  closeSlashParameters();
  slashQuery.value = slashMatch[1] ?? "";
  slashActiveIndex.value = 0;
  if (!slashOpen.value) {
    slashOpen.value = true;
    void workspace.loadCommands();
  }
}

function handleHistoryKeydown(event: KeyboardEvent) {
  if ((event.key !== "ArrowUp" && event.key !== "ArrowDown") || event.shiftKey || event.altKey || event.metaKey || event.ctrlKey) {
    return false;
  }
  const target = event.currentTarget instanceof HTMLTextAreaElement ? event.currentTarget : undefined;
  if (!target || !prompt.history.length) {
    return false;
  }
  const atStart = target.selectionStart === 0 && target.selectionEnd === 0;
  const atEnd = target.selectionStart === target.value.length && target.selectionEnd === target.value.length;
  if (event.key === "ArrowUp" && !atStart) {
    return false;
  }
  if (event.key === "ArrowDown" && !atEnd) {
    return false;
  }
  event.preventDefault();
  if (event.key === "ArrowUp") {
    if (historyIndex.value === -1) {
      historyDraft.value = prompt.text;
      historyIndex.value = 0;
    } else {
      historyIndex.value = Math.min(historyIndex.value + 1, prompt.history.length - 1);
    }
  } else if (historyIndex.value > 0) {
    historyIndex.value -= 1;
  } else {
    historyIndex.value = -1;
  }
  const next = historyIndex.value === -1 ? historyDraft.value : prompt.history[historyIndex.value] ?? "";
  prompt.text = next;
  void nextTick(() => target.setSelectionRange(next.length, next.length));
  return true;
}

function moveSlashActive(delta: number) {
  const count = slashCommands.value.length;
  if (!count) {
    slashActiveIndex.value = 0;
    return;
  }
  slashActiveIndex.value = (slashActiveIndex.value + delta + count) % count;
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

async function handlePaste(event: ClipboardEvent) {
  const files = Array.from(event.clipboardData?.items ?? []).flatMap((item) => {
    if (item.kind !== "file") {
      return [];
    }
    const file = item.getAsFile();
    return file ? [file] : [];
  });
  if (!files.length) {
    return;
  }
  event.preventDefault();
  await addImageFiles(files);
}

function handleImageDragOver(event: DragEvent) {
  if (!Array.from(event.dataTransfer?.types ?? []).includes("Files")) {
    return;
  }
  event.preventDefault();
  draggingImages.value = true;
}

function handleImageDragLeave(event: DragEvent) {
  const nextTarget = event.relatedTarget;
  if (nextTarget instanceof Node && event.currentTarget instanceof HTMLElement && event.currentTarget.contains(nextTarget)) {
    return;
  }
  draggingImages.value = false;
}

async function handleImageDrop(event: DragEvent) {
  const files = Array.from(event.dataTransfer?.files ?? []);
  if (!files.length) {
    draggingImages.value = false;
    return;
  }
  event.preventDefault();
  draggingImages.value = false;
  await addImageFiles(files);
}

// 图片附件按平台 PromptPart file 契约发送，url 保留 data URL 供预览和后端读取。
async function addImageFiles(files: File[]) {
  const images = files.filter((file) => file.type.startsWith("image/"));
  if (!images.length) {
    if (files.length) {
      imageError.value = "Only image files can be attached";
    }
    return;
  }
  imageError.value = undefined;
  for (const file of images) {
    const url = await readFileDataUrl(file);
    if (!url) {
      imageError.value = "Image attachment failed";
      continue;
    }
    const name = file.name || "image";
    prompt.images.push({
      id: `image:${Date.now()}:${imageSequence.value++}:${name}`,
      name,
      mimeType: file.type || "application/octet-stream",
      url
    });
  }
}

function readFileDataUrl(file: File) {
  return new Promise<string>((resolve) => {
    const reader = new FileReader();
    reader.addEventListener("error", () => resolve(""));
    reader.addEventListener("load", () => resolve(typeof reader.result === "string" ? reader.result : ""));
    reader.readAsDataURL(file);
  });
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
  <form
    class="composer"
    :class="{ 'is-dragging-images': draggingImages }"
    aria-label="Prompt composer"
    @submit.prevent="emit('submit')"
    @dragover="handleImageDragOver"
    @dragleave="handleImageDragLeave"
    @drop="handleImageDrop"
  >
    <input
      ref="imageInput"
      class="composer-file-input"
      type="file"
      accept="image/*"
      multiple
      aria-label="Image attachment input"
      @change="handleImageInput"
    />
    <div v-if="draggingImages" class="composer-drag-hint" aria-hidden="true">
      <ImagePlus :size="18" />
      <span>Images</span>
    </div>
    <div class="composer-toolbar" aria-label="Prompt tools">
      <label v-if="workspace.agents.length" class="composer-select">
        <span>Agent</span>
        <select v-model="prompt.runtimeAgent" aria-label="Agent">
          <option value="">Default</option>
          <option v-for="agent in workspace.agents" :key="agent.agentId" :value="agent.agentId">
            {{ agent.name }}
          </option>
        </select>
      </label>
      <label v-if="runtimeModels.length" class="composer-select">
        <span>Model</span>
        <select v-model="prompt.runtimeModel" aria-label="Model">
          <option value="">Default</option>
          <option v-for="model in runtimeModels" :key="`${model.providerId}:${model.id}`" :value="`${model.providerId}/${model.id}`">
            {{ model.name }}
          </option>
        </select>
      </label>
      <label v-if="runtimeVariants.length" class="composer-select">
        <span>Variant</span>
        <select v-model="prompt.runtimeVariant" aria-label="Model variant">
          <option value="">Default</option>
          <option v-for="variant in runtimeVariants" :key="variant" :value="variant">
            {{ variant }}
          </option>
        </select>
      </label>
      <button type="button" class="tool-button" title="Attach file" aria-label="Attach file" @click="addFile">
        <Paperclip :size="15" />
      </button>
      <button type="button" class="tool-button" title="Attach image" aria-label="Attach image" @click="openImagePicker">
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
            <input
              v-model="slashQuery"
              aria-label="Search slash commands"
              autocomplete="off"
              placeholder="Search commands"
              @keydown="handleSlashKeydown"
            />
          </label>
          <div class="slash-list" role="listbox" aria-label="Slash command results">
            <button
              v-for="(command, index) in slashCommands"
              :key="command.commandId"
              class="slash-row"
              :class="{ active: index === slashActiveIndex }"
              type="button"
              role="option"
              :aria-selected="index === slashActiveIndex"
              :aria-label="`/${command.name} ${command.description ?? command.arguments ?? 'opencode command'}`"
              @mouseenter="slashActiveIndex = index"
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

    <section v-if="slashParameterForm" class="slash-parameter-panel" role="region" aria-label="Command parameters">
      <header>
        <div>
          <p class="eyebrow">Command</p>
          <h2>/{{ slashParameterForm.commandName }}</h2>
          <small v-if="slashParameterForm.description">{{ slashParameterForm.description }}</small>
        </div>
        <button class="icon-button" type="button" aria-label="Close command parameters" @click="closeSlashParameters">
          <X :size="15" />
        </button>
      </header>
      <div class="slash-parameter-grid">
        <label v-for="field in slashParameterForm.fields" :key="field.id" class="slash-parameter-field" :data-kind="field.kind">
          <span>{{ field.label }}</span>
          <input
            v-if="field.kind === 'flag'"
            type="checkbox"
            :aria-label="field.label"
            :checked="slashParameterChecked(field)"
            @change="setSlashParameterValue(field, ($event.currentTarget as HTMLInputElement).checked)"
          />
          <input
            v-else
            type="text"
            :aria-label="field.label"
            :placeholder="field.placeholder ?? field.label"
            :value="slashParameterText(field)"
            @input="setSlashParameterValue(field, ($event.currentTarget as HTMLInputElement).value)"
          />
          <small v-if="field.prefix">{{ field.prefix }}</small>
        </label>
      </div>
    </section>

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
        <FileCode2 :size="13" />
        <span>{{ file.path ?? file.name }}</span>
        <button type="button" :aria-label="`Remove ${file.path ?? file.name} attachment`" @click="prompt.removeFile(file.path ?? file.name ?? '')">
          <X :size="12" />
        </button>
      </span>
      <span v-for="reference in prompt.references" :key="reference.id" class="context-chip">
        <span>@{{ reference.label }}</span>
        <button type="button" :aria-label="`Remove ${reference.label} reference`" @click="prompt.removeReference(reference.id)">
          <X :size="12" />
        </button>
      </span>
      <span v-for="agent in prompt.agents" :key="agent.agentId" class="context-chip">
        <span>agent: {{ agent.name ?? agent.agentId }}</span>
        <button type="button" :aria-label="`Remove ${agent.name ?? agent.agentId} agent`" @click="prompt.removeAgent(agent.agentId)">
          <X :size="12" />
        </button>
      </span>
    </div>

    <div v-if="prompt.images.length" class="composer-images" aria-label="Image attachments">
      <figure v-for="image in prompt.images" :key="image.id ?? image.name ?? image.url" class="composer-image">
        <img v-if="image.url" :src="image.url" :alt="image.name ?? 'image attachment'" />
        <div v-else class="composer-image-fallback">
          <ImagePlus :size="18" />
        </div>
        <figcaption>{{ image.name ?? "image" }}</figcaption>
        <button
          type="button"
          :aria-label="`Remove ${image.name ?? 'image'} image attachment`"
          @click="prompt.removeImage(image.id ?? image.name ?? '')"
        >
          <X :size="12" />
        </button>
      </figure>
    </div>
    <div v-if="imageError" class="inline-alert">{{ imageError }}</div>

    <textarea
      v-model="prompt.text"
      :rows="lineCount"
      placeholder="Ask opencode to inspect, edit, test, or explain this workspace..."
      @paste="handlePaste"
      @input="handleTextInput"
      @keydown="handleTextKeydown"
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
