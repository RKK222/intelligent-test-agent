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
const emit = defineEmits<{
  // 预览滚动时上报当前顶部可见的源码行号，供编辑器联动
  scroll: [line: number];
  // 首次渲染完成，供父级做一次初始对齐
  ready: [];
}>();

// 渲染后的 HTML（已消毒），用 shallowRef 避免对大段 HTML 做深度代理
const html = ref("");
// 预览滚动容器（.md-preview 本身）
const scrollEl = ref<HTMLElement | null>(null);
// markdown-it 实例与 DOMPurify 句柄，懒加载后缓存
const mdRef = shallowRef<{ render: (src: string) => string } | null>(null);
const purifyRef = shallowRef<{ sanitize: (dirty: string) => string } | null>(null);
// 首次加载 markdown-it/dompurify 之前给一个轻量占位
const loading = ref(true);
// 是否已发出过 ready（仅首次渲染完成时发一次）
let readyEmitted = false;

let renderTimer: ReturnType<typeof setTimeout> | null = null;
let syncRaf = 0;

// 懒加载 markdown-it + highlight.js + dompurify，仅在首次需要渲染时加载，避免进入首屏 bundle
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
    typographer: false
  });
  // 给顶级块打上源码行号（1 起），用于滚动联动与左侧序号对齐；
  // 只取 level===0，避免列表项/引用内段落数字堆叠
  md.core.ruler.push("source_line", (state) => {
    for (const tok of state.tokens) {
      if (tok.level === 0 && tok.map) {
        tok.attrSet("data-source-line", String(tok.map[0] + 1));
      }
    }
  });
  // fence 默认不会把 token attrs 渲染到 <pre>，覆盖渲染以带上 data-source-line 与 hljs 高亮
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
  mdRef.value = md;
  purifyRef.value = DOMPurifyMod.default;
}

// 实际渲染：markdown-it 转 HTML 后用 DOMPurify 消毒，防御本地文件中的脚本/恶意链接
async function render() {
  await ensureLibs();
  const raw = mdRef.value?.render(props.content ?? "") ?? "";
  html.value = purifyRef.value?.sanitize(raw) ?? "";
  loading.value = false;
  if (!readyEmitted) {
    readyEmitted = true;
    emit("ready");
  }
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

// 滚动到包含/最接近某源码行的块：找到源码行 <= line 的最大块，将其顶端对齐容器顶部
function scrollToSourceLine(line: number) {
  const root = scrollEl.value;
  if (!root) {
    return;
  }
  const blocks = Array.from(root.querySelectorAll<HTMLElement>("[data-source-line]"));
  if (!blocks.length) {
    return;
  }
  let target = blocks[0];
  let targetLine = Number(target.getAttribute("data-source-line")) || 0;
  for (const el of blocks) {
    const l = Number(el.getAttribute("data-source-line")) || 0;
    if (l <= line && l >= targetLine) {
      target = el;
      targetLine = l;
    }
  }
  const rect = target.getBoundingClientRect();
  const rootRect = root.getBoundingClientRect();
  root.scrollTop += rect.top - rootRect.top;
}

// 当前预览顶部可见的源码行号：第一个底部越过容器顶端的块即为当前锚点
function getTopSourceLine(): number {
  const root = scrollEl.value;
  if (!root) {
    return 1;
  }
  const rootTop = root.getBoundingClientRect().top;
  const blocks = Array.from(root.querySelectorAll<HTMLElement>("[data-source-line]"));
  let line = 1;
  for (const el of blocks) {
    if (el.getBoundingClientRect().bottom >= rootTop) {
      line = Number(el.getAttribute("data-source-line")) || line;
      break;
    }
  }
  return line;
}

// 预览滚动：rAF 节流后上报顶部源码行号，供编辑器联动
function onScroll() {
  if (syncRaf) {
    return;
  }
  syncRaf = requestAnimationFrame(() => {
    syncRaf = 0;
    emit("scroll", getTopSourceLine());
  });
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
  if (syncRaf) {
    cancelAnimationFrame(syncRaf);
  }
});

defineExpose({ scrollToSourceLine });
</script>

<template>
  <div
    ref="scrollEl"
    class="md-preview flex h-full min-h-0 flex-col overflow-auto bg-[var(--ta-surface)] pl-14 pr-6 py-4 text-[13px] leading-[1.7] text-[var(--ta-text)]"
    @scroll="onScroll"
  >
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
  position: relative;
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

<!-- 行号 gutter 用非 scoped 全局样式：v-html 注入的 DOM 没有 Vue scoped 哈希，
     scoped 的 :deep() 对 [attr]::before 伪元素不够稳定，放全局块保证命中 -->
<style>
.md-preview .markdown-body [data-source-line] {
  position: relative;
}

.md-preview .markdown-body [data-source-line]::before {
  content: attr(data-source-line);
  position: absolute;
  left: -2.75rem;
  top: 0.15em;
  width: 2.25rem;
  text-align: right;
  font-family: Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 11px;
  line-height: 1.7;
  color: var(--ta-muted);
  font-variant-numeric: tabular-nums;
  user-select: none;
  pointer-events: none;
}
</style>
