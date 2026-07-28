import { defineComponent, h, nextTick } from 'vue';
import { mount } from '@vue/test-utils';
import { createPinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import { NConfigProvider, NMessageProvider } from 'naive-ui';
import { zhCN } from 'date-fns/locale';
import { beforeEach, describe, expect, it } from 'vitest';
import CrontabGenerator from './crontab-generator/crontab-generator.vue';
import RegexTester from './regex-tester/regex-tester.vue';
import Ipv4RangeExpander from './ipv4-range-expander/ipv4-range-expander.vue';
import PercentageCalculator from './percentage-calculator/percentage-calculator.vue';
import TextStatistics from './text-statistics/text-statistics.vue';
import GitMemo from './git-memo/git-memo.vue';
import RegexMemo from './regex-memo/regex-memo.vue';
import { formatMsDuration } from './eta-calculator/eta-calculator.service';
import { i18nPlugin } from '@/plugins/i18n.plugin';

// 使用真实工具组件、Naive UI 和 i18n，仅替换依赖浏览器 Shadow DOM 的正则图形容器。
function mountTool(component: any) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { render: () => null } }],
  });
  const Host = defineComponent({
    setup() {
      return () => h(NConfigProvider, null, {
        default: () => h(NMessageProvider, null, { default: () => h(component) }),
      });
    },
  });

  return mount(Host, {
    global: {
      plugins: [createPinia(), i18nPlugin, router],
      stubs: { 'shadow-root': true },
    },
  });
}

describe('Task 2C 后 42 个工具中文交互', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('Cron 使用中文 locale 解释表达式，并呈现中文辅助表头', () => {
    const wrapper = mountTool(CrontabGenerator);

    expect(wrapper.text()).toContain('每小时');
    expect(wrapper.text()).toContain('显示详细说明');
    expect(wrapper.text()).toContain('等效表达式');
    expect(wrapper.text()).not.toContain('Every minute');
  });

  it('正则工具执行真实匹配，并以中文展示控件和校验错误', async () => {
    const wrapper = mountTool(RegexTester);
    const textareas = wrapper.findAll('textarea');

    await textareas[0].setValue('(foo)');
    await textareas[1].setValue('foo bar');
    await nextTick();

    expect(wrapper.text()).toContain('匹配结果');
    expect(wrapper.text()).toContain('文本中的位置');
    expect(wrapper.text()).toContain('foo');

    await textareas[0].setValue('[');
    await nextTick();
    expect(wrapper.text()).toContain('正则表达式无效');
    expect(wrapper.text()).not.toContain('Invalid regular expression');
  });

  it('IPv4 范围工具保留稳定结果标识，并以中文报告无效范围', async () => {
    const wrapper = mountTool(Ipv4RangeExpander);
    const inputs = wrapper.findAll('input');

    await inputs[0].setValue('192.168.1.1');
    await inputs[1].setValue('192.168.7.255');
    await nextTick();
    expect(wrapper.get('[data-test-id="cidr.new"]').text()).toBe('192.168.0.0/21');
    expect(wrapper.get('[data-test-id="addresses-in-range.new"]').text()).toBe('2,048');

    await inputs[1].setValue('192.168.0.1');
    await nextTick();
    expect(wrapper.text()).toContain('IPv4 地址范围无效');
    expect(wrapper.text()).toContain('交换起始和结束地址');
  });

  it('百分比工具保持真实计算结果，并用中文说明公式', async () => {
    const wrapper = mountTool(PercentageCalculator);

    await wrapper.get('[data-test-id="percentageX"] input').setValue('25');
    await wrapper.get('[data-test-id="percentageY"] input').setValue('80');
    await nextTick();

    expect((wrapper.get('[data-test-id="percentageResult"] input').element as HTMLInputElement).value).toBe('20');
    expect(wrapper.text()).toContain('第一个数值是第二个数值的百分之几');
  });

  it('日期时长使用 date-fns 中文 locale 格式化', () => {
    expect(formatMsDuration(3_723_004, { locale: zhCN })).toBe('1 小时 2 分钟 3 秒 4 ms');
  });

  it('文本统计执行真实统计并显示中文指标', async () => {
    const wrapper = mountTool(TextStatistics);
    await wrapper.get('textarea').setValue('hello world\n第二行');
    await nextTick();

    expect(wrapper.text()).toContain('字符数');
    expect(wrapper.text()).toContain('单词数');
    expect(wrapper.text()).toContain('行数');
    expect(wrapper.text()).toContain('2');
  });

  it('Git 与正则说明文档通过 locale 内容完整渲染为中文', () => {
    const git = mountTool(GitMemo);
    const regex = mountTool(RegexMemo);

    expect(git.text()).toContain('设置全局配置');
    expect(git.text()).toContain('撤销最近一次提交并保留更改');
    expect(git.text()).not.toContain('Set the global config');
    expect(regex.text()).toContain('普通字符');
    expect(regex.text()).toContain('分组与捕获');
    expect(regex.text()).not.toContain('Normal characters');
  });
});
