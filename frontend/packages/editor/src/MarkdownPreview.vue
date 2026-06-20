<script lang="ts">
export type MarkdownPreviewProps = {
  // 待渲染的 Markdown 源码
  content?: string;
};
</script>

<script setup lang="ts">
import { onBeforeUnmount, ref, shallowRef, watch } from "vue";
// github-markdown-css 提供 .markdown-body 基础排版样式，侧载一次即可
import "github-markdown-css/github-markdown.css";

const props = withDefaults(defineProps<MarkdownPreviewProps>(), { content: "" });

// 渲染后的 HTML（已消毒），用 shallowRef 避免对大段 HTML 做深度代理
const html = ref("");
// markdown-it 实例与 DOMPurify 句柄，懒加载后缓存
const mdRef = shallowRef<{ render: (src: string) => string } | null>(null);
const purifyRef = shallowRef<{ sanitize: (dirty: string) => string } | null>(null);
// 首次加载 markdown-it/dompurify 之前给一个轻量占位
const loading = ref(true);

let renderTimer: ReturnType<typeof setTimeout> | null = null;

// 懒加载 markdown-it + dompurify，仅在首次需要渲染时加载，避免进入首屏 bundle
async function ensureLibs() {
  if (mdRef.value && purifyRef.value) {
    return;
  }
  const [MarkdownIt, hljsMod, DOMPurifyMod] = await Promise.all([
    import("markdown-it"),
    import("highlight.js"),
    import("dompurify")
  ]);
  const md = new MarkdownIt.default({
    html: false, // 不直接内联原始 HTML，交给 DOMPurify 兜底
    linkify: true,
    typographer: false,
    // 代码块用 highlight.js 高亮，命中不到语言时回退纯文本
    highlight(str: string, lang: string): string {
      if (lang && hljsMod.default.getLanguage(lang)) {
        try {
          return `<pre><code class="hljs language-${lang}">${hljsMod.default.highlight(str, { language: lang }).value}</code></pre>`;
        } catch {
          // fallthrough
        }
      }
      return `<pre><code class="hljs">${md.utils.escapeHtml(str)}</code></pre>`;
    }
  });
  mdRef.value = md;
  purifyRef.value = DOMPurifyMod.default;
}

// 实际渲染：markdown-it 转 HTML 后用 DOMPurify 消毒，防御本地文件中的脚本/恶意链接
async function render() {
  await ensureLibs();
  const raw = mdRef.value?.render(props.content ?? "") ?? "";
  html.value = purifyRef.value?.sanitize(raw) ?? "";
  loading.value = false;
}

// 防抖渲染：编辑时逐键触发，150ms 合并一次，避免高频重排
function scheduleRender() {
  if (renderTimer) {
    clearTimeout(renderTimer);
  }
  renderTimer = setTimeout(() => {
    void render();
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
    <!-- 经 DOMPurify 消毒后的 HTML，可安全注入；.markdown-body 提供基础排版 -->
    <div v-else v-html="html" class="markdown-body min-w-0" />
  </div>
</template>

<style scoped>
/* github-markdown-css 是全局类，scoped 下需用 :deep() 选中后代；
   颜色覆盖走设计 token，与 IDE chrome 保持一致，避免强白底突兀 */
.markdown-body {
  background: transparent;
  color: var(--ta-text);
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  color: var(--ta-ink);
  border-color: var(--ta-border);
}

.markdown-body :deep(a) {
  color: var(--primary, var(--ta-ink));
}

.markdown-body :deep(blockquote) {
  color: var(--ta-muted);
  border-color: var(--ta-border);
}

.markdown-body :deep(table th),
.markdown-body :deep(table td) {
  border-color: var(--ta-border);
}

.markdown-body :deep(table tr:nth-child(2n)) {
  background-color: var(--ta-control);
}

.markdown-body :deep(hr) {
  background-color: var(--ta-border);
  height: 1px;
  border: 0;
}

.markdown-body :deep(code) {
  background: var(--ta-control);
}

.markdown-body :deep(pre) {
  background: var(--ta-panel-2, var(--ta-control));
  border: 1px solid var(--ta-border);
  border-radius: 6px;
}

.markdown-body :deep(pre code) {
  background: transparent;
}
</style>
