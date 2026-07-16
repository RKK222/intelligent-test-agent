import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import PetCompanionAvatar from "../src/components/PetCompanionAvatar.vue";
import { PET_COMPANIONS } from "../src/components/pet-companions";

describe("PetCompanionAvatar", () => {
  it.each(PET_COMPANIONS)("renders $id process status through both eyes", ({ id }) => {
    const wrapper = mount(PetCompanionAvatar, {
      props: {
        petId: id,
        showStatus: true,
        statusTone: "ready",
      },
    });

    expect(wrapper.get("svg").classes()).toEqual(expect.arrayContaining(["has-status", "status-ready"]));
    expect(wrapper.findAll(".pet-eye")).toHaveLength(2);
    expect(wrapper.get(".pet-eye").attributes("fill")).toBeTruthy();
    expect(wrapper.find('[data-testid="robot-process-status-beacon"]').exists()).toBe(false);
  });

  it("keeps roster avatars in their original eye colors", () => {
    const wrapper = mount(PetCompanionAvatar, {
      props: { petId: "owl" },
    });

    expect(wrapper.get("svg").classes()).not.toContain("has-status");
    expect(wrapper.get(".pet-eye").attributes("fill")).toBe("#3f79cc");
  });

  it("maps initialization, error, and checking states onto the eyes", async () => {
    const wrapper = mount(PetCompanionAvatar, {
      props: {
        petId: "platypus",
        showStatus: true,
        statusTone: "checking",
      },
    });

    expect(wrapper.get("svg").classes()).toContain("status-checking");
    await wrapper.setProps({ statusTone: "needs-initialization" });
    expect(wrapper.get("svg").classes()).toContain("status-needs-initialization");
    await wrapper.setProps({ statusTone: "error" });
    expect(wrapper.get("svg").classes()).toContain("status-error");
  });
});
