import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import FigmaShell from "../src/components/FigmaShell.vue";

const figmaShellSource = readFileSync(resolve(process.cwd(), "apps/agent-web/src/components/FigmaShell.vue"), "utf8");
const figmaChatPanelSource = readFileSync(resolve(process.cwd(), "apps/agent-web/src/components/FigmaChatPanel.vue"), "utf8");
const figmaFileExplorerSource = readFileSync(resolve(process.cwd(), "apps/agent-web/src/components/FigmaFileExplorer.vue"), "utf8");
const figmaEditorAreaSource = readFileSync(resolve(process.cwd(), "apps/agent-web/src/components/FigmaEditorArea.vue"), "utf8");
const agentConfigPanelSource = readFileSync(resolve(process.cwd(), "apps/agent-web/src/components/AgentConfigPanel.vue"), "utf8");
const codeEditorSource = readFileSync(resolve(process.cwd(), "packages/editor/src/CodeEditor.vue"), "utf8");
const globalStylesSource = readFileSync(resolve(process.cwd(), "apps/agent-web/src/styles/globals.css"), "utf8");
const logoAsset = readFileSync(resolve(process.cwd(), "apps/agent-web/src/assets/figma/logo.png"));

const mountedWrappers: Array<{ unmount: () => void }> = [];
const originalInnerWidth = Object.getOwnPropertyDescriptor(window, "innerWidth");
const originalInnerHeight = Object.getOwnPropertyDescriptor(window, "innerHeight");

function mountShell(options?: any) {
  const wrapper = mount(FigmaShell, options);
  mountedWrappers.push(wrapper);
  return wrapper;
}

async function summonRobot(wrapper: ReturnType<typeof mountShell>) {
  await wrapper.get('[data-testid="robot-visibility-toggle"]').trigger("click");
  await wrapper.vm.$nextTick();
}

function dispatchPointer(element: EventTarget, type: string, pointerId: number, clientX: number, clientY: number, pointerType: string) {
  const event = new MouseEvent(type, { bubbles: true, cancelable: true, clientX, clientY });
  Object.defineProperties(event, {
    pointerId: { value: pointerId },
    pointerType: { value: pointerType },
    isPrimary: { value: true }
  });
  element.dispatchEvent(event);
}

