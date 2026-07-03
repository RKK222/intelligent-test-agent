<script lang="ts">
export type MarkdownViewProps = {
  // 待渲染的 Markdown 源码；空串/纯空白时显示占位
  source: string
  // 自定义容器 class，便于嵌套到不同色块的卡片里
  bodyClass?: string
  // 是否对 fence 默认语言做高亮（关闭可避免 highlight.js bundle 加载）
  highlight?: boolean
  // 首次懒加载 Markdown 渲染器时显示的占位文案
  loadingText?: string
}

// Module-level cached references to avoid per-instance initialization & dynamic imports
let mdInstance: any = null
let purifyInstance: any = null
let hljsInstance: any = null
let mermaidInstance: any = null

let loadPromise: Promise<void> | null = null
let mermaidLoadPromise: Promise<void> | null = null
</script>

<script setup lang="ts">
// 聊天窗口内的轻量 Markdown 渲染：
// - 仅首次渲染时按需加载 markdown-it + highlight.js + dompurify，不进首屏同步 bundle
// - html:false + DOMPurify 兜底，保证渲染出来的 HTML 不含脚本/危险链接
// - 与 MarkdownPreview.vue（编辑器预览）解耦：去掉源码行号、滚动联动和 gutter，
//   让本组件专注于"消息气泡里的小型 markdown 卡片"
import { onBeforeUnmount, ref, watch } from 'vue'
import 'github-markdown-css/github-markdown.css'

const props = withDefaults(defineProps<MarkdownViewProps>(), {
  bodyClass: '',
  highlight: true,
  loadingText: '渲染中…',
})

const html = ref('')
const loading = ref(true)
const error = ref<string | null>(null)

let renderTimer: ReturnType<typeof setTimeout> | null = null

// 懒加载三件套；合并并发 Promise，全局复用单例
async function ensureLibs(needMermaid = false) {
  if (needMermaid && !mermaidInstance) {
    if (!mermaidLoadPromise) {
      mermaidLoadPromise = (async () => {
        const m = await import('mermaid')
        mermaidInstance = m.default
        mermaidInstance.initialize({
          startOnLoad: false,
          theme: 'neutral', // Neutral theme for better compatibility with light background
          securityLevel: 'loose',
        })
      })()
    }
    await mermaidLoadPromise
  }

  if (mdInstance && purifyInstance && hljsInstance) {
    return
  }

  if (!loadPromise) {
    loadPromise = (async () => {
      const [MarkdownIt, DOMPurify, hljs] = await Promise.all([
        import('markdown-it'),
        import('dompurify'),
        import('highlight.js/lib/common')
      ])

      const md = new MarkdownIt.default({
        html: false, // 不直接内联原始 HTML，统一交给 DOMPurify
        linkify: true,
        typographer: false,
      })

      md.renderer.rules.fence = (tokens: any[], idx: number, _options: any, _env: any, slf: any) => {
        const token = tokens[idx]
        const lang = token.info ? token.info.trim() : ''
        const attrs = slf.renderAttrs(token)

        if (lang === 'mermaid') {
          const id = `ta-mermaid-${Math.random().toString(36).substring(2, 9)}`
          const escapedCode = md.utils.escapeHtml(token.content)
          return `<div class="mermaid-block is-script" id="${id}" data-content="${encodeURIComponent(token.content)}">
            <div class="ta-mermaid-header">
              <button type="button" class="ta-mermaid-mode-btn is-active" data-mermaid-mode="script" data-block-id="${id}">脚本</button>
              <button type="button" class="ta-mermaid-mode-btn ta-mermaid-preview-btn" data-mermaid-mode="chart" data-block-id="${id}">图表</button>
            </div>
            <pre class="hljs ta-mermaid-script"><code class="language-mermaid">${escapedCode}</code></pre>
            <div class="ta-mermaid-chart" hidden></div>
          </div>`
        }

        let code: string
        if (props.highlight && hljs && lang && hljs.default.getLanguage(lang)) {
          try {
            code = hljs.default.highlight(token.content, {
              language: lang,
            }).value
            return `<pre${attrs}><code class="hljs language-${lang}">${code}</code></pre>`
          } catch {
            // fallthrough 到纯文本转义
          }
        }
        code = md.utils.escapeHtml(token.content)
        return `<pre${attrs}><code class="hljs">${code}</code></pre>`
      }

      mdInstance = md
      purifyInstance = DOMPurify.default
      hljsInstance = hljs
    })()
  }

  await loadPromise
}

