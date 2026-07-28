import { defineComponent, h } from 'vue';
import { mount } from '@vue/test-utils';
import { NMessageProvider } from 'naive-ui';
import { createPinia } from 'pinia';
import { describe, expect, it } from 'vitest';
import SpanCopyable from '@/components/SpanCopyable.vue';
import CFileUpload from './c-file-upload/c-file-upload.vue';
import CSelect from './c-select/c-select.vue';
import CTooltip from './c-tooltip/c-tooltip.vue';
import CKeyValueListItem from './c-key-value-list/c-key-value-list-item.vue';
import { i18nPlugin } from '@/plugins/i18n.plugin';

describe('共享组件中文文案', () => {
  it('复制组件显示中文操作提示', () => {
    const Host = defineComponent({
      setup() {
        return () => h(NMessageProvider, null, { default: () => h(SpanCopyable, { value: 'demo' }) });
      },
    });
    const wrapper = mount(Host, { global: { plugins: [i18nPlugin] } });

    expect(wrapper.getComponent(CTooltip).props('tooltip')).toBe('复制到剪贴板');
  });

  it('文件选择器显示中文引导文案', () => {
    const Host = defineComponent({
      setup() {
        return () => h(CFileUpload);
      },
    });
    const wrapper = mount(Host, { global: { plugins: [createPinia(), i18nPlugin] } });

    expect(wrapper.text()).toContain('拖放文件到此处，或点击选择文件');
    expect(wrapper.text()).toContain('选择文件');
    expect(wrapper.text()).not.toContain('Browse files');
  });

  it('可搜索选择器显示中文占位与空结果文案', async () => {
    const wrapper = mount(CSelect, {
      props: { searchable: true, options: [] },
      global: { plugins: [createPinia(), i18nPlugin] },
    });

    expect(wrapper.text()).toContain('请选择');
    await wrapper.get('.c-select-input').trigger('click');
    expect(wrapper.get('input').attributes('placeholder')).toBe('搜索…');
    expect(wrapper.text()).toContain('未找到结果');
  });

  it('键值列表的空值默认显示中文占位文案', () => {
    const wrapper = mount(CKeyValueListItem, {
      props: { item: { label: '测试字段', value: null } },
      global: { plugins: [i18nPlugin] },
    });

    expect(wrapper.text()).toBe('暂无');
  });
});
