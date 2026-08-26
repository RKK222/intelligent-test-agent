<script lang="ts">
export type MarkdownPreviewProps = {
  // 待渲染的 Markdown 源码
  content?: string;
  /** 工作区图片由宿主通过受控文件通道读取，预览组件不直接拼接 HTTP 地址。 */
  resolveImage?: (source: string) => Promise<Blob>;
  /** 文件切换时强制释放上一份 Markdown 生成的临时图片 URL。 */
  imageContextKey?: string;
};

// Module-level cached references to avoid per-instance initialization & dynamic imports
let mdInstance: any = null;
let purifyInstance: any = null;
let mermaidInstance: any = null;

let loadPromise: Promise<void> | null = null;
let mermaidLoadPromise: Promise<void> | null = null;
</script>

<script setup lang="ts">
import { computed, createApp, defineAsyncComponent, nextTick, onBeforeUnmount, ref, watch, type App } from "vue";
import type { MermaidEditableDiagram } from "./mermaid/diagram";
import { ensureMermaid } from "./mermaid/init";
import { parseMindMapMarkdown } from "./mind-map/markdown";
import { findMindMapBlocks, replaceMindMapBlock } from "./mind-map/markdown-blocks";
import type { MindMapApplyResult, MindMapDocument, MindMapFenceBlock } from "./mind-map/model";
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
const MindMapEditorDialog = defineAsyncComponent(
  () => import("./mind-map/MindMapEditorDialog.vue")
);
type VisualEditorState = {
  blockIndex: number;
  originalSource: string;
  model?: MermaidEditableDiagram;
  error?: string;
};
const visualEditor = ref<VisualEditorState>();
type MindMapEditorState = {
  blockIndex: number;
  sourceLine: number;
  originalSource: string;
  document: MindMapDocument;
  error?: string;
};
const mindMapEditor = ref<MindMapEditorState>();
const mindMapPreviewApps = new Map<HTMLElement, App>();

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
let imageRenderGeneration = 0;
const markdownImageUrls = new Set<string>();

const URI_SCHEME = /^[A-Za-z][A-Za-z0-9+.-]*:/;

/** 外链和 data URL 保持浏览器原生行为，只有相对/工作区绝对路径交给宿主解析。 */
function isWorkspaceImageSource(source: string): boolean {
  const value = source.trim();
  return Boolean(value)
    && !value.startsWith("#")
    && !value.startsWith("//")
    && !URI_SCHEME.test(value);
}

function revokeMarkdownImageUrls() {
  for (const url of markdownImageUrls) URL.revokeObjectURL(url);
  markdownImageUrls.clear();
}

/** 在消毒后的 DOM 上移除相对 src，避免浏览器先向前端站点发出错误请求。 */
function prepareWorkspaceImages(sanitizedHtml: string): string {
  if (!props.resolveImage) return sanitizedHtml;
  const template = document.createElement("template");
  template.innerHTML = sanitizedHtml;
  template.content.querySelectorAll<HTMLImageElement>("img[src]").forEach((image) => {
    const source = image.getAttribute("src") ?? "";
    if (!isWorkspaceImageSource(source)) return;
    image.removeAttribute("src");
    image.setAttribute("data-ta-workspace-image", source);
    image.classList.add("ta-markdown-workspace-image", "is-loading");
  });
  return template.innerHTML;
}

function replaceMissingWorkspaceImage(image: HTMLImageElement, source: string) {
  const placeholder = document.createElement("span");
  placeholder.className = "ta-markdown-image-error";
  placeholder.setAttribute("role", "status");
  placeholder.textContent = `图片未上传：${source}`;
  image.replaceWith(placeholder);
}

/** v-html 落入真实 DOM 后再异步读取二进制，并用可回收的 object URL 展示。 */
async function hydrateWorkspaceImages(generation: number) {
  if (!props.resolveImage || generation !== imageRenderGeneration) return;
  const images = Array.from(
    scrollEl.value?.querySelectorAll<HTMLImageElement>("img[data-ta-workspace-image]") ?? []
  );
  await Promise.all(images.map(async (image) => {
    const source = image.getAttribute("data-ta-workspace-image") ?? "";
    try {
      const blob = await props.resolveImage!(source);
      const url = URL.createObjectURL(blob);
      if (generation !== imageRenderGeneration || !image.isConnected) {
        URL.revokeObjectURL(url);
        return;
      }
      markdownImageUrls.add(url);
      image.src = url;
      image.classList.remove("is-loading");
      image.removeAttribute("data-ta-workspace-image");
    } catch {
      if (generation === imageRenderGeneration && image.isConnected) {
        replaceMissingWorkspaceImage(image, source);
      }
    }
  }));
}

