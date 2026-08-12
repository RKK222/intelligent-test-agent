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
    expect(wrapper.text()).toContain("数独");
    expect(wrapper.text()).toContain("贪吃蛇");
    expect(wrapper.text()).toContain("桌面弹球");
    await wrapper.get('[data-testid="pet-game-open-tetris"]').trigger("click");

    expect(wrapper.find('[data-testid="pet-tetris"]').exists()).toBe(true);
    expect(wrapper.findAll('.pet-tetris-cell')).toHaveLength(160);
    expect(wrapper.findAll('.pet-tetris-cell[class*="is-"]').length).toBeGreaterThan(0);
    expect(wrapper.get('[data-testid="pet-tetris-next"]').text()).toContain("下一个");
    expect(wrapper.findAll('.pet-tetris-preview-cell[class*="is-"]').length).toBeGreaterThan(0);
    expect(wrapper.get('[data-testid="pet-tetris-level"]').text()).toContain("等级 1");

    await wrapper.get('[aria-label="直接落下"]').trigger("click");
    expect(wrapper.text()).toContain("分数");
    await wrapper.trigger("keydown", { key: "p" });
    expect(wrapper.text()).toContain("已暂停");
    await wrapper.get(".pet-tetris-controls button:last-child").trigger("click");
    expect(wrapper.text()).toContain("下落中");

    wrapper.unmount();
  });

  it("keeps the first minesweeper reveal safe and chords around a matched flag", async () => {
    vi.spyOn(Math, "random").mockReturnValue(0);
    const wrapper = mount(PetMiniGames);
    await wrapper.get('[data-testid="pet-game-open-minesweeper"]').trigger("click");

    const cells = wrapper.findAll('.pet-mine-cell');
    expect(cells).toHaveLength(64);
    await cells[0]!.trigger("click");
    expect(wrapper.text()).not.toContain("踩雷了");
    expect(cells[0]!.attributes("aria-label")).not.toContain("地雷");

    const revealedBeforeChord = wrapper.findAll('.pet-mine-cell.is-revealed').length;
    await cells[1]!.trigger("dblclick");
    expect(wrapper.findAll('.pet-mine-cell.is-revealed')).toHaveLength(revealedBeforeChord);

    await cells[10]!.trigger("contextmenu");
    expect(cells[10]!.attributes("aria-label")).toContain("已插旗");
    await cells[1]!.trigger("dblclick");
    expect(cells[2]!.classes()).toContain("is-revealed");
    expect(wrapper.findAll('.pet-mine-cell.is-revealed').length).toBeGreaterThan(revealedBeforeChord);

    await wrapper.get('[aria-label="重开扫雷"]').trigger("click");
    expect(wrapper.text()).toContain("第一步安全");
    expect(wrapper.findAll('.pet-mine-cell.is-revealed')).toHaveLength(0);
  });

  it("randomizes minesweeper and sudoku difficulty for each new round", async () => {
    vi.spyOn(Math, "random")
      .mockReturnValueOnce(0)
      .mockReturnValueOnce(0)
      .mockReturnValueOnce(0.99)
      .mockReturnValueOnce(0.99);
    const wrapper = mount(PetMiniGames);

    await wrapper.get('[data-testid="pet-game-open-minesweeper"]').trigger("click");
    expect(wrapper.text()).toContain("难度 1");
    await wrapper.get('[aria-label="重开扫雷"]').trigger("click");
    expect(wrapper.text()).toContain("难度 5");

    await wrapper.findAll('.pet-game-tabs button')[2]!.trigger("click");
    expect(wrapper.text()).toContain("难度 1");
    await wrapper.get('[aria-label="重开数独"]').trigger("click");
    expect(wrapper.text()).toContain("难度 5");
  });

  it("supports sudoku selection, keyboard input, error checking and restart", async () => {
    const wrapper = mount(PetMiniGames);
    await wrapper.get('[data-testid="pet-game-open-sudoku"]').trigger("click");

    const cells = wrapper.findAll('.pet-sudoku-cell');
    expect(cells).toHaveLength(81);
    expect(cells.filter((cell) => cell.attributes("disabled") !== undefined).length).toBeGreaterThan(0);

    await cells[2]!.trigger("click");
    await wrapper.trigger("keydown", { key: "4" });
    expect(wrapper.findAll('.pet-sudoku-cell')[2]!.text()).toBe("4");
    expect(wrapper.findAll('.pet-sudoku-cell')[2]!.classes()).not.toContain("is-error");

    await wrapper.get('[aria-label="填写数字 5"]').trigger("click");
    expect(wrapper.findAll('.pet-sudoku-cell')[2]!.classes()).toContain("is-error");
    expect(wrapper.text()).toContain("需要检查");

    await wrapper.get('[aria-label="重开数独"]').trigger("click");
    expect(wrapper.findAll('.pet-sudoku-cell')[2]!.text()).toBe("");
    expect(wrapper.text()).toContain("选一格开始填写");
    expect(wrapper.text()).toMatch(/难度 [1-5]/);
  });

  it("runs and pauses snake with keyboard and compact controls", async () => {
    vi.useFakeTimers();
    vi.spyOn(Math, "random").mockReturnValue(0);
    const wrapper = mount(PetMiniGames);
    await wrapper.get('[data-testid="pet-game-open-snake"]').trigger("click");

    expect(wrapper.findAll('.pet-snake-cell')).toHaveLength(144);
    expect(wrapper.findAll('.pet-snake-cell.is-head')).toHaveLength(1);
    expect(wrapper.findAll('.pet-snake-cell.is-body')).toHaveLength(2);
    expect(wrapper.findAll('.pet-snake-cell.is-food')).toHaveLength(1);
    expect(wrapper.text()).toContain("等级 1");

    await wrapper.trigger("keydown", { key: "ArrowUp" });
    await vi.advanceTimersByTimeAsync(200);
    expect(wrapper.text()).toContain("得分 0");
    await wrapper.get(".pet-snake-controls button:last-child").trigger("click");
    expect(wrapper.text()).toContain("已暂停");

    wrapper.unmount();
  });

  it("runs the desktop pinball gravity, mission, ball-save, flipper, pause and tilt rules", async () => {
    vi.useFakeTimers();
    const wrapper = mount(PetMiniGames);
    await wrapper.get('[data-testid="pet-game-open-pinball"]').trigger("click");

    expect(wrapper.find('[data-testid="pet-pinball-board"]').exists()).toBe(true);
    expect(wrapper.findAll('.pet-pinball-bumper')).toHaveLength(3);
    expect(wrapper.findAll('.pet-pinball-rollovers span')).toHaveLength(3);
    expect(wrapper.findAll('.pet-pinball-drop-target')).toHaveLength(3);
    expect(wrapper.findAll('.pet-pinball-sling')).toHaveLength(2);
    expect(wrapper.find('.pet-pinball-spinner').exists()).toBe(true);
    expect(wrapper.find('.pet-pinball-scoop').exists()).toBe(true);
    expect(wrapper.findAll('.pet-pinball-lives > span:not(.is-spent)')).toHaveLength(3);
    expect(wrapper.text()).toContain("按住发射杆蓄力");
    expect(wrapper.text()).toContain("任务 1 · 点亮 A / B / C 翻滚灯");

    const ballBeforeLaunch = wrapper.get('.pet-pinball-ball').attributes("style");
    const launcher = wrapper.get('[aria-label="按住蓄力，松开发射弹珠"]');
    await launcher.trigger("pointerdown", { pointerId: 1 });
    await vi.advanceTimersByTimeAsync(400);
    expect(launcher.text()).toMatch(/\d+%/);
    await launcher.trigger("pointerup", { pointerId: 1 });
    expect(wrapper.text()).toContain("技能发射判定中");
    expect(wrapper.text()).toContain("救球9s");

    await vi.advanceTimersByTimeAsync(64);
    expect(wrapper.get('.pet-pinball-ball').attributes("style")).not.toBe(ballBeforeLaunch);
    await vi.advanceTimersByTimeAsync(1000);
    expect(wrapper.get('[data-testid="pet-pinball-score"]').text()).not.toContain("00000");
    expect(wrapper.findAll('.pet-pinball-feature-strip strong')[0]!.text()).toBe("×2");

    const pinballSetup = (wrapper.vm as unknown as {
      $: { setupState: {
        pinballRolloverLights: boolean[];
        pinballDropTargets: boolean[];
        pinballScore: number;
        pinballBalls: Array<{ x: number; y: number; previousX: number; previousY: number; vx: number; vy: number }>;
      } };
    }).$.setupState;
    Object.assign(pinballSetup.pinballBalls[0]!, { x: 160, y: 190, previousX: 160, previousY: 190, vx: 0, vy: 0 });
    const gravityStartY = pinballSetup.pinballBalls[0]!.y;
    await vi.advanceTimersByTimeAsync(160);
    const gravityMidY = pinballSetup.pinballBalls[0]!.y;
    await vi.advanceTimersByTimeAsync(160);
    const gravityEndY = pinballSetup.pinballBalls[0]!.y;
    expect(gravityMidY - gravityStartY).toBeGreaterThan(5);
    expect(gravityEndY - gravityMidY).toBeGreaterThan(gravityMidY - gravityStartY);
    Object.assign(pinballSetup.pinballBalls[0]!, { y: 430, previousY: 420, vy: 300 });
    await vi.advanceTimersByTimeAsync(16);
    expect(wrapper.text()).toContain("BALL SAVE · 自动补球");
    expect(pinballSetup.pinballBalls[0]!.vy).toBeLessThan(-700);

    pinballSetup.pinballRolloverLights = [true, true, true];
    pinballSetup.pinballDropTargets = [true, true, true];
    Object.assign(pinballSetup.pinballBalls[0]!, { x: 244, y: 218, previousX: 244, previousY: 214, vx: 0, vy: 100 });
    await wrapper.vm.$nextTick();
    await vi.advanceTimersByTimeAsync(32);
    expect(wrapper.findAll('.pet-pinball-ball')).toHaveLength(3);
    expect(wrapper.text()).toContain("多球模式 · 场上 3 球");
    expect(pinballSetup.pinballBalls.every((ball) => ball.vy < -500)).toBe(true);

    pinballSetup.pinballScore = 14_999;
    Object.assign(pinballSetup.pinballBalls[0]!, { x: 90, y: 150, previousX: 90, previousY: 145, vx: 0, vy: 100 });
    await vi.advanceTimersByTimeAsync(16);
    expect(wrapper.findAll('.pet-pinball-lives > span:not(.is-spent)')).toHaveLength(4);

    await wrapper.trigger("keydown", { key: "ArrowLeft" });
    expect(wrapper.get('.pet-pinball-flipper.is-left').classes()).toContain("is-pressed");
    await wrapper.trigger("keyup", { key: "ArrowLeft" });
    expect(wrapper.get('.pet-pinball-flipper.is-left').classes()).not.toContain("is-pressed");

    await wrapper.get('.pet-pinball-controls .is-pause').trigger("click");
    expect(wrapper.text()).toContain("已暂停");
    await wrapper.get('.pet-pinball-controls .is-pause').trigger("click");
    await wrapper.get('[aria-label="向左晃台"]').trigger("click");
    await wrapper.get('[aria-label="向右晃台"]').trigger("click");
    await wrapper.get('[aria-label="向左晃台"]').trigger("click");
    expect(wrapper.text()).toContain("TILT · 挡板失效");
    expect(wrapper.find('.pet-pinball-overlay').text()).toContain("本球奖励分已锁定");
    wrapper.unmount();
  });

  it("emits close from the game header", async () => {
    const wrapper = mount(PetMiniGames);
    await wrapper.get('[aria-label="关闭小宠物游戏"]').trigger("click");
    expect(wrapper.emitted("close")).toEqual([[]]);
  });
});
