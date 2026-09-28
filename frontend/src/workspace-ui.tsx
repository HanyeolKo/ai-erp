import type { ReactNode } from "react";

export const iconNames = [
  "chevron-left", "chevron-right", "calendar", "filter", "plus", "edit",
  "clock", "check", "draft", "cancel", "users", "history", "roadmap",
  "tree", "list", "board", "milestone", "search",
] as const;
export type IconName = (typeof iconNames)[number];

const paths: Record<IconName, string> = {
  "chevron-left": "M15 5 8 12l7 7",
  "chevron-right": "m9 5 7 7-7 7",
  calendar: "M5 7.5A2.5 2.5 0 0 1 7.5 5h9A2.5 2.5 0 0 1 19 7.5v10A2.5 2.5 0 0 1 16.5 20h-9A2.5 2.5 0 0 1 5 17.5zm4-5v5m6-5v5M5 10h14",
  filter: "M4 5h16l-6.25 7.3v5.7l-3.5 1.8v-7.5z",
  plus: "M12 5v14M5 12h14",
  edit: "m5 19 3.2-.7L18.6 8a2.2 2.2 0 0 0-3.1-3.1L5.7 14.9zM14 6.4l3.6 3.6",
  clock: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-14v5l3.5 2",
  check: "M20 11.2V12a8 8 0 1 1-4.75-7.32M20 5 11.5 13.5 8 10",
  draft: "M12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16Zm0 4v4m0 3h.01",
  cancel: "M12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16Zm-3 5 6 6m0-6-6 6",
  users: "M16 20v-1.5a3.5 3.5 0 0 0-3.5-3.5h-5A3.5 3.5 0 0 0 4 18.5V20m6-9a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm5.5-5.7a3 3 0 0 1 0 5.85M17 15a3.5 3.5 0 0 1 3 3.5V20",
  history: "M4 12a8 8 0 1 0 2.35-5.65L4 8.7M4 4v4.7h4.7M12 8v4l2.7 1.6",
  roadmap: "M4 18h16M6 16V8l6-4v12m6 0V6l-6-2",
  tree: "M5 5h5v4H5zM14 15h5v4h-5zM5 15h5v4H5zM10 7h4v10m0-5h4",
  list: "M5 6h1m3 0h10M5 12h1m3 0h10M5 18h1m3 0h10",
  board: "M5 5h5v14H5zm9 0h5v8h-5z",
  milestone: "m12 4 2.2 4.5L19 9.2l-3.5 3.4.8 4.9-4.3-2.3-4.3 2.3.8-4.9L5 9.2l4.8-.7z",
  search: "m20 20-4.5-4.5m2-5.5a7.5 7.5 0 1 1-15 0 7.5 7.5 0 0 1 15 0Z",
};

export function Icon({ name, className }: { name: IconName; className?: string }) {
  return <svg className={className} width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d={paths[name]} /></svg>;
}

export function Avatar({ name }: { name?: string | null }) {
  const label = name?.trim() || "미배정";
  const initials = label === "미배정" ? "–" : label.split(/\s+/).map(part => part[0]).join("").slice(0, 2).toUpperCase();
  return <span className="workspace-avatar" title={label} aria-label={label}><span aria-hidden="true">{initials}</span><span className="workspace-avatar-name">{label}</span></span>;
}

export type StatusTone = "neutral" | "blue" | "amber" | "green" | "red";
export function StatusBadge({ children, tone = "neutral", icon }: { children: ReactNode; tone?: StatusTone; icon?: IconName }) {
  return <span className={`status-badge status-badge--${tone}`}>{icon && <Icon name={icon} />}<span>{children}</span></span>;
}