// 懒加载 markdown-it + highlight.js + dompurify，仅在首次需要渲染时加载，避免进入首屏 bundle
async function ensureLibs(needMermaid = false) {
  if (needMermaid) {
    mermaidInstance = await ensureMermaid();
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
        let mindMapIndex = 0;
        for (const tok of state.tokens) {
          if (tok.level === 0 && tok.map) {
            tok.attrSet("data-source-line", String(tok.map[0] + 1));
          }
          if (tok.type === "fence" && tok.info.trim() === "mermaid") {
            tok.meta = { ...(tok.meta ?? {}), mermaidIndex };
            mermaidIndex += 1;
          }
          if (tok.type === "fence" && tok.info.trim().split(/\s+/)[0] === "mind") {
            tok.meta = { ...(tok.meta ?? {}), mindMapIndex };
            mindMapIndex += 1;
          }
        }
      });
      // fence 默认不会把 token attrs 渲染到 <pre>，覆盖渲染以带上 data-source-line 与 hljs 高亮
      md.renderer.rules.fence = (tokens, idx, _options, env, slf) => {
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
        if (lang.split(/\s+/)[0] === "mind") {
          const id = `ta-mind-map-${Math.random().toString(36).substring(2, 9)}`;
          const visibleBlockIndex = typeof token.meta?.mindMapIndex === "number" ? token.meta.mindMapIndex : 0;
          const sourceLine = (token.map?.[0] ?? 0) + 2;
          // markdown-it 负责展示，领域扫描器负责精确替换；用源码行绑定两者，避免
          // 嵌套或长闭合 fence 造成仅按序号定位时替换到另一块。
          const sourceBlock = (env?.mindMapBlocks as MindMapFenceBlock[] | undefined)
            ?.find((block) => block.sourceLine === sourceLine);
          const blockIndex = sourceBlock?.index ?? -1;
          const source = sourceBlock?.source ?? token.content;
          const parsed = parseMindMapMarkdown(source);
          const escapedCode = md.utils.escapeHtml(source);
          const contextError = parsed.status.message
            ? `第 ${visibleBlockIndex + 1} 个 mind 块（源码第 ${sourceLine} 行）：${parsed.status.message}`
            : !sourceBlock
              ? `第 ${visibleBlockIndex + 1} 个 mind 块（源码第 ${sourceLine} 行）：当前位置只支持源码和安全预览`
            : "";
          const warning = contextError
            ? `<div class="ta-mind-map-warning" role="status">${md.utils.escapeHtml(contextError)}</div>`
            : "";
          const editDisabled = sourceBlock && parsed.status.canEdit && parsed.document
            ? ""
            : " disabled aria-disabled=\"true\"";
          const previewDisabled = parsed.document ? "" : " disabled aria-disabled=\"true\"";
          return `<div${attrs} class="mind-map-block is-source" id="${id}" data-content="${encodeURIComponent(source)}" data-block-index="${blockIndex}" data-source-start-line="${sourceLine}">
            <div class="ta-mind-map-header">
              <button type="button" class="ta-mind-map-mode-btn is-active" data-mind-map-mode="source" data-block-id="${id}">源码</button>
              <button type="button" class="ta-mind-map-mode-btn" data-mind-map-mode="preview" data-block-id="${id}"${previewDisabled}>预览</button>
              <button type="button" class="ta-mind-map-mode-btn" data-mind-map-mode="edit" data-block-id="${id}"${editDisabled}>编辑</button>
            </div>
            ${warning}
            <pre class="hljs ta-mind-map-source"><code class="language-mind">${escapedCode}</code></pre>
            <div class="ta-mind-map-chart" hidden></div>
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
  const mindMapButton = target.closest(".ta-mind-map-mode-btn") as HTMLButtonElement | null;
  if (mindMapButton) {
    await handleMindMapMode(mindMapButton);
    return;
  }
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

function activateMindMapMode(block: HTMLElement, button: HTMLButtonElement): void {
  block.querySelectorAll<HTMLButtonElement>(".ta-mind-map-mode-btn").forEach((item) => {
    item.classList.toggle("is-active", item === button);
  });
}

function unmountMindMapPreview(chart: HTMLElement): void {
  const app = mindMapPreviewApps.get(chart);
  if (app) {
    app.unmount();
    mindMapPreviewApps.delete(chart);
  }
}

function unmountMindMapPreviews(): void {
  for (const [chart, app] of mindMapPreviewApps) {
    app.unmount();
    mindMapPreviewApps.delete(chart);
  }
}

/** 预览按钮点击后才创建独立 Vue 子树，因此 SimpleMindMap 不进入 Markdown 首次渲染链路。 */
async function showMindMapPreview(block: HTMLElement, button: HTMLButtonElement): Promise<void> {
  const source = decodeURIComponent(block.getAttribute("data-content") ?? "");
  const parsed = parseMindMapMarkdown(source);
  const script = block.querySelector<HTMLElement>(".ta-mind-map-source");
  const chart = block.querySelector<HTMLElement>(".ta-mind-map-chart");
  if (!chart || !parsed.document) return;
  activateMindMapMode(block, button);
  button.disabled = true;
  const originalText = button.textContent ?? "预览";
  button.textContent = "准备中";
  try {
    unmountMindMapPreview(chart);
    chart.innerHTML = "";
    chart.hidden = false;
    if (script) script.hidden = true;
    const { default: MindMapCanvas } = await import("./mind-map/MindMapCanvas.vue");
    if (!chart.isConnected) return;
    const app = createApp(MindMapCanvas, {
      document: parsed.document,
      readonly: true,
      generationKey: `markdown-mind-${block.getAttribute("data-block-index") ?? "0"}`
    });
    mindMapPreviewApps.set(chart, app);
    app.mount(chart);
  } catch (error) {
    chart.hidden = false;
    if (script) script.hidden = true;
    chart.textContent = `思维导图预览失败：${errorMessage(error)}`;
  } finally {
    button.disabled = false;
    button.textContent = originalText;
  }
}

async function handleMindMapMode(button: HTMLButtonElement): Promise<void> {
  if (button.disabled) return;
  const blockId = button.getAttribute("data-block-id");
  const block = blockId ? document.getElementById(blockId) : null;
  if (!block) return;
  const mode = button.getAttribute("data-mind-map-mode") ?? "source";
  const script = block.querySelector<HTMLElement>(".ta-mind-map-source");
  const chart = block.querySelector<HTMLElement>(".ta-mind-map-chart");
  if (mode === "source") {
    activateMindMapMode(block, button);
    if (script) script.hidden = false;
    if (chart) chart.hidden = true;
    return;
  }
  if (mode === "preview") {
    await showMindMapPreview(block, button);
    return;
  }
  const originalSource = decodeURIComponent(block.getAttribute("data-content") ?? "");
  const parsed = parseMindMapMarkdown(originalSource);
  if (!parsed.status.canEdit || !parsed.document) return;
  mindMapEditor.value = {
    blockIndex: Number(block.getAttribute("data-block-index") ?? "0"),
    sourceLine: Number(block.getAttribute("data-source-start-line") ?? "1"),
    originalSource,
    document: parsed.document
  };
}

function applyMindMapEditor(result: MindMapApplyResult): void {
  const state = mindMapEditor.value;
  if (!state) return;
  try {
    // Editor 已完成序列化后二次解析；这里再做 fence 身份与原文并发保护。
    const markdown = replaceCurrentMindMapBlock(
      props.content,
      state.blockIndex,
      result.content,
      state.originalSource
    );
    emit("change", markdown);
    mindMapEditor.value = undefined;
  } catch (error) {
    mindMapEditor.value = {
      ...state,
      error: `第 ${state.blockIndex + 1} 个 mind 块（源码第 ${state.sourceLine} 行）：${errorMessage(error)}`
    };
  }
}

function replaceCurrentMindMapBlock(
  markdown: string,
  blockIndex: number,
  source: string,
  expectedSource: string
): string {
  // 静态 import 会把极小的 fence 工具留在 editor chunk，不会带入 SimpleMindMap。
  return replaceMindMapBlock(markdown, blockIndex, source, expectedSource);
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
  const generation = ++imageRenderGeneration;
  revokeMarkdownImageUrls();
  try {
    await ensureLibs();
    if (generation !== imageRenderGeneration) return;
    unmountMindMapPreviews();
    const raw = mdInstance?.render(displayContent.value, {
      mindMapBlocks: findMindMapBlocks(displayContent.value)
    }) ?? "";
    const sanitized = purifyInstance?.sanitize(raw) ?? "";
    html.value = prepareWorkspaceImages(sanitized);
    renderError.value = null;
    loading.value = false;
    await nextTick();
    // 图片读取不阻塞 Markdown ready；迟到结果由 generation 与 DOM 连接状态共同丢弃。
    void hydrateWorkspaceImages(generation);
  } catch (error) {
    if (generation !== imageRenderGeneration) return;
    // 依赖懒加载或单段 Markdown 解析失败时仍展示原文，避免后端已有内容在预览区变成白板。
    html.value = "";
    renderError.value = error instanceof Error ? error.message : String(error);
    loading.value = false;
  } finally {
    if (generation === imageRenderGeneration && !readyEmitted) {
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
  () => [props.content, props.imageContextKey],
  () => scheduleRender(),
  { immediate: true }
);

onBeforeUnmount(() => {
  imageRenderGeneration += 1;
  revokeMarkdownImageUrls();
  if (renderTimer) {
    clearTimeout(renderTimer);
  }
  if (syncRaf) {
    cancelAnimationFrame(syncRaf);
  }
  unmountMindMapPreviews();
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
    <MindMapEditorDialog
      v-if="mindMapEditor"
      :document="mindMapEditor.document"
      :block-index="mindMapEditor.blockIndex"
      :source-line="mindMapEditor.sourceLine"
      :error="mindMapEditor.error"
      @apply="applyMindMapEditor"
      @cancel="mindMapEditor = undefined"
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

.markdown-body :deep(.ta-markdown-workspace-image) {
  max-width: 100%;
  height: auto;
}

.markdown-body :deep(.ta-markdown-workspace-image.is-loading) {
  display: inline-block;
  min-width: 160px;
  min-height: 72px;
  border: 1px dashed var(--ta-border);
  border-radius: 6px;
  background: var(--ta-control);
}

.markdown-body :deep(.ta-markdown-image-error) {
  display: inline-flex;
  max-width: 100%;
  min-height: 40px;
  align-items: center;
  padding: 8px 10px;
  border: 1px dashed color-mix(in srgb, var(--ta-border) 70%, #b91c1c);
  border-radius: 6px;
  color: var(--ta-muted);
  background: var(--ta-control);
  overflow-wrap: anywhere;
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

/* mind 围栏沿用 Mermaid 的紧凑控制语言，但把画布固定为独立工作面，
   让“源码 / 预览 / 编辑”三态在长文档里仍可一眼辨认。 */
.markdown-body :deep(.mind-map-block) {
  margin: 8px 0;
  padding: 6px;
  overflow: hidden;
  border: 1px solid var(--ta-border);
  border-radius: 6px;
  background: var(--ta-panel-2, var(--ta-control));
}

.markdown-body :deep(.ta-mind-map-header) {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin: 0 0 6px;
  padding: 2px;
  border: 1px solid var(--ta-border, #dbe2ea);
  border-radius: 6px;
  background: var(--ta-control, #f1f5f9);
}

.markdown-body :deep(.ta-mind-map-mode-btn) {
  display: inline-flex;
  min-width: 38px;
  height: 20px;
  align-items: center;
  justify-content: center;
  padding: 0 7px;
  border: 0;
  border-radius: 4px;
  color: var(--ta-text);
  background: transparent;
  font: 11px/18px inherit;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.markdown-body :deep(.ta-mind-map-mode-btn:hover:not(:disabled)) {
  background: var(--ta-border, #dbe2ea);
}

.markdown-body :deep(.ta-mind-map-mode-btn.is-active) {
  color: var(--ta-ink);
  background: var(--ta-surface);
  box-shadow: 0 1px 2px rgb(15 23 42 / 6%);
}

.markdown-body :deep(.ta-mind-map-mode-btn:focus-visible) {
  outline: 2px solid var(--primary, #7f1e2b);
  outline-offset: 1px;
}

.markdown-body :deep(.ta-mind-map-mode-btn:disabled) {
  cursor: not-allowed;
  opacity: 0.52;
}

.markdown-body :deep(.ta-mind-map-source[hidden]),
.markdown-body :deep(.ta-mind-map-chart[hidden]) {
  display: none !important;
}

.markdown-body :deep(.ta-mind-map-source) {
  margin: 0;
}

.markdown-body :deep(.ta-mind-map-chart) {
  width: 100%;
  height: clamp(280px, 48vh, 520px);
  min-width: 360px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--ta-surface, #ffffff);
}

.markdown-body :deep(.ta-mind-map-warning) {
  margin: 0 0 6px;
  padding: 7px 9px;
  border: 1px solid var(--ta-mind-map-warning-border, rgba(185, 28, 28, 0.28));
  border-radius: 5px;
  color: var(--destructive, #9f1239);
  background: var(--ta-mind-map-warning-bg, rgba(185, 28, 28, 0.07));
  font-size: 11px;
  line-height: 1.5;
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
