<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type ReasoningPartViewProps = {
  part: Extract<MessagePart, { type: "reasoning" }>;
  streamingTextByPartId?: Record<string, string>;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import OcDisclosure from "../primitives/OcDisclosure.vue";
import { compactPartPreview, readPartText } from "../../state/part-text";

const props = defineProps<ReasoningPartViewProps>();
const source = computed(() => readPartText(props.part, props.streamingTextByPartId));

const subtitleText = computed(() => {
  const status = props.part.status;
  if (status === "completed" || status === "success") {
    return "已完成";
  }
  if (status === "running") {
    return "思考中";
  }
  if (status === "failed" || status === "error") {
    return "失败";
  }
  return status;
});

const detailText = computed(() => compactPartPreview(source.value));
</script>

<template>
  <OcDisclosure
    class="oc-reasoning-part"
    title="思考状态"
    :detail="detailText"
    :subtitle="subtitleText"
    :status="part.status"
    :default-open="false"
  >
    <div class="oc-reasoning-part__body">
      <!-- 单条 reasoning 同样使用纯文本，避免展开时触发 Markdown 动态加载和高亮渲染。 -->
      <pre class="oc-reasoning-part__plain">{{ source || "暂无详细思考内容" }}</pre>
    </div>
  </OcDisclosure>
</template>
