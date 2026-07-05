<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { FileText } from "lucide-vue-next";
import type { CSSProperties } from "vue";
import type { OpencodeLikeConversationState } from "../state/types";
import { createConversationLocatorTurns } from "../state/conversation-locator";

const props = defineProps<{
  state: OpencodeLikeConversationState;
}>();

const rootRef = ref<HTMLElement | null>(null);
const triggerRef = ref<HTMLElement | null>(null);
const panelRef = ref<HTMLElement | null>(null);
const isOpen = ref(false);
const activeTurnId = ref("");
const panelStyle = ref<CSSProperties>({});
let scrollContainer: HTMLElement | Window | null = null;
let scrollFrame = 0;
let closeTimer: number | undefined;

const turns = computed(() => createConversationLocatorTurns(props.state));
const shouldShow = computed(() => turns.value.length > 3);

watch(
  turns,
  (value) => {
    if (value.length === 0) {
      activeTurnId.value = "";
      isOpen.value = false;
      return;
    }
    if (!value.some((turn) => turn.id === activeTurnId.value)) {
      activeTurnId.value = value[0]?.id ?? "";
    }
  },
  { immediate: true }
);

watch(isOpen, (open) => {
  if (open) {
    nextTick(() => {
      updatePanelPosition();
      updateActiveTurn();
    });
  }
});

watch(shouldShow, (show) => {
  if (!show) {
    unbindScrollContainer();
    isOpen.value = false;
    return;
  }
  nextTick(() => {
    bindScrollContainer();
    updateActiveTurn();
  });
});

onMounted(() => {
  if (shouldShow.value) {
    bindScrollContainer();
    updateActiveTurn();
  }
  window.addEventListener("resize", updatePanelPosition);
  document.addEventListener("pointerdown", handleDocumentPointerDown);
  document.addEventListener("keydown", handleDocumentKeydown);
});

onBeforeUnmount(() => {
  unbindScrollContainer();
  window.removeEventListener("resize", updatePanelPosition);
  document.removeEventListener("pointerdown", handleDocumentPointerDown);
  document.removeEventListener("keydown", handleDocumentKeydown);
  if (closeTimer) {
    window.clearTimeout(closeTimer);
  }
  if (scrollFrame) {
    window.cancelAnimationFrame(scrollFrame);
  }
});

function openPanel() {
  if (!shouldShow.value) {
    return;
  }
  cancelClose();
  isOpen.value = true;
}

function togglePanel() {
  if (isOpen.value) {
    isOpen.value = false;
    return;
  }
  openPanel();
}

function scheduleClose() {
  cancelClose();
  closeTimer = window.setTimeout(() => {
    isOpen.value = false;
  }, 160);
}

function cancelClose() {
  if (!closeTimer) {
    return;
  }
  window.clearTimeout(closeTimer);
  closeTimer = undefined;
}

function bindScrollContainer() {
  unbindScrollContainer();
  scrollContainer = findScrollContainer();
  scrollContainer?.addEventListener("scroll", handleScroll, { passive: true });
}

function unbindScrollContainer() {
  scrollContainer?.removeEventListener("scroll", handleScroll);
  scrollContainer = null;
}

function findScrollContainer(): HTMLElement | Window {
  const root = rootRef.value;
  const container = root?.closest(".ta-thread-viewport, .figma-chat-scroll");
  return container instanceof HTMLElement ? container : window;
}

function handleScroll() {
  if (scrollFrame) {
    return;
  }
  scrollFrame = window.requestAnimationFrame(() => {
    scrollFrame = 0;
    updateActiveTurn();
    if (isOpen.value) {
      updatePanelPosition();
    }
  });
}

function updatePanelPosition() {
  const trigger = triggerRef.value;
  if (!trigger || typeof window === "undefined") {
    return;
  }
  const rect = trigger.getBoundingClientRect();
  const panelWidth = 340;
  const left = Math.max(12, rect.left - panelWidth - 10);
  const top = Math.min(Math.max(88, rect.top + rect.height / 2), Math.max(88, window.innerHeight - 88));
  panelStyle.value = {
    left: `${left}px`,
    top: `${top}px`,
    width: `${panelWidth}px`
  };
}

