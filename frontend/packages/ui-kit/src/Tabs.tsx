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
    <div className={cn("flex gap-1 border-b border-slate-800 bg-slate-950/70 px-2 py-1", className)}>
      {items.map((item) => (
        <button
          key={item.id}
          type="button"
          onClick={() => onValueChange(item.id)}
          className={cn(
            "rounded-md px-2 py-1 text-[12px] text-slate-400 transition hover:bg-slate-800 hover:text-slate-100",
            value === item.id && "bg-slate-800 text-slate-100 shadow-[inset_0_-2px_0_#4da3ff]"
          )}
        >
          {item.label}
          {item.count !== undefined ? <span className="ml-1 text-slate-500">{item.count}</span> : null}
        </button>
      ))}
    </div>
  );
}
