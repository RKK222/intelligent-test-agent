<script setup lang="ts">
import type { MessagePart, SessionMessage } from "@test-agent/shared-types";
import { ref } from "vue";
import { Bot, Brain, ChevronDown, FileText, RadioTower, UserRound, Wrench } from "lucide-vue-next";

type TimelinePart = MessagePart & { text?: string; payload?: Record<string, unknown>; eventType?: string };
type TimelineMessage = SessionMessage & { parts?: TimelinePart[] };

defineProps<{ messages: TimelineMessage[] }>();

const partOpenOverrides = ref<Record<string, boolean>>({});

function roleLabel(message: TimelineMessage) {
  return message.role.toUpperCase() === "USER" ? "You" : "opencode";
}

function messageLabel(message: TimelineMessage) {
  return `${roleLabel(message)} message`;
}

function visibleParts(message: TimelineMessage) {
  return (message.parts ?? []).filter((part) => part.type !== "text" || textFromPart(part));
}

function textFromPart(part: TimelinePart) {
  return typeof part.text === "string" ? part.text : "";
}

function partTitle(part: TimelinePart) {
  if (part.type === "reasoning") {
    return part.title ?? "Reasoning";
  }
  if (part.type === "tool") {
    return part.toolName;
  }
  if (part.type === "file") {
    return part.path ?? part.name ?? "file";
  }
  if (part.type === "event") {
    return part.eventType;
  }
  return part.type;
}

function partRegionLabel(part: TimelinePart) {
  if (part.type === "reasoning") {
    return `Reasoning ${partTitle(part)}`;
  }
  if (part.type === "tool") {
    return `Tool ${partTitle(part)}`;
  }
  if (part.type === "file") {
    return `File ${partTitle(part)}`;
  }
  if (part.type === "event") {
    return `Event ${partTitle(part)}`;
  }
  return `Message part ${partTitle(part)}`;
}

function partKey(message: TimelineMessage, part: TimelinePart) {
  return `${message.messageId}:${part.partId}`;
}

function partStatus(part: TimelinePart) {
  return "status" in part ? part.status : undefined;
}

function isCollapsiblePart(part: TimelinePart) {
  return part.type === "reasoning" || part.type === "tool";
}

function partDefaultOpen(part: TimelinePart) {
  if (!isCollapsiblePart(part)) {
    return true;
  }
  const status = partStatus(part);
  // opencode 原 App 会让运行中和异常 part 保持展开，已完成的工具/推理摘要交给用户按需展开。
  return status === "running" || status === "pending" || status === "error";
}

function isPartOpen(message: TimelineMessage, part: TimelinePart) {
  const override = partOpenOverrides.value[partKey(message, part)];
  return override ?? partDefaultOpen(part);
}

function setPartOpen(message: TimelineMessage, part: TimelinePart, open: boolean) {
  partOpenOverrides.value = {
    ...partOpenOverrides.value,
    [partKey(message, part)]: open
  };
}

function togglePartOpen(message: TimelineMessage, part: TimelinePart) {
  setPartOpen(message, part, !isPartOpen(message, part));
}

function partToggleLabel(message: TimelineMessage, part: TimelinePart) {
  return `${isPartOpen(message, part) ? "Collapse" : "Expand"} ${partRegionLabel(part)}`;
}

function payloadPreview(value: unknown) {
  if (value === undefined || value === null || value === "") {
    return "";
  }
  if (typeof value === "string") {
    return value;
  }
  // Tool/event payload 可能是结构化 JSON，这里只做只读摘要，避免 timeline 被大对象撑开。
  return JSON.stringify(value, null, 2);
}
</script>

<template>
  <section class="timeline" aria-label="Session timeline">
    <article
      v-for="message in messages"
      :key="message.messageId"
      class="message-row"
      :data-role="message.role.toLowerCase()"
      :aria-label="messageLabel(message)"
    >
      <div class="message-avatar">
        <UserRound v-if="message.role.toUpperCase() === 'USER'" :size="16" />
        <Bot v-else :size="16" />
      </div>
      <div class="message-body">
        <div class="message-meta">
          <strong>{{ message.role.toUpperCase() === "USER" ? "You" : "opencode" }}</strong>
          <span>{{ message.createdAt }}</span>
        </div>
        <div v-if="visibleParts(message).length" class="message-parts">
          <template v-for="part in visibleParts(message)" :key="part.partId">
            <p v-if="part.type === 'text'" class="message-text-part">{{ textFromPart(part) }}</p>
            <section
              v-else
              class="message-part-card"
              :data-part-type="part.type"
              :data-state="'status' in part ? part.status : undefined"
              role="region"
              :aria-label="partRegionLabel(part)"
            >
              <button
                v-if="isCollapsiblePart(part)"
                class="message-part-toggle"
                type="button"
                :aria-expanded="isPartOpen(message, part)"
                :aria-label="partToggleLabel(message, part)"
                @click="togglePartOpen(message, part)"
              >
                <Brain v-if="part.type === 'reasoning'" :size="14" />
                <Wrench v-else :size="14" />
                <span>{{ partTitle(part) }}</span>
                <small v-if="partStatus(part)">{{ partStatus(part) }}</small>
                <ChevronDown class="message-part-chevron" :class="{ flipped: isPartOpen(message, part) }" :size="14" />
              </button>
              <header v-else>
                <FileText v-if="part.type === 'file'" :size="14" />
                <RadioTower v-else :size="14" />
                <span>{{ partTitle(part) }}</span>
                <small v-if="partStatus(part)">{{ partStatus(part) }}</small>
              </header>
              <template v-if="isPartOpen(message, part)">
                <p v-if="part.type === 'reasoning'">{{ textFromPart(part) }}</p>
                <pre v-else-if="part.type === 'tool' && (part.input || part.output)">{{ payloadPreview(part.output ?? part.input) }}</pre>
                <p v-else-if="part.type === 'file'">{{ part.path ?? part.name }}</p>
                <pre v-else-if="part.type === 'event'">{{ payloadPreview(part.payload) }}</pre>
              </template>
            </section>
          </template>
        </div>
        <p v-else>{{ message.content }}</p>
      </div>
    </article>
    <div v-if="!messages.length" class="empty-state timeline-empty">
      <Bot :size="30" />
      <strong>No messages yet</strong>
      <span>Use the composer to start a platform-backed opencode run.</span>
    </div>
  </section>
</template>
