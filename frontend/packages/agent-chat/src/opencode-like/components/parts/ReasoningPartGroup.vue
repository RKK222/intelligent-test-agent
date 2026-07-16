<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";
import type { WorkStatusState } from "../../state/types";

export type ReasoningPartGroupProps = {
  parts: Array<Extract<MessagePart, { type: "reasoning" }>>;
  busy?: boolean;
  streamingTextByPartId?: Record<string, string>;
  statusOverride?: WorkStatusState;
  showEmptyStatus?: boolean;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import OcDisclosure from "../primitives/OcDisclosure.vue";
import { compactPartPreview, readPartText } from "../../state/part-text";

const props = defineProps<ReasoningPartGroupProps>();

const source = computed(() =>
  props.parts
    .map((part) => readPartText(part, props.streamingTextByPartId).trim())
    .filter(Boolean)
    .join("\n\n")
);

const aggregateStatus = computed(() => {
  if (props.statusOverride) return props.statusOverride;
  if (props.busy) return "running";
  const statuses = props.parts.map((part) => part.status ?? "").map((status) => status.toLowerCase());
  if (statuses.some((status) => status === "running" || status === "pending")) return "running";
  if (statuses.some((status) => status === "failed" || status === "error")) return "failed";
  if (statuses.some((status) => status === "completed" || status === "success")) return "completed";
  return props.parts.at(-1)?.status;
});

const subtitleText = computed(() => {
  if (aggregateStatus.value === "running") return "思考中";
  if (aggregateStatus.value === "retry") return "重试中";
  if (aggregateStatus.value === "failed" || aggregateStatus.value === "error") return "失败";
  if (aggregateStatus.value === "cancelled") return "已停止";
  return "已完成";
});

const detailText = computed(() => compactPartPreview(source.value));
const hasDetail = computed(() => source.value.trim().length > 0);
</script>

<template>
  <OcDisclosure
    v-if="hasDetail || !showEmptyStatus"
    class="oc-reasoning-part"
    title="思考状态"
    :detail="detailText"
    :subtitle="subtitleText"
    :status="aggregateStatus"
    :default-open="false"
  >
    <div class="oc-reasoning-part__body">
      <!-- 思考过程常在用户点击时才展开；用纯文本避免首次挂载 Markdown/highlight 造成卡顿。 -->
      <pre class="oc-reasoning-part__plain">{{ source || "暂无详细思考内容" }}</pre>
    </div>
  </OcDisclosure>
  <section v-else class="oc-reasoning-part oc-disclosure">
    <div class="oc-disclosure__trigger oc-disclosure__trigger--static" role="status" aria-live="polite">
      <span class="oc-tool__title">思考状态</span>
      <span :class="['oc-tool__status', aggregateStatus ? `is-${aggregateStatus}` : '']">{{ subtitleText }}</span>
    </div>
  </section>
</template>
