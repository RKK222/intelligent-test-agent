import { fireEvent, render, screen } from "@testing-library/vue";
import DiffReviewPanel from "@/components/DiffReviewPanel.vue";

const files = [
  {
    path: "src/App.vue",
    status: "modified",
    additions: 3,
    deletions: 1,
    patch: [
      "@@ -1,3 +1,4 @@",
      " import { ref } from 'vue'",
      "-const title = 'old'",
      "+const title = 'new'",
      "+const mode = ref('review')",
      "@@ -20,2 +21,3 @@",
      " function save() {",
      "+  return mode.value",
      " }"
    ].join("\n")
  },
  {
    path: "tests/App.test.ts",
    status: "added",
    additions: 4,
    deletions: 0,
    patch: ["@@ -0,0 +1,4 @@", "+import { test } from 'vitest'", "+test('renders', () => {})"].join("\n")
  }
];

describe("DiffReviewPanel", () => {
  it("renders file summary, switches focused file, toggles diff style, and navigates hunks", async () => {
    render(DiffReviewPanel, { props: { files } });

    expect(screen.getByRole("region", { name: "Diff review" })).toBeInTheDocument();
    expect(screen.getByText("2 files")).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Diff review" })).toHaveTextContent("+7 -1");
    expect(screen.getByRole("region", { name: "Diff review" })).toHaveTextContent("3 hunks");
    expect(screen.getByRole("button", { name: "src/App.vue modified +3 -1" })).toHaveAttribute("aria-current", "true");
    expect(screen.getByText("Hunk 1 / 2")).toBeInTheDocument();
    expect(screen.getByText("+const title = 'new'")).toBeInTheDocument();

    await fireEvent.click(screen.getByRole("button", { name: "Next hunk" }));
    expect(screen.getByText("Hunk 2 / 2")).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Diff review" })).toHaveTextContent("+ return mode.value");

    await fireEvent.click(screen.getByRole("button", { name: "Split" }));
    expect(screen.getByRole("button", { name: "Split" })).toHaveAttribute("aria-pressed", "true");

    await fireEvent.click(screen.getByRole("button", { name: "tests/App.test.ts added +4 -0" }));
    expect(screen.getByRole("button", { name: "tests/App.test.ts added +4 -0" })).toHaveAttribute("aria-current", "true");
    expect(screen.getByText("Hunk 1 / 1")).toBeInTheDocument();
  });

  it("renders an actionable empty state", () => {
    render(DiffReviewPanel, { props: { files: [] } });

    expect(screen.getByRole("region", { name: "Diff review" })).toBeInTheDocument();
    expect(screen.getByText("No proposed diff for this session.")).toBeInTheDocument();
  });
});
