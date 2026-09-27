import type { TeamReviewEntry } from "@test-agent/shared-types";

/** Git 作者与来源成员不是同一个概念；未提交文件不冒用目录所有者作为修改人。 */
export function formatTeamReviewMetadata(entry: TeamReviewEntry): string {
  if (entry.latestUncertain) return "最新待核验 · 请选择成员";
  const { file, source } = entry.selected;
  const time = file.changedAt || file.fileTime;
  const formatted = time ? new Date(time).toLocaleString("zh-CN", {
    month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false
  }) : "时间未知";
  if (file.deleted) return `已删除 · 来源 ${source.username}`;
  if (file.timeType === "GIT_COMMIT") return `${file.author || "作者未知"} · ${formatted}`;
  return `来源 ${source.username} · ${file.timeType === "FILE_TIME" ? "文件时间 " + formatted : "作者/时间未知"}`;
}
