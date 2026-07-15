import { describe, expect, it } from "vitest";
import { render } from "@testing-library/vue";
import ShimmerDivider from "../src/ShimmerDivider.vue";

describe("ShimmerDivider Component", () => {
  it("使用默认参数渲染时应该表现正确", () => {
    const { container } = render(ShimmerDivider);

    // 检查最外层容器是否渲染，且默认高度为 1px
    const divider = container.firstElementChild as HTMLElement;
    expect(divider).toBeTruthy();
    expect(divider.dataset.orientation).toBe("horizontal");
    expect(divider.style.width).toBe("100%");
    expect(divider.style.height).toBe("1px");

    // 检查内部流光轨迹是否应用了淡出效果和默认 2s 的持续时间
    const shimmerTrack = container.querySelector(".ta-shimmer-track") as HTMLElement;
    expect(shimmerTrack).toBeTruthy();
    expect(shimmerTrack.classList.contains("ta-fade-mask")).toBe(true);
    expect(shimmerTrack.classList.contains("ta-shimmer-track--static")).toBe(false);
    expect(shimmerTrack.style.getPropertyValue("--ta-shimmer-duration")).toBe("2s");
  });

  it("支持纵向布局并把 height 作为线宽", () => {
    const { container } = render(ShimmerDivider, {
      props: { orientation: "vertical", height: 2 }
    });

    const divider = container.firstElementChild as HTMLElement;
    const shimmerTrack = container.querySelector(".ta-shimmer-track") as HTMLElement;
    expect(divider.dataset.orientation).toBe("vertical");
    expect(divider.style.width).toBe("2px");
    expect(divider.style.height).toBe("100%");
    expect(shimmerTrack.classList.contains("ta-shimmer-track--vertical")).toBe(true);
  });

  it("支持保留渐变但停止动画", () => {
    const { container } = render(ShimmerDivider, {
      props: { animated: false }
    });

    const shimmerTrack = container.querySelector(".ta-shimmer-track") as HTMLElement;
    expect(shimmerTrack.classList.contains("ta-shimmer-track--static")).toBe(true);
  });

  it("当提供不同的 speed 属性时应该正确计算动画持续时间", () => {
    // 1. 测试 speed="fast"
    const { container: containerFast } = render(ShimmerDivider, {
      props: { speed: "fast" }
    });
    const trackFast = containerFast.querySelector(".ta-shimmer-track") as HTMLElement;
    expect(trackFast.style.getPropertyValue("--ta-shimmer-duration")).toBe("1s");

    // 2. 测试 speed="slow"
    const { container: containerSlow } = render(ShimmerDivider, {
      props: { speed: "slow" }
    });
    const trackSlow = containerSlow.querySelector(".ta-shimmer-track") as HTMLElement;
    expect(trackSlow.style.getPropertyValue("--ta-shimmer-duration")).toBe("4s");

    // 3. 测试 speed="normal"
    const { container: containerNormal } = render(ShimmerDivider, {
      props: { speed: "normal" }
    });
    const trackNormal = containerNormal.querySelector(".ta-shimmer-track") as HTMLElement;
    expect(trackNormal.style.getPropertyValue("--ta-shimmer-duration")).toBe("2s");

    // 4. 测试自定义数值 speed=1.5 (秒)
    const { container: containerCustom } = render(ShimmerDivider, {
      props: { speed: 1.5 }
    });
    const trackCustom = containerCustom.querySelector(".ta-shimmer-track") as HTMLElement;
    expect(trackCustom.style.getPropertyValue("--ta-shimmer-duration")).toBe("1.5s");
  });

  it("当提供不同的 height 属性时应该正确应用高度样式", () => {
    // 1. 传入纯数字作为像素高度 (2)
    const { container: containerNum } = render(ShimmerDivider, {
      props: { height: 2 }
    });
    const dividerNum = containerNum.firstElementChild as HTMLElement;
    expect(dividerNum.style.height).toBe("2px");

    // 2. 传入带单位的高度字符串 ("3rem")
    const { container: containerStr } = render(ShimmerDivider, {
      props: { height: "3rem" }
    });
    const dividerStr = containerStr.firstElementChild as HTMLElement;
    expect(dividerStr.style.height).toBe("3rem");
  });

  it("当 fade 属性为 false 时应该不应用淡出 mask", () => {
    const { container } = render(ShimmerDivider, {
      props: { fade: false }
    });
    const shimmerTrack = container.querySelector(".ta-shimmer-track") as HTMLElement;
    expect(shimmerTrack.classList.contains("ta-fade-mask")).toBe(false);
  });
});
