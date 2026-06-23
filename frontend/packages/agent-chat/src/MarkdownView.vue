<script lang="ts">
export type MarkdownViewProps = {
  // 待渲染的 Markdown 源码；空串/纯空白时显示占位
  source: string;
  // 自定义容器 class，便于嵌套到不同色块的卡片里
  bodyClass?: string;
  // 是否对 fence 默认语言做高亮（关闭可避免 highlight.js bundle 加载）
  highlight?: boolean;
};
</script>

<script setup lang="ts">
// 聊天窗口内的轻量 Markdown 渲染：
// - 仅首次渲染时按需加载 markdown-it + highlight.js + dompurify，不进首屏同步 bundle
// - html:false + DOMPurify 兜底，保证渲染出来的 HTML 不含脚本/危险链接
// - 与 MarkdownPreview.vue（编辑器预览）解耦：去掉源码行号、滚动联动和 gutter，
//   让本组件专注于"消息气泡里的小型 markdown 卡片"
import { onBeforeUnmount, ref, shallowRef, watch } from "vue";
import "github-markdown-css/github-markdown.css";

const props = withDefaults(defineProps<MarkdownViewProps>(), {
  bodyClass: "",
  highlight: true
});

const html = ref("");
const loading = ref(true);
const error = ref<string | null>(null);
// shallowRef 避免对大段 HTML 做深度代理
const mdRef = shallowRef<{ render: (src: string) => string } | null>(null);
const purifyRef = shallowRef<{ sanitize: (dirty: string) => string } | null>(null);

let renderTimer: ReturnType<typeof setTimeout> | null = null;

// 懒加载三件套；只在首个 MarkdownView 挂载时触发一次，后续实例复用缓存的句柄
async function ensureLibs() {
  if (mdRef.value && purifyRef.value) {
    return;
  }
  const [MarkdownIt, DOMPurifyMod, hljsMod] = await Promise.all([
    import("markdown-it"),
    import("dompurify"),
    props.highlight ? import("highlight.js") : Promise.resolve(null)
  ]);
  const md = new MarkdownIt.default({
    html: false, // 不直接内联原始 HTML，统一交给 DOMPurify
    linkify: true,
    typographer: false
  });
  if (props.highlight && hljsMod) {
    // fence 默认不会把 token attrs 渲染到 <pre>，覆盖渲染以带上 hljs 高亮
    md.renderer.rules.fence = (tokens, idx, _options, _env, slf) => {
      const token = tokens[idx];
      const lang = token.info ? token.info.trim() : "";
      const attrs = slf.renderAttrs(token);
      let code: string;
      if (lang && hljsMod.default.getLanguage(lang)) {
        try {
          code = hljsMod.default.highlight(token.content, { language: lang }).value;
          return `<pre${attrs}><code class="hljs language-${lang}">${code}</code></pre>`;
        } catch {
          // fallthrough 到纯文本转义
        }
      }
      code = md.utils.escapeHtml(token.content);
      return `<pre${attrs}><code class="hljs">${code}</code></pre>`;
    };
  }
  mdRef.value = md;
  purifyRef.value = DOMPurifyMod.default;
}

async function render() {
  try {
    await ensureLibs();
    const raw = mdRef.value?.render(props.source ?? "") ?? "";
    html.value = purifyRef.value?.sanitize(raw) ?? "";
    error.value = null;
  } catch (err) {
    // 渲染失败时降级为转义后的纯文本，避免气泡里出现空白
    error.value = err instanceof Error ? err.message : "render failed";
    html.value = "";
  } finally {
    loading.value = false;
  }
}

// 内容变化时合并多次连续输入，150ms 后统一渲染一次
function scheduleRender() {
  if (renderTimer) {
    clearTimeout(renderTimer);
  }
  renderTimer = setTimeout(() => {
    void render();
  }, 150);
}

watch(
  () => props.source,
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
  <div
    :class="['ta-md-view min-w-0', bodyClass]"
    data-testid="md-view"
  >
    <div v-if="loading" class="text-[12px] text-[var(--ta-chat-muted)]">渲染中…</div>
    <div v-else-if="error" class="whitespace-pre-wrap text-[12px] text-[var(--ta-chat-muted)]">{{ source }}</div>
    <div v-else-if="!source.trim()" class="text-[12px] text-[var(--ta-chat-muted)]">无内容</div>
    <!-- 经 DOMPurify 消毒后的 HTML，可安全注入；.markdown-body 提供基础排版 -->
    <div v-else v-html="html" class="markdown-body min-w-0" />
  </div>
</template>

<style scoped>
/* github-markdown-css 是全局类，scoped 下用 :deep() 命中后代。
   颜色覆盖走 design token，与聊天消息气泡主题保持一致，避免在深色卡片里出现刺眼白底 */
.markdown-body {
  position: relative;
  background: transparent;
  color: var(--ta-chat-text);
  font-family: inherit;
  font-size: 13px;
  line-height: 1.65;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  color: var(--ta-chat-text);
  border-color: var(--ta-chat-border);
  margin: 0.6em 0 0.4em;
}

.markdown-body :deep(h1) { font-size: 1.15em; }
.markdown-body :deep(h2) { font-size: 1.08em; }
.markdown-body :deep(h3) { font-size: 1.02em; }
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) { font-size: 0.98em; }

.markdown-body :deep(p) {
  margin: 0.4em 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.4em 0;
  padding-left: 1.4em;
}

.markdown-body :deep(li) {
  margin: 0.1em 0;
}

.markdown-body :deep(a) {
  color: var(--ta-cyan, var(--ta-chat-subtle));
  text-decoration: underline;
  text-underline-offset: 2px;
}

.markdown-body :deep(blockquote) {
  color: var(--ta-chat-muted);
  border-left: 3px solid var(--ta-chat-border);
  padding: 0.2em 0.8em;
  margin: 0.4em 0;
  background: var(--ta-chat-process-bg, transparent);
  border-radius: 0 4px 4px 0;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 0.4em 0;
}

.markdown-body :deep(table th),
.markdown-body :deep(table td) {
  border: 1px solid var(--ta-chat-border);
  padding: 4px 8px;
}

.markdown-body :deep(table tr:nth-child(2n)) {
  background: var(--ta-chat-process-bg, transparent);
}

.markdown-body :deep(hr) {
  background: var(--ta-chat-border);
  height: 1px;
  border: 0;
  margin: 0.6em 0;
}

.markdown-body :deep(code) {
  background: var(--ta-chat-process-bg);
  padding: 0.1em 0.35em;
  border-radius: 3px;
  font-size: 0.92em;
  font-family: Menlo, Monaco, Consolas, "Liberation Mono", monospace;
}

.markdown-body :deep(pre) {
  background: var(--ta-chat-detail-bg);
  border: 1px solid var(--ta-chat-border);
  border-radius: 6px;
  padding: 8px 10px;
  margin: 0.4em 0;
  overflow: auto;
  max-height: 320px;
  font-size: 12px;
  line-height: 1.55;
}

.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
  border-radius: 0;
  font-size: inherit;
}

.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: 4px;
}
</style>
