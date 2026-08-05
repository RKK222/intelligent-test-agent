import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import { nextTick } from "vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser, SupportAccessIncidentSuggestion } from "@test-agent/shared-types";
import SupportAccessPanel from "../src/components/system/SupportAccessPanel.vue";

const currentUser: CurrentUser = {
  userId: "usr_admin",
  username: "admin",
  unifiedAuthId: "AUTH_1",
  roles: ["SUPER_ADMIN"]
};

function backendApi(
  suggestion: Promise<SupportAccessIncidentSuggestion>
): BackendApiClient {
  return {
    getRecentSupportAccessIncident: vi.fn().mockReturnValue(suggestion),
    listUsers: vi.fn().mockResolvedValue({ items: [], page: 1, size: 100, total: 0 })
  } as Partial<BackendApiClient> as BackendApiClient;
}

function renderPanel(api: BackendApiClient) {
  return render(SupportAccessPanel, {
    props: { currentUser },
    global: { provide: { api } }
  });
}

describe("support access panel incident prefill", () => {
  it("prefills the current admin recent persisted incident", async () => {
    const api = backendApi(Promise.resolve({ incidentId: "  INC-PERSISTED  " }));
    const view = renderPanel(api);

    const input = view.getByLabelText("工单号") as HTMLInputElement;
    await waitFor(() => expect(input.value).toBe("INC-PERSISTED"));
    expect(api.getRecentSupportAccessIncident).toHaveBeenCalledTimes(1);
  });

  it("does not overwrite manual input while the suggestion request is pending", async () => {
    let resolveSuggestion!: (value: SupportAccessIncidentSuggestion) => void;
    const suggestion = new Promise<SupportAccessIncidentSuggestion>((resolve) => {
      resolveSuggestion = resolve;
    });
    const view = renderPanel(backendApi(suggestion));
    const input = view.getByLabelText("工单号") as HTMLInputElement;

    await fireEvent.update(input, "INC-MANUAL");
    resolveSuggestion({ incidentId: "INC-PERSISTED" });
    await suggestion;
    await nextTick();

    expect(input.value).toBe("INC-MANUAL");
  });
});
