<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ChevronLeft, ChevronRight, FileDiff } from "lucide-vue-next";
import type { RunDiffFile } from "@test-agent/shared-types";
import MonacoDiffEditor from "@/components/MonacoDiffEditor.vue";

type DiffLine = { id: string; text: string; kind: "hunk" | "add" | "del" | "ctx" };
type DiffHunk = { id: string; header: string; lines: DiffLine[] };

const props = defineProps<{ files: RunDiffFile[] }>();
const activePath = ref("");
const hunkIndex = ref(0);
const diffStyle = ref<"unified" | "split">("unified");

const totals = computed(() =>
  props.files.reduce(
    (next, file) => ({
      additions: next.additions + file.additions,
      deletions: next.deletions + file.deletions,
      hunks: next.hunks + parseHunks(file.patch).length
    }),
    { additions: 0, deletions: 0, hunks: 0 }
  )
);
const activeFile = computed(() => props.files.find((file) => file.path === activePath.value) ?? props.files[0]);
const activeHunks = computed(() => (activeFile.value ? parseHunks(activeFile.value.patch) : []));
const activeHunk = computed(() => activeHunks.value[hunkIndex.value]);

watch(
  () => props.files.map((file) => file.path).join("\n"),
  () => {
    activePath.value = props.files[0]?.path ?? "";
    hunkIndex.value = 0;
  },
  { immediate: true }
);

function selectFile(path: string) {
  activePath.value = path;
  hunkIndex.value = 0;
}

function nextHunk() {
  hunkIndex.value = Math.min(hunkIndex.value + 1, activeHunks.value.length - 1);
}

function previousHunk() {
  hunkIndex.value = Math.max(hunkIndex.value - 1, 0);
}

// 轻量解析 unified patch，保留 hunk 边界以复刻 review 面板的跳转体验。
function parseHunks(patch: string) {
  const hunks: DiffHunk[] = [];
  let current: DiffHunk | undefined;
  patch.split("\n").forEach((line, index) => {
    if (line.startsWith("@@")) {
      current = { id: `${index}-${line}`, header: line, lines: [] };
      hunks.push(current);
      return;
    }
    if (!current) {
      current = { id: `implicit-${index}`, header: "File changes", lines: [] };
      hunks.push(current);
    }
    current.lines.push({
      id: `${index}-${line}`,
      text: line,
      kind: line.startsWith("+") ? "add" : line.startsWith("-") ? "del" : "ctx"
    });
  });
  return hunks;
}
</script>

<template>
  <section class="diff-review-panel" aria-label="Diff review" role="region">
    <template v-if="files.length">
      <header class="diff-review-header">
        <div>
          <div class="section-label">Diff review</div>
          <strong>{{ files.length }} {{ files.length === 1 ? "file" : "files" }}</strong>
          <small>+{{ totals.additions }} -{{ totals.deletions }} · {{ totals.hunks }} {{ totals.hunks === 1 ? "hunk" : "hunks" }}</small>
        </div>
        <div class="diff-style-toggle" aria-label="Diff style">
          <button type="button" :aria-pressed="diffStyle === 'unified'" @click="diffStyle = 'unified'">Unified</button>
          <button type="button" :aria-pressed="diffStyle === 'split'" @click="diffStyle = 'split'">Split</button>
        </div>
      </header>

      <div class="diff-review-layout">
        <nav class="diff-file-list" aria-label="Changed files">
          <button
            v-for="file in files"
            :key="file.path"
            type="button"
            :aria-current="activeFile?.path === file.path"
            :aria-label="`${file.path} ${file.status} +${file.additions} -${file.deletions}`"
            @click="selectFile(file.path)"
          >
            <span class="diff-kind-dot" :data-state="file.status" />
            <span>
              <strong>{{ file.path }}</strong>
              <small>{{ file.status }} · +{{ file.additions }} -{{ file.deletions }}</small>
            </span>
          </button>
        </nav>

        <div class="diff-file-detail" :data-style="diffStyle">
          <div class="diff-detail-toolbar">
            <span><FileDiff :size="14" />{{ activeFile?.path }}</span>
            <div class="diff-hunk-nav">
              <button class="icon-button" type="button" aria-label="Previous hunk" :disabled="hunkIndex === 0" @click="previousHunk">
                <ChevronLeft :size="14" />
              </button>
              <small v-if="activeHunks.length">Hunk {{ hunkIndex + 1 }} / {{ activeHunks.length }}</small>
              <small v-else>No hunks</small>
              <button
                class="icon-button"
                type="button"
                aria-label="Next hunk"
                :disabled="hunkIndex >= activeHunks.length - 1"
                @click="nextHunk"
              >
                <ChevronRight :size="14" />
              </button>
            </div>
          </div>

          <MonacoDiffEditor :file="activeFile" :diff-style="diffStyle" />

          <div v-if="activeHunk" class="diff-hunk">
            <code class="diff-hunk-header">{{ activeHunk.header }}</code>
            <code v-for="line in activeHunk.lines" :key="line.id" class="diff-line" :data-kind="line.kind">{{ line.text }}</code>
          </div>
          <pre v-else class="diff-empty-patch">{{ activeFile?.patch || "No patch preview" }}</pre>
        </div>
      </div>
    </template>

    <div v-else class="empty-state diff-empty-state">
      <FileDiff :size="22" />
      <strong>No proposed diff for this session.</strong>
      <span>Diffs appear after RunEvent SSE or session diff loading.</span>
    </div>
  </section>
</template>
