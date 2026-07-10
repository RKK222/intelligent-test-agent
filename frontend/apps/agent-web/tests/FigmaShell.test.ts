import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import FigmaShell from "../src/components/FigmaShell.vue";

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

function dispatchPointer(element: Element, type: string, pointerId: number, clientX: number, clientY: number, pointerType: string) {
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

  it("reuses the pet head, antenna, and eye geometry in the visibility toggle", async () => {
    const wrapper = mountShell();
    await summonRobot(wrapper);
    const petSvg = wrapper.get(".robot-svg");
    const toggleSvg = wrapper.get('[data-testid="robot-visibility-toggle"] svg');

    expect(toggleSvg.attributes("width")).toBe("24");
    expect(toggleSvg.attributes("height")).toBe("28");
    expect(toggleSvg.attributes("viewBox")).toBe("5 0 14 16");

    for (const selector of [
      ".robot-antenna-l",
      ".robot-antenna-l-tip",
      ".robot-antenna-r",
      ".robot-antenna-r-tip",
      ".robot-head",
      ".robot-eye"
    ]) {
      expect(toggleSvg.get(selector).attributes()).toEqual(petSvg.get(selector).attributes());
    }
  });

  it("persists a pointer drag after crossing the movement threshold", async () => {
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 100, y: 100 }));
    const wrapper = mountShell();
    await wrapper.vm.$nextTick();
    await summonRobot(wrapper);
    const robot = wrapper.get('[data-testid="figma-robot"]');

    dispatchPointer(robot.element, "pointerdown", 7, 110, 110, "mouse");
    dispatchPointer(robot.element, "pointermove", 7, 112, 112, "mouse");
    dispatchPointer(robot.element, "pointerup", 7, 112, 112, "mouse");
    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify({ x: 100, y: 100 }));

    dispatchPointer(robot.element, "pointerdown", 8, 110, 110, "touch");
    dispatchPointer(robot.element, "pointermove", 8, 170, 160, "touch");
    dispatchPointer(robot.element, "pointerup", 8, 170, 160, "touch");
    await wrapper.vm.$nextTick();

    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify({ x: 160, y: 150 }));
    expect(robot.attributes("style")).toContain("left: 160px");
    expect(robot.attributes("style")).toContain("top: 150px");
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
    expect(robot.attributes("style")).toContain("left: 288px");
    expect(robot.attributes("style")).toContain("top: 200px");
    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify({ x: 288, y: 200 }));
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
    expect(wrapper.get('[data-testid="figma-robot"]').attributes("style")).toContain("left: 168px");
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
      dispatchPointer(robot.element, "pointermove", 9, 140, 140, "mouse");
      dispatchPointer(robot.element, "pointerup", 9, 140, 140, "mouse");
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
    dispatchPointer(robot.element, "pointermove", 10, 125, 135, "pen");
    dispatchPointer(robot.element, "pointerup", 10, 125, 135, "pen");
    await wrapper.vm.$nextTick();

    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify({ x: 125, y: 135 }));
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

  it("cleans up an interrupted drag after lost pointer capture and window blur", async () => {
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 100, y: 100 }));
    const wrapper = mountShell();
    await wrapper.vm.$nextTick();
    await summonRobot(wrapper);
    const robot = wrapper.get('[data-testid="figma-robot"]');

    dispatchPointer(robot.element, "pointerdown", 11, 100, 100, "mouse");
    dispatchPointer(robot.element, "pointermove", 11, 140, 130, "mouse");
    dispatchPointer(robot.element, "lostpointercapture", 11, 140, 130, "mouse");
    await wrapper.vm.$nextTick();

    expect(document.body.style.cursor).toBe("");
    expect(document.body.style.userSelect).toBe("");
    expect(window.localStorage.getItem("figma-shell-robot-pos")).toBe(JSON.stringify({ x: 140, y: 130 }));

    dispatchPointer(robot.element, "pointerdown", 12, 140, 130, "touch");
    dispatchPointer(robot.element, "pointermove", 12, 160, 160, "touch");
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
    window.localStorage.setItem("figma-shell-robot-pos", JSON.stringify({ x: 100, y: 100 }));
    const wrapper = mountShell();
    await summonRobot(wrapper);
    const robot = wrapper.get('[data-testid="figma-robot"]');
    const robotElement = robot.element as HTMLElement;
    const initialX = Number.parseInt(robotElement.style.left, 10);
    const initialY = Number.parseInt(robotElement.style.top, 10);
    dispatchPointer(robotElement, "pointerdown", 13, initialX, initialY, "mouse");
    dispatchPointer(robotElement, "pointermove", 13, initialX + 20, initialY + 30, "mouse");
    dispatchPointer(robotElement, "pointerup", 13, initialX + 20, initialY + 30, "mouse");
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

  it("can enter the added shake and celebration actions", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
    const random = vi.spyOn(Math, "random").mockReturnValue(0.8);
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

  it("toggles the pet immediately and restores a saved manual position after a full idle minute", async () => {
    vi.useFakeTimers();
    vi.spyOn(document, "hasFocus").mockReturnValue(true);
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

  it("restarts the hidden pet timer after activity and only counts while focused and visible", async () => {
    vi.useFakeTimers();
    const hasFocus = vi.spyOn(document, "hasFocus").mockReturnValue(true);
    const hidden = vi.spyOn(document, "hidden", "get").mockReturnValue(false);
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

  it("shows runtime inventory before the application switch and opens details", async () => {
    const wrapper = mountShell({
      props: {
        currentUserName: "developer",
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
          plugins: [],
          mcpTools: [{ id: "read-file", name: "read_file", status: "mcp" }],
          mcpResources: [{ id: "repo", name: "Repository", status: "git" }]
        }
      } as any
    });

    const headerRight = wrapper.get(".figma-header-right");
    const summary = wrapper.get('[data-testid="runtime-inventory-summary"]');
    const appSwitch = wrapper.get(".figma-app-menu-wrapper");
    expect(headerRight.element.compareDocumentPosition(summary.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(summary.element.compareDocumentPosition(appSwitch.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(summary.text()).toContain("Agent 2");
    expect(summary.text()).toContain("Skill 1");
    expect(summary.text()).toContain("MCP 2");
    expect(summary.text()).toContain("Plugin 0");

    await summary.trigger("click");

    expect(wrapper.find('[data-testid="runtime-inventory-panel"]').exists()).toBe(true);
    expect(wrapper.text()).toContain("Build");
    expect(wrapper.text()).toContain("test-skill");
    expect(wrapper.text()).toContain("filesystem");
    expect(wrapper.text()).toContain("read_file");
    expect(wrapper.text()).toContain("Repository");
    expect(wrapper.text()).toContain("当前运行态未提供独立 Plugin 目录");
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
});
