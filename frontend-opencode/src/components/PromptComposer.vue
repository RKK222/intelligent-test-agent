<script setup lang="ts">
import { computed } from "vue";
import { AtSign, FileCode2, ImagePlus, Paperclip, SendHorizontal, Slash } from "lucide-vue-next";
import { usePromptStore } from "@/stores/prompt";

defineProps<{ busy?: boolean }>();
const emit = defineEmits<{ submit: [] }>();
const prompt = usePromptStore();
const lineCount = computed(() => Math.max(3, Math.min(12, prompt.text.split(/\r?\n/).length + 1)));

function addReference() {
  const id = `ref_${prompt.references.length + 1}`;
  prompt.references.push({ id, label: "Current selection", uri: "selection://editor" });
}

function addFile() {
  const path = "src/App.vue";
  if (!prompt.files.some((item) => item.path === path)) {
    prompt.files.push({ path, name: "App.vue" });
  }
}
</script>

<template>
  <form class="composer" aria-label="Prompt composer" @submit.prevent="emit('submit')">
    <div class="composer-toolbar" aria-label="Prompt tools">
      <button type="button" class="tool-button" title="Attach file" @click="addFile">
        <Paperclip :size="15" />
      </button>
      <button type="button" class="tool-button" title="Attach image">
        <ImagePlus :size="15" />
      </button>
      <button type="button" class="tool-button" title="Mention file or symbol" @click="addReference">
        <AtSign :size="15" />
      </button>
      <button type="button" class="tool-button" title="Slash command">
        <Slash :size="15" />
      </button>
      <label class="shell-toggle">
        <input v-model="prompt.shellMode" type="checkbox" />
        <span>shell</span>
      </label>
    </div>

    <div class="composer-context" v-if="prompt.files.length || prompt.references.length || prompt.agents.length">
      <span v-for="file in prompt.files" :key="file.path ?? file.name" class="context-chip">
        <FileCode2 :size="13" />{{ file.path ?? file.name }}
      </span>
      <span v-for="reference in prompt.references" :key="reference.id" class="context-chip">@{{ reference.label }}</span>
      <span v-for="agent in prompt.agents" :key="agent.agentId" class="context-chip">agent: {{ agent.name ?? agent.agentId }}</span>
    </div>

    <textarea
      v-model="prompt.text"
      :rows="lineCount"
      placeholder="Ask opencode to inspect, edit, test, or explain this workspace..."
      @keydown.meta.enter.prevent="emit('submit')"
      @keydown.ctrl.enter.prevent="emit('submit')"
    />

    <div class="composer-footer">
      <span>{{ prompt.parts.length }} part{{ prompt.parts.length === 1 ? "" : "s" }}</span>
      <button class="send-button" type="submit" :disabled="busy || !prompt.canSubmit">
        <SendHorizontal :size="16" />
        <span>{{ busy ? "Sending" : "Send" }}</span>
      </button>
    </div>
  </form>
</template>
