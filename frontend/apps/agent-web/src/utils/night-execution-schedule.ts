const MINUTE_MS = 60_000;
const DAY_MS = 24 * 60 * MINUTE_MS;
const BEIJING_OFFSET_MS = 8 * 60 * MINUTE_MS;

const pad = (value: number) => String(value).padStart(2, "0");

/** 把绝对时间显式格式化为北京时间 datetime-local 值，不读取浏览器本地时区。 */
export function formatBeijingDateTimeInput(value: Date): string {
  const beijing = new Date(value.getTime() + BEIJING_OFFSET_MS);
  return `${beijing.getUTCFullYear()}-${pad(beijing.getUTCMonth() + 1)}-${pad(beijing.getUTCDate())}`
    + `T${pad(beijing.getUTCHours())}:${pad(beijing.getUTCMinutes())}`;
}

/** 解析北京时间 datetime-local；非法日期（例如 2 月 30 日）严格返回 null。 */
export function parseBeijingDateTimeInput(value: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value);
  if (!match) return null;
  const [, year, month, day, hour, minute] = match;
  const parsed = new Date(Date.UTC(
    Number(year),
    Number(month) - 1,
    Number(day),
    Number(hour) - 8,
    Number(minute),
    0,
    0
  ));
  return formatBeijingDateTimeInput(parsed) === value ? parsed : null;
}

/** 快捷选择从当前分钟向后偏移，结果始终清除秒和毫秒。 */
export function customScheduleAtOffset(now: Date, minutes: number): Date {
  const currentMinute = Math.floor(now.getTime() / MINUTE_MS) * MINUTE_MS;
  return new Date(currentMinute + minutes * MINUTE_MS);
}

/** datetime-local 输入使用的服务端同口径边界：下一完整分钟至 24 小时内最后一分钟。 */
export function adminCustomScheduleBounds(now: Date): { min: string; max: string } {
  const min = customScheduleAtOffset(now, 1);
  const max = new Date(Math.floor((now.getTime() + DAY_MS) / MINUTE_MS) * MINUTE_MS);
  return {
    min: formatBeijingDateTimeInput(min),
    max: formatBeijingDateTimeInput(max)
  };
}

export type AdminCustomScheduleValidation =
  | { valid: true; slotStart: string }
  | { valid: false; reason: string };

/** 提交前重新按当前时刻校验；服务端仍是最终权限和时间边界。 */
export function validateAdminCustomSchedule(
  value: string,
  now: Date
): AdminCustomScheduleValidation {
  const parsed = parseBeijingDateTimeInput(value);
  if (!parsed) {
    return { valid: false, reason: "请输入有效的北京时间" };
  }
  const earliest = customScheduleAtOffset(now, 1);
  const latest = new Date(now.getTime() + DAY_MS);
  if (parsed.getTime() < earliest.getTime() || parsed.getTime() > latest.getTime()) {
    return { valid: false, reason: "请选择下一完整分钟至未来 24 小时内的时间" };
  }
  return { valid: true, slotStart: parsed.toISOString() };
}