async function handleMdViewClick(event: MouseEvent) {
  const target = event.target as HTMLElement
  const btn = target.closest('.ta-mermaid-mode-btn') as HTMLButtonElement | null
  if (!btn) return

  const blockId = btn.getAttribute('data-block-id')
  if (!blockId) return

  const block = document.getElementById(blockId)
  if (!block) return

  const mode = btn.getAttribute('data-mermaid-mode') ?? 'script'
  const scriptEl = block.querySelector<HTMLElement>('.ta-mermaid-script')
  const chartEl = block.querySelector<HTMLElement>('.ta-mermaid-chart')

  block.querySelectorAll<HTMLButtonElement>('.ta-mermaid-mode-btn').forEach(button => {
    button.classList.toggle('is-active', button === btn)
  })

  if (mode === 'script') {
    if (scriptEl) scriptEl.hidden = false
    if (chartEl) chartEl.hidden = true
    block.classList.add('is-script')
    block.classList.remove('is-chart')
    return
  }

  if (!chartEl) return
  const content = decodeURIComponent(block.getAttribute('data-content') ?? '')

  btn.disabled = true
  const originalText = btn.textContent ?? '图表'
  btn.textContent = '渲染中'

  try {
    await ensureLibs(true)
    if (mermaidInstance && !chartEl.innerHTML.trim()) {
      const { svg } = await mermaidInstance.render(blockId + '-svg', content)
      chartEl.innerHTML = svg
    }
    if (scriptEl) scriptEl.hidden = true
    chartEl.hidden = false
    block.classList.add('is-chart')
    block.classList.remove('is-script')
  } catch (err) {
    console.error('Mermaid render error:', err)
    if (scriptEl) scriptEl.hidden = false
    chartEl.hidden = true
    block.classList.add('is-script')
    block.classList.remove('is-chart')

    const badDiv = document.getElementById(`d${blockId}-svg`)
    if (badDiv) badDiv.remove()
  } finally {
    btn.disabled = false
    btn.textContent = originalText
  }
}

async function render() {
  try {
    await ensureLibs(false) // Don't load mermaid on initial render
    const raw = mdInstance?.render(props.source ?? '') ?? ''
    const sanitized = purifyInstance?.sanitize(raw) ?? ''

    html.value = sanitized
    error.value = null
  } catch (err) {
    // 渲染失败时降级为转义后的纯文本，避免气泡里出现空白
    error.value = err instanceof Error ? err.message : 'render failed'
    html.value = ''
  } finally {
    loading.value = false
  }
}

// 内容变化时合并多次连续输入，150ms 后统一渲染一次
function scheduleRender() {
  if (renderTimer) {
    clearTimeout(renderTimer)
  }
  renderTimer = setTimeout(() => {
    void render()
  }, 150)
}

watch(
  () => props.source,
  () => scheduleRender(),
  { immediate: true }
)

onBeforeUnmount(() => {
  if (renderTimer) {
    clearTimeout(renderTimer)
  }
})
</script>

<template>
  <div :class="['ta-md-view min-w-0', bodyClass]" data-testid="md-view" @click="handleMdViewClick">
    <div v-if="loading" class="text-[12px] text-[var(--ta-chat-muted)]">
      {{ loadingText }}
    </div>
    <div
      v-else-if="error"
      class="whitespace-pre-wrap text-[12px] text-[var(--ta-chat-muted)]"
    >
      {{ source }}
    </div>
    <div
      v-else-if="!source.trim()"
      class="text-[12px] text-[var(--ta-chat-muted)]"
    >
      无内容
    </div>
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
  line-height: 1.1;
  margin-top: 6px;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  color: var(--ta-chat-text);
  border-color: var(--ta-chat-border);
  margin: 6px 0 2px !important;
}

