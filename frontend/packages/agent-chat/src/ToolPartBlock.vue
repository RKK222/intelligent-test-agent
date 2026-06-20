<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type ToolPartBlockProps = {
  part: Extract<MessagePart, { type: "tool" }>;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import ProcessDisclosure from "./ProcessDisclosure.vue";
import ToolDetail from "./ToolDetail.vue";
import { normalizeProcessStatus, toolPartIsSkill } from "./process-status";
import { skillNameFromPart, toolPurpose } from "./chat-utils";

const props = defineProps<ToolPartBlockProps>();
const skill = computed(() => toolPartIsSkill(props.part));
const purpose = computed(() => toolPurpose(props.part));
const skillName = computed(() => (skill.value ? skillNameFromPart(props.part) : undefined));
const title = computed(() => (skill.value ? "Skill 调用" : "能力调用"));
const summaryText = computed(() =>
  skill.value
    ? `${skillName.value ?? ""}${skillName.value && purpose.value ? "｜" : ""}${purpose.value ?? ""}`
    : `${props.part.toolName}${purpose.value ? `｜${purpose.value}` : ""}`
);
const defaultOpen = computed(() => normalizeProcessStatus(props.part.status) === "running");
const detailLabel = computed(() => (skill.value ? skillName.value ?? "Skill 调用" : props.part.toolName));
</script>

<template>
  <ProcessDisclosure
    :id="part.partId"
    :title="title"
    :status="part.status"
    :status-kind="skill ? 'skill' : 'tool'"
    :summary="summaryText"
    :default-open="defaultOpen"
    :test-id="`${skill ? 'skill' : 'tool'}-part-${part.partId}`"
  >
    <ToolDetail
      :label="detailLabel"
      :status="part.status"
      :purpose="purpose"
      :input="part.input"
      :output="part.output"
      :metadata="part.metadata"
      :status-kind="skill ? 'skill' : 'tool'"
      :started-at="part.startedAt"
      :ended-at="part.endedAt"
    />
  </ProcessDisclosure>
</template>
