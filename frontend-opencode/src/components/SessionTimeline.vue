<script setup lang="ts">
import type { SessionMessage } from "@test-agent/shared-types";
import { Bot, UserRound } from "lucide-vue-next";

defineProps<{ messages: SessionMessage[] }>();
</script>

<template>
  <section class="timeline" aria-label="Session timeline">
    <article v-for="message in messages" :key="message.messageId" class="message-row" :data-role="message.role.toLowerCase()">
      <div class="message-avatar">
        <UserRound v-if="message.role.toUpperCase() === 'USER'" :size="16" />
        <Bot v-else :size="16" />
      </div>
      <div class="message-body">
        <div class="message-meta">
          <strong>{{ message.role.toUpperCase() === "USER" ? "You" : "opencode" }}</strong>
          <span>{{ message.createdAt }}</span>
        </div>
        <p>{{ message.content }}</p>
      </div>
    </article>
    <div v-if="!messages.length" class="empty-state timeline-empty">
      <Bot :size="30" />
      <strong>No messages yet</strong>
      <span>Use the composer to start a platform-backed opencode run.</span>
    </div>
  </section>
</template>
