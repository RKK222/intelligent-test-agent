import { fireEvent, render, screen, waitFor } from "@testing-library/vue";
import { createPinia, setActivePinia } from "pinia";
import FileTreePanel from "@/components/FileTreePanel.vue";
import { usePlatformStore } from "@/stores/platform";
import { useWorkspaceStore } from "@/stores/workspace";

describe("FileTreePanel", () => {
  it("loads runtime files, opens directories, and searches through platform fs APIs", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const platform = usePlatformStore();
    const workspace = useWorkspaceStore();
    const calls: Array<[string, string | undefined, string]> = [];
    workspace.selectedWorkspaceId = "wrk_1";

    Object.defineProperty(platform, "api", {
      value: {
        listRuntimeFiles: async (workspaceId?: string, path = ".") => {
          calls.push(["list", workspaceId, path]);
          if (path === "src") {
            return { data: [{ path: "src/App.vue", name: "App.vue", directory: false, size: 1200 }] };
          }
          return {
            data: [
              { path: "src", name: "src", directory: true },
              { path: "README.md", name: "README.md", directory: false, size: 512 }
            ]
          };
        },
        findRuntimeFiles: async (workspaceId?: string, query = "") => {
          calls.push(["find", workspaceId, query]);
          return { items: [{ path: "README.md", name: "README.md", directory: false, size: 512 }] };
        }
      }
    });

    render(FileTreePanel, { global: { plugins: [pinia] } });

    expect(await screen.findByRole("button", { name: "Open src directory" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Select README.md file" })).toBeInTheDocument();

    await fireEvent.click(screen.getByRole("button", { name: "Open src directory" }));
    expect(await screen.findByRole("button", { name: "Select src/App.vue file" })).toBeInTheDocument();

    await fireEvent.update(screen.getByLabelText("Search workspace tree"), "read");
    expect(await screen.findByRole("button", { name: "Select README.md file" })).toBeInTheDocument();
    expect(calls).toEqual([
      ["list", "wrk_1", "."],
      ["list", "wrk_1", "src"],
      ["find", "wrk_1", "read"]
    ]);
  });

  it("renders empty and error states for runtime files", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const platform = usePlatformStore();
    const workspace = useWorkspaceStore();
    workspace.selectedWorkspaceId = "wrk_1";
    let fail = false;

    Object.defineProperty(platform, "api", {
      value: {
        listRuntimeFiles: async () => {
          if (fail) {
            throw new Error("runtime unavailable");
          }
          return { data: [] };
        },
        findRuntimeFiles: async () => ({ data: [] })
      }
    });

    const view = render(FileTreePanel, { global: { plugins: [pinia] } });

    await waitFor(() => expect(document.body).toHaveTextContent("No files found"));

    fail = true;
    view.unmount();
    render(FileTreePanel, { global: { plugins: [pinia] } });

    await waitFor(() => expect(document.body).toHaveTextContent("runtime unavailable"));
  });
});
