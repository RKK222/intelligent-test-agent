<script setup lang="ts">
import { computed, ref } from "vue";
import type { CommandInfo } from "@test-agent/shared-types";
import { AtSign, FileCode2, ImagePlus, Paperclip, Search, SendHorizontal, Slash } from "lucide-vue-next";
import { usePromptStore } from "@/stores/prompt";
import { useWorkspaceStore } from "@/stores/workspace";

defineProps<{ busy?: boolean }>();
const emit = defineEmits<{ submit: [] }>();
const prompt = usePromptStore();
const workspace = useWorkspaceStore();
const slashOpen = ref(false);
const slashQuery = ref("");
const lineCount = computed(() => Math.max(3, Math.min(12, prompt.text.split(/\r?\n/).length + 1)));
const slashCommands = computed(() => {
  const query = slashQuery.value.trim().toLowerCase();
  if (!query) {
    return workspace.commands;
  }
  return workspace.commands.filter((command) =>
    `${command.name} ${command.description ?? ""} ${command.arguments ?? ""}`.toLowerCase().includes(query)
  );
});

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

async function toggleSlashCommands() {
  slashOpen.value = !slashOpen.value;
  if (slashOpen.value) {
    await workspace.loadCommands();
  }
}

function selectSlashCommand(command: CommandInfo) {
  prompt.insertSlashCommand(command);
  slashOpen.value = false;
  slashQuery.value = "";
}
</script>

<template>
  <form class="composer" aria-label="Prompt composer" @submit.prevent="emit('submit')">
    <div class="composer-toolbar" aria-label="Prompt tools">
      <button type="button" class="tool-button" title="Attach file" aria-label="Attach file" @click="addFile">
        <Paperclip :size="15" />
      </button>
      <button type="button" class="tool-button" title="Attach image" aria-label="Attach image">
        <ImagePlus :size="15" />
      </button>
      <button type="button" class="tool-button" title="Mention file or symbol" aria-label="Mention file or symbol" @click="addReference">
        <AtSign :size="15" />
      </button>
      <div class="composer-tool-wrap">
        <button type="button" class="tool-button" title="Slash command" aria-label="Open slash commands" @click="toggleSlashCommands">
          <Slash :size="15" />
        </button>
        <section v-if="slashOpen" class="slash-menu" role="dialog" aria-label="Slash commands">
          <label class="slash-search">
            <Search :size="14" />
            <input v-model="slashQuery" aria-label="Search slash commands" autocomplete="off" placeholder="Search commands" />
          </label>
          <div class="slash-list">
            <button
              v-for="command in slashCommands"
              :key="command.commandId"
              class="slash-row"
              type="button"
              :aria-label="`/${command.name} ${command.description ?? command.arguments ?? 'opencode command'}`"
              @click="selectSlashCommand(command)"
            >
              <strong>/{{ command.name }}</strong>
              <small>{{ command.description ?? command.arguments ?? "opencode command" }}</small>
            </button>
          </div>
          <div v-if="!slashCommands.length" class="empty-note">No matching commands</div>
        </section>
      </div>
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
