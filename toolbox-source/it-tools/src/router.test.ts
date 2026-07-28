import messages from '@intlify/unplugin-vue-i18n/messages';
import { describe, expect, it } from 'vitest';
import { createI18n } from 'vue-i18n';
import router from './router';

type LocaleTree = Record<string, unknown>;
const localeMessages = messages!;

function leafKeys(value: LocaleTree, prefix = ''): string[] {
  return Object.entries(value).flatMap(([key, child]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return child && typeof child === 'object' && !Array.isArray(child)
      ? leafKeys(child as LocaleTree, path)
      : [path];
  });
}

describe('IT-Tools 中文路由元数据契约', () => {
  it('英文与中文资源拥有完全相同的叶子 key', () => {
    expect(leafKeys(localeMessages.en as LocaleTree).sort()).toEqual(leafKeys(localeMessages.zh as LocaleTree).sort());
  });

  it('85 条启动路由均提供实际中文标题和描述', () => {
    const toolRoutes = router.getRoutes().filter(route => route.meta.isTool);
    const zhI18n = createI18n({ legacy: false, locale: 'zh', messages: localeMessages });

    expect(toolRoutes).toHaveLength(85);
    for (const route of toolRoutes) {
      const slug = route.path.slice(1);
      const titleKey = `tools.${slug}.title`;
      const descriptionKey = `tools.${slug}.description`;

      expect(zhI18n.global.te(titleKey), `${slug} 缺少中文标题`).toBe(true);
      expect(zhI18n.global.te(descriptionKey), `${slug} 缺少中文描述`).toBe(true);
      const title = zhI18n.global.t(titleKey);
      const description = zhI18n.global.t(descriptionKey);

      expect(title, `${slug} 缺少中文标题`).toEqual(expect.stringMatching(/[\u3400-\u9fff]/));
      expect(description, `${slug} 缺少中文描述`).toEqual(expect.stringMatching(/[\u3400-\u9fff]/));
      expect(route.meta.name, `${slug} 路由标题未使用中文资源`).toBe(title);
      expect(route.meta.description, `${slug} 路由描述未使用中文资源`).toBe(description);
    }
  });
});
