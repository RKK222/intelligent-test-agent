<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from "vue";
import type * as Monaco from "monaco-editor";
import type { RunDiffFile } from "@test-agent/shared-types";
import { buildDiffEditorContent } from "@/utils/diffPreview";
import { loadMonacoEditor, type MonacoModule } from "@/utils/monacoDiff";

const props = defineProps<{ file?: RunDiffFile; diffStyle: "unified" | "split" }>();

const host = ref<HTMLElement>();
const status = ref<"waiting" | "loading" | "ready" | "error">("waiting");
const error = ref("");
const editorRef = shallowRef<Monaco.editor.IStandaloneDiffEditor>();
const modelsRef = shallowRef<{ original: Monaco.editor.ITextModel; modified: Monaco.editor.ITextModel }>();
const content = computed(() => (props.file ? buildDiffEditorContent(props.file.path, props.file.patch) : undefined));
let monaco: MonacoModule | undefined;
let observer: IntersectionObserver | undefined;
let disposed = false;
let loading: Promise<void> | undefined;

const statusText = computed(() => {
  if (!props.file) {
    return "No file selected";
  }
  if (status.value === "ready") {
    return `${props.diffStyle === "split" ? "Split" : "Unified"} editor`;
  }
  if (status.value === "loading") {
    return "Loading editor";
  }
  if (status.value === "error") {
    return "Editor fallback";
  }
  return "Lazy editor";
});

onMounted(() => {
  observeHost();
});

onBeforeUnmount(() => {
  disposed = true;
  observer?.disconnect();
  disposeEditor();
});

watch(
  () => [props.file?.path, props.file?.patch, props.diffStyle],
  () => {
    if (!editorRef.value || !monaco) {
      return;
    }
    updateEditorModels();
  }
);

function observeHost() {
  if (!host.value || typeof window === "undefined") {
    return;
  }
  if (!("IntersectionObserver" in window)) {
    status.value = "waiting";
    return;
  }
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        observer?.disconnect();
        void mountEditor();
      }
    },
    { rootMargin: "180px" }
  );
  observer.observe(host.value);
}

async function mountEditor() {
  if (loading) {
    return loading;
  }
  loading = mountEditorNow().finally(() => {
    loading = undefined;
  });
  return loading;
}

async function mountEditorNow() {
  if (!props.file || !host.value || disposed) {
    return;
  }
  status.value = "loading";
  error.value = "";
  try {
    monaco ??= await loadMonacoEditor();
    if (disposed || !host.value) {
      return;
    }
    ensureTheme(monaco);
    editorRef.value ??= monaco.editor.createDiffEditor(host.value, {
      automaticLayout: true,
      contextmenu: false,
      enableSplitViewResizing: true,
      folding: false,
      glyphMargin: false,
      lineNumbersMinChars: 3,
      minimap: { enabled: false },
      readOnly: true,
      renderOverviewRuler: false,
      renderSideBySide: props.diffStyle === "split",
      scrollBeyondLastLine: false,
      theme: "opencode-diff",
      wordWrap: "off"
    });
    updateEditorModels();
    await nextTick();
    editorRef.value?.layout();
    status.value = "ready";
  } catch (cause) {
    status.value = "error";
    error.value = cause instanceof Error ? cause.message : "Monaco editor failed to load";
  }
}

function updateEditorModels() {
  if (!monaco || !editorRef.value || !content.value) {
    return;
  }
  disposeModels();
  const uriBase = encodeURIComponent(content.value.path);
  const version = simpleHash(`${content.value.path}\n${content.value.original}\n${content.value.modified}`);
  const original = monaco.editor.createModel(
    content.value.original,
    content.value.language,
    monaco.Uri.parse(`opencode-diff://original/${uriBase}?v=${version}`)
  );
  const modified = monaco.editor.createModel(
    content.value.modified,
    content.value.language,
    monaco.Uri.parse(`opencode-diff://modified/${uriBase}?v=${version}`)
  );
  modelsRef.value = { original, modified };
  editorRef.value.updateOptions({ renderSideBySide: props.diffStyle === "split" });
  editorRef.value.setModel({ original, modified });
}

function disposeEditor() {
  disposeModels();
  editorRef.value?.dispose();
  editorRef.value = undefined;
}

function disposeModels() {
  modelsRef.value?.original.dispose();
  modelsRef.value?.modified.dispose();
  modelsRef.value = undefined;
}

function ensureTheme(value: MonacoModule) {
  value.editor.defineTheme("opencode-diff", {
    base: "vs-dark",
    inherit: true,
    rules: [],
    colors: {
      "editor.background": "#0f1217",
      "editor.foreground": "#d7dde8",
      "diffEditor.insertedTextBackground": "#1f6f4a33",
      "diffEditor.removedTextBackground": "#8f313133"
    }
  });
}

function simpleHash(value: string) {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) | 0;
  }
  return Math.abs(hash).toString(36);
}
</script>

<template>
  <section class="monaco-diff-preview" role="region" aria-label="Monaco diff editor" :data-state="status" :data-style="diffStyle">
    <div class="monaco-diff-toolbar">
      <span>{{ file?.path ?? "No file selected" }}</span>
      <small>{{ statusText }}</small>
      <button v-if="status === 'waiting' || status === 'error'" type="button" @click="mountEditor">Load editor</button>
    </div>
    <div ref="host" class="monaco-diff-host" aria-hidden="true" />
    <p v-if="status === 'error'" class="monaco-diff-error">{{ error }}</p>
  </section>
</template>
