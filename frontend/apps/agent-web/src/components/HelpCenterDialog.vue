<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { BookOpenText, Bot, ExternalLink, Search } from "lucide-vue-next";
import {
  buildManualQuestionPrompt,
  DEFAULT_HELP_TOPIC,
  HELP_TOPICS,
  helpDocumentUrl,
  helpTopicById,
  normalizeHelpTopic,
  type HelpTopicId
} from "./help-center";

const props = defineProps<{
  open: boolean;
  initialTopic?: string | null;
  sideQuestionAvailable?: boolean;
  sideQuestionAnswer?: string | null;
  sideQuestionError?: string | null;
  sideQuestionLoading?: boolean;
  sideQuestionProgress?: string | null;
}>();

const emit = defineEmits<{
  (event: "close"): void;
  (event: "ask-pet", question: string): void;
}>();

const activeTopic = ref<HelpTopicId>(DEFAULT_HELP_TOPIC);
const question = ref("");
const questionSubmitted = ref(false);
const activeTopicInfo = computed(() => helpTopicById(activeTopic.value));
const manualUrl = computed(() => helpDocumentUrl(activeTopic.value));

watch(
  () => [props.open, props.initialTopic] as const,
  ([open, topic]) => {
    if (!open) return;
    activeTopic.value = normalizeHelpTopic(topic);
    questionSubmitted.value = false;
  },
  { immediate: true }
);

function close() {
  emit("close");
}

function selectTopic(topic: HelpTopicId) {
  activeTopic.value = topic;
  questionSubmitted.value = false;
}

function askPet() {
  const normalizedQuestion = question.value.trim();
  if (!props.sideQuestionAvailable || !normalizedQuestion || props.sideQuestionLoading) return;
  questionSubmitted.value = true;
  emit("ask-pet", buildManualQuestionPrompt(activeTopic.value, normalizedQuestion));
}

function openManualInNewTab() {
  window.open(manualUrl.value, "_blank", "noopener,noreferrer");
}
</script>

<template>
  <el-dialog
    :model-value="open"
    width="min(1180px, 94vw)"
    align-center
    :close-on-click-modal="false"
    class="ta-help-center-dialog"
    aria-label="用户手册"
    @update:model-value="(value: boolean) => { if (!value) close() }"
  >
    <template #header>
      <div class="ta-help-center-header">
        <div class="ta-help-center-heading">
          <span class="ta-help-center-mark" aria-hidden="true"><BookOpenText :size="18" /></span>
          <div>
            <h2>用户手册</h2>
            <p>搜索操作说明，或让小宠物依据当前章节回答</p>
          </div>
        </div>
        <el-button text class="ta-help-center-new-tab" title="在新标签页打开当前章节" @click="openManualInNewTab">
          <ExternalLink :size="15" />
          新窗口
        </el-button>
      </div>
    </template>

    <div class="ta-help-center-shell" data-testid="help-center-dialog">
      <nav class="ta-help-center-nav" aria-label="手册章节">
        <div class="ta-help-center-nav-caption"><Search :size="13" /> 手册目录</div>
        <button
          v-for="topic in HELP_TOPICS"
          :key="topic.id"
          type="button"
          :class="['ta-help-center-topic', topic.id === activeTopic && 'is-active']"
          :aria-current="topic.id === activeTopic ? 'page' : undefined"
          @click="selectTopic(topic.id)"
        >
          <strong>{{ topic.label }}</strong>
          <span>{{ topic.description }}</span>
        </button>
      </nav>

      <main class="ta-help-center-manual">
        <iframe
          :key="activeTopic"
          :src="manualUrl"
          :title="`用户手册：${activeTopicInfo.label}`"
          data-testid="help-center-frame"
        />
      </main>

      <aside class="ta-help-center-pet" aria-label="小宠物手册问答">
        <div class="ta-help-center-pet-title">
          <span aria-hidden="true"><Bot :size="17" /></span>
          <div>
            <strong>问问小宠物</strong>
            <small>当前资料：{{ activeTopicInfo.label }}</small>
          </div>
        </div>
        <p class="ta-help-center-pet-tip">
          提问时会携带当前章节，不写入主对话历史。
        </p>
        <el-input
          v-model="question"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          resize="none"
          data-testid="help-center-question-input"
          :disabled="!sideQuestionAvailable"
          :placeholder="sideQuestionAvailable ? '例如：为什么初始化按钮不能点击？' : '请先在主对话发送一条消息'"
          @keydown.enter.exact.prevent="askPet"
        />
        <el-button
          type="primary"
          class="ta-help-center-ask"
          data-testid="help-center-question-submit"
          :loading="sideQuestionLoading"
          :disabled="!sideQuestionAvailable || !question.trim() || sideQuestionLoading"
          @click="askPet"
        >
          向小宠物提问
        </el-button>
        <p v-if="!sideQuestionAvailable" class="ta-help-center-pet-unavailable">
          手册和全文搜索仍可使用；建立主对话后即可追问。
        </p>
        <div v-else-if="sideQuestionError && questionSubmitted" class="ta-help-center-pet-error" role="alert">
          {{ sideQuestionError }}
        </div>
        <div
          v-else-if="sideQuestionAnswer && questionSubmitted"
          class="ta-help-center-pet-answer"
          data-testid="help-center-question-answer"
          role="status"
        >
          {{ sideQuestionAnswer }}
        </div>
        <div v-else-if="sideQuestionLoading && questionSubmitted" class="ta-help-center-pet-progress" role="status">
          {{ sideQuestionProgress || "正在查阅当前章节" }}
        </div>
      </aside>
    </div>
  </el-dialog>
