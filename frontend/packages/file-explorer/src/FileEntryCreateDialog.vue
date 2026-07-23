<script setup lang="ts">
import { nextTick, ref } from "vue";
import { FilePlus2, Upload, X } from "lucide-vue-next";

const props = withDefaults(defineProps<{
  /** 根目录在目标路径块中的业务名称。 */
  rootLabel?: string;
  /** 是否展示本机文件上传入口。 */
  allowUpload?: boolean;
  /** Agent 配置根目录可额外创建 OpenCode Agent/Skill 标准模板。 */
  allowAgentTemplates?: boolean;
  /** 复用统一面板时允许调用方提供具体业务标题。 */
  title?: string;
  dialogLabel?: string;
}>(), {
  rootLabel: "工作区根目录",
  allowUpload: true,
  allowAgentTemplates: false,
  title: "",
  dialogLabel: ""
});

const emit = defineEmits<{
  createEntry: [directory: string, name: string, type: "file" | "directory"];
  createAgentTemplate: [directory: string, name: string, type: "agent" | "skill", englishName?: string];
  requestUpload: [directory: string];
}>();

const visible = ref(false);
const targetDirectory = ref("");
const entryType = ref<"file" | "directory" | "upload" | "agent" | "skill">("file");
const entryName = ref("");
const englishName = ref("");
const errorMessage = ref("");
const nameInput = ref<HTMLInputElement | null>(null);

/** 工作空间根按钮、目录行和 Agent 配置树共用同一入口与名称校验。 */
function open(directory: string) {
  targetDirectory.value = directory;
  entryType.value = "file";
  entryName.value = "";
  englishName.value = "";
  errorMessage.value = "";
  visible.value = true;
  void nextTick(() => nameInput.value?.focus());
}

function close() {
  visible.value = false;
  errorMessage.value = "";
}

function submit() {
  if (entryType.value === "upload") {
    if (!props.allowUpload) return;
    emit("requestUpload", targetDirectory.value);
    close();
    return;
  }
  const name = entryName.value.trim();
  if (!name) {
    errorMessage.value = "请输入名称";
    return;
  }
  if (name.includes("/") || name.includes("\\") || name === "." || name === "..") {
    errorMessage.value = "名称不能包含路径分隔符";
    return;
  }
  if (entryType.value === "agent" || entryType.value === "skill") {
    if (!props.allowAgentTemplates) return;
    const optionalEnglishName = englishName.value.trim();
    if (optionalEnglishName && !/^[A-Za-z0-9][A-Za-z0-9 ._+&-]*$/.test(optionalEnglishName)) {
      errorMessage.value = "英文名称只能包含英文字母、数字、空格及 . _ + & -";
      return;
    }
    emit("createAgentTemplate", targetDirectory.value, name, entryType.value, optionalEnglishName || undefined);
  } else {
    emit("createEntry", targetDirectory.value, name, entryType.value);
  }
  close();
}

const resolvedTitle = () => props.title || (props.allowUpload ? "新建或上传" : "新建");
const resolvedDialogLabel = () => props.dialogLabel || (props.allowUpload ? "新建或上传文件" : "新建文件或文件夹");

function entryLabel() {
  if (entryType.value === "file") return "文件名";
  if (entryType.value === "directory") return "文件夹名";
  if (entryType.value === "agent") return "Agent 中文名称";
  return "Skill 中文名称";
}

function entryPlaceholder() {
  if (entryType.value === "file") return "例如：README.md";
  if (entryType.value === "directory") return "例如：docs";
  if (entryType.value === "agent") return "例如：支付测试 Agent";
  return "例如：支付测试技能";
}

function entryHelp() {
  if (entryType.value === "file") return "创建空白普通文件，由你自行编写内容；不会自动生成可调用的 Agent 或 Skill。";
  if (entryType.value === "directory") return "创建普通目录，用于整理配置素材；不会生成 Agent 或 Skill 模板。";
  if (entryType.value === "agent") return "用于定义可选择、可调用的 Agent。英文名称选填；留空时按完整拼音生成英文文件名 agents/<技术标识>.md。";
  return "用于封装可复用 Skill。英文名称选填；留空时按完整拼音生成英文目录 skills/<技术标识>/，并创建 SKILL.md、rules、templates。";
}

defineExpose({ open });
</script>

