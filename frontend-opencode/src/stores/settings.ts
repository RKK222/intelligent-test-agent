import { defineStore } from "pinia";
import { computed, ref, watch } from "vue";

const STORAGE_KEY = "frontend-opencode:settings";

export const useSettingsStore = defineStore("settings", () => {
  const stored = readStored();
  const theme = ref<"dark" | "light" | "system">(stored.theme ?? "dark");
  const density = ref<"compact" | "comfortable">(stored.density ?? "compact");
  const sound = ref(Boolean(stored.sound));
  const keymap = ref<"default" | "vim">(stored.keymap ?? "default");

  const effectiveTheme = computed(() => {
    if (theme.value !== "system") {
      return theme.value;
    }
    return matchMedia?.("(prefers-color-scheme: light)").matches ? "light" : "dark";
  });

  watch(
    () => ({ theme: theme.value, density: density.value, sound: sound.value, keymap: keymap.value }),
    (value) => localStorage.setItem(STORAGE_KEY, JSON.stringify(value)),
    { deep: true }
  );

  return { theme, density, sound, keymap, effectiveTheme };
});

function readStored() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "{}") as {
      theme?: "dark" | "light" | "system";
      density?: "compact" | "comfortable";
      sound?: boolean;
      keymap?: "default" | "vim";
    };
  } catch {
    return {};
  }
}
