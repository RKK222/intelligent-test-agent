export const PET_COMPANIONS = [
  {
    id: "sniffer",
    name: "Bug 嗅探者",
    species: "侦探犬",
    duty: "精准发现漏洞",
    accent: "#45b98f",
  },
  {
    id: "chameleon",
    name: "全域环境扫描仪",
    species: "机械变色龙",
    duty: "跨环境全域扫描",
    accent: "#7f8fe8",
  },
  {
    id: "platypus",
    name: "多模态异形体",
    species: "电感鸭嘴兽",
    duty: "感知异常数据流",
    accent: "#35b7bd",
  },
  {
    id: "owl",
    name: "全天候静默监视器",
    species: "机械哨兵猫头鹰",
    duty: "持续守护质量",
    accent: "#4d86d9",
  },
  {
    id: "glitch",
    name: "缺陷清理官",
    species: "Bug 吞噬怪",
    duty: "收纳并清理缺陷",
    accent: "#e57d79",
  },
] as const;

export type PetCompanionId = (typeof PET_COMPANIONS)[number]["id"];
export type PetCompanion = (typeof PET_COMPANIONS)[number];
export type PetDisplayMode = "daily" | "random" | "selected";

export type PetPreference = {
  mode: PetDisplayMode;
  selectedPetId: PetCompanionId;
  randomDate?: string;
  randomPetId?: PetCompanionId;
};

export const PET_PREFERENCE_STORAGE_KEY = "test-agent.pet-companion.v1";

const DEFAULT_PREFERENCE: PetPreference = {
  mode: "daily",
  selectedPetId: "sniffer",
};

const PET_IDS = new Set<PetCompanionId>(PET_COMPANIONS.map((pet) => pet.id));

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
    return {
      mode: parsed.mode as PetDisplayMode,
      selectedPetId: isPetCompanionId(parsed.selectedPetId) ? parsed.selectedPetId : DEFAULT_PREFERENCE.selectedPetId,
      randomDate: typeof parsed.randomDate === "string" ? parsed.randomDate : undefined,
      randomPetId: isPetCompanionId(parsed.randomPetId) ? parsed.randomPetId : undefined,
    };
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
