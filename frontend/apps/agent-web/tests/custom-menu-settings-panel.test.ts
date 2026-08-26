// @vitest-environment jsdom

import { defineComponent } from "vue";
import { fireEvent, render } from "@testing-library/vue";
import { describe, expect, it } from "vitest";
import CustomMenuSettingsPanel from "../src/components/settings/CustomMenuSettingsPanel.vue";
import type { CustomMenuItem } from "../src/components/custom-menus";

const ElInputStub = defineComponent({
  props: ["modelValue", "placeholder"],
  emits: ["update:modelValue"],
  template: `<input :placeholder="placeholder" :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />`
});

const ElSelectStub = defineComponent({
  props: ["modelValue"],
  emits: ["update:modelValue"],
  template: `<select :value="modelValue" @change="$emit('update:modelValue', $event.target.value)"><slot /></select>`
});

const ElOptionStub = defineComponent({
  props: ["label", "value"],
  template: `<option :value="value">{{ label }}</option>`
});

const ElButtonStub = defineComponent({
  props: ["disabled", "type"],
  emits: ["click"],
  template: `<button type="button" :disabled="disabled" @click="$emit('click')"><slot /></button>`
});

function renderPanel(customMenus: CustomMenuItem[] = []) {
  return render(CustomMenuSettingsPanel, {
    props: { customMenus },
    global: {
      stubs: {
        ElInput: ElInputStub,
        ElSelect: ElSelectStub,
        ElOption: ElOptionStub,
        ElButton: ElButtonStub
      }
    }
  });
}

describe("CustomMenuSettingsPanel", () => {
  it("adds a normalized custom menu from the form", async () => {
    const view = renderPanel();
    await fireEvent.update(view.getByPlaceholderText("例如：质量看板"), "  质量看板  ");
    await fireEvent.update(view.getByPlaceholderText("https://example.com 或 /internal-page"), "/quality/dashboard");
    await fireEvent.click(view.getByTestId("custom-menu-submit"));

    const events = view.emitted()["custom-menus-change"] as unknown[][] | undefined;
    const emitted = events?.[0]?.[0] as CustomMenuItem[];
    expect(emitted).toHaveLength(1);
    expect(emitted[0]).toMatchObject({ name: "质量看板", icon: "globe", url: "/quality/dashboard" });
    expect(emitted[0].id).toMatch(/^menu-/);
  });

  it("shows URL validation errors without emitting a menu", async () => {
    const view = renderPanel();
    await fireEvent.update(view.getByPlaceholderText("例如：质量看板"), "错误链接");
    await fireEvent.update(view.getByPlaceholderText("https://example.com 或 /internal-page"), "javascript:alert(1)");
    await fireEvent.click(view.getByTestId("custom-menu-submit"));

    expect(view.getByRole("alert").textContent).toContain("URL 需以 http://、https:// 或 / 开头");
    expect(view.emitted()["custom-menus-change"]).toBeUndefined();
  });

  it("requires an inline confirmation before deleting a configured menu", async () => {
    const menu: CustomMenuItem = { id: "menu-existing-01", name: "质量看板", icon: "chart", url: "/quality" };
    const view = renderPanel([menu]);
    await fireEvent.click(view.getByRole("button", { name: "删除 质量看板" }));
    expect(view.getByRole("group", { name: "确认删除 质量看板" })).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "删除" }));
    const events = view.emitted()["custom-menus-change"] as unknown[][] | undefined;
    expect(events?.[0]?.[0]).toEqual([]);
  });
});
