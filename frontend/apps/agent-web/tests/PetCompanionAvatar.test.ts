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
    expect(wrapper.find(".pet-status-disc").exists()).toBe(false);
    expect(wrapper.get(".pet-status-halo").attributes("fill")).toBe("none");
    expect(wrapper.get(".pet-status-halo").attributes("stroke")).toBe("#78c6d2");
    expect(wrapper.find('[data-testid="robot-process-status-beacon"]').exists()).toBe(false);
  });

  it("uses the supplied raster avatar and hides status overlays in the roster", () => {
    const wrapper = mount(PetCompanionAvatar, {
      props: { petId: "panda" },
    });

    expect(wrapper.get("svg").classes()).not.toContain("has-status");
    expect(wrapper.get("image").attributes("href")).toBeTruthy();
    expect(wrapper.get(".pet-status-disc").attributes("fill")).toBe("#e5ddd2");
  });

  it("maps initialization, error, and checking states onto the status halo", async () => {
    const wrapper = mount(PetCompanionAvatar, {
      props: {
        petId: "fox",
        showStatus: true,
        statusTone: "checking",
      },
    });

    expect(wrapper.get("svg").classes()).toContain("status-checking");
    expect(wrapper.get(".pet-status-halo").attributes("stroke")).toBe("#b2c0cc");
    await wrapper.setProps({ statusTone: "needs-initialization" });
    expect(wrapper.get("svg").classes()).toContain("status-needs-initialization");
    expect(wrapper.get(".pet-status-halo").attributes("stroke")).toBe("#ef8e84");
    await wrapper.setProps({ statusTone: "error" });
    expect(wrapper.get("svg").classes()).toContain("status-error");
    expect(wrapper.get(".pet-status-halo").attributes("stroke")).toBe("#ef8e84");
  });
});
