import figlet from 'figlet';
import { defineComponent, h } from 'vue';
import { mount } from '@vue/test-utils';
import { createPinia } from 'pinia';
import { NConfigProvider, NMessageProvider } from 'naive-ui';
import { describe, expect, it } from 'vitest';
import EmojiCard from './emoji-picker/emoji-card.vue';
import GitMemo from './git-memo/git-memo.vue';
import { i18nPlugin, translate } from '@/plugins/i18n.plugin';

function mountTool(component: any, props: Record<string, unknown> = {}) {
  const Host = defineComponent({
    setup() {
      return () => h(NConfigProvider, null, {
        default: () => h(NMessageProvider, null, { default: () => h(component, props) }),
      });
    },
  });

  return mount(Host, {
    global: {
      plugins: [createPinia(), i18nPlugin],
    },
  });
}

describe('Task 2C 复审修复', () => {
  it('使用纯 ASCII 默认文本并由 Figlet Standard 完整生成', () => {
    const defaultText = translate('tools.ascii-text-drawer.ui.defaultText');

    expect(defaultText).toBe('ASCII ART');
    expect(figlet.textSync(defaultText, { font: 'Standard' }).split('\n')[0]).toBe('     _    ____   ____ ___ ___      _    ____ _____ ');
  });

  it('Emoji 卡片显示 Unicode 标准名称，且名称不是重复码点', () => {
    const wrapper = mountTool(EmojiCard, {
      emojiInfo: {
        emoji: '😀',
        name: 'grinning face',
        title: 'Grinning face',
        slug: 'grinning_face',
        group: 'Smileys & Emotion',
        emoji_version: '1.0',
        unicode_version: '1.0',
        skin_tone_support: false,
        keywords: ['face', 'grin'],
        codePoints: '0x1f600',
        unicode: '\\ud83d\\ude00',
      },
    });

    expect(wrapper.get('[font-bold]').text()).toBe('Grinning face');
    expect(wrapper.get('[font-bold]').text()).not.toBe('0x1f600');
    expect(wrapper.text().match(/0x1f600/g)).toHaveLength(1);
  });

  it('中文 Git 说明保留标准英文命令占位符', () => {
    const wrapper = mountTool(GitMemo);
    const text = wrapper.text();

    expect(text).toContain('[name]');
    expect(text).toContain('[email]');
    expect(text).toContain('[commit message]');
    expect(text).toContain('[branch-name]');
    expect(text).not.toContain('[姓名]');
    expect(text).not.toContain('[邮箱]');
    expect(text).not.toContain('[提交信息]');
    expect(text).not.toContain('[分支名称]');
  });
});
