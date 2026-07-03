<!-- 作废说明：旧结构化卡片主路径已被 opencode-like/OpencodeTimeline 取代；仅为历史兼容保留，不再扩展新能力。 -->
<script lang="ts">
export type ToolPayloadBlockProps = {
  id: string;
  title: string;
  payload: Record<string, unknown>;
  defaultOpen: boolean;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import ProcessDisclosure from "./ProcessDisclosure.vue";
import ToolDetail from "./ToolDetail.vue";
import { record, skillNameFromPayload, text } from "./chat-utils";

const props = defineProps<ToolPayloadBlockProps>();
const toolName = computed(() => text(props.payload.toolName) ?? text(props.payload.tool) ?? text(props.payload.rawType) ?? "tool");
const skill = computed(() => toolName.value.toLowerCase() === "skill");
const status = computed(() => text(props.payload.status) ?? (props.title.includes("开始") ? "running" : "completed"));
const purpose = computed(() => text(props.payload.summary) ?? text(props.payload.message) ?? text(props.payload.title));
const input = computed(() => record(props.payload.input));
const metadata = computed(() => record(props.payload.metadata));
const output = computed(() => props.payload.output ?? props.payload.rawOutput);
const name = computed(() => (skill.value ? skillNameFromPayload(props.payload) : toolName.value));
const cardTitle = computed(() => (skill.value ? "Skill 调用" : props.title));
const summaryText = computed(() =>
  skill.value
    ? `${name.value ?? ""}${name.value && purpose.value ? "｜" : ""}${purpose.value ?? ""}`
    : `${toolName.value}${purpose.value ? `｜${purpose.value}` : ""}`
);
</script>

<template>
  <ProcessDisclosure
    :id="id"
    :title="cardTitle"
    :status="status"
    :status-kind="skill ? 'skill' : 'tool'"
    :summary="summaryText"
    :default-open="defaultOpen"
    :test-id="`timeline-card-${id}`"
  >
    <ToolDetail
      :label="name ?? toolName"
      :status="status"
      :purpose="purpose"
      :input="input"
      :output="output"
      :metadata="metadata"
      :path="text(payload.path)"
      :status-kind="skill ? 'skill' : 'tool'"
      :started-at="text(payload.startedAt)"
      :ended-at="text(payload.endedAt)"
    />
  </ProcessDisclosure>
</template>
