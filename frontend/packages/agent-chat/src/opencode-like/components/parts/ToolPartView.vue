<script lang="ts">
import type { Component } from "vue";
import type { MessagePart, SubagentSession } from "@test-agent/shared-types";

export type ToolPartViewProps = {
  part: Extract<MessagePart, { type: "tool" }>;
  subagentsBySessionId?: Record<string, SubagentSession>;
  subagentByTaskPartId?: Record<string, string>;
  nested?: boolean;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import GenericToolView from "../tools/GenericToolView.vue";
import BashToolView from "../tools/BashToolView.vue";
import ReadToolView from "../tools/ReadToolView.vue";
import ListToolView from "../tools/ListToolView.vue";
import GlobToolView from "../tools/GlobToolView.vue";
import GrepToolView from "../tools/GrepToolView.vue";
import EditToolView from "../tools/EditToolView.vue";
import WriteToolView from "../tools/WriteToolView.vue";
import ApplyPatchToolView from "../tools/ApplyPatchToolView.vue";
import WebFetchToolView from "../tools/WebFetchToolView.vue";
import WebSearchToolView from "../tools/WebSearchToolView.vue";
import TaskToolView from "../tools/TaskToolView.vue";
import SkillToolView from "../tools/SkillToolView.vue";
import QuestionToolView from "../tools/QuestionToolView.vue";
import { normalizeToolName } from "../../state/tool-registry";

const props = defineProps<ToolPartViewProps>();
const emit = defineEmits<{ selectSubagent: [sessionId: string] }>();

const component = computed<Component>(() => {
  const name = normalizeToolName(props.part);
  if (name === "bash") return BashToolView;
  if (name === "read") return ReadToolView;
  if (name === "list") return ListToolView;
  if (name === "glob") return GlobToolView;
  if (name === "grep") return GrepToolView;
  if (name === "edit") return EditToolView;
  if (name === "write") return WriteToolView;
  if (name === "apply_patch") return ApplyPatchToolView;
  if (name === "webfetch" || name === "web_fetch") return WebFetchToolView;
  if (name === "websearch" || name === "web_search") return WebSearchToolView;
  if (name === "task") return TaskToolView;
  if (name === "skill") return SkillToolView;
  if (name === "question") return QuestionToolView;
  return GenericToolView;
});

const subagent = computed(() => {
  const sessionId = props.subagentByTaskPartId?.[props.part.partId];
  return sessionId ? props.subagentsBySessionId?.[sessionId] : undefined;
});
</script>

<template>
  <component
    :is="component"
    :part="part"
    :subagent="subagent"
    :nested="nested"
    @select-subagent="(sessionId: string) => emit('selectSubagent', sessionId)"
  />
</template>
