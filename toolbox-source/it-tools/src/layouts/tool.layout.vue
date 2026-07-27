<script lang="ts" setup>
import { useHead } from '@vueuse/head';
import type { HeadObject } from '@vueuse/head';
import { useRoute } from 'vue-router';

const route = useRoute();
const { t } = useI18n();
const i18nKey = computed<string>(() => route.path.replace(/^\//, ''));
const toolTitle = computed<string>(() => t(`tools.${i18nKey.value}.title`, String(route.meta.name ?? '')));
const toolDescription = computed<string>(() => t(
  `tools.${i18nKey.value}.description`,
  String(route.meta.description ?? ''),
));
const head = computed<HeadObject>(() => ({
  title: `${toolTitle.value} - 工具盒子`,
  meta: [{ name: 'description', content: toolDescription.value }],
}));
useHead(head);
</script>

<template>
  <div class="platform-tool-shell">
    <header class="platform-tool-bar">
      <a href="/toolbox">工具盒子</a>
      <span aria-hidden="true">/</span>
      <strong>{{ toolTitle }}</strong>
    </header>
    <main class="platform-tool-main">
      <div class="platform-tool-heading">
        <h1>{{ toolTitle }}</h1>
        <p>{{ toolDescription }}</p>
      </div>
      <div class="platform-tool-content">
        <slot />
      </div>
    </main>
  </div>
</template>

<style lang="less" scoped>
.platform-tool-shell {
  min-height: 100vh;
  color: #172033;
  background: #f3f5f7;
}
.platform-tool-bar {
  position: sticky;
  z-index: 20;
  top: 0;
  display: flex;
  height: 40px;
  align-items: center;
  gap: 9px;
  padding: 0 16px;
  border-bottom: 1px solid #dce2ea;
  color: #687386;
  font-size: 12px;
  background: rgb(255 255 255 / 96%);
  backdrop-filter: blur(8px);
}
.platform-tool-bar a {
  color: #315f9b;
  font-weight: 700;
  text-decoration: none;
}
.platform-tool-bar strong {
  overflow: hidden;
  color: #172033;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.platform-tool-main {
  box-sizing: border-box;
  max-width: 1280px;
  margin: 0 auto;
  padding: 28px 24px 56px;
}
.platform-tool-heading {
  max-width: 760px;
  margin: 0 auto 24px;
}
.platform-tool-heading h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 650;
  letter-spacing: -0.025em;
}
.platform-tool-heading p {
  margin: 8px 0 0;
  color: #687386;
  font-size: 13px;
  line-height: 1.6;
}
.platform-tool-content {
  display: flex;
  flex-flow: row wrap;
  justify-content: center;
  gap: 16px;
}
.platform-tool-content :deep(> *) {
  flex: 0 1 600px;
}
@media (max-width: 640px) {
  .platform-tool-main { padding: 22px 14px 40px; }
}
</style>
