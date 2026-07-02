import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import FigmaShell from "../src/components/FigmaShell.vue";

describe("FigmaShell", () => {
  it("shows process status with server name and resolved address", async () => {
    const wrapper = mount(FigmaShell, {
      props: {
        currentUserName: "888888888",
        opencodeProcessStatus: {
          status: "NEEDS_INITIALIZATION",
          initializable: true,
          message: "opencode 进程不可用，需要重新初始化",
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
    const wrapper = mount(FigmaShell, {
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
    const wrapper = mount(FigmaShell, {
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
    const wrapper = mount(FigmaShell, {
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
