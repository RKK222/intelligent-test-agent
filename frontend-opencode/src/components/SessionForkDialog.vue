<script setup lang="ts">
import type { SessionMessage } from "@test-agent/shared-types";
import { GitBranch, X } from "lucide-vue-next";

defineProps<{
  messages: SessionMessage[];
  busy?: boolean;
}>();

defineEmits<{
  close: [];
  select: [messageId: string];
}>();

function previewMessage(content: string) {
  return content.replace(/\s+/g, " ").trim().slice(0, 180) || "Empty prompt";
}

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
}
</script>

<template>
  <div class="modal-backdrop" @click.self="$emit('close')">
    <section class="fork-dialog" role="dialog" aria-modal="true" aria-label="Fork session">
      <header>
        <div>
          <p class="eyebrow">Session</p>
          <h2>Fork from message</h2>
        </div>
        <button class="icon-button" type="button" aria-label="Close fork dialog" @click="$emit('close')">
          <X :size="15" />
        </button>
      </header>

      <div v-if="messages.length" class="fork-list" role="list">
        <button
          v-for="message in messages"
          :key="message.messageId"
          class="fork-row"
          type="button"
          :aria-label="`Fork from ${previewMessage(message.content)}`"
          :disabled="busy"
          @click="$emit('select', message.messageId)"
        >
          <GitBranch :size="15" />
          <span>
            <strong>{{ previewMessage(message.content) }}</strong>
            <small>{{ formatTime(message.createdAt) }}</small>
          </span>
        </button>
      </div>
      <div v-else class="empty-state fork-empty">
        <GitBranch :size="28" />
        <strong>No messages to fork from</strong>
        <span>Send a prompt before creating a fork.</span>
      </div>
    </section>
  </div>
</template>
