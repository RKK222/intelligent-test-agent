import { describe, expect, it, vi } from "vitest";
import {
  PET_PREFERENCE_STORAGE_KEY,
  PET_SCALE_DEFAULT,
  PET_SCALE_MAX,
  normalizePetScale,
  dailyPetId,
  loadPetPreference,
  localDateKey,
  resolvePetPreference,
  savePetPreference,
  type PetPreference,
} from "../src/components/pet-companions";

describe("pet companions", () => {
  it("normalizes pet sizes to the supported range and step", () => {
    expect(normalizePetScale(0.4)).toBe(0.75);
    expect(normalizePetScale(1.13)).toBe(1.15);
    expect(normalizePetScale(PET_SCALE_DEFAULT)).toBe(1.5);
    expect(normalizePetScale(2)).toBe(2);
    expect(normalizePetScale(PET_SCALE_MAX)).toBe(2.5);
    expect(normalizePetScale(3)).toBe(PET_SCALE_MAX);
    expect(normalizePetScale("invalid")).toBe(PET_SCALE_DEFAULT);
  });

  it("rotates deterministically by local calendar day", () => {
    const firstDay = new Date(2026, 6, 15, 8, 30);
    const sameDay = new Date(2026, 6, 15, 23, 59);
    const nextDay = new Date(2026, 6, 16, 0, 1);

    expect(localDateKey(firstDay)).toBe("2026-07-15");
    expect(dailyPetId(sameDay)).toBe(dailyPetId(firstDay));
    expect(dailyPetId(nextDay)).not.toBe(dailyPetId(firstDay));
  });

  it("keeps a random companion stable for the day and redraws on the next day", () => {
    const random = vi.fn().mockReturnValueOnce(0.2).mockReturnValueOnce(0.8);
    const preference: PetPreference = { mode: "random", selectedPetId: "sniffer" };

    const first = resolvePetPreference(preference, new Date(2026, 6, 15), random);
    const sameDay = resolvePetPreference(first.preference, new Date(2026, 6, 15, 22), random);
    const nextDay = resolvePetPreference(sameDay.preference, new Date(2026, 6, 16), random);

    expect(sameDay.petId).toBe(first.petId);
    expect(random).toHaveBeenCalledTimes(2);
    expect(nextDay.petId).not.toBe(first.petId);
  });

  it("falls back safely for malformed storage and persists valid preferences", () => {
    const storage = {
      value: "{broken",
      getItem: vi.fn(() => storage.value),
      setItem: vi.fn((_: string, value: string) => { storage.value = value; }),
    };

    expect(loadPetPreference(storage)).toMatchObject({ mode: "daily", selectedPetId: "sniffer" });

    const selected: PetPreference = { mode: "selected", selectedPetId: "bird" };
    savePetPreference(storage, selected);
    expect(storage.setItem).toHaveBeenCalledWith(PET_PREFERENCE_STORAGE_KEY, JSON.stringify(selected));
    expect(loadPetPreference(storage)).toEqual(selected);
  });

  it("maps removed role ids to their replacement companions", () => {
    const storage = {
      getItem: vi.fn(() => JSON.stringify({ mode: "selected", selectedPetId: "owl", randomPetId: "glitch" })),
    };

    expect(loadPetPreference(storage)).toMatchObject({
      mode: "selected",
      selectedPetId: "bird",
      randomPetId: "hedgehog",
    });
  });

  it("loads and clamps a persisted pet size without breaking old records", () => {
    const storage = {
      getItem: vi.fn(() => JSON.stringify({ mode: "selected", selectedPetId: "bird", scale: 1.37 })),
    };

    expect(loadPetPreference(storage)).toMatchObject({ scale: 1.35 });
    expect(loadPetPreference({ getItem: vi.fn(() => JSON.stringify({ mode: "selected", selectedPetId: "bird" })) }))
      .not.toHaveProperty("scale");
  });
});