<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="ta-file-dialog-overlay"
      @keydown.esc="close"
      @click.self="close"
    >
      <section
        role="dialog"
        aria-modal="true"
        :aria-label="resolvedDialogLabel()"
        class="ta-file-dialog"
      >
        <header class="ta-file-dialog-header">
          <div class="ta-file-dialog-heading">
            <span class="ta-file-dialog-icon"><FilePlus2 :size="16" :stroke-width="1.7" /></span>
            <div>
              <h2>{{ resolvedTitle() }}</h2>
              <p>选择要在当前目录执行的操作</p>
            </div>
          </div>
          <button type="button" class="ta-file-dialog-close" aria-label="关闭" @click="close">
            <X :size="15" :stroke-width="1.7" />
          </button>
        </header>
        <div class="ta-file-dialog-body">
          <div class="ta-file-dialog-path">
            <span>目标目录</span>
            <code>{{ targetDirectory || rootLabel }}</code>
          </div>
          <div class="ta-file-dialog-field">
            <label>操作类型</label>
            <div
              role="radiogroup"
              aria-label="操作类型"
              class="ta-file-dialog-segments"
              :class="{ 'has-upload': allowUpload, 'has-agent-templates': allowAgentTemplates }"
            >
              <button type="button" role="radio" :aria-checked="entryType === 'file'" :class="{ 'is-active': entryType === 'file' }" @click="entryType = 'file'">
                文件
              </button>
              <button type="button" role="radio" :aria-checked="entryType === 'directory'" :class="{ 'is-active': entryType === 'directory' }" @click="entryType = 'directory'">
                文件夹
              </button>
              <button
                v-if="allowUpload"
                type="button"
                role="radio"
                :aria-checked="entryType === 'upload'"
                :class="{ 'is-active': entryType === 'upload' }"
                @click="entryType = 'upload'"
              >
                上传
              </button>
              <button
                v-if="allowAgentTemplates"
                type="button"
                role="radio"
                :aria-checked="entryType === 'agent'"
                :class="{ 'is-active': entryType === 'agent' }"
                @click="entryType = 'agent'"
              >
                Agent
              </button>
              <button
                v-if="allowAgentTemplates"
                type="button"
                role="radio"
                :aria-checked="entryType === 'skill'"
                :class="{ 'is-active': entryType === 'skill' }"
                @click="entryType = 'skill'"
              >
                Skill
              </button>
            </div>
          </div>
          <div v-if="entryType !== 'upload'" class="ta-file-dialog-field">
            <label :for="`new-entry-${entryType}`">{{ entryLabel() }}</label>
            <input
              :id="`new-entry-${entryType}`"
              ref="nameInput"
              v-model="entryName"
              type="text"
              :placeholder="entryPlaceholder()"
              class="ta-file-dialog-input"
              @keydown.enter="submit"
            />
            <span v-if="allowAgentTemplates" class="ta-file-dialog-help">{{ entryHelp() }}</span>
            <template v-if="entryType === 'agent' || entryType === 'skill'">
              <label :for="`new-entry-${entryType}-english`">英文名称（选填）</label>
              <input
                :id="`new-entry-${entryType}-english`"
                v-model="englishName"
                type="text"
                :placeholder="entryType === 'agent' ? '例如：Payment Testing Agent' : '例如：Payment Testing'"
                class="ta-file-dialog-input"
                @keydown.enter="submit"
              />
            </template>
            <span v-if="errorMessage" class="ta-file-dialog-error">{{ errorMessage }}</span>
          </div>
          <div v-else class="ta-file-dialog-upload-note">
            <Upload :size="17" :stroke-width="1.6" />
            <div>
              <strong>从本机选择文件</strong>
              <span>支持一次选择多个文件，上传时不会覆盖同名内容。</span>
            </div>
          </div>
        </div>
        <footer class="ta-file-dialog-footer">
          <button type="button" class="ta-file-dialog-button" @click="close">取消</button>
          <button
            type="button"
            class="ta-file-dialog-button is-primary"
            :disabled="entryType !== 'upload' && !entryName.trim()"
            @click="submit"
          >
            {{ entryType === 'upload' ? '选择文件' : '创建' }}
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.ta-file-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 2700;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.4);
  backdrop-filter: blur(8px);
}

