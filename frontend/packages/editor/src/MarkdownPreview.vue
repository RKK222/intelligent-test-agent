<script lang="ts">
export type MarkdownPreviewProps = {
  // 待渲染的 Markdown 源码
  content?: string;
};

// Module-level cached references to avoid per-instance initialization & dynamic imports
let mdInstance: any = null;
let purifyInstance: any = null;
let mermaidInstance: any = null;

let loadPromise: Promise<void> | null = null;
let mermaidLoadPromise: Promise<void> | null = null;
</script>

<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, ref, watch } from "vue";
import type { MermaidEditableDiagram } from "./mermaid/diagram";
// github-markdown-css 提供 .markdown-body 基础排版样式，侧载一次即可
import "github-markdown-css/github-markdown.css";

const props = withDefaults(defineProps<MarkdownPreviewProps>(), { content: "" });
const emit = defineEmits<{
  // 预览滚动时上报当前顶部可见的源码行号，供编辑器联动
  scroll: [line: number];
  // 首次渲染完成，供父级做一次初始对齐
  ready: [];
  // 可视化编辑应用后上报完整 Markdown，继续复用 CodeEditor 的 change/save 链路
  change: [content: string];
}>();

const MermaidEditorDialog = defineAsyncComponent(
  () => import("./mermaid/visual-editor/MermaidEditorDialog.vue")
);
type VisualEditorState = {
  blockIndex: number;
  originalSource: string;
  model?: MermaidEditableDiagram;
  error?: string;
};
const visualEditor = ref<VisualEditorState>();

// 渲染后的 HTML（已消毒），用 shallowRef 避免对大段 HTML 做深度代理
const html = ref("");
// 预览滚动容器（.md-preview 本身）
const scrollEl = ref<HTMLElement | null>(null);
// 首次加载 markdown-it/dompurify 之前给一个轻量占位
const loading = ref(true);
const renderError = ref<string | null>(null);
const displayContent = computed(() => (typeof props.content === "string" ? props.content : ""));
// 是否已发出过 ready（仅首次渲染完成时发一次）
let readyEmitted = false;

let renderTimer: ReturnType<typeof setTimeout> | null = null;
let syncRaf = 0;

