import { mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import PetMiniGames from "../src/components/PetMiniGames.vue";

describe("PetMiniGames", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("opens a playable tetris board with keyboard and button controls", async () => {
    vi.useFakeTimers();
    vi.spyOn(Math, "random").mockReturnValue(0);
    const wrapper = mount(PetMiniGames);

    expect(wrapper.text()).toContain("俄罗斯方块");
    expect(wrapper.text()).toContain("扫雷");
    await wrapper.get('[data-testid="pet-game-open-tetris"]').trigger("click");

    expect(wrapper.find('[data-testid="pet-tetris"]').exists()).toBe(true);
    expect(wrapper.findAll('.pet-tetris-cell')).toHaveLength(160);
    expect(wrapper.findAll('.pet-tetris-cell[class*="is-"]').length).toBeGreaterThan(0);

    await wrapper.get('[aria-label="直接落下"]').trigger("click");
    expect(wrapper.text()).toContain("分数");
    await wrapper.trigger("keydown", { key: "p" });
    expect(wrapper.text()).toContain("已暂停");
    await wrapper.get(".pet-tetris-controls button:last-child").trigger("click");
    expect(wrapper.text()).toContain("下落中");

    wrapper.unmount();
  });

  it("keeps the first minesweeper reveal safe and supports flags and restart", async () => {
    vi.spyOn(Math, "random").mockReturnValue(0);
    const wrapper = mount(PetMiniGames);
    await wrapper.get('[data-testid="pet-game-open-minesweeper"]').trigger("click");

    const cells = wrapper.findAll('.pet-mine-cell');
    expect(cells).toHaveLength(64);
    await cells[63]!.trigger("contextmenu");
    expect(cells[63]!.attributes("aria-label")).toContain("已插旗");

    await cells[0]!.trigger("click");
    expect(wrapper.text()).not.toContain("踩雷了");
    expect(cells[0]!.attributes("aria-label")).not.toContain("地雷");

    await wrapper.get('[aria-label="重开扫雷"]').trigger("click");
    expect(wrapper.text()).toContain("第一步安全");
    expect(wrapper.findAll('.pet-mine-cell.is-revealed')).toHaveLength(0);
  });

  it("emits close from the game header", async () => {
    const wrapper = mount(PetMiniGames);
    await wrapper.get('[aria-label="关闭小宠物游戏"]').trigger("click");
    expect(wrapper.emitted("close")).toEqual([[]]);
  });
});