function updateActiveTurn() {
  const root = rootRef.value?.closest(".oc-timeline-root");
  const rows = Array.from(root?.querySelectorAll<HTMLElement>("[data-oc-turn-id]") ?? []);
  if (rows.length === 0) {
    return;
  }
  const viewportRect = getViewportRect();
  const anchorY = viewportRect.top + viewportRect.height * 0.38;
  let bestRow = rows[0];
  let bestDistance = Number.POSITIVE_INFINITY;

  // 当前轮次按视口中线上方最近的用户消息估算，避免长回答滚动时高亮跳动。
  for (const row of rows) {
    const rect = row.getBoundingClientRect();
    const distance = Math.abs(rect.top - anchorY);
    if (rect.top <= anchorY && distance <= bestDistance) {
      bestRow = row;
      bestDistance = distance;
    }
  }

  activeTurnId.value = bestRow.dataset.ocTurnId ?? activeTurnId.value;
}

function getViewportRect(): Pick<DOMRect, "top" | "height"> {
  if (scrollContainer instanceof HTMLElement) {
    return scrollContainer.getBoundingClientRect();
  }
  return { top: 0, height: window.innerHeight || 720 };
}

function scrollToTurn(turnId: string) {
  const root = rootRef.value?.closest(".oc-timeline-root");
  const target = Array.from(root?.querySelectorAll<HTMLElement>("[data-oc-turn-id]") ?? []).find(
    (row) => row.dataset.ocTurnId === turnId
  );
  if (!target) {
    return;
  }
  const behavior = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth";
  target.scrollIntoView({ behavior, block: "start" });
  activeTurnId.value = turnId;
  isOpen.value = false;
}

function handleDocumentPointerDown(event: PointerEvent) {
  const target = event.target;
  if (!(target instanceof Node)) {
    return;
  }
  if (triggerRef.value?.contains(target) || panelRef.value?.contains(target)) {
    return;
  }
  isOpen.value = false;
}

function handleDocumentKeydown(event: KeyboardEvent) {
  if (event.key === "Escape") {
    isOpen.value = false;
  }
}
</script>

<template>
  <div
    v-if="shouldShow"
    ref="rootRef"
    class="oc-conversation-locator"
    @mouseenter="openPanel"
    @mouseleave="scheduleClose"
  >
    <button
      ref="triggerRef"
      type="button"
      class="oc-conversation-locator__trigger"
      data-testid="oc-conversation-locator-trigger"
      aria-label="打开对话定位器"
      :aria-expanded="isOpen"
      @click="togglePanel"
      @focus="openPanel"
      @blur="scheduleClose"
    >
      <span
        v-for="turn in turns"
        :key="turn.id"
        aria-hidden="true"
        :class="{ 'is-active': activeTurnId === turn.id }"
      />
    </button>

    <Teleport to="body">
      <div
        v-if="isOpen"
        ref="panelRef"
        class="oc-conversation-locator__panel"
        data-testid="oc-conversation-locator-panel"
        :style="panelStyle"
        role="dialog"
        aria-label="对话定位"
        @mouseenter="cancelClose"
        @mouseleave="scheduleClose"
      >
        <div class="oc-conversation-locator__panel-title">对话定位</div>
        <div class="oc-conversation-locator__list" role="list">
          <button
            v-for="turn in turns"
            :key="turn.id"
            type="button"
            :class="['oc-conversation-locator__item', activeTurnId === turn.id ? 'is-active' : '']"
            :aria-label="`定位到第 ${turn.index} 轮对话`"
            @click="scrollToTurn(turn.id)"
          >
            <span class="oc-conversation-locator__index">{{ turn.index }}</span>
            <span class="oc-conversation-locator__item-body">
              <span class="oc-conversation-locator__item-title">{{ turn.title }}</span>
              <span class="oc-conversation-locator__item-summary">{{ turn.summary }}</span>
              <span v-if="turn.files.length > 0" class="oc-conversation-locator__files">
                <span v-for="file in turn.files" :key="file.path" class="oc-conversation-locator__file" :title="file.path">
                  <FileText class="oc-conversation-locator__file-icon" />
                  {{ file.label }}
                </span>
                <span v-if="turn.extraFileCount > 0" class="oc-conversation-locator__file-more">
                  +{{ turn.extraFileCount }}
                </span>
              </span>
            </span>
          </button>
        </div>
      </div>
    </Teleport>
  </div>
</template>
