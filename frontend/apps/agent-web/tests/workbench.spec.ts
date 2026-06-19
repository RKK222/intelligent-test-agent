import { expect, test } from "@playwright/test";

test("workbench opens a workspace file with mocked backend api", async ({ page }) => {
  await page.route("**/api/workspaces**", async (route) => {
    const url = new URL(route.request().url());
    if (route.request().method() === "GET" && url.pathname === "/api/workspaces") {
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          success: true,
          traceId: "trace_e2e",
          data: {
            items: [
              {
                workspaceId: "wrk_1234567890abcdef",
                name: "demo-tests",
                rootPath: "/tmp/demo",
                status: "ACTIVE",
                createdAt: "2026-06-19T00:00:00Z",
                updatedAt: "2026-06-19T00:00:00Z"
              }
            ],
            page: 0,
            size: 50,
            total: 1
          }
        })
      });
      return;
    }
    if (route.request().method() === "GET" && url.pathname.endsWith("/files")) {
      const path = url.searchParams.get("path") ?? "";
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          success: true,
          traceId: "trace_e2e",
          data:
            path === "tests"
              ? [
                  {
                    path: "tests/checkout.spec.ts",
                    name: "checkout.spec.ts",
                    directory: false,
                    size: 120,
                    lastModifiedAt: "2026-06-19T00:00:00Z"
                  }
                ]
              : [
                  {
                    path: "tests",
                    name: "tests",
                    directory: true,
                    size: 0,
                    lastModifiedAt: "2026-06-19T00:00:00Z"
                  },
                  {
                    path: "package.json",
                    name: "package.json",
                    directory: false,
                    size: 80,
                    lastModifiedAt: "2026-06-19T00:00:00Z"
                  }
                ]
        })
      });
      return;
    }
    if (route.request().method() === "GET" && url.pathname.endsWith("/files/content")) {
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          success: true,
          traceId: "trace_e2e",
          data: {
            path: "tests/checkout.spec.ts",
            content: "import { test } from '@playwright/test';\n\ntest('checkout', async () => {});\n",
            encoding: "utf-8",
            size: 80,
            readonly: false
          }
        })
      });
      return;
    }
    if (route.request().method() === "PUT" && url.pathname.endsWith("/files/content")) {
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({ success: true, traceId: "trace_e2e", data: null })
      });
      return;
    }
    await route.fallback();
  });

  await page.goto("/");

  await expect(page.getByText("TestAgent IDE")).toBeVisible();
  await expect(page.getByRole("banner").getByText("demo-tests")).toBeVisible();
  await page.getByRole("button", { name: /tests/ }).click();
  await page.getByRole("button", { name: /checkout.spec.ts/ }).click();
  await expect(page.getByText("tests/checkout.spec.ts")).toBeVisible();
  await expect(page.getByRole("button", { name: /保存/ })).toBeVisible();
});
