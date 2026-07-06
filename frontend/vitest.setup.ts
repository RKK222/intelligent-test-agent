import "@testing-library/jest-dom/vitest";

// 屏蔽 Monaco Editor 内部由取消操作导致的未捕获 Promise Rejection
if (typeof window !== "undefined") {
  window.addEventListener("unhandledrejection", (event) => {
    if (
      event.reason &&
      (event.reason === "Canceled" ||
        event.reason.name === "Canceled" ||
        event.reason.message === "Canceled")
    ) {
      event.preventDefault();
    }
  });
}
