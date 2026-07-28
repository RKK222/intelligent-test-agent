import { defineComponent, h, nextTick } from 'vue';
import { mount } from '@vue/test-utils';
import { createPinia } from 'pinia';
import { NMessageProvider } from 'naive-ui';
import { describe, expect, it } from 'vitest';
import Encryption from './encryption/encryption.vue';
import JsonToYaml from './json-to-yaml-converter/json-to-yaml.vue';
import DateConverter from './date-time-converter/date-time-converter.vue';
import UrlParser from './url-parser/url-parser.vue';
import MetaTagGenerator from './meta-tag-generator/meta-tag-generator.vue';
import InputCopyable from '@/components/InputCopyable.vue';
import TextareaCopyable from '@/components/TextareaCopyable.vue';
import CInputText from '@/ui/c-input-text/c-input-text.vue';
import { i18nPlugin } from '@/plugins/i18n.plugin';

// 使用真实 Naive UI provider 和真实工具组件，避免只验证 mock 或标题字符串。
function mountTool(component: any) {
  const Host = defineComponent({
    setup() {
      return () => h(NMessageProvider, null, { default: () => h(component) });
    },
  });

  return mount(Host, { global: { plugins: [createPinia(), i18nPlugin] } });
}

describe('Task 2B 代表性工具中文交互', () => {
  it('加密工具以中文呈现字段，并在输入变化后生成真实密文', async () => {
    const wrapper = mountTool(Encryption);
    const textareas = wrapper.findAll('textarea');

    expect(wrapper.text()).toContain('加密');
    expect(wrapper.text()).toContain('密钥');
    expect(wrapper.text()).not.toContain('Your secret key');

    await textareas[0].setValue('需要加密的文本');
    await nextTick();

    expect(textareas[1].element.value).not.toBe('需要加密的文本');
    expect(textareas[1].element.value.length).toBeGreaterThan(16);
  });

  it('转换工具生成真实 YAML，并以中文报告 JSON 校验错误', async () => {
    const wrapper = mountTool(JsonToYaml);
    const input = wrapper.get('[data-test-id="input"]');

    expect(wrapper.text()).toContain('您的 JSON');
    expect(wrapper.text()).toContain('由 JSON 转换的 YAML');

    await input.setValue('{"foo":"bar","items":[1,2]}');
    await nextTick();
    expect(wrapper.get('[data-test-id="area-content"]').text()).toContain('foo: bar');

    await input.setValue('{invalid');
    await nextTick();
    expect(wrapper.text()).toContain('提供的 JSON 无效');
    expect(wrapper.text()).not.toContain('Provided JSON is not valid');
  });

  it('日期工具自动识别输入格式，并保持转换结果不变', async () => {
    const wrapper = mountTool(DateConverter);
    const input = wrapper.get('[data-test-id="date-time-converter-input"]');

    await input.setValue('2023-04-12T23:10:24+02:00');
    await nextTick();

    expect(wrapper.text()).toContain('ISO 8601');
    expect(wrapper.text()).toContain('Unix 时间戳');
    expect(wrapper.text()).toContain('RFC 7231');
    const outputs = wrapper.findAllComponents(InputCopyable);
    expect(outputs.some(item => item.props('value') === '1681333824')).toBe(true);
    expect(outputs.some(item => item.props('value') === 'Wed, 12 Apr 2023 21:10:24 GMT')).toBe(true);
  });

  it('URL 表单解析真实字段，并显示中文校验反馈', async () => {
    const wrapper = mountTool(UrlParser);
    const input = wrapper.get('input');

    await input.setValue('https://alice:secret@example.com:8443/path?a=1');
    await nextTick();

    expect(wrapper.text()).toContain('用户名');
    expect(wrapper.text()).toContain('端口');
    const fields = wrapper.findAllComponents(InputCopyable);
    expect(fields.some(item => item.props('value') === 'alice')).toBe(true);
    expect(fields.some(item => item.props('value') === '8443')).toBe(true);

    await input.setValue('not a url');
    await nextTick();
    expect(wrapper.text()).toContain('URL 无效');
  });

  it('Open Graph schema 字段中文渲染后仍能生成真实 Meta 标签', async () => {
    const wrapper = mountTool(MetaTagGenerator);

    expect(wrapper.text()).toContain('基本信息');
    expect(wrapper.text()).toContain('页面类型');
    expect(wrapper.text()).toContain('标题');

    const inputs = wrapper.findAllComponents(CInputText);
    await inputs[0].get('input').setValue('测试页面');
    await nextTick();

    const generatedMeta = wrapper.getComponent(TextareaCopyable).props('value');
    expect(generatedMeta).toContain('property="og:title" value="测试页面"');
    expect(wrapper.text()).toContain('您的 Meta 标签');
  });
});
