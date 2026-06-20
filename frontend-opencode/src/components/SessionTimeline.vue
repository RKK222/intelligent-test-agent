<script setup lang="ts">
import type { MessagePart, SessionMessage } from "@test-agent/shared-types";
import { Bot, Brain, FileText, RadioTower, UserRound, Wrench } from "lucide-vue-next";

type TimelinePart = MessagePart & { text?: string; payload?: Record<string, unknown>; eventType?: string };
type TimelineMessage = SessionMessage & { parts?: TimelinePart[] };

defineProps<{ messages: TimelineMessage[] }>();

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
              <header>
                <Brain v-if="part.type === 'reasoning'" :size="14" />
                <Wrench v-else-if="part.type === 'tool'" :size="14" />
                <FileText v-else-if="part.type === 'file'" :size="14" />
                <RadioTower v-else :size="14" />
                <span>{{ partTitle(part) }}</span>
                <small v-if="'status' in part && part.status">{{ part.status }}</small>
              </header>
              <p v-if="part.type === 'reasoning'">{{ textFromPart(part) }}</p>
              <pre v-else-if="part.type === 'tool' && (part.input || part.output)">{{ payloadPreview(part.output ?? part.input) }}</pre>
              <p v-else-if="part.type === 'file'">{{ part.path ?? part.name }}</p>
              <pre v-else-if="part.type === 'event'">{{ payloadPreview(part.payload) }}</pre>
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
