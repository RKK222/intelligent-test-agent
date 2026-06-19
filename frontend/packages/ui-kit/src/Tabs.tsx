"use client";

import { cn } from "./lib";

export type TabItem<T extends string> = {
  id: T;
  label: string;
  count?: number;
};

export function SegmentedTabs<T extends string>({
  items,
  value,
  onValueChange,
  className
}: {
  items: TabItem<T>[];
  value: T;
  onValueChange: (value: T) => void;
  className?: string;
}) {
  return (
    <div className={cn("flex gap-1 border-b border-[var(--ta-border)] bg-[#0d1628] px-2 py-1", className)}>
      {items.map((item) => (
        <button
          key={item.id}
          type="button"
          onClick={() => onValueChange(item.id)}
          className={cn(
            "rounded-md px-2 py-1 text-[12px] text-[var(--ta-muted)] transition hover:bg-[#122044] hover:text-[var(--ta-text)]",
            value === item.id && "bg-[#17244a] text-[var(--ta-text)] shadow-[inset_0_-2px_0_var(--ta-accent)]"
          )}
        >
          {item.label}
          {item.count !== undefined ? <span className="ml-1 text-slate-500">{item.count}</span> : null}
        </button>
      ))}
    </div>
  );
}