</template>

<style scoped>
.ta-help-center-header,
.ta-help-center-heading,
.ta-help-center-pet-title,
.ta-help-center-nav-caption {
  display: flex;
  align-items: center;
}

.ta-help-center-header {
  justify-content: space-between;
  gap: 16px;
  padding-right: 30px;
}

.ta-help-center-heading {
  gap: 10px;
}

.ta-help-center-mark,
.ta-help-center-pet-title > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: 10px;
  background: rgb(49 91 117 / 12%);
  color: var(--ta-accent, #315b75);
}

.ta-help-center-heading h2 {
  margin: 0;
  color: var(--ta-text, #18202f);
  font-size: 16px;
  line-height: 22px;
}

.ta-help-center-heading p {
  margin: 2px 0 0;
  color: var(--ta-muted, #667085);
  font-size: 12px;
  line-height: 17px;
}

.ta-help-center-new-tab {
  gap: 5px;
}

.ta-help-center-shell {
  display: grid;
  grid-template-columns: 184px minmax(0, 1fr) 250px;
  height: min(690px, calc(100vh - 118px));
  min-height: 480px;
  overflow: hidden;
  border-top: 1px solid var(--ta-border, #e5e7eb);
  border-bottom: 1px solid var(--ta-border, #e5e7eb);
}

.ta-help-center-nav,
.ta-help-center-pet {
  min-width: 0;
  overflow: auto;
  background: var(--ta-panel-2, #f8fafc);
}

.ta-help-center-nav {
  padding: 12px 9px;
  border-right: 1px solid var(--ta-border, #e5e7eb);
}

.ta-help-center-nav-caption {
  gap: 5px;
  padding: 0 8px 9px;
  color: var(--ta-muted, #667085);
  font-size: 12px;
  font-weight: 600;
}

.ta-help-center-topic {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 2px;
  margin: 1px 0;
  padding: 9px 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--ta-text, #18202f);
  cursor: pointer;
  text-align: left;
}

.ta-help-center-topic:hover,
.ta-help-center-topic.is-active {
  border-color: var(--ta-border, #d8dee9);
  background: #fff;
}

.ta-help-center-topic.is-active {
  box-shadow: inset 3px 0 0 var(--ta-accent, #315b75);
}

.ta-help-center-topic strong {
  font-size: 13px;
  line-height: 18px;
}

.ta-help-center-topic span {
  color: var(--ta-muted, #667085);
  font-size: 11px;
  line-height: 16px;
}

.ta-help-center-manual {
  min-width: 0;
  min-height: 0;
  background: #fff;
}

.ta-help-center-manual iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  background: #fff;
}

.ta-help-center-pet {
  padding: 16px;
  border-left: 1px solid var(--ta-border, #e5e7eb);
}

.ta-help-center-pet-title {
  gap: 9px;
}

.ta-help-center-pet-title > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.ta-help-center-pet-title strong {
  font-size: 13px;
  line-height: 18px;
}

.ta-help-center-pet-title small {
  overflow: hidden;
  color: var(--ta-muted, #667085);
  font-size: 11px;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ta-help-center-pet-tip,
.ta-help-center-pet-unavailable {
  margin: 10px 0;
  color: var(--ta-muted, #667085);
  font-size: 12px;
  line-height: 18px;
}

.ta-help-center-ask {
  width: 100%;
  margin-top: 10px;
}

.ta-help-center-pet-answer,
.ta-help-center-pet-error,
.ta-help-center-pet-progress {
  margin-top: 12px;
  padding: 10px;
  border: 1px solid var(--ta-border, #d8dee9);
  border-radius: 8px;
  background: #fff;
  color: var(--ta-text, #18202f);
  font-size: 12px;
  line-height: 18px;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.ta-help-center-pet-error {
  border-color: #f3c7c3;
  color: #b42318;
}

.ta-help-center-pet-progress {
  color: var(--ta-muted, #667085);
}

@media (max-width: 920px) {
  .ta-help-center-shell {
    grid-template-columns: 150px minmax(0, 1fr);
  }

  .ta-help-center-pet {
    display: none;
  }
}
</style>

<style>
.el-dialog.ta-help-center-dialog {
  max-height: calc(100vh - 40px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.el-dialog.ta-help-center-dialog .el-dialog__header {
  padding: 12px 16px;
  margin: 0;
}

.el-dialog.ta-help-center-dialog .el-dialog__body {
  min-height: 0;
  padding: 0;
  overflow: hidden;
}
</style>
