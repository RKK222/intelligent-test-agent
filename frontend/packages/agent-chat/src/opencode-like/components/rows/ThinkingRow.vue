<script setup lang="ts">
import { Bot } from "lucide-vue-next";
</script>

<template>
  <div class="oc-thinking-frame oc-assistant-frame has-header">
    <div class="oc-assistant-frame__avatar" aria-hidden="true">
      <Bot class="oc-assistant-frame__avatar-icon" />
    </div>
    <div class="oc-assistant-frame__content">
      <div class="oc-thinking-row">
        <span class="oc-thinking-dot" aria-hidden="true" />
        <span>思考中</span>
      </div>
    </div>
  </div>
</template>
