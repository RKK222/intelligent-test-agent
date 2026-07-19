export const PET_COMPANIONS = [
  {
    id: "deer",
    name: "寻迹鹿",
    species: "敏捷小鹿",
    duty: "快速发现线索",
    accent: "#d89b55",
  },
  {
    id: "red-panda",
    name: "巡查红熊猫",
    species: "红熊猫",
    duty: "敏锐巡检与定位",
    accent: "#d66b4d",
  },
  {
    id: "dragon",
    name: "深夜龙",
    species: "夜巡龙",
    duty: "全域扫描与守护",
    accent: "#4b79d5",
  },
  {
    id: "fox",
    name: "星探狐",
    species: "探索狐狸",
    duty: "敏锐定位异常",
    accent: "#9c7be4",
  },
  {
    id: "panda",
    name: "稳态熊猫",
    species: "熊猫",
    duty: "稳定运行与质量守护",
    accent: "#6f8791",
  },
  {
    id: "raccoon",
    name: "工具浣熊",
    species: "浣熊",
    duty: "工具排查与修复",
    accent: "#9b7c6b",
  },
  {
    id: "cat",
    name: "夜巡猫",
    species: "黑白猫",
    duty: "安静监控与异常提醒",
    accent: "#8f79bb",
  },
] as const;

export type PetCompanionId = (typeof PET_COMPANIONS)[number]["id"];
export type PetCompanion = (typeof PET_COMPANIONS)[number];
export type PetDisplayMode = "daily" | "random" | "selected";

export type PetPreference = {
  mode: PetDisplayMode;
  selectedPetId: PetCompanionId;
  /** 宠物显示比例；旧版本没有该字段时按默认 150% 兼容。 */
  scale?: number;
  randomDate?: string;
  randomPetId?: PetCompanionId;
};

export const PET_PREFERENCE_STORAGE_KEY = "test-agent.pet-companion.v1";
export const PET_SCALE_MIN = 0.75;
export const PET_SCALE_DEFAULT = 1.5;
export const PET_SCALE_MAX = 2.5;
export const PET_SCALE_STEP = 0.05;

const DEFAULT_PREFERENCE: PetPreference = {
  mode: "daily",
  selectedPetId: "deer",
};

const PET_IDS = new Set<PetCompanionId>(PET_COMPANIONS.map((pet) => pet.id));

/** 将输入和历史偏好规整到兼容旧版浏览器的固定步进范围。 */
export function normalizePetScale(value: unknown): number {
  const numeric = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(numeric)) return PET_SCALE_DEFAULT;
  const bounded = Math.min(PET_SCALE_MAX, Math.max(PET_SCALE_MIN, numeric));
  return Number((Math.round(bounded / PET_SCALE_STEP) * PET_SCALE_STEP).toFixed(2));
}

// 角色替换后仍兼容旧浏览器里的选择记录，把原角色映射到新的同职责伙伴。
const LEGACY_PET_ID_ALIASES: Record<string, PetCompanionId> = {
  sniffer: "deer",
  bunny: "red-panda",
  bird: "raccoon",
  hedgehog: "panda",
  chameleon: "dragon",
  platypus: "panda",
  octopus: "dragon",
  owl: "cat",
  glitch: "raccoon",
  shark: "dragon",
};

function normalizePetCompanionId(value: unknown): PetCompanionId | undefined {
  if (isPetCompanionId(value)) return value;
  return typeof value === "string" ? LEGACY_PET_ID_ALIASES[value] : undefined;
}

export function isPetCompanionId(value: unknown): value is PetCompanionId {
  return typeof value === "string" && PET_IDS.has(value as PetCompanionId);
}

export function localDateKey(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * 按本地自然日轮换伙伴，使用 UTC 仅计算日期序号，避免夏令时导致同一天索引漂移。
 */
export function dailyPetId(date: Date): PetCompanionId {
  const dayIndex = Math.floor(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / 86_400_000);
  return PET_COMPANIONS[((dayIndex % PET_COMPANIONS.length) + PET_COMPANIONS.length) % PET_COMPANIONS.length]!.id;
}

export function loadPetPreference(storage: Pick<Storage, "getItem"> | undefined): PetPreference {
  if (!storage) return { ...DEFAULT_PREFERENCE };
  try {
    const raw = storage.getItem(PET_PREFERENCE_STORAGE_KEY);
    if (!raw) return { ...DEFAULT_PREFERENCE };
    const parsed = JSON.parse(raw) as Partial<PetPreference>;
    if (!(["daily", "random", "selected"] as const).includes(parsed.mode as PetDisplayMode)) {
      return { ...DEFAULT_PREFERENCE };
    }
    const preference: PetPreference = {
      mode: parsed.mode as PetDisplayMode,
      selectedPetId: normalizePetCompanionId(parsed.selectedPetId) ?? DEFAULT_PREFERENCE.selectedPetId,
      randomDate: typeof parsed.randomDate === "string" ? parsed.randomDate : undefined,
      randomPetId: normalizePetCompanionId(parsed.randomPetId),
    };
    if (typeof parsed.scale === "number" && Number.isFinite(parsed.scale)) {
      preference.scale = normalizePetScale(parsed.scale);
    }
    return preference;
  } catch {
    return { ...DEFAULT_PREFERENCE };
  }
}

export function savePetPreference(storage: Pick<Storage, "setItem"> | undefined, preference: PetPreference): void {
  if (!storage) return;
  try {
    storage.setItem(PET_PREFERENCE_STORAGE_KEY, JSON.stringify(preference));
  } catch {
    // 隐私模式或存储配额异常时只保留当前页面内的选择，不阻断宠物交互。
  }
}

/**
 * 随机模式在一个自然日内保持稳定；跨日时重新抽取，并尽量避免连续两天出现同一伙伴。
 */
export function resolvePetPreference(
  preference: PetPreference,
  date: Date,
  random: () => number = Math.random,
): { preference: PetPreference; petId: PetCompanionId } {
  if (preference.mode === "selected") {
    return { preference, petId: preference.selectedPetId };
  }
  if (preference.mode === "daily") {
    return { preference, petId: dailyPetId(date) };
  }

  const dateKey = localDateKey(date);
  if (preference.randomDate === dateKey && isPetCompanionId(preference.randomPetId)) {
    return { preference, petId: preference.randomPetId };
  }

  const candidates = PET_COMPANIONS.filter((pet) => pet.id !== preference.randomPetId);
  const normalizedRandom = Math.min(0.999_999, Math.max(0, random()));
  const petId = candidates[Math.floor(normalizedRandom * candidates.length)]!.id;
  return {
    petId,
    preference: {
      ...preference,
      randomDate: dateKey,
      randomPetId: petId,
    },
  };
}

export function getPetCompanion(petId: PetCompanionId): PetCompanion {
  return PET_COMPANIONS.find((pet) => pet.id === petId) ?? PET_COMPANIONS[0];
}