.ta-file-dialog {
  display: flex;
  width: min(440px, calc(100vw - 28px));
  overflow: hidden;
  flex-direction: column;
  border: 1px solid var(--ta-border-strong, #cfcfcf);
  border-radius: 12px;
  background: var(--ta-panel-2, #fff);
  box-shadow: 0 24px 48px -12px rgba(0, 0, 0, 0.18);
  color: var(--ta-text, #333);
}

.ta-file-dialog-header,
.ta-file-dialog-footer {
  display: flex;
  align-items: center;
  background: var(--ta-surface, #fff);
  padding: 14px 20px;
}

.ta-file-dialog-header {
  justify-content: space-between;
  border-bottom: 1px solid var(--ta-border, #eaeaea);
}

.ta-file-dialog-heading {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 12px;
}

.ta-file-dialog-heading h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.ta-file-dialog-heading p {
  margin: 4px 0 0;
  color: var(--ta-muted, #7a7a7a);
  font-size: 12px;
}

.ta-file-dialog-icon {
  display: inline-flex;
  width: 32px;
  height: 32px;
  flex: none;
  align-items: center;
  justify-content: center;
  border: 1px solid #cce0ff;
  border-radius: 8px;
  background: #ecf3fe;
  color: #2563eb;
}

.ta-file-dialog-close {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--ta-muted, #7a7a7a);
  cursor: pointer;
}

.ta-file-dialog-close:hover {
  background: var(--ta-hover, #eef1f5);
  color: var(--ta-text, #333);
}

.ta-file-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
}

.ta-file-dialog-path,
.ta-file-dialog-field {
  display: grid;
  gap: 6px;
}

.ta-file-dialog-path span,
.ta-file-dialog-field > label {
  color: var(--ta-muted, #7a7a7a);
  font-size: 12px;
  font-weight: 500;
}

.ta-file-dialog-path code {
  overflow: hidden;
  border: 1px solid var(--ta-border, #eaeaea);
  border-radius: 8px;
  background: var(--ta-bg, #f0f4fa);
  padding: 8px 12px;
  color: var(--ta-subtle, #444);
  font-family: var(--font-mono, "Geist Mono", monospace);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ta-file-dialog-segments {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4px;
  border: 1px solid var(--ta-border, #eaeaea);
  border-radius: 8px;
  background: var(--ta-bg, #f0f4fa);
  padding: 4px;
}

.ta-file-dialog-segments.has-upload {
  grid-template-columns: repeat(3, 1fr);
}

.ta-file-dialog-segments.has-agent-templates {
  grid-template-columns: repeat(5, 1fr);
}

.ta-file-dialog-segments button {
  min-height: 30px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ta-muted, #7a7a7a);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
}

.ta-file-dialog-segments button.is-active {
  background: var(--ta-surface, #fff);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  color: var(--ta-text, #333);
  font-weight: 600;
}

.ta-file-dialog-input {
  width: 100%;
  height: 38px;
  border: 1px solid var(--ta-border-strong, #cfcfcf);
  border-radius: 8px;
  outline: none;
  background: var(--ta-surface, #fff);
  padding: 0 12px;
  color: var(--ta-text, #333);
  font-size: 13px;
}

.ta-file-dialog-input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}

.ta-file-dialog-error {
  color: var(--ta-error, #9e3b34);
  font-size: 12px;
}

.ta-file-dialog-help {
  color: var(--ta-muted, #7a7a7a);
  font-size: 11px;
  line-height: 1.55;
}

.ta-file-dialog-upload-note {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  border: 2px dashed #bfdbfe;
  border-radius: 10px;
  background: #f0f7ff;
  padding: 24px 16px;
  color: #2563eb;
  text-align: center;
}

.ta-file-dialog-upload-note div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ta-file-dialog-upload-note span {
  color: #60a5fa;
  font-size: 11px;
}

.ta-file-dialog-footer {
  justify-content: flex-end;
  gap: 10px;
  border-top: 1px solid var(--ta-border, #eaeaea);
}

.ta-file-dialog-button {
  min-width: 80px;
  height: 34px;
  border: 1px solid var(--ta-border-strong, #cfcfcf);
  border-radius: 8px;
  background: var(--ta-surface, #fff);
  color: var(--ta-text, #333);
  padding: 0 16px;
  font-size: 13px;
  cursor: pointer;
}

.ta-file-dialog-button.is-primary {
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
}

.ta-file-dialog-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
</style>