describe("FigmaShell", () => {
  afterEach(() => {
    mountedWrappers.splice(0).forEach((wrapper) => wrapper.unmount());
    vi.restoreAllMocks();
    vi.useRealTimers();
    Object.defineProperty(window, "innerWidth", originalInnerWidth!);
    Object.defineProperty(window, "innerHeight", originalInnerHeight!);
    window.localStorage.removeItem("figma-shell-robot-pos");
    window.localStorage.removeItem("figma-shell-robot-fixed");
    window.localStorage.removeItem("test-agent.pet-companion.v1");
    document.querySelector('[data-testid="pointer-event-blocker"]')?.remove();
  });

  it("isolates the ICBC palette to the outer shell and preserves the chat surface", () => {
    expect(globalStylesSource).toContain("--ta-shell-accent: #c8161d");
    expect(globalStylesSource).toContain("--ta-shell-canvas:");
    expect(globalStylesSource).toContain("#F7F9FC");
    expect(globalStylesSource).toContain("--ta-shell-header: #ffffff");
    expect(globalStylesSource).toContain("--ta-shell-header-text: #000000");
    expect(globalStylesSource).toContain("--ta-shell-sidebar: #ffffff");
    expect(globalStylesSource).toContain("--ta-shell-gap: 8px");
    expect(globalStylesSource).toContain("--ta-shell-radius: 8px");
    expect(globalStylesSource).toContain("--ta-accent: #333333");
    expect(globalStylesSource).toContain("--ta-chat-user-bg: #B2EDDF");
    expect(logoAsset.subarray(0, 8)).toEqual(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]));
    expect(logoAsset.length).toBeGreaterThan(1_000);
    expect(figmaShellSource).toContain('import logoUrl from "../assets/figma/logo.png";');
    expect(figmaShellSource).toMatch(/\.figma-title\s*\{[^}]*color: var\(--ta-shell-brand, #111827\)/s);
    expect(figmaShellSource).toMatch(/\.figma-subtitle\s*\{[^}]*color: var\(--ta-shell-brand-strong, #7f1e2b\)/s);
    expect(figmaShellSource).toContain("--ta-tree-active: var(--ta-shell-accent-soft, #ffffff)");
    expect(figmaShellSource).toMatch(/\.figma-activity-bar\s*\{[^}]*background: transparent[^}]*border-right: 0/s);
    expect(figmaShellSource).toMatch(/\.figma-header\s*\{[^}]*display: grid;[^}]*grid-template-columns: max-content minmax\(0, 1fr\) max-content;[^}]*background: transparent/s);
    expect(figmaShellSource).toMatch(/\.figma-body\s*\{[^}]*padding: var\(--ta-shell-gap, 8px\)[^}]*background: transparent/s);
    expect(figmaShellSource).toMatch(/\.figma-header-left\s*\{[^}]*align-items: center;[^}]*justify-content: flex-start;[^}]*height: 100%;[^}]*transform: translateY\(calc\(var\(--ta-shell-gap, 8px\) \/ 2\)\)/s);
    expect(figmaShellSource).toMatch(/\.figma-title-group\s*\{[^}]*justify-content: center;[^}]*align-items: flex-start/s);
    expect(figmaShellSource).toMatch(/\.figma-header-center\s*\{[^}]*justify-self: center;[^}]*transform: translateY\(calc\(var\(--ta-shell-gap, 8px\) \/ 2\)\)/s);
    expect(figmaShellSource).toMatch(/\.figma-header-right\s*\{[^}]*justify-self: end;[^}]*transform: translateY\(calc\(var\(--ta-shell-gap, 8px\) \/ 2\)\)/s);
    expect(figmaShellSource).not.toContain("left: 150px");
    expect(figmaShellSource).toMatch(/\.figma-header-help\s*\{[^}]*border: 0;[^}]*background: transparent;[^}]*color: var\(--ta-shell-muted, #6b7280\)/s);
    expect(figmaShellSource).toMatch(/\.figma-header-help > svg\s*\{[^}]*display: block/s);
    expect(figmaShellSource).toMatch(/\.figma-header-help:active,[\s\S]*?\.figma-header-help\.is-open\s*\{[^}]*background: var\(--ta-shell-accent-soft, #fdf2f2\);[^}]*color: var\(--ta-shell-accent-strong, #991b1b\)/s);
    expect(figmaShellSource).toContain('<BookOpen :size="20" :stroke-width="1.5" />');
    expect(figmaShellSource).toContain('<FlaskConical :size="19" :stroke-width="1.5" />');
    expect(figmaShellSource).toMatch(/\.figma-runtime-inventory-summary\s*\{[^}]*border: 1px solid var\(--ta-shell-border, #e5e7eb\);[^}]*background: transparent/s);
    expect(figmaShellSource).toContain('data-testid="header-workspace-selector"');
    expect(figmaShellSource).toContain('data-testid="header-version-selector"');
    expect(figmaShellSource).toContain('data-testid="header-context-rail"');
    expect(figmaShellSource).toMatch(/\.figma-context-rail\s*\{[^}]*height: 34px;[^}]*border: 1px solid var\(--ta-shell-border, #e5e7eb\);[^}]*border-radius: 11px;[^}]*background: transparent/s);
    expect(figmaShellSource).toMatch(/\.figma-context-rail::before\s*\{[^}]*width: 3px;[^}]*height: 15px;[^}]*background: var\(--ta-shell-accent, #c8161d\)/s);
    expect(figmaShellSource).toMatch(/\.figma-context-rail\.has-open-menu\s*\{[^}]*border-color: var\(--ta-shell-accent, #c8161d\)/s);
    expect(figmaShellSource).toMatch(/\.figma-context-rail > :not\(:first-child\)::before\s*\{[^}]*background: var\(--ta-shell-border, #e5e7eb\)/s);
    expect(figmaShellSource).toMatch(/\.figma-app-menu-trigger,[\s\S]*?\.figma-context-menu-trigger\s*\{[^}]*height: 26px;[^}]*border: 0;[^}]*background: transparent/s);
    expect(figmaShellSource).toMatch(/\.figma-app-menu-dropdown\s*\{[^}]*border-radius: 12px;[^}]*box-shadow:\s*0 12px 32px rgba\(15, 23, 42, 0\.10\),\s*0 2px 8px rgba\(15, 23, 42, 0\.06\)/s);
    expect(figmaShellSource).toMatch(/\.figma-app-menu-item\s*\{[^}]*border-radius: 8px/s);
    expect(figmaShellSource).toMatch(/\.figma-user-avatar-btn\s*\{[^}]*border: 1px solid var\(--ta-shell-border, #e5e7eb\);[^}]*background: var\(--ta-shell-surface, #fff\)/s);
    expect(figmaShellSource).toMatch(/\.figma-user-avatar\s*\{[^}]*background: var\(--ta-shell-hover, #f3f4f6\);[^}]*color: var\(--ta-shell-header-text, #000000\)/s);
    expect(figmaShellSource).toMatch(/\.figma-user-avatar--compact\s*\{[^}]*font-size: 12px/s);
    expect(figmaShellSource).toContain('{{ userInitial }}</span>');
    expect(figmaShellSource).toContain("--ta-tree-bg: var(--ta-shell-sidebar, #ffffff)");
    expect(figmaFileExplorerSource).toMatch(/\.figma-file-explorer\s*\{[^}]*background: var\(--ta-tree-bg\)/s);
    expect(figmaFileExplorerSource).toMatch(/\.figma-fe-body\s*\{[^}]*background: var\(--ta-tree-bg\)/s);
    expect(agentConfigPanelSource).toContain("background: var(--ta-tree-bg");
    expect(figmaEditorAreaSource).toMatch(/\.figma-editor-area\s*\{[^}]*background: #fff/s);
    expect(figmaEditorAreaSource).toMatch(/\.figma-editor-tabs\s*\{[^}]*background: #fff/s);
    expect(figmaEditorAreaSource).toMatch(/\.figma-editor-tab--active\s*\{[^}]*border-top-color: var\(--ta-shell-accent, #c8161d\)/s);
    expect(codeEditorSource).toContain("bg-[var(--ta-panel-2)]");
    expect(globalStylesSource).toContain("--ta-panel-2: #ffffff");
    for (const panelClass of ["figma-panel-left", "figma-panel-center", "figma-panel-right"]) {
      const panelRuleStart = figmaShellSource.indexOf(`.${panelClass} {`);
      const panelRule = figmaShellSource.slice(panelRuleStart, figmaShellSource.indexOf("\n}", panelRuleStart) + 2);
      expect(panelRule).toContain("border-radius: var(--ta-shell-radius, 8px)");
      expect(panelRule).toContain("box-shadow: var(--ta-shell-shadow");
    }

    const chatBodyRule = figmaShellSource.match(/\.figma-chat-body\s*\{[^}]+\}/)?.[0];
    expect(chatBodyRule).toContain("background: #ffffff");
    expect(chatBodyRule).not.toContain("--ta-shell-");
    expect(figmaChatPanelSource).not.toContain("--ta-shell-");
  });

  it("opens the built-in manual from the global help entry", async () => {
    const wrapper = mountShell();

    await wrapper.get('[data-testid="help-center-open"]').trigger("click");

    expect(wrapper.emitted("open-help")?.[0]).toEqual(["getting-started"]);
  });

  it("places the platform experience icon beside the manual instead of duplicating it in the app menu", async () => {
    const wrapper = mountShell({
      props: {
        apps: [{ id: "app_coss", name: "F-COSS", description: "已启用" }],
        selectedAppId: "app_coss"
      }
    });
    const right = wrapper.get(".figma-header-right");
    const experience = right.get('[data-testid="experience-workspace-open"]');
    const help = right.get('[data-testid="help-center-open"]');
    const children = Array.from(right.element.children);

    expect(children.indexOf(experience.element) + 1).toBe(children.indexOf(help.element));
    expect(experience.attributes("aria-label")).toBe("进入平台体验");
    expect(experience.attributes("aria-pressed")).toBe("false");
    await experience.trigger("click");
    expect(wrapper.emitted("open-experience")).toHaveLength(1);

    await wrapper.get(".figma-app-menu-trigger").trigger("click");
    expect(wrapper.find('[role="option"]').text()).not.toContain("平台体验");

    await wrapper.setProps({ workspaceKind: "EXPERIENCE" });
    expect(experience.attributes("aria-label")).toBe("退出平台体验");
    expect(experience.attributes("aria-pressed")).toBe("true");

    await wrapper.setProps({ fixedWorkspace: true });
    expect(wrapper.find('[data-testid="experience-workspace-open"]').exists()).toBe(false);
  });

  it("places the notification bell after the experience and manual icons and hides it in the fixed share workbench", async () => {
    const wrapper = mountShell({
      props: {
        notificationUnreadCount: 2,
        notifications: [],
      },
    });
    const right = wrapper.get('.figma-header-right');
    const experience = right.get('[data-testid="experience-workspace-open"]');
    const help = right.get('[data-testid="help-center-open"]');
    const bell = right.get('[data-testid="notification-center-trigger"]');
    const inventory = right.get('[data-testid="runtime-inventory-summary"]');
    const children = Array.from(right.element.children);

    expect(children.indexOf(experience.element) + 1).toBe(children.indexOf(help.element));
    expect(children.indexOf(help.element)).toBeLessThan(children.indexOf(bell.element.parentElement!));
    expect(children.indexOf(bell.element.parentElement!)).toBeLessThan(children.indexOf(inventory.element.parentElement!));
    expect(bell.text()).toContain('2');
    await bell.trigger('click');
    expect(wrapper.emitted('refresh-notifications')).toHaveLength(1);

    await wrapper.setProps({ fixedWorkspace: true });
    expect(wrapper.find('[data-testid="experience-workspace-open"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="notification-center-trigger"]').exists()).toBe(false);
  });

  it("exposes the pet button and manual as onboarding targets", () => {
    const wrapper = mountShell();

    expect(wrapper.get('[data-onboarding="pet"]').attributes("data-testid")).toBe("robot-visibility-toggle");
    expect(wrapper.get('[data-onboarding="manual"]').attributes("aria-label")).toBe("打开用户手册");
  });

  it("restores a saved robot root position as the next natural start position", async () => {
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 120, y: 180 }));

    const wrapper = mountShell();
    await wrapper.vm.$nextTick();
    await summonRobot(wrapper);
    const robot = wrapper.get('[data-testid="figma-robot"]');

    expect(robot.attributes("style")).toContain("left: 120px");
    expect(robot.attributes("style")).toContain("top: 180px");
    expect(robot.classes()).toContain("figma-robot-agent");
    expect(robot.find(".state-idle").exists()).toBe(true);
  });

  it("reuses the same active companion artwork in the visibility toggle", async () => {
    const wrapper = mountShell();
    await summonRobot(wrapper);
    const petSvg = wrapper.get(".robot-svg");
    const toggleSvg = wrapper.get('[data-testid="robot-visibility-toggle"] svg');

    expect(toggleSvg.attributes("viewBox")).toBe("0 0 64 64");
    expect(toggleSvg.attributes("aria-label")).toBe(petSvg.attributes("aria-label"));
    expect(toggleSvg.classes().find((className) => className.startsWith("is-")))
      .toBe(petSvg.classes().find((className) => className.startsWith("is-")));
  });

  it("fixes the pet by default on first summon and prevents random motion", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    const wrapper = mountShell();
    await summonRobot(wrapper);

    const robot = wrapper.get('[data-testid="figma-robot"]');
    expect(robot.find(".robot-pin-indicator").exists()).toBe(true);
    expect(window.localStorage.getItem("figma-shell-robot-fixed")).toBe("true");

    await vi.advanceTimersByTimeAsync(30_000);
    expect(robot.find(".state-idle").exists()).toBe(true);
    expect(robot.find(".state-walking").exists()).toBe(false);
    expect(robot.find(".state-jumping-up").exists()).toBe(false);
    expect(robot.find(".state-jumping-down").exists()).toBe(false);
  });

  it("lets the user choose a companion and persists the selected mode", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    const wrapper = mountShell();
    await summonRobot(wrapper);

    await wrapper.get('[data-testid="figma-robot"]').trigger("click");
    await vi.advanceTimersByTimeAsync(250);
    await wrapper.vm.$nextTick();
    await wrapper.get('button[aria-label="选择小宠物"]').trigger("click");
    await wrapper.get('button[aria-label="选择星探狐"]').trigger("click");

    expect(wrapper.get(".robot-svg").classes()).toContain("is-fox");
    expect(wrapper.get("#figma-robot-side-question-title").text()).toBe("问问小宠物");
    expect(wrapper.get(".robot-svg").classes()).toContain("has-status");
    expect(wrapper.get(".robot-svg").find(".pet-status-halo").attributes("fill")).toBe("none");
    expect(wrapper.find('[data-testid="robot-process-status-beacon"]').exists()).toBe(false);
    expect(JSON.parse(window.localStorage.getItem("test-agent.pet-companion.v1")!)).toMatchObject({
      mode: "selected",
      selectedPetId: "fox",
    });
  });

  it("shows personal runtime controls in the first pet panel and confirms before emitting", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    const wrapper = mountShell({
      props: {
        canManagePublicAgentConfig: true,
        canManageWorkspaceAgentConfig: true
      }
    });
    await summonRobot(wrapper);

    await wrapper.get('[data-testid="figma-robot"]').trigger("click");
    await vi.advanceTimersByTimeAsync(250);
    await wrapper.vm.$nextTick();

    expect(wrapper.find('[data-testid="pet-runtime-reload-actions"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="pet-companion-settings"]').exists()).toBe(false);
    await wrapper.get('button[aria-label="重载公共个人配置"]').trigger("click");
    const publicConfirmation = wrapper.get('[data-testid="pet-runtime-reload-confirm"]').text();
    expect(publicConfirmation).toContain("公共个人 worktree");
    expect(publicConfirmation).toContain("未提交内容不会被删除");
    expect(publicConfirmation).toContain("启动或重启");
    expect(wrapper.emitted("personal-runtime-reload")).toBeUndefined();

    await wrapper.get('[data-testid="pet-runtime-reload-confirm"] button.is-primary').trigger("click");
    expect(wrapper.emitted("personal-runtime-reload")?.[0]).toEqual([{ scope: "PUBLIC" }]);

    await wrapper.get('button[aria-label="重载应用个人配置"]').trigger("click");
    const applicationConfirmation = wrapper.get('[data-testid="pet-runtime-reload-confirm"]').text();
    expect(applicationConfirmation).toContain("feature 固定提交");
    expect(applicationConfirmation).toContain("不会 stash、reset");
    expect(applicationConfirmation).toContain("未提交内容");
  });

  it("closes a pending workspace runtime reload and rejects its stale confirm after capability revocation", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    const wrapper = mountShell({
      props: {
        canManageWorkspaceAgentConfig: true
      }
    });
    await summonRobot(wrapper);
    await wrapper.get('[data-testid="figma-robot"]').trigger("click");
    await vi.advanceTimersByTimeAsync(250);
    await wrapper.vm.$nextTick();

    await wrapper.get('button[aria-label="重载应用个人配置"]').trigger("click");
    const staleConfirm = wrapper.get('[data-testid="pet-runtime-reload-confirm"] button.is-primary').element;
    await wrapper.setProps({ canManageWorkspaceAgentConfig: false });

    expect(wrapper.find('[data-testid="pet-runtime-reload-confirm"]').exists()).toBe(false);
    staleConfirm.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    expect(wrapper.emitted("personal-runtime-reload")).toBeUndefined();
  });

  it("hides runtime controls while the pet selection page is open", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    const wrapper = mountShell({
      props: {
        canManagePublicAgentConfig: true,
        canManageWorkspaceAgentConfig: true
      }
    });
    await summonRobot(wrapper);
    await wrapper.get('[data-testid="figma-robot"]').trigger("click");
    await vi.advanceTimersByTimeAsync(250);
    await wrapper.vm.$nextTick();

    await wrapper.get('button[aria-label="选择小宠物"]').trigger("click");
    expect(wrapper.find('[data-testid="pet-companion-settings"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="pet-runtime-reload-actions"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="pet-runtime-reload-confirm"]').exists()).toBe(false);
  });

  it("hides personal Agent update controls without update permission", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    const wrapper = mountShell();
    await summonRobot(wrapper);
    await wrapper.get('[data-testid="figma-robot"]').trigger("click");
    await vi.advanceTimersByTimeAsync(250);
    await wrapper.vm.$nextTick();

    expect(wrapper.find('[data-testid="pet-runtime-reload-actions"]').exists()).toBe(false);
  });

  it("allows adjusting the pet size and persists it with the companion preference", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    const wrapper = mountShell();
    await summonRobot(wrapper);

    await wrapper.get('[data-testid="figma-robot"]').trigger("click");
    await vi.advanceTimersByTimeAsync(250);
    await wrapper.vm.$nextTick();
    await wrapper.get('button[aria-label="选择小宠物"]').trigger("click");

    const range = wrapper.get('[data-testid="pet-size-range"]');
    expect(range.attributes("max")).toBe("2.5");
    expect(wrapper.get('[data-testid="pet-size-value"]').text()).toBe("150%");
    expect(wrapper.get('[data-testid="figma-robot"]').attributes("style")).toContain("width: 66px");
    expect(wrapper.get('[data-testid="figma-robot"]').attributes("style")).toContain("height: 72px");

    await range.setValue("2.5");
    expect(wrapper.get('[data-testid="pet-size-value"]').text()).toBe("250%");
    expect(wrapper.get('[data-testid="figma-robot"]').attributes("style")).toContain("width: 110px");
    expect(wrapper.get('[data-testid="figma-robot"]').attributes("style")).toContain("height: 120px");
    expect(JSON.parse(window.localStorage.getItem("test-agent.pet-companion.v1")!)).toMatchObject({ scale: 2.5 });

    await wrapper.get('button[aria-label="缩小小宠物"]').trigger("click");
    expect(wrapper.get('[data-testid="pet-size-value"]').text()).toBe("245%");
  });

  it("persists a pointer drag after crossing the movement threshold", async () => {
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 100, y: 100 }));
    const wrapper = mountShell();
    await wrapper.vm.$nextTick();
    await summonRobot(wrapper);
    const robot = wrapper.get('[data-testid="figma-robot"]');

    dispatchPointer(robot.element, "pointerdown", 7, 110, 110, "mouse");
    dispatchPointer(window, "pointermove", 7, 112, 112, "mouse");
    dispatchPointer(window, "pointerup", 7, 112, 112, "mouse");
    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify({ x: 100, y: 100 }));

    dispatchPointer(robot.element, "pointerdown", 8, 110, 110, "touch");
    dispatchPointer(window, "pointermove", 8, 170, 160, "touch");
    dispatchPointer(window, "pointerup", 8, 170, 160, "touch");
    await wrapper.vm.$nextTick();

    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify({ x: 160, y: 150 }));
    expect(robot.attributes("style")).toContain("left: 160px");
    expect(robot.attributes("style")).toContain("top: 150px");
  });

  it("drags successfully even if pointer capture throws DOMException in the compatibility path", async () => {
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 100, y: 100 }));
    const wrapper = mountShell();
    await wrapper.vm.$nextTick();
    await summonRobot(wrapper);
    const robot = wrapper.get('[data-testid="figma-robot"]');
    const setPointerCapture = vi.fn(() => {
      throw new DOMException("pointer capture unavailable", "NotFoundError");
    });
    Object.defineProperty(robot.element, "setPointerCapture", {
      configurable: true,
      value: setPointerCapture,
    });

    dispatchPointer(robot.element, "pointerdown", 81, 100, 100, "mouse");
    dispatchPointer(window, "pointermove", 81, 145, 135, "mouse");
    dispatchPointer(window, "pointerup", 81, 145, 135, "mouse");
    await wrapper.vm.$nextTick();

    expect(setPointerCapture).toHaveBeenCalled();
    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify({ x: 145, y: 135 }));
  });

  it("keeps dragging when a workbench child stops pointer event propagation", async () => {
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 100, y: 100 }));
    const wrapper = mountShell();
    await wrapper.vm.$nextTick();
    await summonRobot(wrapper);
    const robot = wrapper.get('[data-testid="figma-robot"]');
    const eventBlocker = document.createElement("div");
    eventBlocker.dataset.testid = "pointer-event-blocker";
    eventBlocker.addEventListener("pointermove", (event) => event.stopPropagation());
    eventBlocker.addEventListener("pointerup", (event) => event.stopPropagation());
    document.body.appendChild(eventBlocker);

    dispatchPointer(robot.element, "pointerdown", 82, 100, 100, "mouse");
    dispatchPointer(eventBlocker, "pointermove", 82, 150, 140, "mouse");
    dispatchPointer(eventBlocker, "pointerup", 82, 150, 140, "mouse");
    await wrapper.vm.$nextTick();

    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify({ x: 150, y: 140 }));
    expect(document.body.style.cursor).toBe("");
    expect(document.body.style.userSelect).toBe("");
  });

  it("ignores move and release events from a non-active pointer", async () => {
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 100, y: 100 }));
    const wrapper = mountShell();
    await wrapper.vm.$nextTick();
    await summonRobot(wrapper);
    const robot = wrapper.get('[data-testid="figma-robot"]');

    dispatchPointer(robot.element, "pointerdown", 20, 100, 100, "touch");
    dispatchPointer(window, "pointermove", 21, 170, 160, "touch");
    dispatchPointer(window, "pointerup", 21, 170, 160, "touch");
    expect(robot.attributes("style")).toContain("left: 100px");
    expect(robot.attributes("style")).toContain("top: 100px");
    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify({ x: 100, y: 100 }));

    dispatchPointer(window, "pointermove", 20, 150, 140, "touch");
    dispatchPointer(window, "pointerup", 20, 150, 140, "touch");
    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify({ x: 150, y: 140 }));
  });

  it("clamps and persists a manually positioned robot when the viewport shrinks", async () => {
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 900, y: 700 }));
    Object.defineProperty(window, "innerWidth", { configurable: true, value: 320 });
    Object.defineProperty(window, "innerHeight", { configurable: true, value: 240 });

    const wrapper = mountShell();
    await window.dispatchEvent(new Event("resize"));
    await wrapper.vm.$nextTick();
    await summonRobot(wrapper);

    const robot = wrapper.get('[data-testid="figma-robot"]');
    expect(robot.attributes("style")).toContain("left: 246px");
    expect(robot.attributes("style")).toContain("top: 160px");
    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify({ x: 246, y: 160 }));
  });

  it("does not overwrite the saved start position when natural motion is reclamped on resize", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    vi.spyOn(Math, "random").mockReturnValue(0);
    const savedPosition = { x: 280, y: 100 };
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify(savedPosition));
    const wrapper = mountShell();
    await summonRobot(wrapper);

    // 让自然动作先把当前内存坐标移动到另一个位置，再模拟窗口缩小。
    await vi.advanceTimersByTimeAsync(1_100);
    Object.defineProperty(window, "innerWidth", { configurable: true, value: 200 });
    Object.defineProperty(window, "innerHeight", { configurable: true, value: 240 });
    await window.dispatchEvent(new Event("resize"));
    await wrapper.vm.$nextTick();

    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify(savedPosition));
    expect(wrapper.get('[data-testid="figma-robot"]').attributes("style")).toContain("left: 126px");
  });

  it("ignores malformed robot positions and storage access failures", async () => {
    for (const invalidPosition of ["not-json", JSON.stringify({ x: "120", y: 180 }), JSON.stringify({ x: 120 })]) {
      window.localStorage.setItem("figma-shell-robot-pos", invalidPosition);
      const wrapper = mountShell();
      await wrapper.vm.$nextTick();
      expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(false);
    }

    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("storage unavailable");
    });
    expect(() => mountShell()).not.toThrow();
    vi.restoreAllMocks();

    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 100, y: 100 }));
    const setItem = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("storage unavailable");
    });
    const wrapper = mountShell();
    await wrapper.vm.$nextTick();
    await summonRobot(wrapper);
    const robot = wrapper.get('[data-testid="figma-robot"]');
    expect(() => {
      dispatchPointer(robot.element, "pointerdown", 9, 100, 100, "mouse");
      dispatchPointer(window, "pointermove", 9, 140, 140, "mouse");
      dispatchPointer(window, "pointerup", 9, 140, 140, "mouse");
    }).not.toThrow();
    await wrapper.vm.$nextTick();
    expect(setItem).toHaveBeenCalled();
    expect(robot.attributes("style")).toContain("left: 140px");
    expect(robot.attributes("style")).toContain("top: 140px");
  });

  it("persists a pen Pointer drag", async () => {
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 100, y: 100 }));
    const wrapper = mountShell();
    await wrapper.vm.$nextTick();
    await summonRobot(wrapper);
    const robot = wrapper.get('[data-testid="figma-robot"]');

    dispatchPointer(robot.element, "pointerdown", 10, 100, 100, "pen");
    dispatchPointer(window, "pointermove", 10, 125, 135, "pen");
    dispatchPointer(window, "pointerup", 10, 125, 135, "pen");
    await wrapper.vm.$nextTick();

    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify({ x: 125, y: 135 }));
  });

  it("drags successfully using fallback MouseEvents when PointerEvent is simulated as missing", async () => {
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 100, y: 100 }));
    const wrapper = mountShell();
    await wrapper.vm.$nextTick();
    await summonRobot(wrapper);
    const robot = wrapper.get('[data-testid="figma-robot"]');

    dispatchPointer(robot.element, "pointerdown", 99, 100, 100, "mouse");

    const moveEvent = new MouseEvent("mousemove", { bubbles: true, clientX: 130, clientY: 140 });
    window.dispatchEvent(moveEvent);

    const upEvent = new MouseEvent("mouseup", { bubbles: true, clientX: 130, clientY: 140 });
    window.dispatchEvent(upEvent);

    await wrapper.vm.$nextTick();
    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify({ x: 130, y: 140 }));
  });

  it("moves the pet with arrow keys and persists the clamped position", async () => {
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 100, y: 100 }));
    const wrapper = mountShell();
    await wrapper.vm.$nextTick();
    await summonRobot(wrapper);
    const robot = wrapper.get('[data-testid="figma-robot"]');

    expect(robot.attributes("tabindex")).toBe("0");
    expect(robot.attributes("aria-label")).toContain("方向键移动");
    await robot.trigger("keydown", { key: "ArrowRight" });
    await robot.trigger("keydown", { key: "ArrowDown" });

    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify({ x: 108, y: 108 }));
  });

  it("cleans up an interrupted drag after pointer cancel and window blur", async () => {
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 100, y: 100 }));
    const wrapper = mountShell();
    await wrapper.vm.$nextTick();
    await summonRobot(wrapper);
    const robot = wrapper.get('[data-testid="figma-robot"]');

    dispatchPointer(robot.element, "pointerdown", 11, 100, 100, "mouse");
    dispatchPointer(window, "pointermove", 11, 140, 130, "mouse");
    dispatchPointer(window, "pointercancel", 11, 140, 130, "mouse");
    await wrapper.vm.$nextTick();

    expect(document.body.style.cursor).toBe("");
    expect(document.body.style.userSelect).toBe("");
    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify({ x: 140, y: 130 }));

    dispatchPointer(robot.element, "pointerdown", 12, 140, 130, "touch");
    dispatchPointer(window, "pointermove", 12, 160, 160, "touch");
    window.dispatchEvent(new Event("blur"));
    window.dispatchEvent(new Event("blur"));
    await wrapper.vm.$nextTick();

    expect(document.body.style.cursor).toBe("");
    expect(document.body.style.userSelect).toBe("");
    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify({ x: 160, y: 160 }));
  });

  it("resumes natural actions and natural exit timers after an effective drag", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    vi.spyOn(Math, "random").mockReturnValue(0);
    window.localStorage.setItem("figma-shell-robot-fixed", "false");
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 100, y: 100 }));
    const wrapper = mountShell();
    await summonRobot(wrapper);
    const robot = wrapper.get('[data-testid="figma-robot"]');
    const robotElement = robot.element as HTMLElement;
    const initialX = Number.parseInt(robotElement.style.left, 10);
    const initialY = Number.parseInt(robotElement.style.top, 10);
    dispatchPointer(robotElement, "pointerdown", 13, initialX, initialY, "mouse");
    dispatchPointer(window, "pointermove", 13, initialX + 20, initialY + 30, "mouse");
    dispatchPointer(window, "pointerup", 13, initialX + 20, initialY + 30, "mouse");
    await wrapper.vm.$nextTick();

    const savedPosition = window.localStorage.getItem("figma-shell-robot-pos");
    const parsedSavedPosition = JSON.parse(savedPosition!);
    expect(parsedSavedPosition.x).toBeGreaterThan(initialX);
    expect(parsedSavedPosition.y).toBeGreaterThan(initialY);

    await vi.advanceTimersByTimeAsync(1_100);
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="figma-robot"] .state-walking').exists()).toBe(true);

    await vi.advanceTimersByTimeAsync(18_000);
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(false);
    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(savedPosition);
  });

  it("pauses natural pet motion while the mouse hovers over it", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    vi.spyOn(Math, "random").mockReturnValue(0);
    window.localStorage.setItem("figma-shell-robot-fixed", "false");
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 100, y: 100 }));
    const wrapper = mountShell();
    await summonRobot(wrapper);
    const robot = wrapper.get('[data-testid="figma-robot"]');

    dispatchPointer(robot.element, "pointerenter", 14, 100, 100, "mouse");
    await vi.advanceTimersByTimeAsync(3_000);
    await wrapper.vm.$nextTick();
    expect(robot.find(".state-idle").exists()).toBe(true);
    expect(robot.find(".state-walking").exists()).toBe(false);

    dispatchPointer(robot.element, "pointerleave", 14, 100, 100, "mouse");
    await vi.advanceTimersByTimeAsync(1_100);
    await wrapper.vm.$nextTick();
    expect(robot.find(".state-walking").exists()).toBe(true);
  });

  it("can enter the added shake and celebration actions", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    const random = vi.spyOn(Math, "random").mockReturnValue(0.8);
    window.localStorage.setItem("figma-shell-robot-fixed", "false");
    const wrapper = mountShell();
    await summonRobot(wrapper);
    await vi.advanceTimersByTimeAsync(1_801);
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="figma-robot"] .state-shaking').exists()).toBe(true);

    random.mockReturnValue(0.9);
    await vi.advanceTimersByTimeAsync(3_000);
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="figma-robot"] .state-celebrating').exists()).toBe(true);
  });

  it("keeps the summoned pet visible when the user continues interacting with the page", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    const wrapper = mountShell();
    await summonRobot(wrapper);

    window.dispatchEvent(new MouseEvent("mousemove", { bubbles: true }));
    window.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "a", bubbles: true }));
    window.dispatchEvent(new Event("scroll"));
    await vi.advanceTimersByTimeAsync(2_000);
    await wrapper.vm.$nextTick();

    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(true);
  });

  it("keeps a manually summoned pet visible until the user hides or repositions it", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    vi.spyOn(Math, "random").mockReturnValue(0);
    window.localStorage.setItem("figma-shell-robot-fixed", "false");
    const wrapper = mountShell();
    await summonRobot(wrapper);

    await vi.advanceTimersByTimeAsync(20_000);
    await wrapper.vm.$nextTick();

    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(true);
  });

  it("places the pet toggle in the lower activity rail and keeps the avatar compact", () => {
    const wrapper = mountShell({
      slots: {
        activity: '<nav class="figma-activity-nav"><div class="figma-activity-bottom"><button class="figma-activity-btn" aria-label="系统设置">设置</button></div></nav>'
      }
    });

    const toggle = wrapper.get('[data-testid="robot-visibility-toggle"]');
    expect(toggle.classes()).toContain("figma-robot-visibility-toggle--activity");
    expect(wrapper.get(".figma-activity-bar").find('[data-testid="robot-visibility-toggle"]').exists()).toBe(true);
    expect(wrapper.get(".figma-user-avatar").classes()).toContain("figma-user-avatar--compact");
  });

  it("opens games from the shared pet dialog without a separate activity button", async () => {
    vi.useFakeTimers();
    const wrapper = mountShell({ props: { canPlayPetGames: true } });
    await summonRobot(wrapper);

    expect(wrapper.find('[data-testid="robot-game-toggle"]').exists()).toBe(false);
    await wrapper.get('[data-testid="figma-robot"]').trigger("click");
    await vi.advanceTimersByTimeAsync(250);
    await wrapper.vm.$nextTick();
    await wrapper.get('[aria-label="打开宠物小游戏"]').trigger("click");

    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="pet-mini-games"]').exists()).toBe(true);
    expect(wrapper.text()).toContain("俄罗斯方块");
    expect(wrapper.text()).toContain("扫雷");
    expect(wrapper.text()).toContain("数独");
    expect(wrapper.text()).toContain("贪吃蛇");

    await wrapper.get('[aria-label="关闭宠物旁路问答"]').trigger("click");
    expect(wrapper.find('[data-testid="pet-mini-games"]').exists()).toBe(false);
  });

  it("hides the pet game entry for non-super administrators", async () => {
    vi.useFakeTimers();
    const wrapper = mountShell();
    await summonRobot(wrapper);

    await wrapper.get('[data-testid="figma-robot"]').trigger("click");
    await vi.advanceTimersByTimeAsync(250);
    await wrapper.vm.$nextTick();

    expect(wrapper.find('[aria-label="打开宠物小游戏"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="pet-mini-games"]').exists()).toBe(false);
  });

  it("opens a transient side-question bubble from the pet and emits the question", async () => {
    vi.useFakeTimers();
    const wrapper = mountShell({
      props: {
        sideQuestionAnswer: "当前上下文已经完成初始化。"
      }
    });
    await summonRobot(wrapper);

    await wrapper.get('[data-testid="figma-robot"]').trigger("click");
    await vi.advanceTimersByTimeAsync(250);
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="robot-side-question"]').exists()).toBe(true);

    const input = wrapper.get('[data-testid="robot-side-question-input"]');
    await input.setValue("刚才做了什么？");
    await wrapper.get('[data-testid="robot-side-question-submit"]').trigger("click");

    expect(wrapper.emitted("robot-side-question")?.[0]).toEqual(["刚才做了什么？"]);
    expect(wrapper.get('[data-testid="robot-side-question-answer"]').text()).toContain("当前上下文已经完成初始化");
  });

  it("keeps games available but disables pet conversation until a main session exists", async () => {
    vi.useFakeTimers();
    const wrapper = mountShell({
      props: {
        canPlayPetGames: true,
        showProcessStatusInPet: true,
        sideQuestionAvailable: false,
        opencodeProcessStatus: {
          status: "READY",
          initializable: false,
          message: "TestAgent 进程已就绪",
          serviceStatus: "RUNNING",
          serviceAddress: "127.0.0.1:4096"
        }
      }
    });
    await summonRobot(wrapper);
    await wrapper.get('[data-testid="figma-robot"]').trigger("click");
    await vi.advanceTimersByTimeAsync(250);

    expect(wrapper.find('[data-testid="robot-process-status"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="robot-side-question"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="robot-side-question-input"]').attributes("disabled")).toBeDefined();
    expect(wrapper.get('[data-testid="robot-side-question-input"]').attributes("placeholder")).toBe("请先选择工作区并初始化服务");
    await wrapper.get('[aria-label="打开宠物小游戏"]').trigger("click");
    expect(wrapper.find('[data-testid="pet-mini-games"]').exists()).toBe(true);
    expect(wrapper.emitted("robot-side-question")).toBeUndefined();
  });

  it("shows real side-question progress and keeps the dialog open on outside clicks while loading", async () => {
    vi.useFakeTimers();
    const wrapper = mountShell({
      props: {
        sideQuestionLoading: true,
        sideQuestionProgress: "正在读取当前上下文"
      }
    });
    await summonRobot(wrapper);
    await wrapper.get('[data-testid="figma-robot"]').trigger("click");
    await vi.advanceTimersByTimeAsync(250);

    expect(wrapper.get('[data-testid="robot-side-question-progress"]').text()).toBe("正在读取当前上下文");
    await wrapper.get(".figma-app").trigger("click");
    expect(wrapper.find('[data-testid="robot-side-question"]').exists()).toBe(true);

    await wrapper.get('[aria-label="关闭宠物旁路问答"]').trigger("click");
    expect(wrapper.find('[data-testid="robot-side-question"]').exists()).toBe(false);
    expect(wrapper.emitted("close-robot-side-question")).toHaveLength(1);
  });

  it("closes the side-question subscription owner when the pet is hidden", async () => {
    vi.useFakeTimers();
    const wrapper = mountShell({ props: { sideQuestionLoading: true } });
    await summonRobot(wrapper);
    await wrapper.get('[data-testid="figma-robot"]').trigger("click");
    await vi.advanceTimersByTimeAsync(250);
    expect(wrapper.find('[data-testid="robot-side-question"]').exists()).toBe(true);

    await wrapper.get('[data-testid="robot-visibility-toggle"]').trigger("click");

    expect(wrapper.find('[data-testid="robot-side-question"]').exists()).toBe(false);
    expect(wrapper.emitted("close-robot-side-question")).toHaveLength(1);
  });

  it("keeps an automatically appeared pet and its open dialog visible until the user closes it", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    vi.spyOn(Math, "random").mockReturnValue(0);
    const wrapper = mountShell();

    await vi.advanceTimersByTimeAsync(60_000);
    await wrapper.get('[data-testid="figma-robot"]').trigger("click");
    await vi.advanceTimersByTimeAsync(250);
    expect(wrapper.find('[data-testid="robot-side-question"]').exists()).toBe(true);

    await vi.advanceTimersByTimeAsync(15_000);

    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="robot-side-question"]').exists()).toBe(true);
    expect(wrapper.emitted("close-robot-side-question")).toBeUndefined();
  });

  it("keeps a failed side question editable so the user can revise and retry", async () => {
    vi.useFakeTimers();
    const wrapper = mountShell({ props: { sideQuestionError: "暂时无法回答" } });
    await summonRobot(wrapper);
    await wrapper.get('[data-testid="figma-robot"]').trigger("click");
    await vi.advanceTimersByTimeAsync(250);

    const input = wrapper.get('[data-testid="robot-side-question-input"]');
    await input.setValue("修改后的问题");
    expect(input.attributes("disabled")).toBeUndefined();
    await wrapper.get('[data-testid="robot-side-question-submit"]').trigger("click");

    expect(wrapper.emitted("robot-side-question")?.[0]).toEqual(["修改后的问题"]);
    expect(wrapper.get(".figma-robot-side-question-error").text()).toBe("暂时无法回答");
  });

  it("toggles the pet immediately and restores a saved manual position after a full idle minute", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    window.localStorage.setItem("figma-shell-robot-fixed", "false");
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 140, y: 160 }));
    const wrapper = mountShell();
    await wrapper.vm.$nextTick();

    const toggle = wrapper.get('[data-testid="robot-visibility-toggle"]');
    expect(toggle.attributes("aria-label")).toBe("唤起小宠物");
    expect(toggle.attributes("aria-pressed")).toBe("false");
    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(false);

    await toggle.trigger("click");
    expect(toggle.attributes("aria-label")).toBe("收起小宠物");
    expect(toggle.attributes("aria-pressed")).toBe("true");
    expect(wrapper.get('[data-testid="figma-robot"]').attributes("style")).toContain("left: 140px");

    await toggle.trigger("click");
    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(false);
    await vi.advanceTimersByTimeAsync(59_000);
    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(false);
    await vi.advanceTimersByTimeAsync(1_000);
    await wrapper.vm.$nextTick();

    const restored = wrapper.get('[data-testid="figma-robot"]');
    expect(restored.attributes("style")).toContain("left: 140px");
    expect(restored.attributes("style")).toContain("top: 160px");
    expect(restored.find(".state-idle").exists()).toBe(true);
  });

  it("starts collapsed on page entry even when the fixed pet preference was saved", async () => {
    window.localStorage.setItem("figma-shell-robot-fixed", "true");
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 140, y: 160 }));
    const wrapper = mountShell();
    await wrapper.vm.$nextTick();

    const toggle = wrapper.get('[data-testid="robot-visibility-toggle"]');
    expect(toggle.attributes("aria-label")).toBe("唤起小宠物");
    expect(toggle.attributes("aria-pressed")).toBe("false");
    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(false);

    await toggle.trigger("click");
    expect(toggle.attributes("aria-label")).toBe("收起小宠物");
    expect(wrapper.get('[data-testid="figma-robot"]').attributes("style")).toContain("left: 140px");
  });

  it("restarts the hidden pet timer after activity and only counts while focused and visible", async () => {
    vi.useFakeTimers();
    const hasFocus = vi.spyOn(document, "hasFocus").mockReturnValue(true);
    const hidden = vi.spyOn(document, "hidden", "get").mockReturnValue(false);
    window.localStorage.setItem("figma-shell-robot-fixed", "false");
    const wrapper = mountShell();

    await vi.advanceTimersByTimeAsync(30_000);
    window.dispatchEvent(new MouseEvent("mousedown"));
    await vi.advanceTimersByTimeAsync(30_000);
    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(false);
    await vi.advanceTimersByTimeAsync(30_000);
    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(true);

    await wrapper.get('[data-testid="robot-visibility-toggle"]').trigger("click");
    hidden.mockReturnValue(true);
    document.dispatchEvent(new Event("visibilitychange"));
    await vi.advanceTimersByTimeAsync(60_000);
    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(false);
    hidden.mockReturnValue(false);
    hasFocus.mockReturnValue(true);
    window.dispatchEvent(new Event("focus"));
    await vi.advanceTimersByTimeAsync(60_000);
    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(true);
  });

  it("does not close an open header menu when toggling the pet", async () => {
    const wrapper = mountShell();
    const appMenu = wrapper.get(".figma-app-menu-trigger");
    await appMenu.trigger("click");
    expect(wrapper.find(".figma-app-menu-dropdown").exists()).toBe(true);

    await wrapper.get('[data-testid="robot-visibility-toggle"]').trigger("click");
    expect(wrapper.find(".figma-app-menu-dropdown").exists()).toBe(true);
  });

  it("keeps application context centered and places manual, runtime inventory, and user on the right", async () => {
    const wrapper = mountShell({
      props: {
        currentUserName: "developer",
        helpCenterOpen: true,
        apps: [{ id: "app_coss", name: "F-COSS", description: "已启用" }],
        selectedAppId: "app_coss",
        runtimeInventory: {
          agents: [
            { id: "build", name: "Build", status: "primary" },
            { id: "review", name: "Review", status: "subagent" }
          ],
          skills: [{ id: "skill-test", name: "test-skill", description: "测试技能" }],
          mcp: [
            { id: "filesystem", name: "filesystem", status: "connected" },
            { id: "github", name: "github", status: "failed" }
          ],
          tools: [{ id: "read-file", name: "read_file", status: "mcp" }],
          plugins: [],
          mcpResources: [{ id: "repo", name: "Repository", status: "git" }]
        }
      } as any
    });

    const headerCenter = wrapper.get(".figma-header-center");
    const headerRight = wrapper.get(".figma-header-right");
    const help = wrapper.get('[data-testid="help-center-open"]');
    const summary = wrapper.get('[data-testid="runtime-inventory-summary"]');
    const appSwitch = wrapper.get(".figma-app-menu-wrapper");
    const workspaceSwitch = wrapper.get('[data-testid="header-workspace-selector"]');
    const versionSwitch = wrapper.get('[data-testid="header-version-selector"]');
    const userSwitch = wrapper.get(".figma-user-avatar-btn");
    const contextRail = wrapper.get('[data-testid="header-context-rail"]');
    const railSegments = contextRail.findAll(
      ":scope > .figma-app-menu-wrapper, :scope > .figma-workspace-menu-wrapper, :scope > .figma-version-menu-wrapper"
    );
    expect(railSegments.map((segment) => segment.classes()[0])).toEqual([
      "figma-app-menu-wrapper",
      "figma-workspace-menu-wrapper",
      "figma-version-menu-wrapper"
    ]);
    expect(contextRail.classes()).not.toContain("has-open-menu");
    expect(headerCenter.element.contains(appSwitch.element)).toBe(true);
    expect(appSwitch.element.compareDocumentPosition(workspaceSwitch.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(workspaceSwitch.element.compareDocumentPosition(versionSwitch.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(headerRight.element.contains(help.element)).toBe(true);
    expect(help.classes()).toContain("is-open");
    expect(help.attributes("aria-pressed")).toBe("true");
    expect(help.element.compareDocumentPosition(summary.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(summary.element.compareDocumentPosition(userSwitch.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(summary.text()).toContain("Agent 2");
    expect(summary.text()).toContain("Skill 1");
    expect(summary.text()).toContain("MCP 2");
    expect(summary.text()).toContain("Tool 1");
    expect(summary.text()).toContain("Plugin 0");

    await appSwitch.get("button").trigger("click");
    expect(contextRail.classes()).toContain("has-open-menu");
    expect(appSwitch.get("button").attributes("aria-expanded")).toBe("true");

    await summary.trigger("click");

    expect(wrapper.find('[data-testid="runtime-inventory-panel"]').exists()).toBe(true);
    expect(wrapper.text()).toContain("Build");
    expect(wrapper.text()).toContain("test-skill");
    expect(wrapper.text()).toContain("filesystem");
    expect(wrapper.text()).toContain("read_file");
    expect(wrapper.text()).toContain("Repository");
    expect(wrapper.text()).toContain("当前运行态未提供独立 Plugin 目录");
  });

  it("resizes the runtime inventory from its left edge and supports page fullscreen", async () => {
    const wrapper = mountShell({
      props: {
        runtimeInventory: { agents: [], skills: [], mcp: [], tools: [], plugins: [] }
      }
    });

    await wrapper.get('[data-testid="runtime-inventory-summary"]').trigger("click");
    const panel = wrapper.get('[data-testid="runtime-inventory-panel"]');
    const handle = wrapper.get('button[aria-label="调整运行态资源详情宽度"]');
    expect(panel.attributes("data-layout-mode")).toBe("window");
    expect(panel.element.getAttribute("style")).toContain("width: 520px");

    dispatchPointer(handle.element, "pointerdown", 91, 500, 100, "mouse");
    dispatchPointer(window, "pointermove", 91, 420, 100, "mouse");
    dispatchPointer(window, "pointerup", 91, 420, 100, "mouse");
    await wrapper.vm.$nextTick();
    expect(panel.element.getAttribute("style")).toContain("width: 600px");

    await wrapper.get('button[aria-label="进入全屏"]').trigger("click");
    await wrapper.vm.$nextTick();
    const fullscreenPanel = document.body.querySelector<HTMLElement>('[data-testid="runtime-inventory-panel"]');
    expect(fullscreenPanel?.dataset.layoutMode).toBe("fullscreen");
    expect(fullscreenPanel?.style.width).toBe("100vw");
    expect(document.body.querySelector('button[aria-label="调整运行态资源详情宽度"]')).toBeNull();

    (document.body.querySelector('button[aria-label="退出全屏"]') as HTMLButtonElement).click();
    await wrapper.vm.$nextTick();
    expect(wrapper.get('[data-testid="runtime-inventory-panel"]').element.getAttribute("style")).toContain("width: 600px");
  });

  it("reuses the existing workspace version loading and selection callbacks from the header", async () => {
    const workspaceA = {
      workspaceId: "workspace-a",
      workspaceName: "核心服务",
      branch: "main",
      enabled: true,
      versions: [{ versionId: "version-a", version: "20260701", branch: "release/a" }]
    };
    const latestWorkspaceBVersion = { versionId: "version-b-latest", version: "20260715", branch: "release/b-latest" };
    const workspaceB = {
      workspaceId: "workspace-b",
      workspaceName: "批量回归",
      branch: "develop",
      enabled: true,
      versions: [latestWorkspaceBVersion, { versionId: "version-b-old", version: "20260701", branch: "release/b-old" }]
    };
    const workspaceC = {
      workspaceId: "workspace-c",
      workspaceName: "待加载空间",
      branch: "feature/c",
      enabled: true
    };
    const wrapper = mountShell({
      props: {
        appTemplates: [workspaceA, workspaceB, workspaceC],
        selectedWorkspaceTemplateId: workspaceA.workspaceId,
        selectedVersionId: "version-a"
      } as any
    });

    const workspaceButton = wrapper.get('[data-testid="header-workspace-selector"]');
    const versionButton = wrapper.get('[data-testid="header-version-selector"]');
    expect(workspaceButton.text()).toContain("核心服务");
    expect(versionButton.text()).toContain("20260701");

    await workspaceButton.trigger("click");
    const workspaceBItem = wrapper.findAll(".figma-workspace-menu-wrapper .figma-app-menu-item")
      .find((item) => item.text().includes("批量回归"));
    expect(workspaceBItem).toBeTruthy();
    await workspaceBItem!.trigger("mousedown");
    // 顶部显示以父层实际完成的切换为准，避免菜单先显示新工作区而左侧仍是旧目录。
    expect(workspaceButton.text()).toContain("核心服务");
    expect(versionButton.text()).toContain("20260701");
    expect(wrapper.emitted("select-version")?.[0]?.[0]).toEqual({ template: workspaceB, version: latestWorkspaceBVersion });
    await wrapper.setProps({
      selectedWorkspaceTemplateId: workspaceB.workspaceId,
      selectedVersionId: latestWorkspaceBVersion.versionId
    } as any);
    expect(workspaceButton.text()).toContain("批量回归");
    expect(versionButton.text()).toContain("20260715");

    await versionButton.trigger("click");
    const versionBItem = wrapper.findAll(".figma-version-menu-wrapper .figma-app-menu-item")
      .find((item) => item.text().includes("20260701"));
    expect(versionBItem).toBeTruthy();
    await versionBItem!.trigger("mousedown");
    expect(wrapper.emitted("select-version")?.[1]?.[0]).toEqual({ template: workspaceB, version: workspaceB.versions[1] });

    await workspaceButton.trigger("click");
    const workspaceCItem = wrapper.findAll(".figma-workspace-menu-wrapper .figma-app-menu-item")
      .find((item) => item.text().includes("待加载空间"));
    expect(workspaceCItem).toBeTruthy();
    await workspaceCItem!.trigger("mousedown");
    expect(wrapper.emitted("load-versions")?.[0]).toEqual(["workspace-c"]);

    const latestWorkspaceCVersion = { versionId: "version-c-latest", version: "20260731", branch: "release/c-latest" };
    await wrapper.setProps({
      appTemplates: [workspaceA, workspaceB, { ...workspaceC, versions: [latestWorkspaceCVersion] }]
    } as any);
    await wrapper.vm.$nextTick();
    expect(versionButton.text()).toContain("20260715");
    expect(wrapper.emitted("select-version")?.[2]?.[0]).toEqual({
      template: expect.objectContaining({ workspaceId: "workspace-c" }),
      version: latestWorkspaceCVersion
    });
    await wrapper.setProps({
      selectedWorkspaceTemplateId: workspaceC.workspaceId,
      selectedVersionId: latestWorkspaceCVersion.versionId
    } as any);
    expect(workspaceButton.text()).toContain("待加载空间");
    expect(versionButton.text()).toContain("20260731");
  });

  it("groups app code repositories, automation repositories, and test workspaces in the header workspace menu", async () => {
    const downloadedRepository = {
      repositoryId: "repo-code",
      name: "应用代码库",
      englishName: "app-code",
      downloadState: "DOWNLOADED_ACTIVE",
      generation: 3,
      purpose: "TEAM",
      branch: "main",
      selectedPaths: [],
      occupied: false,
      openable: true,
      manageable: true,
      serverSummaries: []
    };
    const notDownloadedRepository = {
      ...downloadedRepository,
      repositoryId: "repo-new",
      name: "尚未拉取",
      downloadState: "NOT_DOWNLOADED",
      generation: null
    };
    const unavailableRepository = {
      ...downloadedRepository,
      repositoryId: "repo-unavailable",
      name: "副本未就绪",
      openable: false,
      unavailableReason: "当前服务器副本未就绪"
    };
    const testWorkspace = {
      workspaceId: "workspace-test",
      workspaceName: "测试工作空间",
      branch: "feature/test",
      enabled: true,
      versions: [{ versionId: "version-test", version: "20260731", branch: "feature/test" }]
    };
    const automationWorkspace = {
      ...testWorkspace,
      workspaceId: "workspace-automation",
      workspaceName: "接口自动化",
      repositoryType: "AUTOMATION_CODE_REPOSITORY",
      branch: "main",
      versions: [{ versionId: "version-automation", version: "20260813", branch: "main" }]
    };
    const wrapper = mountShell({
      props: {
        showAppSource: true,
        appSourceRepositories: [downloadedRepository, notDownloadedRepository, unavailableRepository],
        appTemplates: [testWorkspace, automationWorkspace],
        selectedWorkspaceTemplateId: testWorkspace.workspaceId,
        selectedVersionId: "version-test"
      } as any
    });

    const workspaceButton = wrapper.get('[data-testid="header-workspace-selector"]');
    await workspaceButton.trigger("click");

    expect(wrapper.emitted("load-app-source-repositories")).toHaveLength(1);
    const sectionTitles = wrapper.findAll(".figma-context-menu-section-title");
    expect(sectionTitles[0].text()).toContain("应用代码库");
    expect(sectionTitles[0].text()).toContain("管理");
    expect(sectionTitles[1].text()).toBe("自动化代码库");
    expect(sectionTitles[2].text()).toBe("测试工作空间");
    expect(sectionTitles.every((title) => title.find(".figma-context-menu-type-icon").exists())).toBe(true);
    expect(wrapper.get('[aria-label="管理尚未拉取源码"]').classes()).toContain("is-not-downloaded");
    expect(wrapper.get('[aria-label="管理尚未拉取源码"]').attributes("disabled")).toBeUndefined();
    expect(wrapper.get('[aria-label="打开副本未就绪源码"]').attributes("disabled")).toBe("");
    expect(wrapper.get('[aria-label="打开副本未就绪源码"]').attributes("title")).toBe("当前服务器副本未就绪");
    expect(wrapper.findAll(".figma-workspace-menu-wrapper .figma-app-menu-item")
      .some((item) => item.text().includes("测试工作空间"))).toBe(true);
    expect(wrapper.findAll(".figma-workspace-menu-wrapper .figma-app-menu-item")
      .some((item) => item.text().includes("接口自动化"))).toBe(true);

    await wrapper.get('[aria-label="管理尚未拉取源码"]').trigger("mousedown");
    expect(wrapper.emitted("manage-app-source-repository")?.[0]).toEqual([notDownloadedRepository]);

    await workspaceButton.trigger("click");
    await wrapper.get('[aria-label="打开应用代码库源码"]').trigger("mousedown");
    expect(wrapper.emitted("open-app-source-repository")?.[0]).toEqual([downloadedRepository]);
  });

  it("shows the active code repository and source snapshot state in the header", async () => {
    const repository = {
      repositoryId: "repo-code",
      name: "应用代码库",
      englishName: "app-code",
      downloadState: "DOWNLOADED_ACTIVE",
      generation: 3,
      purpose: "TEAM",
      branch: "main",
      selectedPaths: [],
      occupied: false,
      openable: true,
      manageable: true,
      serverSummaries: []
    };
    const wrapper = mountShell({
      props: {
        showAppSource: true,
        workspaceKind: "APP_SOURCE",
        appSourceRepositories: [repository],
        selectedAppSourceRepositoryId: repository.repositoryId,
        workspaceName: "应用代码库工作区"
      } as any
    });

    const workspaceButton = wrapper.get('[data-testid="header-workspace-selector"]');
    const versionButton = wrapper.get('[data-testid="header-version-selector"]');
    expect(workspaceButton.text()).toContain("应用代码库");
    expect(workspaceButton.find(".figma-context-trigger-type-icon").exists()).toBe(true);
    expect(versionButton.text()).toContain("源码快照");
    expect(versionButton.attributes("disabled")).toBe("");

    await workspaceButton.trigger("click");
    expect(wrapper.get('[aria-label="打开应用代码库源码"]').classes()).toContain("is-active");
  });

  it("shows process status with server name and resolved address", async () => {
    const wrapper = mountShell({
      props: {
        currentUserName: "888888888",
        opencodeProcessStatus: {
          status: "NEEDS_INITIALIZATION",
          initializable: true,
          message: "TestAgent 进程不可用，需要重新初始化",
          linuxServerId: "server-a",
          port: 82,
          serviceStatus: "NOT_RUNNING",
          serviceAddress: "192.168.100.171:82",
          checkedAt: "2026-07-02T00:00:00Z"
        },
        opencodeProcessLoading: false
      }
    });

    await wrapper.get(".figma-user-avatar-btn").trigger("click");

    expect(wrapper.get(".figma-user-menu-service-text").text()).toBe("未运行(server-a / 192.168.100.171:82)");
    expect(wrapper.get('[data-testid="restart-own-process"]').text()).toContain("重启 TestAgent 进程");
  });

  it("emits process restart from the avatar menu, shows pending state and hides it in shared workspaces", async () => {
    const wrapper = mountShell({ props: { currentUserName: "developer" } });

    await wrapper.get(".figma-user-avatar-btn").trigger("click");
    const restartButton = wrapper.get('[data-testid="restart-own-process"]');
    expect(restartButton.attributes("disabled")).toBeUndefined();
    await restartButton.trigger("click");
    expect(wrapper.emitted("restart-process")).toHaveLength(1);

    await wrapper.setProps({ processRestarting: true });
    expect(wrapper.get('[data-testid="restart-own-process"]').attributes("disabled")).toBeDefined();
    expect(wrapper.get('[data-testid="restart-own-process"]').text()).toContain("正在重启");

    await wrapper.setProps({ fixedWorkspace: true });
    expect(wrapper.find('[data-testid="restart-own-process"]').exists()).toBe(false);
  });

  it("opens the focused side-question input directly when the process and main session are ready", async () => {
    vi.useFakeTimers();
    const wrapper = mountShell({
      attachTo: document.body,
      props: {
        opencodeProcessStatus: {
          status: "READY",
          initializable: false,
          message: "TestAgent 进程已就绪",
          serviceStatus: "RUNNING",
          serviceAddress: "127.0.0.1:4096"
        },
        opencodeProcessLoading: false,
        showProcessStatusInPet: true,
        sideQuestionAvailable: true
      }
    });

    await summonRobot(wrapper);
    expect(wrapper.get(".robot-svg").classes()).toContain("status-ready");
    expect(wrapper.get('[data-testid="robot-visibility-toggle"] svg').classes()).toContain("status-ready");

    await wrapper.get('[data-testid="figma-robot"]').trigger("click");
    await vi.advanceTimersByTimeAsync(250);
    await wrapper.vm.$nextTick();

    expect(wrapper.find('[data-testid="robot-process-status"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="robot-side-question"]').exists()).toBe(true);
    expect(document.activeElement).toBe(wrapper.get('[data-testid="robot-side-question-input"]').element);
  });

  it("auto-opens the first initialization prompt with a red breathing pet", async () => {
    vi.useFakeTimers();
    const wrapper = mountShell({
      props: {
        opencodeProcessStatus: {
          status: "NEEDS_INITIALIZATION",
          initializable: true,
          message: "TestAgent 进程不可用，需要重新初始化",
          serviceStatus: "UNASSIGNED"
        },
        opencodeProcessLoading: false,
        opencodeProcessInitializing: false,
        showProcessStatusInPet: true
      }
    });

    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(true);
    expect(wrapper.get('[data-testid="robot-visibility-toggle"]').classes()).toContain("is-process-alert");
    expect(wrapper.get(".robot-svg").classes()).toContain("status-needs-initialization");
    expect(wrapper.get('[data-testid="robot-visibility-toggle"] svg').classes()).toContain("status-needs-initialization");

    const card = wrapper.get('[data-testid="robot-process-status"]');
    expect(card.text()).toContain("要现在帮你初始化吗");
    await card.get(".figma-robot-process-init").trigger("click");
    expect(wrapper.emitted("initialize-process")).toEqual([[]]);

    await card.get('[aria-label="关闭宠物进程状态"]').trigger("click");
    await wrapper.setProps({ opencodeProcessLoading: true });
    await wrapper.setProps({ opencodeProcessLoading: false });
    expect(wrapper.find('[data-testid="robot-process-status"]').exists()).toBe(false);
  });

  it("restarts a stopped assigned process from the activity pet and summons the pet after ready", async () => {
    const wrapper = mountShell({
      props: {
        opencodeProcessStatus: {
          status: "NEEDS_INITIALIZATION",
          initializable: true,
          message: "TestAgent 专属进程未运行",
          serviceStatus: "NOT_RUNNING",
          linuxServerId: "server-a",
          containerId: "ctr_01",
          port: 4096,
          checkedAt: "2026-07-21T00:00:00Z"
        },
        opencodeProcessLoading: false,
        opencodeProcessInitializing: false,
        showProcessStatusInPet: true,
        onboardingActive: true
      }
    });

    const toggle = wrapper.get('[data-testid="robot-visibility-toggle"]');
    expect(toggle.attributes("aria-label")).toBe("启动 TestAgent 进程并唤起小宠物");
    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(false);

    await toggle.trigger("click");
    expect(wrapper.emitted("initialize-process")).toEqual([[]]);
    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(false);

    await wrapper.setProps({ opencodeProcessInitializing: true });
    expect(toggle.attributes("aria-label")).toBe("正在启动 TestAgent 进程");
    expect(toggle.attributes("disabled")).toBeDefined();

    await wrapper.setProps({
      opencodeProcessInitializing: false,
      opencodeProcessStatus: {
        status: "READY",
        initializable: false,
        message: "TestAgent 进程可用",
        serviceStatus: "RUNNING",
        linuxServerId: "server-a",
        containerId: "ctr_01",
        port: 4096,
        checkedAt: "2026-07-21T00:00:01Z"
      }
    });

    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="robot-process-status"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="robot-side-question"]').exists()).toBe(false);
    expect(toggle.attributes("aria-label")).toBe("收起小宠物");
  });

  it("does not summon the pet later when the direct stopped-process restart fails", async () => {
    const stoppedProcess = {
      status: "NEEDS_INITIALIZATION",
      initializable: true,
      message: "TestAgent 专属进程未运行",
      serviceStatus: "NOT_RUNNING",
      linuxServerId: "server-a",
      containerId: "ctr_01",
      port: 4096,
      checkedAt: "2026-07-21T00:00:00Z"
    };
    const wrapper = mountShell({
      props: {
        opencodeProcessStatus: stoppedProcess,
        opencodeProcessLoading: false,
        opencodeProcessInitializing: false,
        showProcessStatusInPet: true,
        onboardingActive: true
      }
    });

    await wrapper.get('[data-testid="robot-visibility-toggle"]').trigger("click");
    await wrapper.setProps({ opencodeProcessInitializing: true });
    await wrapper.setProps({ opencodeProcessInitializing: false, opencodeProcessStatus: stoppedProcess });
    await wrapper.setProps({
      opencodeProcessStatus: {
        ...stoppedProcess,
        status: "READY",
        initializable: false,
        serviceStatus: "RUNNING"
      }
    });

    expect(wrapper.find('[data-testid="figma-robot"]').exists()).toBe(false);
  });

  it("defers the initialization panel until the onboarding guide ends", async () => {
    const wrapper = mountShell({
      props: {
        opencodeProcessStatus: {
          status: "NEEDS_INITIALIZATION",
          initializable: true,
          message: "TestAgent 进程不可用，需要重新初始化",
          serviceStatus: "UNASSIGNED"
        },
        opencodeProcessLoading: false,
        showProcessStatusInPet: true,
        onboardingActive: true
      }
    });

    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="robot-process-status"]').exists()).toBe(false);

    await wrapper.setProps({ onboardingActive: false });
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="robot-process-status"]').exists()).toBe(true);
  });

  it("shows server name without inventing an address when service address is missing", async () => {
    const wrapper = mountShell({
      props: {
        currentUserName: "888888888",
        opencodeProcessStatus: {
          status: "UNAVAILABLE",
          initializable: false,
          message: "目标服务器后端不可用",
          linuxServerId: "server-a",
          port: 82,
          serviceStatus: "NOT_RUNNING",
          checkedAt: "2026-07-02T00:00:00Z"
        },
        opencodeProcessLoading: false
      }
    });

    await wrapper.get(".figma-user-avatar-btn").trigger("click");

    expect(wrapper.get(".figma-user-menu-service-text").text()).toBe("未运行(server-a)");
    expect(wrapper.text()).not.toContain("server-a:82");
  });

  it("shows unknown instead of unassigned when process status query has no data", async () => {
    const wrapper = mountShell({
      props: {
        currentUserName: "888888888",
        opencodeProcessStatus: null,
        opencodeProcessLoading: false
      }
    });

    await wrapper.get(".figma-user-avatar-btn").trigger("click");

    expect(wrapper.get(".figma-user-menu-service-text").text()).toBe("状态未知");
    expect(wrapper.text()).not.toContain("待分配专属进程");
  });

  it("can open join app overlay and emit join-app event", async () => {
    const wrapper = mountShell({
      props: {
        currentUserName: "developer",
        apps: [
          { id: "app_coss", name: "F-COSS", description: "已启用" }
        ],
        joinableApps: [
          { appId: "app_gcms", appName: "F-GCMS" }
        ]
      },
      global: {
        stubs: {
          ElSelect: {
            props: ["modelValue"],
            emits: ["update:modelValue"],
            template: `<select :value="modelValue" @change="$emit('update:modelValue', $event.target.value)"><slot /></select>`
          },
          ElOption: {
            props: ["label", "value"],
            template: `<option :value="value">{{ label }}</option>`
          },
          ElButton: {
            emits: ["click"],
            template: `<button type="button" @click="$emit('click')"><slot /></button>`
          }
        }
      }
    });

    // 1. Open the application dropdown
    await wrapper.get(".figma-app-menu-trigger").trigger("click");

    // 2. Expect to see the "+ 加入其他应用" button/row
    const addBtn = wrapper.get(".is-add-app");
    expect(addBtn.text()).toContain("加入其他应用");

    // 3. Click "+ 加入其他应用"
    await addBtn.trigger("mousedown");

    // 4. Expect the overlay to show up
    const overlay = wrapper.get(".figma-add-app-overlay");
    expect(overlay.get(".figma-joined-app-tag").text()).toBe("F-COSS");

    // 5. Select joinable app and click save
    const select = overlay.get("select");
    await select.setValue("app_gcms");
    
    // Save button should trigger submitJoinApp which emits "join-app"
    const saveBtn = overlay.findAll("button").find(btn => btn.text().includes("保存"))!;
    await saveBtn.trigger("click");

    expect(wrapper.emitted("join-app")).toBeTruthy();
    expect(wrapper.emitted("join-app")![0][0]).toBe("app_gcms");
  });

  it("toggles the fixed state on double click and halts behavior cycles", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    window.localStorage.setItem("figma-shell-robot-fixed", "false");
    const wrapper = mountShell();
    await wrapper.vm.$nextTick();
    await summonRobot(wrapper);
    const robot = wrapper.get('[data-testid="figma-robot"]');

    // Double click: click twice rapidly (< 250ms)
    await robot.trigger("click");
    await vi.advanceTimersByTimeAsync(50);
    await robot.trigger("click");
    await wrapper.vm.$nextTick();

    // Verify visual pin indicator is rendered and fixed status saved
    expect(robot.find(".robot-pin-indicator").exists()).toBe(true);
    expect(window.localStorage.getItem("figma-shell-robot-fixed")).toBe("true");

    // Double click again to cancel
    await robot.trigger("click");
    await vi.advanceTimersByTimeAsync(50);
    await robot.trigger("click");
    await wrapper.vm.$nextTick();

    expect(robot.find(".robot-pin-indicator").exists()).toBe(false);
    expect(window.localStorage.getItem("figma-shell-robot-fixed")).toBe("false");
  });

  it("opens side question dialogue on single click after delay, and closes it when clicking elsewhere", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    const wrapper = mountShell();
    await wrapper.vm.$nextTick();
    await summonRobot(wrapper);
    const robot = wrapper.get('[data-testid="figma-robot"]');

    // Single click
    await robot.trigger("click");
    // Verify question is not open immediately
    expect(wrapper.find('[data-testid="robot-side-question"]').exists()).toBe(false);

    // Wait 250ms
    await vi.advanceTimersByTimeAsync(250);
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="robot-side-question"]').exists()).toBe(true);

    // Click elsewhere (the main figma-app wrapper)
    await wrapper.get(".figma-app").trigger("click");
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="robot-side-question"]').exists()).toBe(false);
  });
});