// 懒加载 markdown-it + highlight.js + dompurify，仅在首次需要渲染时加载，避免进入首屏 bundle
async function ensureLibs(needMermaid = false) {
  if (needMermaid && !mermaidInstance) {
    if (!mermaidLoadPromise) {
      mermaidLoadPromise = (async () => {
        const [mermaidMod, elkLayouts] = await Promise.all([
          import("mermaid"),
          import("@mermaid-js/layout-elk")
        ]);
        const instance = (mermaidMod as any).default ?? mermaidMod;
        mermaidInstance = (instance.initialize && instance.render) ? instance : (instance.default ?? instance);
        const loaders = (elkLayouts as any).default ?? elkLayouts;
        if (mermaidInstance.registerLayoutLoaders && loaders) {
          mermaidInstance.registerLayoutLoaders(loaders);
        }
        mermaidInstance.initialize({
          startOnLoad: false,
          theme: "neutral",
          securityLevel: "loose",
          layout: "elk",
        });
      })();
    }
    await mermaidLoadPromise;
  }

  if (mdInstance && purifyInstance) {
    return;
  }

  if (!loadPromise) {
    loadPromise = (async () => {
      const [MarkdownIt, hljsMod, DOMPurifyMod] = await Promise.all([
        import("markdown-it"),
        import("highlight.js/lib/common"),
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
        let mermaidIndex = 0;
        for (const tok of state.tokens) {
          if (tok.level === 0 && tok.map) {
            tok.attrSet("data-source-line", String(tok.map[0] + 1));
          }
          if (tok.type === "fence" && tok.info.trim() === "mermaid") {
            tok.meta = { ...(tok.meta ?? {}), mermaidIndex };
            mermaidIndex += 1;
          }
        }
      });
      // fence 默认不会把 token attrs 渲染到 <pre>，覆盖渲染以带上 data-source-line 与 hljs 高亮
      md.renderer.rules.fence = (tokens, idx, _options, _env, slf) => {
        const token = tokens[idx];
        const lang = token.info ? token.info.trim() : "";
        const attrs = slf.renderAttrs(token);
        if (lang === "mermaid") {
          const id = `ta-mermaid-${Math.random().toString(36).substring(2, 9)}`;
          const escapedCode = md.utils.escapeHtml(token.content);
          const mermaidIndex = typeof token.meta?.mermaidIndex === "number" ? token.meta.mermaidIndex : 0;
          return `<div${attrs} class="mermaid-block is-script" id="${id}" data-content="${encodeURIComponent(token.content)}" data-block-index="${mermaidIndex}">
            <div class="ta-mermaid-header">
              <button type="button" class="ta-mermaid-mode-btn is-active" data-mermaid-mode="script" data-block-id="${id}">脚本</button>
              <button type="button" class="ta-mermaid-mode-btn ta-mermaid-preview-btn" data-mermaid-mode="chart" data-block-id="${id}">图表</button>
              <button type="button" class="ta-mermaid-mode-btn ta-mermaid-visual-btn" data-mermaid-mode="visual" data-block-id="${id}">可视化编辑</button>
            </div>
            <pre class="hljs ta-mermaid-script"><code class="language-mermaid">${escapedCode}</code></pre>
            <div class="ta-mermaid-chart" hidden></div>
          </div>`;
        }
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
      mdInstance = md;
      purifyInstance = DOMPurifyMod.default;
    })();
  }

  await loadPromise;
}

async function handleMdPreviewClick(event: MouseEvent) {
  const target = event.target as HTMLElement;
  const btn = target.closest(".ta-mermaid-mode-btn") as HTMLButtonElement | null;
  if (!btn) return;

  const blockId = btn.getAttribute("data-block-id");
  if (!blockId) return;

  const block = document.getElementById(blockId);
  if (!block) return;

  const mode = btn.getAttribute("data-mermaid-mode") ?? "script";
  const scriptEl = block.querySelector<HTMLElement>(".ta-mermaid-script");
  const chartEl = block.querySelector<HTMLElement>(".ta-mermaid-chart");

  if (mode === "visual") {
    await openVisualEditor(block, btn);
    return;
  }

  block.querySelectorAll<HTMLButtonElement>(".ta-mermaid-mode-btn").forEach((button) => {
    button.classList.toggle("is-active", button === btn);
  });

  if (mode === "script") {
    if (scriptEl) scriptEl.hidden = false;
    if (chartEl) chartEl.hidden = true;
    block.classList.add("is-script");
    block.classList.remove("is-chart");
    return;
  }

  if (!chartEl) return;
  const content = decodeURIComponent(block.getAttribute("data-content") ?? "");
  const originalText = btn.textContent ?? "图表";
  btn.disabled = true;
  btn.textContent = "渲染中";

  try {
    await ensureLibs(true);
    const hasError = chartEl.querySelector(".ta-mermaid-error");
    if (mermaidInstance && (!chartEl.innerHTML.trim() || hasError)) {
      const { svg } = await mermaidInstance.render(blockId + "-svg", content);
      chartEl.innerHTML = svg;
    }
    if (scriptEl) scriptEl.hidden = true;
    chartEl.hidden = false;
    block.classList.add("is-chart");
    block.classList.remove("is-script");
  } catch (err) {
    console.error("Mermaid render error:", err);
    const badDiv = document.getElementById(`d${blockId}-svg`);
    if (badDiv) badDiv.remove();

    const errMsg = err instanceof Error ? err.message : String(err);
    const escapedMsg = errMsg
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
    chartEl.innerHTML = `<div class="ta-mermaid-error">
      <div class="ta-mermaid-error-title">图表解析错误</div>
      <pre class="ta-mermaid-error-detail">${escapedMsg}</pre>
    </div>`;
    if (scriptEl) scriptEl.hidden = true;
    chartEl.hidden = false;
    block.classList.add("is-chart");
    block.classList.remove("is-script");
  } finally {
    btn.disabled = false;
    btn.textContent = originalText;
  }
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

/** 点击时才加载 Mermaid parser、领域 parser 和 Vue Flow 对话框，避免影响 Markdown 首屏。 */
async function openVisualEditor(block: HTMLElement, button: HTMLButtonElement) {
  const originalSource = decodeURIComponent(block.getAttribute("data-content") ?? "");
  const blockIndex = Number(block.getAttribute("data-block-index") ?? "0");
  const originalText = button.textContent ?? "可视化编辑";
  button.disabled = true;
  button.textContent = "准备中";
  try {
    await ensureLibs(true);
    await mermaidInstance.parse(originalSource);
    const { parseMermaidDiagram } = await import("./mermaid/diagram");
    visualEditor.value = {
      blockIndex,
      originalSource,
      model: parseMermaidDiagram(originalSource)
    };
  } catch (error) {
    visualEditor.value = {
      blockIndex,
      originalSource,
      error: errorMessage(error)
    };
  } finally {
    button.disabled = false;
    button.textContent = originalText;
  }
}

/** 序列化和官方 parser 二次校验都成功后，才把完整 Markdown 交回现有 change 链路。 */
async function applyVisualEditor(diagram: MermaidEditableDiagram) {
  const state = visualEditor.value;
  if (!state) return;
  try {
    const [{ serializeMermaidDiagram }, { replaceMermaidBlock }] = await Promise.all([
      import("./mermaid/diagram"),
      import("./mermaid/markdown-blocks")
    ]);
    const source = serializeMermaidDiagram(diagram);
    await ensureLibs(true);
    await mermaidInstance.parse(source);
    const markdown = replaceMermaidBlock(props.content, state.blockIndex, source, state.originalSource);
    emit("change", markdown);
    visualEditor.value = undefined;
  } catch (error) {
    visualEditor.value = { ...state, model: diagram, error: errorMessage(error) };
  }
}

// 实际渲染：markdown-it 转 HTML 后用 DOMPurify 消毒，防御本地 file 中的脚本/恶意链接
async function render() {
  try {
    await ensureLibs();
    const raw = mdInstance?.render(displayContent.value) ?? "";
    html.value = purifyInstance?.sanitize(raw) ?? "";
    renderError.value = null;
  } catch (error) {
    // 依赖懒加载或单段 Markdown 解析失败时仍展示原文，避免后端已有内容在预览区变成白板。
    html.value = "";
    renderError.value = error instanceof Error ? error.message : String(error);
  } finally {
    loading.value = false;
    if (!readyEmitted) {
      readyEmitted = true;
      emit("ready");
    }
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
    style="padding:28px;"
    @click="handleMdPreviewClick"
    @scroll="onScroll"
  >
    <div v-if="loading" class="text-[12px] text-[var(--ta-muted)]">正在准备预览…</div>
    <div v-else-if="!displayContent.trim()" class="text-[12px] text-[var(--ta-muted)]">无内容</div>
    <div v-else-if="renderError" class="md-preview-fallback">
      <div class="mb-2 text-[12px] text-[var(--ta-muted)]">Markdown 预览暂不可用，已显示原文。</div>
      <pre>{{ displayContent }}</pre>
    </div>
    <!-- 经 DOMPurify 消毒后的 HTML，可安全注入；.markdown-body 提供基础排版 -->
    <div v-else v-html="html" class="markdown-body min-w-0" />
    <MermaidEditorDialog
      v-if="visualEditor"
      :model="visualEditor.model"
      :error="visualEditor.error"
      @apply="applyVisualEditor"
      @cancel="visualEditor = undefined"
    />
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

.md-preview-fallback {
  min-width: 0;
  color: var(--ta-text);
  font-family: var(--ta-font-mono, ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.md-preview-fallback pre {
  margin: 0;
  white-space: inherit;
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

/* github-markdown-css 将 table 设为 display:block 以实现横向滚动，
   但这会导致 border-collapse 失效，产生多余行间空隙。
   这里恢复为 display:table 使 border-collapse 正常工作。
   编辑器预览的父容器已有 overflow-auto，无需依赖 table 自身的 block 滚动。 */
.markdown-body :deep(table) {
  display: table;
  border-collapse: collapse;
}

.markdown-body :deep(table th),
.markdown-body :deep(table td) {
  border-color: var(--ta-border);
}

/* 去除 github-markdown-css 在每行顶部的独立边框 */
.markdown-body :deep(table tr) {
  border-top: none;
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

.markdown-body :deep(.mermaid-block) {
  background: var(--ta-panel-2, var(--ta-control));
  border: 1px solid var(--ta-border);
  border-radius: 6px;
  padding: 6px;
  margin: 8px 0;
  overflow-x: auto;
}

.markdown-body :deep(.ta-mermaid-header) {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin: 0 0 6px;
  padding: 2px;
  border: 1px solid color-mix(in srgb, var(--ta-border) 70%, transparent);
  border-radius: 6px;
  background: color-mix(in srgb, var(--ta-control) 72%, #ffffff);
}

.markdown-body :deep(.ta-mermaid-mode-btn) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 20px;
  padding: 0 6px;
  background: transparent;
  border: 0;
  border-radius: 4px;
  color: var(--ta-text);
  font-family: inherit;
  font-size: 11px;
  line-height: 18px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.markdown-body :deep(.ta-mermaid-mode-btn:hover) {
  background: color-mix(in srgb, var(--ta-border) 72%, transparent);
}

.markdown-body :deep(.ta-mermaid-mode-btn.is-active) {
  background: var(--ta-surface);
  color: var(--ta-ink);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}

.markdown-body :deep(.ta-mermaid-mode-btn:disabled) {
  opacity: 0.6;
  cursor: not-allowed;
}

.markdown-body :deep(.ta-mermaid-script[hidden]),
.markdown-body :deep(.ta-mermaid-chart[hidden]) {
  display: none !important;
}

.markdown-body :deep(.ta-mermaid-chart) {
  display: flex;
  justify-content: center;
  min-width: 360px;
}

.markdown-body :deep(.ta-mermaid-error) {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  padding: 12px;
  background: var(--ta-mermaid-error-bg, rgba(239, 68, 68, 0.06));
  border: 1px dashed var(--ta-mermaid-error-border, rgba(239, 68, 68, 0.4));
  border-radius: 6px;
  color: var(--ta-mermaid-error-text, #b91c1c);
  font-family: inherit;
  margin: 4px 0;
  text-align: left;
}

.markdown-body :deep(.ta-mermaid-error-title) {
  font-weight: 600;
  font-size: 12px;
  margin-bottom: 6px;
}

.markdown-body :deep(.ta-mermaid-error-detail) {
  margin: 0;
  padding: 8px;
  background: rgba(0, 0, 0, 0.03);
  border-radius: 4px;
  font-family: Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 11px;
  line-height: 1.4;
  white-space: pre-wrap;
  word-break: break-all;
  width: 100%;
  color: inherit;
  border: none;
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
