import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.route("**/api/workspaces**", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        success: true,
        traceId: "trace_e2e",
        data: {
          items: [
            {
              workspaceId: "wrk_1",
              name: "opencode",
              rootPath: "/repo",
              status: "ACTIVE",
              createdAt: "2026-06-20T00:00:00Z",
              updatedAt: "2026-06-20T00:00:00Z"
            }
          ],
          page: 1,
          size: 20,
          total: 1
        }
      })
    })
  );
  await page.route("**/api/sessions**", (route) =>
    route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({ success: true, traceId: "trace_e2e", data: { items: [], page: 1, size: 20, total: 0 } })
    })
  );
  await page.route("**/api/agents**", (route) =>
    route.fulfill({ contentType: "application/json", body: JSON.stringify({ success: true, traceId: "trace_e2e", data: [] }) })
  );
  await page.route("**/api/models**", (route) =>
    route.fulfill({ contentType: "application/json", body: JSON.stringify({ success: true, traceId: "trace_e2e", data: [] }) })
  );
  await page.route("**/api/providers**", (route) =>
    route.fulfill({ contentType: "application/json", body: JSON.stringify({ success: true, traceId: "trace_e2e", data: [] }) })
  );
});

test("renders desktop opencode workspace shell", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("banner")).toContainText("opencode");
  await expect(page.getByRole("button", { name: /new session/i })).toBeVisible();
  await expect(page.getByLabel("Sessions").getByText("/repo")).toBeVisible();
});
