import { render, waitFor } from "@testing-library/vue";
import { describe, expect, it, vi } from "vitest";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser } from "@test-agent/shared-types";
import SettingsPersonalPanel from "../src/components/settings/SettingsPersonalPanel.vue";

const user: CurrentUser = {
  userId: "usr_user",
  username: "user",
  unifiedAuthId: "AUTH_USER",
  roles: ["USER"]
};

function renderPanel() {
  const api = {
    ...({} as BackendApiClient),
    listPersonalSshKeys: vi.fn().mockResolvedValue([])
  };
  return render(SettingsPersonalPanel, {
    props: { currentUser: user, pageActive: true, localClientVisible: false },
    global: { provide: { api } }
  });
}

describe("SettingsPersonalPanel SSH private-key file selection", () => {
  it("reads an extensionless private key and explains how to reveal .ssh on Kylin", async () => {
    const view = renderPanel();
    const fileInput = await waitFor(() =>
      view.getByTestId("ssh-private-key-file-input") as HTMLInputElement
    );

    expect(fileInput.hasAttribute("accept")).toBe(false);
    expect(view.getByText("Ctrl", { selector: "kbd" })).toBeTruthy();
    expect(view.getByText("H", { selector: "kbd" })).toBeTruthy();
    expect(view.getByText(".ssh", { selector: "code" })).toBeTruthy();

    const privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\r\nexample\r\n-----END OPENSSH PRIVATE KEY-----\r\n";
    const keyFile = new File([privateKey], "id_ed25519", { type: "application/octet-stream" });
    Object.defineProperty(fileInput, "files", { configurable: true, value: [keyFile] });
    fileInput.dispatchEvent(new Event("change", { bubbles: true }));

    await waitFor(() => expect(view.getByDisplayValue("id_ed25519")).toBeTruthy());
    const privateKeyTextarea = view.getByPlaceholderText("-----BEGIN OPENSSH PRIVATE KEY-----") as HTMLTextAreaElement;
    expect(privateKeyTextarea.value).toBe(
      "-----BEGIN OPENSSH PRIVATE KEY-----\nexample\n-----END OPENSSH PRIVATE KEY-----\n"
    );
    expect(view.getByText("已选择：id_ed25519")).toBeTruthy();
  });
});
