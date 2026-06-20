<script lang="ts">
export type MarkdownPreviewProps = {
  // 待渲染的 Markdown 源码
  content?: string;
};
</script>

<script setup lang="ts">
import { onBeforeUnmount, ref, shallowRef, watch } from "vue";

const props = withDefaults(defineProps<MarkdownPreviewProps>(), { content: "" });

// 渲染后的 HTML（已消毒），用 shallowRef 避免对大段 HTML 做深度代理
const html = ref("");
// marked / DOMPurify 模块句柄，懒加载后缓存
const markedRef = shallowRef<((src: string) => string) | null>(null);
const purifyRef = shallowRef<{ sanitize: (dirty: string) => string } | null>(null);
// 渲染中态：首次加载 marked/dompurify 之前给一个轻量占位
const loading = ref(true);

let renderTimer: ReturnType<typeof setTimeout> | null = null;

// 懒加载 marked + dompurify，仅在首次需要渲染时加载，避免进入首屏 bundle
async function ensureLibs() {
  if (markedRef.value && purifyRef.value) {
    return;
  }
  const [{ marked }, DOMPurifyMod] = await Promise.all([
    import("marked"),
    import("dompurify")
  ]);
  // marked v15 默认同步解析；开启 GFM 表格/删除线等
  marked.setOptions({ gfm: true, breaks: false });
  markedRef.value = (src: string) => marked.parse(src, { async: false }) as string;
  purifyRef.value = DOMPurifyMod.default;
}

// 实际渲染：marked 转 HTML 后用 DOMPurify 消毒，防御本地文件中的脚本/恶意链接
async function render() {
  await ensureLibs();
  const raw = markedRef.value?.(props.content ?? "") ?? "";
  html.value = purifyRef.value?.sanitize(raw) ?? "";
  loading.value = false;
}

// 防抖渲染：编辑时逐键触发，150ms 合并一次，避免高频重排
function scheduleRender() {
  if (renderTimer) {
    clearTimeout(renderTimer);
  }
  renderTimer = setTimeout(() => {
    render();
  }, 150);
}

watch(
  () => props.content,
  () => scheduleRender(),
  { immediate: true }
);

onBeforeUnmount(() => {
  if (renderTimer) {
    clearTimeout(renderTimer);
  }
});
</script>

<template>
  <div class="md-preview flex h-full min-h-0 flex-col overflow-auto bg-[var(--ta-surface)] px-6 py-4 text-[13px] leading-[1.7] text-[var(--ta-text)]">
    <div v-if="loading" class="text-[12px] text-[var(--ta-muted)]">正在准备预览…</div>
    <div v-else-if="!content.trim()" class="text-[12px] text-[var(--ta-muted)]">无内容</div>
    <!-- 经 DOMPurify 消毒后的 HTML，可安全注入 -->
    <div v-else v-html="html" class="md-body min-w-0" />
  </div>
</template>

<style scoped>
/* 渲染后的 Markdown 排版，颜色全部走设计 token，不写死十六进制色 */
.md-body :deep() {
  word-break: break-word;
}

.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3),
.md-body :deep(h4) {
  margin: 1.2em 0 0.5em;
  font-weight: 600;
  line-height: 1.3;
  color: var(--ta-ink);
}

.md-body :deep(h1) {
  font-size: 1.5em;
  padding-bottom: 0.3em;
  border-bottom: 1px solid var(--ta-border);
}

.md-body :deep(h2) {
  font-size: 1.3em;
  padding-bottom: 0.25em;
  border-bottom: 1px solid var(--ta-border);
}

.md-body :deep(h3) {
  font-size: 1.15em;
}

.md-body :deep(h4) {
  font-size: 1em;
}

.md-body :deep(p) {
  margin: 0.6em 0;
}

.md-body :deep(a) {
  color: var(--primary, var(--ta-ink));
  text-decoration: underline;
  text-underline-offset: 2px;
}

.md-body :deep(ul),
.md-body :deep(ol) {
  margin: 0.6em 0;
  padding-left: 1.6em;
}

.md-body :deep(li) {
  margin: 0.25em 0;
}

.md-body :deep(li > ul),
.md-body :deep(li > ol) {
  margin: 0.25em 0;
}

.md-body :deep(blockquote) {
  margin: 0.8em 0;
  padding: 0.2em 0.9em;
  border-left: 3px solid var(--ta-border);
  color: var(--ta-muted);
}

.md-body :deep(blockquote p) {
  margin: 0.3em 0;
}

/* 行内代码：浅灰底圆角，对齐 .ta-codeblock 的观感 */
.md-body :deep(:not(pre) > code) {
  padding: 0.15em 0.4em;
  border-radius: 4px;
  background: var(--ta-control);
  font-family: Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 0.9em;
  color: var(--ta-ink);
}

/* 代码块：白底发丝边框，与编辑器 .ta-codeblock 风格一致 */
.md-body :deep(pre) {
  margin: 0.8em 0;
  padding: 0.8em 1em;
  border: 1px solid var(--ta-border);
  border-radius: 6px;
  background: var(--ta-panel-2, var(--ta-control));
  overflow: auto;
  font-family: Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 0.88em;
  line-height: 1.6;
}

.md-body :deep(pre code) {
  padding: 0;
  background: transparent;
  border-radius: 0;
  color: var(--ta-text);
}

.md-body :deep(table) {
  margin: 0.8em 0;
  border-collapse: collapse;
  width: 100%;
  font-size: 0.92em;
}

.md-body :deep(th),
.md-body :deep(td) {
  padding: 0.4em 0.7em;
  border: 1px solid var(--ta-border);
  text-align: left;
}

.md-body :deep(th) {
  background: var(--ta-control);
  font-weight: 600;
  color: var(--ta-ink);
}

.md-body :deep(hr) {
  margin: 1.2em 0;
  border: 0;
  border-top: 1px solid var(--ta-border);
}

.md-body :deep(img) {
  max-width: 100%;
  border-radius: 4px;
}

.md-body :deep(strong) {
  font-weight: 600;
  color: var(--ta-ink);
}
</style>