.markdown-body :deep(h1) {
  font-size: 1.12em;
}
.markdown-body :deep(h2) {
  font-size: 1.06em;
}
.markdown-body :deep(h3) {
  font-size: 1.02em;
}
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  font-size: 0.98em;
}

.markdown-body :deep(p) {
  margin: 2px 0 !important;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 2px 0 !important;
  /* github-markdown-css 设了 padding-left: 2em，聊天气泡空间有限，
     去除缩进让列表更紧凑 */
  padding-left: 0 !important;
}

.markdown-body :deep(li) {
  margin: 1px 0 !important;
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
  margin: 2px 0 !important;
  background: var(--ta-chat-process-bg, transparent);
  border-radius: 0 4px 4px 0;
}

.markdown-body :deep(table) {
  /* github-markdown-css 将 table 设为 display:block 以实现横向滚动，
     但这会导致 border-collapse 失效，th/td 与 tr 的边框各自独立渲染，
     在表头与表体之间、行与行之间产生多余空隙。
     这里恢复为 display:table 使 border-collapse 正常工作。
     加 !important 确保覆盖 github-markdown-css 的 display:block。 */
  display: table !important;
  border-collapse: collapse;
  margin: 4px 0 !important;
  line-height: 1.25 !important;
  font-size: 12px;
}

.markdown-body :deep(table th),
.markdown-body :deep(table td) {
  border: 1px solid var(--ta-chat-border);
  padding: 2px 5px !important;
}

/* 去除 github-markdown-css 在每行顶部加的独立边框，
   避免与 th/td 边框叠加后在行间产生双重分割线 */
.markdown-body :deep(table tr) {
  border-top: none;
}

.markdown-body :deep(table tr:nth-child(2n)) {
  background: var(--ta-chat-process-bg, transparent);
}

.markdown-body :deep(hr) {
  background: var(--ta-chat-border);
  height: 1px;
  border: 0;
  margin: 4px 0 !important;
}

.markdown-body :deep(code) {
  background: var(--ta-chat-process-bg);
  padding: 0.1em 0.35em;
  border-radius: 3px;
  font-size: 0.92em;
  font-family: Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
}

.markdown-body :deep(pre) {
  background: var(--ta-chat-detail-bg);
  border: 1px solid var(--ta-chat-border);
  border-radius: 6px;
  padding: 6px 8px !important;
  margin: 4px 0 !important;
  overflow: auto;
  max-height: 320px;
  font-size: 12px;
  line-height: 1.4;
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

.markdown-body :deep(.mermaid-block) {
  background: var(--ta-chat-detail-bg, rgba(0, 0, 0, 0.05));
  border: 1px solid var(--ta-chat-border, var(--ta-border));
  border-radius: 6px;
  padding: 6px;
  margin: 6px 0;
  overflow-x: auto;
}

.markdown-body :deep(.ta-mermaid-header) {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin: 0 0 6px;
  padding: 2px;
  border: 1px solid color-mix(in srgb, var(--ta-chat-border, #e4e4e7) 70%, transparent);
  border-radius: 6px;
  background: color-mix(in srgb, var(--ta-chat-detail-bg, #f4f4f5) 72%, #ffffff);
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
  color: var(--ta-chat-text, #18181b);
  font-size: 11px;
  line-height: 18px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.markdown-body :deep(.ta-mermaid-mode-btn:hover) {
  background: color-mix(in srgb, var(--ta-chat-border, #e4e4e7) 72%, transparent);
}

.markdown-body :deep(.ta-mermaid-mode-btn.is-active) {
  background: var(--ta-surface, #ffffff);
  color: var(--ta-chat-text, #18181b);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}

.markdown-body :deep(.ta-mermaid-mode-btn:disabled) {
  opacity: 0.6;
  cursor: not-allowed;
}

.markdown-body :deep(.ta-mermaid-chart) {
  display: flex;
  justify-content: center;
  min-width: 360px;
}
</style>
