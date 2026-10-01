import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type Notifications } from "./api/client";
import { keys } from "./state";
import { captureSession, isSessionContextActive } from "./session";
import { internalLink, Link, Notice, QueryState } from "./ui";

type NotificationRow = Notifications[number];
type NotificationContent = NonNullable<NotificationRow["content"]>;

const notificationLabels: Record<string, string> = {
  SCHEDULE_CREATED: "\uc77c\uc815 \uc0dd\uc131", SCHEDULE_CHANGED: "\uc77c\uc815 \ubcc0\uacbd", SCHEDULE_UPDATED: "\uc77c\uc815 \ubcc0\uacbd",
  SCHEDULE_CONFIRMED: "\uc77c\uc815 \ud655\uc815", SCHEDULE_CANCELLED: "\uc77c\uc815 \ucde8\uc18c", SCHEDULE_ACKNOWLEDGED: "\ucc38\uc5ec\uc790 \ud655\uc778",
  CALENDAR_SYNCED: "Calendar \ub3d9\uae30\ud654 \uc644\ub8cc", CALENDAR_SYNC_FAILED: "Calendar \ub3d9\uae30\ud654 \uc2e4\ud328",
  INVITATION_CREATED: "\ucd08\ub300 \uc0dd\uc131", INVITATION_ACCEPTED: "\ucd08\ub300 \uc218\ub77d", INVITATION_REJECTED: "\ucd08\ub300 \uac70\uc808",
};
const fieldLabels: Record<string, string> = {
  title: "\uc77c\uc815 \uc774\ub984", startsAt: "\uc2dc\uc791 \uc2dc\uac04", endsAt: "\uc885\ub8cc \uc2dc\uac04",
  status: "\uc0c1\ud0dc", description: "\uc124\uba85", participants: "\ucc38\uc5ec\uc790",
};
const copy = {
  eyebrow: "\ubaa8\ub4e0 \ud504\ub85c\uc81d\ud2b8", heading: "\uc54c\ub9bc", emptyHeading: "\uc0c8 \uc54c\ub9bc\uc774 \uc5c6\uc2b5\ub2c8\ub2e4",
  emptyBody: "\uc0c8\ub85c\uc6b4 \ud504\ub85c\uc81d\ud2b8 \ud65c\ub3d9\uc774 \uc0dd\uae30\uba74 \uc5ec\uae30\uc5d0 \ud45c\uc2dc\ub429\ub2c8\ub2e4.",
  restrictedTitle: "\uc81c\ud55c\ub41c \uc54c\ub9bc", restrictedMessage: "\uc774 \ud56d\ubaa9\uc5d0 \uc811\uadfc\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4.",
  actorUnavailable: "\ud589\uc704\uc790 \uc815\ubcf4 \uc5c6\uc74c", eventState: "\uc774\ubca4\ud2b8 \ub2f9\uc2dc \uc0c1\ud0dc", currentState: "\ud604\uc7ac \uc0c1\ud0dc",
  unavailable: "\uad00\ub828 \uc77c\uc815\uc744 \ud604\uc7ac \uc0ac\uc6a9\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4.",
  currentProject: "\ud604\uc7ac \ud504\ub85c\uc81d\ud2b8", currentSchedule: "\ud604\uc7ac \uc77c\uc815", genericUnavailable: "\uc54c\ub9bc \uc0c1\uc138 \uc815\ubcf4\ub97c \uc0ac\uc6a9\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4.",
  scheduleLink: "\uad00\ub828 \uc77c\uc815 \ubcf4\uae30", projectLink: "\uad00\ub828 \ud504\ub85c\uc81d\ud2b8 \ubcf4\uae30", read: "\uc77d\uc74c", markRead: "\uc77d\uc74c \ucc98\ub9ac",
  previous: "\uc774\uc804", next: "\ub2e4\uc74c", page: "\ud398\uc774\uc9c0",
};
function validDate(value: string | null | undefined): Date | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? date : null;
}
function dateLabel(value: string | null | undefined) {
  const date = validDate(value);
  return date ? new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(date) : "\uc2dc\uac04 \uc815\ubcf4 \uc5c6\uc74c";
}
function statusLabel(value: string | null | undefined) {
  return ({ DRAFT: "\ucd08\uc548", CONFIRMED: "\ud655\uc815", CANCELLED: "\ucde8\uc18c" } as Record<string, string>)[value ?? ""] ?? value ?? "\uc0c1\ud0dc \uc815\ubcf4 \uc5c6\uc74c";
}
function summaryLabel(value: string | null | undefined) {
  return ({
    "Schedule created": "\uc77c\uc815\uc744 \uc0dd\uc131\ud588\uc2b5\ub2c8\ub2e4.",
    "Schedule details changed": "\uc77c\uc815 \ub0b4\uc6a9\uc774 \ubcc0\uacbd\ub418\uc5c8\uc2b5\ub2c8\ub2e4.",
    "Schedule confirmed": "\uc77c\uc815\uc774 \ud655\uc815\ub418\uc5c8\uc2b5\ub2c8\ub2e4.",
    "Schedule cancelled": "\uc77c\uc815\uc774 \ucde8\uc18c\ub418\uc5c8\uc2b5\ub2c8\ub2e4.",
    "Schedule changed": "\uc77c\uc815\uc774 \ubcc0\uacbd\ub418\uc5c8\uc2b5\ub2c8\ub2e4.",
    "Change details unavailable": "\ubcc0\uacbd \uc0c1\uc138 \uc815\ubcf4\ub97c \uc0ac\uc6a9\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4.",
    "Notification details unavailable": copy.genericUnavailable,
  } as Record<string, string>)[value ?? ""] ?? value ?? "\ubcc0\uacbd \uc0c1\uc138 \uc815\ubcf4\ub97c \uc0ac\uc6a9\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4.";
}
function fieldValue(field: NonNullable<NotificationContent["changedFields"]>[number], value: string | null | undefined) {
  if (value == null) return "\ubcc0\uacbd\ub428";
  return field.field === "startsAt" || field.field === "endsAt" ? dateLabel(value) : field.field === "status" ? statusLabel(value) : value;
}
function changes(content: NotificationContent) {
  return (content.changedFields ?? []).map((field, index) => <li key={`${field.field}-${index}`}>
    <span>{fieldLabels[field.field ?? ""] ?? field.label ?? "\ubcc0\uacbd \ud56d\ubaa9"}: </span>
    {field.before != null && field.after != null
      ? <>{fieldValue(field, field.before)} &rarr; {fieldValue(field, field.after)}</>
      : <span>{fieldValue(field, field.after ?? field.before)}</span>}
  </li>);
}
function Row({ notification, onNavigate, readPending, markRead }: {
  notification: NotificationRow;
  onNavigate?: () => void;
  readPending: boolean;
  markRead: () => void;
}) {
  const content = notification.content;
  const href = internalLink(notification.link);
  const occurredAt = content?.occurredAt ?? notification.createdAt;
  const time = validDate(occurredAt);
  const isRich = content?.provenance === "SNAPSHOT";
  const restricted = content?.provenance === "RESTRICTED" || notification.type === "RESTRICTED_ITEM";
  const title = notificationLabels[notification.type] ?? "\uc54c\ub9bc";
  return <li>
    <div className="notification-main">
      {isRich && content?.projectName && content.scheduleTitle
        ? <strong>{content.projectName} &rarr; {content.scheduleTitle} · {title}</strong>
        : <strong>{restricted ? copy.restrictedTitle : title}</strong>}
      {isRich && <>
        <p className="notification-meta">{content?.actorDisplayName ?? copy.actorUnavailable} · <time dateTime={time?.toISOString()}>{dateLabel(occurredAt)}</time></p>
        <p className="notification-summary">{summaryLabel(content?.summary)}</p>
        {!!content?.changedFields?.length && <ul className="notification-changes">{changes(content)}</ul>}
        {content?.scheduleStatus && <p className="notification-meta">{copy.eventState}: {statusLabel(content.scheduleStatus)}</p>}
        {content?.currentScheduleStatus && content.currentScheduleStatus !== content.scheduleStatus && <p className="notification-meta">{copy.currentState}: {statusLabel(content.currentScheduleStatus)}</p>}
        {content?.resourceAvailable === false && <p className="notification-meta">{copy.unavailable}</p>}
      </>}
      {content?.provenance === "LEGACY" && <>
        <p className="notification-summary">{summaryLabel(content.summary)}</p>
        {content.currentProjectName && <p className="notification-meta">{copy.currentProject}: {content.currentProjectName}</p>}
        {content.currentScheduleTitle && <p className="notification-meta">{copy.currentSchedule}: {content.currentScheduleTitle}</p>}
        {content.currentScheduleStatus && <p className="notification-meta">{copy.currentState}: {statusLabel(content.currentScheduleStatus)}</p>}
        {content.resourceAvailable === false && <p className="notification-meta">{copy.unavailable}</p>}
      </>}
      {restricted && <p className="notification-meta">{copy.restrictedMessage}</p>}
      {!isRich && content?.provenance !== "LEGACY" && !restricted && <p className="notification-meta">{summaryLabel(content?.summary ?? "Notification details unavailable")}</p>}
      {!isRich && <time dateTime={time?.toISOString()}>{dateLabel(occurredAt)}</time>}
    </div>
    <div className="notification-actions">
      {href && <Link to={href} onClick={onNavigate}>{notification.link?.includes("/schedules/") ? copy.scheduleLink : copy.projectLink}</Link>}
      {notification.readAt ? <span className="notification-read">{copy.read}</span> : <button type="button" disabled={readPending} onClick={markRead}>{copy.markRead}</button>}
    </div>
  </li>;
}
export function NotificationContent({ onNavigate, compact = false }: { onNavigate?: () => void; compact?: boolean }) {
  const [page, setPage] = useState(0);
  const queryClient = useQueryClient();
  const list = useQuery({ queryKey: [...keys.notifications, page], queryFn: () => api.notifications(page) });
  const read = useMutation({ mutationFn: api.readNotification, onMutate: captureSession, onSuccess: (_, __, context) => isSessionContextActive(context) ? queryClient.invalidateQueries({ queryKey: keys.notifications }) : undefined });
  return <div className={compact ? "notification-content notification-content--compact" : "notification-content"}>
    {!compact && <p className="eyebrow">{copy.eyebrow}</p>}{!compact && <h1>{copy.heading}</h1>}
    <QueryState query={list} />
    {list.isSuccess && (list.data.length ? <ul className="notification-list">{list.data.map(notification =>
      <Row key={notification.id} notification={notification} onNavigate={onNavigate} readPending={read.isPending}
        markRead={() => !read.isPending && read.mutate(notification.id)} />)}</ul> : <div className="empty-state"><h2>{copy.emptyHeading}</h2><p>{copy.emptyBody}</p></div>)}
    {read.isError && <Notice error={read.error} />}
    <div className="pagination"><button type="button" disabled={!page || list.isFetching} onClick={() => setPage(page - 1)}>{copy.previous}</button><span>{page + 1} {copy.page}</span><button type="button" disabled={!list.isSuccess || list.data.length < 100 || list.isFetching} onClick={() => setPage(page + 1)}>{copy.next}</button></div>
  </div>;
}
