import { fireEvent, render, screen, waitFor } from "@testing-library/vue";
import { createPinia, setActivePinia } from "pinia";
import SidePanel from "@/components/SidePanel.vue";
import { usePlatformStore } from "@/stores/platform";
import { useWorkspaceStore } from "@/stores/workspace";

describe("SidePanel", () => {
  it("exposes opencode panel tabs with selected state", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const platform = usePlatformStore();
    const workspace = useWorkspaceStore();
    workspace.selectedWorkspaceId = "wrk_1";

    Object.defineProperty(platform, "api", {
      value: {
        listRuntimeFiles: async () => ({ data: [] }),
        findRuntimeFiles: async () => ({ data: [] })
      }
    });

    render(SidePanel, { global: { plugins: [pinia] } });

    const review = screen.getByRole("tab", { name: "Review" });
    const files = screen.getByRole("tab", { name: "Files" });
    expect(review).toHaveAttribute("aria-selected", "true");
    expect(files).toHaveAttribute("aria-selected", "false");

    await fireEvent.click(files);

    await waitFor(() => expect(files).toHaveAttribute("aria-selected", "true"));
    expect(review).toHaveAttribute("aria-selected", "false");
    expect(screen.getByRole("tabpanel", { name: "Files" })).toBeInTheDocument();
  });
});
