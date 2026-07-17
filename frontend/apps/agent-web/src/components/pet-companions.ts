export const PET_COMPANIONS = [
  {
    id: "sniffer",
    name: "Bug 嗅探者",
    species: "侦探犬",
    duty: "精准发现漏洞",
    accent: "#45b98f",
  },
  {
    id: "bunny",
    name: "雷达兔",
    species: "雷达兔",
    duty: "环境扫描与快速感知",
    accent: "#48bdb7",
  },
  {
    id: "fox",
    name: "星探狐",
    species: "探索狐狸",
    duty: "敏锐定位异常",
    accent: "#e78a55",
  },
  {
    id: "bird",
    name: "巡检小鸟",
    species: "巡检小鸟",
    duty: "巡检与持续监控",
    accent: "#f0b52b",
  },
  {
    id: "hedgehog",
    name: "数据刺猬",
    species: "数据刺猬",
    duty: "收集分析与深度排查",
    accent: "#7662d8",
  },
] as const;

export type PetCompanionId = (typeof PET_COMPANIONS)[number]["id"];
export type PetCompanion = (typeof PET_COMPANIONS)[number];
export type PetDisplayMode = "daily" | "random" | "selected";

export type PetPreference = {
  mode: PetDisplayMode;
  selectedPetId: PetCompanionId;
  /** 宠物显示比例；旧版本没有该字段时按 100% 兼容。 */
  scale?: number;
  randomDate?: string;
  randomPetId?: PetCompanionId;
};

export const PET_PREFERENCE_STORAGE_KEY = "test-agent.pet-companion.v1";
export const PET_SCALE_MIN = 0.75;
export const PET_SCALE_MAX = 1.5;
export const PET_SCALE_STEP = 0.05;

const DEFAULT_PREFERENCE: PetPreference = {
  mode: "daily",
  selectedPetId: "sniffer",
};

const PET_IDS = new Set<PetCompanionId>(PET_COMPANIONS.map((pet) => pet.id));

/** 将输入和历史偏好规整到兼容旧版浏览器的固定步进范围。 */
export function normalizePetScale(value: unknown): number {
  const numeric = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(numeric)) return 1;
  const bounded = Math.min(PET_SCALE_MAX, Math.max(PET_SCALE_MIN, numeric));
  return Number((Math.round(bounded / PET_SCALE_STEP) * PET_SCALE_STEP).toFixed(2));
}

// 角色替换后仍兼容旧浏览器里的选择记录，把原角色映射到新的同职责伙伴。
const LEGACY_PET_ID_ALIASES: Record<string, PetCompanionId> = {
  chameleon: "bunny",
  platypus: "fox",
  octopus: "fox",
  cat: "fox",
  owl: "bird",
  glitch: "hedgehog",
  shark: "hedgehog",
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
