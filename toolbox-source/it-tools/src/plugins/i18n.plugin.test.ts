import { defineComponent, h } from 'vue';
import { mount, shallowMount } from '@vue/test-utils';
import { createPinia } from 'pinia';
import { createHead } from '@vueuse/head';
import { NConfigProvider, dateZhCN, zhCN } from 'naive-ui';
import { useI18n } from 'vue-i18n';
import { beforeEach, describe, expect, it } from 'vitest';
import App from '@/App.vue';
import router from '@/router';
import BaseLayout from '@/layouts/base.layout.vue';
import { i18nPlugin, translate } from './i18n.plugin';

describe('平台固定中文 locale', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('默认 locale 与 fallback locale 均为中文', () => {
    const LocaleProbe = defineComponent({
      setup() {
        const { fallbackLocale, locale } = useI18n();
        return () => h('div', `${locale.value}/${fallbackLocale.value}`);
      },
    });

    const wrapper = mount(LocaleProbe, { global: { plugins: [i18nPlugin] } });

    expect(wrapper.text()).toBe('zh/zh');
    expect(translate('common.copy')).toBe('复制');
  });

  it('历史英文偏好不能覆盖平台中文且 Naive UI 使用中文 locale', async () => {
    localStorage.setItem('locale', 'en');
    await router.push('/base64-file-converter');
    await router.isReady();

    const wrapper = mount(App, {
      global: {
        plugins: [createPinia(), createHead(), i18nPlugin, router],
      },
    });
    const configProvider = wrapper.getComponent(NConfigProvider);

    expect(translate('common.copy')).toBe('复制');
    expect(configProvider.props('locale')).toBe(zhCN);
    expect(configProvider.props('dateLocale')).toBe(dateZhCN);
    expect(localStorage.getItem('locale')).toBe('en');
  });

  it('共享布局不再提供语言切换入口', () => {
    const MenuLayoutStub = defineComponent({
      setup(_, { slots }) {
        return () => h('div', [slots.sider?.(), slots.content?.()]);
      },
    });
    const wrapper = shallowMount(BaseLayout, {
      global: {
        plugins: [createPinia(), i18nPlugin],
        provide: {
          plausible: { trackEvent: () => undefined },
        },
        stubs: { MenuLayout: MenuLayoutStub },
      },
    });

    expect(wrapper.find('locale-selector-stub').exists()).toBe(false);
  });
});
