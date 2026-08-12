import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import PetGoldMinerGame from "../src/components/PetGoldMinerGame.vue";

describe("PetGoldMinerGame", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("swings, catches the centered gold and retracts according to its weight", async () => {
    vi.useFakeTimers();
    vi.spyOn(Math, "random").mockReturnValue(0);
    const wrapper = mount(PetGoldMinerGame, { props: { active: true } });

    expect(wrapper.findAll(".pet-miner-item")).toHaveLength(8);
    expect(wrapper.get('[data-testid="pet-miner-score"]').text()).toBe("¥0");
    expect(wrapper.get('[data-testid="pet-miner-difficulty"]').text()).toContain("Lv1 · 重载矿脉");

    // 初始摆角从 -56° 开始，约 1.04 秒后对准中央的大金块。
    await vi.advanceTimersByTimeAsync(1_040);
    await wrapper.get(".pet-miner-controls .is-primary").trigger("click");
    expect(wrapper.text()).toContain("钩索下探中");

    await vi.advanceTimersByTimeAsync(176);
    expect(wrapper.text()).toContain("回收 大金块");
    expect(wrapper.get(".pet-miner-item.is-grabbed").attributes("aria-label")).toContain("大金块");

    // 重载矿脉同时增加重量和矿价，回收完成后才按本层补偿价结算。
    await vi.advanceTimersByTimeAsync(760);
    expect(wrapper.get('[data-testid="pet-miner-score"]').text()).toBe("¥541");
    expect(wrapper.findAll(".pet-miner-item")).toHaveLength(7);
    expect(wrapper.text()).toContain("大金块 入账 +¥541");
    wrapper.unmount();
  });

  it("uses dynamite on a grabbed heavy item and pauses without consuming time", async () => {
    vi.useFakeTimers();
    vi.spyOn(Math, "random").mockReturnValue(0);
    const wrapper = mount(PetGoldMinerGame, { props: { active: true } });

    await vi.advanceTimersByTimeAsync(1_040);
    await wrapper.get(".pet-miner-controls .is-primary").trigger("click");
    await vi.advanceTimersByTimeAsync(176);
    await wrapper.get(".pet-miner-controls .is-dynamite").trigger("click");

    expect(wrapper.text()).toContain("炸药 × 1");
    expect(wrapper.text()).toContain("炸掉 大金块");
    expect(wrapper.findAll(".pet-miner-item")).toHaveLength(7);
    expect(wrapper.get('[data-testid="pet-miner-score"]').text()).toBe("¥0");

    await vi.advanceTimersByTimeAsync(160);
    const timeBeforePause = wrapper.get(".pet-miner-instrument-row > div:last-child strong").text();
    await wrapper.get('[aria-label="暂停黄金矿工"]').trigger("click");
    expect(wrapper.text()).toContain("勘探暂停");
    await vi.advanceTimersByTimeAsync(2_000);
    expect(wrapper.get(".pet-miner-instrument-row > div:last-child strong").text()).toBe(timeBeforePause);
    wrapper.unmount();
  });

  it("settles the level and advances with a higher target after reaching the quota", async () => {
    vi.useFakeTimers();
    vi.spyOn(Math, "random").mockReturnValue(0);
    const wrapper = mount(PetGoldMinerGame, { props: { active: true } });
    const setup = (wrapper.vm as unknown as {
      $: { setupState: { minerScore: number; minerTimeLeftMs: number } };
    }).$.setupState;

    setup.minerScore = 1_650;
    setup.minerTimeLeftMs = 16;
    await vi.advanceTimersByTimeAsync(16);
    expect(wrapper.get(".pet-miner-overlay").text()).toContain("达标");

    await wrapper.get(".pet-miner-overlay button").trigger("click");
    expect(wrapper.text()).toContain("第 2 层");
    expect(wrapper.text()).toContain("¥2650");
    expect(wrapper.get('[data-testid="pet-miner-difficulty"]').text()).toContain("Lv2 · 急速摆钩");
    expect(wrapper.findAll(".pet-miner-item")).toHaveLength(8);
    wrapper.unmount();
  });
});
