<!-- 作废说明：旧气泡消息 part 渲染路径已被 opencode-like/OpencodeTimeline 取代；仅为历史兼容保留，不再扩展新能力。 -->
<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type AgentChipProps = {
  part: Extract<MessagePart, { type: "agent" }>;
};
</script>

<script setup lang="ts">
import { Bot } from "lucide-vue-next";
import PartMarker from "./PartMarker.vue";

defineProps<AgentChipProps>();
</script>

<template>
  <PartMarker :icon="Bot" accent="muted" :test-id="`agent-part-${part.partId}`">
    <span class="text-[var(--ta-chat-subtle)]">Agent</span>
    <span class="text-[var(--ta-chat-text)]">· {{ part.name }}</span>
    <span v-if="part.source" class="text-[var(--ta-chat-muted)]">· 来源 {{ part.source.value }}</span>
  </PartMarker>
</template>
