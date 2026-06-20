<script setup lang="ts">
import { ref } from "vue";
import { Copy, ExternalLink, Share2, Trash2 } from "lucide-vue-next";
import { useSessionStore } from "@/stores/session";

const session = useSessionStore();
const open = ref(false);
const copyState = ref<"idle" | "copied" | "failed">("idle");

async function publish() {
  const url = await session.publishShare();
  await copyShareLink(url);
}

async function unpublish() {
  await session.unpublishShare();
  copyState.value = "idle";
}

async function copyShareLink(value = session.shareUrl) {
  if (!value) {
    return;
  }
  try {
    await navigator.clipboard?.writeText(value);
    copyState.value = "copied";
  } catch {
    copyState.value = "failed";
  }
}
</script>

<template>
  <div class="share-popover-wrap">
    <button class="icon-text" type="button" aria-label="Share session" @click="open = !open">
      <Share2 :size="15" />{{ session.shareUrl ? "Shared" : "Share" }}
    </button>
    <section v-if="open" class="session-share-popover" role="dialog" aria-label="Session share">
      <div>
        <p class="section-label">Publish on web</p>
        <p v-if="session.shareUrl" class="share-copy">This session is public to anyone with the link.</p>
        <p v-else class="share-copy">Create a public opencode share link through the platform backend.</p>
      </div>
      <button
        v-if="!session.shareUrl"
        class="primary-action share-wide"
        type="button"
        aria-label="Publish session"
        :disabled="session.sharing"
        @click="publish"
      >
        <Share2 :size="14" />{{ session.sharing ? "Publishing..." : "Publish" }}
      </button>
      <template v-else>
        <input class="share-url-input" :value="session.shareUrl" readonly aria-label="Shared session URL" />
        <div class="share-actions">
          <button class="icon-text" type="button" aria-label="Copy share link" @click="copyShareLink()">
            <Copy :size="14" />{{ copyState === "copied" ? "Copied" : "Copy" }}
          </button>
          <button
            class="icon-text danger-action"
            type="button"
            aria-label="Unpublish session"
            :disabled="session.sharing"
            @click="unpublish"
          >
            <Trash2 :size="14" />{{ session.sharing ? "Unpublishing..." : "Unpublish" }}
          </button>
          <a class="primary-action" :href="session.shareUrl" target="_blank" rel="noreferrer" aria-label="View shared session">
            <ExternalLink :size="14" />View
          </a>
        </div>
      </template>
      <div v-if="session.shareError || copyState === 'failed'" class="inline-alert">
        {{ session.shareError ?? "Share URL copy failed" }}
      </div>
    </section>
  </div>
</template>
