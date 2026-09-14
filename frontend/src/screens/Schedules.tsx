import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, api, PAGE_SIZE, type Dashboard as DashboardData, type Schedules as Rows } from "../api/client";
import { accessKey, accessRead, acknowledgement, capabilities, clearAccessDenial, isAccessError, keys, recordAccessDenial, refreshSchedule, useMe, useProject } from "../state";
import { captureSession, isSessionContextActive } from "../session";
import { Dialog, Link, ProjectMissing, QueryState, Shell } from "../ui";
import { civilDateBoundary, dateInZone, displayTime, navigateDate, shiftDate, utcToLocalDateTime, localDateTimeToUtc, validZone, viewWindow } from "../time";
import { applyMonthChange, applyWeekMove, applyWeekResize, editBody, eventDayMinute, eventSpan as directEventSpan, isValidChange, previewText, weekLaneLayout, type CalendarChange, type CalendarSchedule, type LocalPoint } from "./calendar-direct-manipulation";
import "./schedules.css";

const statusLabels: Record<string, string> = {
    ALL: "전체 상태",
    DRAFT: "초안",
    CONFIRMED: "확정",
    CANCELLED: "취소",
};
const ackLabels: Record<string, string> = {
    ALL: "전체 확인 상태",
    PENDING: "확인 대기",
    ACKNOWLEDGED: "확인 완료",
    NOT_REQUIRED: "확인 대상 아님",
};
const statusLabel = (value: string) => statusLabels[value] ?? "상태 확인 필요";
const ackLabel = (value: string) => ackLabels[value] ?? "확인 상태 확인 필요";

function TimedList({ rows }: { rows: DashboardData["upcomingSchedules"] }) {
    if (!rows.length) return <p className="schedule-empty">예정된 일정이 없습니다.</p>;
    return <ul className="schedule-list schedule-list--overview">
        {rows.map(schedule => <li key={schedule.id}>
            <Link to={`/projects/${schedule.projectId}/schedules/${schedule.id}`}>{schedule.title}</Link>
            <span className="schedule-status">{statusLabel(schedule.status)}</span>
            <time dateTime={schedule.startsAt}>{displayTime(schedule.startsAt)}</time>
            <span aria-hidden="true">~</span>
            <time dateTime={schedule.endsAt}>{displayTime(schedule.endsAt)}</time>
        </li>)}
    </ul>;
}

export function Dashboard({ id }: { id: string }) {
    const project = useProject(id);
    const me = useMe();
    const dashboard = useQuery({ queryKey: keys.dashboard(id), queryFn: () => api.dashboard(id), enabled: project.isSuccess && !!project.data });
    if (!project.isSuccess) return <Shell><QueryState query={project} loadingMessage="프로젝트를 불러오는 중입니다." errorMessage="프로젝트를 불러오지 못했습니다." /></Shell>;
    if (!project.data) return <Shell><ProjectMissing onRetry={() => project.refetch()} isFetching={project.isFetching} /></Shell>;
    if (isAccessError(dashboard.error)) return <Shell><h1>대시보드를 불러올 수 없습니다</h1><QueryState query={dashboard} /></Shell>;
    const canCreate = dashboard.isSuccess && capabilities(project.data, me.data!.id).create;
    const isEmpty = dashboard.isSuccess && dashboard.data.scheduleCount === 0 && !dashboard.data.upcomingSchedules.length && !dashboard.data.actionQueue.length;
    const manager = project.data.role === "MANAGER";
    const onboardingTitle = manager
        ? "구성원을 초대하고 첫 일정을 만들어보세요."
        : canCreate
            ? "첫 일정을 만들어보세요."
            : "아직 일정이 없습니다.";
    const onboardingDescription = manager
        ? "프로젝트에 함께할 사람을 초대한 뒤 필요한 일정을 등록할 수 있습니다."
        : canCreate
            ? "필요한 일정을 등록해 프로젝트의 다음 작업을 시작하세요."
            : "조회 권한으로 일정 내용을 확인할 수 있습니다. 일정 작성은 관리자에게 요청하세요.";
    return <Shell project={project.data}>
        <div className="schedule-screen schedule-overview">
            <header className="schedule-heading">
                <div><p className="eyebrow">{project.data.name}</p><h1>프로젝트 개요</h1><p className="lead">다가오는 일정과 확인이 필요한 작업을 한 곳에서 살펴보세요.</p></div>
                <div className="schedule-actions">
                    {isEmpty && manager && <Link className="button button-primary" to={`/projects/${id}/invitations/new`}>구성원 초대</Link>}
                    {canCreate && <Link className={`button ${isEmpty && manager ? "button-secondary" : "button-primary"}`} to={`/projects/${id}/schedules/new`}>일정 만들기</Link>}
                    {!isEmpty && manager && <Link className="button button-secondary" to={`/projects/${id}/invitations/new`}>구성원 초대</Link>}
                </div>
            </header>
            <QueryState query={dashboard} />
            {dashboard.isSuccess && <>
                {isEmpty ? <section className="schedule-onboarding" aria-labelledby="onboarding-title"><p className="eyebrow">첫 단계</p><h2 id="onboarding-title">{onboardingTitle}</h2><p>{onboardingDescription}</p></section> : <>
                    <section aria-labelledby="upcoming-title" className="schedule-section"><div className="section-heading"><div><p className="eyebrow">다음 2주</p><h2 id="upcoming-title">예정된 일정</h2></div><Link to={`/projects/${id}/schedules`}>전체 일정 보기</Link></div><TimedList rows={dashboard.data.upcomingSchedules} /></section>
                    <section aria-labelledby="queue-title" className="schedule-section"><div className="section-heading"><div><p className="eyebrow">확인이 필요한 항목</p><h2 id="queue-title">처리 대기</h2></div></div>{dashboard.data.actionQueue.length ? <TimedList rows={dashboard.data.actionQueue} /> : <p className="schedule-empty">처리할 항목이 없습니다.</p>}</section>
                    <section aria-labelledby="overview-signal-title" className="schedule-signal-section"><div className="section-heading"><div><p className="eyebrow">현재 신호</p><h2 id="overview-signal-title">이번 프로젝트의 흐름</h2></div></div><div className="schedule-radar"><div><span>전체 일정</span><strong>{dashboard.data.scheduleCount}</strong></div><div><span>확인 대기</span><strong>{dashboard.data.pendingAcknowledgementCount}</strong></div><div><span>Calendar 확인 필요</span><strong>{dashboard.data.calendarRiskCount}</strong></div></div></section>
                </>}
            </>}
        </div>
    </Shell>;
}

const weekdays = ["월", "화", "수", "목", "금", "토", "일"];
const filterOptions = (values: string[], labels: Record<string, string>) => values.map(value => <option key={value} value={value}>{labels[value] ?? "상태 확인 필요"}</option>);

type ScheduleRow = { s: Rows[number]; ack: string };

function eventSpan(schedule: Rows[number], zone: string) {
    const startDay = dateInZone(schedule.startsAt, zone);
    const endDay = dateInZone(new Date(new Date(schedule.endsAt).getTime() - 1).toISOString(), zone);
    return { startDay, endDay };
}

function overlapRow(schedule: Rows[number], day: string, zone: string) {
    return dateInZone(schedule.startsAt, zone) <= day && dateInZone(new Date(new Date(schedule.endsAt).getTime() - 1).toISOString(), zone) >= day;
}

function clockMinutes(instant: string, zone: string) {
    const time = displayTime(instant, zone).slice(11);
    return Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
}

function eventDayLabel(schedule: Rows[number], day: string, zone: string) {
    const { startDay, endDay } = eventSpan(schedule, zone);
    const start = displayTime(schedule.startsAt, zone).slice(11);
    const endDate = dateInZone(schedule.endsAt, zone);
    const end = endDate > endDay && displayTime(schedule.endsAt, zone).slice(11) === "00:00"
        ? "24:00"
        : displayTime(schedule.endsAt, zone).slice(11);
    if (startDay === endDay) return `${start}–${end}`;
    if (day === startDay) return `${start} 시작`;
    if (day === endDay) return `${end} 종료`;
    return "계속";
}

function sortEventsForDay(rows: ScheduleRow[], day: string, zone: string) {
    return [...rows].sort((a, b) => {
        const start = (row: ScheduleRow) => eventSpan(row.s, zone).startDay === day ? clockMinutes(row.s.startsAt, zone) : 0;
        const byTime = start(a) - start(b);
        if (byTime) return byTime;
        const byTitle = a.s.title === b.s.title ? 0 : a.s.title < b.s.title ? -1 : 1;
        return byTitle || (a.s.id === b.s.id ? 0 : a.s.id < b.s.id ? -1 : 1);
    });
}

function eventStatusClass(status: string) {
    if (status === "CONFIRMED") return "month-event--confirmed";
    if (status === "CANCELLED") return "month-event--cancelled";
    return "month-event--draft";
}

type DirectEventProps = {
    schedule: CalendarSchedule;
    day: string;
    zone: string;
    view: "month" | "week";
    projectId: string;
    days: string[];
    disabled?: boolean;
    resizeDisabled?: boolean;
    onPreview: (value: string | null, target?: DirectPreview | null) => void;
    onCommit: (schedule: CalendarSchedule, change: { startsAt: string; endsAt: string }, token: string) => void;
    activeInteraction: React.MutableRefObject<string | null>;
    onInteractionChange: (token: string | null) => void;
};

type DirectPreview = { id: string; view: "month" | "week"; day: string; start: number; end: number };
type Gesture = { x: number; y: number; change: CalendarChange; moved: boolean; origin: LocalPoint; token: string; pointerId: number; capture: HTMLElement | null };

function previewFor(schedule: CalendarSchedule, change: { startsAt: string; endsAt: string }, day: string, view: "month" | "week", zone: string): DirectPreview {
    if (view === "month") return { id: schedule.id, view, day, start: 0, end: 1440 };
    const position = eventDayMinute({ ...schedule, ...change }, day, zone);
    return { id: schedule.id, view, day, start: position.start, end: position.end };
}

function CalendarEvent({ schedule, day, days, zone, view, projectId, disabled, resizeDisabled, onPreview, onCommit, activeInteraction, onInteractionChange }: DirectEventProps) {
    const [gesture, setGesture] = useState<Gesture | null>(null);
    const gestureRef = useRef<Gesture | null>(null);
    const movedRef = useRef(false);
    const span = directEventSpan(schedule, zone);
    const editable = !disabled && schedule.status !== "CANCELLED";
    const startHandle = editable && !resizeDisabled && span.startDay === day;
    const endHandle = editable && !resizeDisabled && span.endDay === day;
    const clear = (pointerId?: number) => {
        const current = gestureRef.current;
        if (current?.capture && pointerId !== undefined) {
            try { current.capture.releasePointerCapture(pointerId); } catch { /* capture may already be released */ }
        }
        if (current && activeInteraction.current === current.token) { activeInteraction.current = null; onInteractionChange(null); }
        gestureRef.current = null;
        setGesture(null);
        onPreview(null, null);
    };
    useEffect(() => { const escape = (event: KeyboardEvent) => { if (event.key === "Escape" && gesture) { event.preventDefault(); clear(); } }; window.addEventListener("keydown", escape); return () => window.removeEventListener("keydown", escape); }, [gesture]);
    useEffect(() => () => { clear(); movedRef.current = false; }, [day, view, zone, onPreview]);
    const cellAt = (event: PointerEvent | React.PointerEvent) => {
        if (typeof document.elementFromPoint !== "function") return undefined;
        const element = document.elementFromPoint(event.clientX, event.clientY) as HTMLElement | null;
        const cell = element?.closest<HTMLElement>("[data-date]");
        return cell?.dataset.date && days.includes(cell.dataset.date) ? cell : undefined;
    };
    const dayAt = (event: PointerEvent | React.PointerEvent) => {
        const cell = cellAt(event);
        return cell?.dataset.date;
    };
    const minuteAt = (event: React.PointerEvent) => {
        const cell = cellAt(event);
        const canvas = cell?.querySelector<HTMLElement>(".day-events");
        const rect = canvas?.getBoundingClientRect();
        if (!rect || !rect.height || event.clientY < rect.top || event.clientY > rect.bottom) return undefined;
        return Math.max(0, Math.min(1440, (event.clientY - rect.top) / rect.height * 1440));
    };
    const begin = (event: React.PointerEvent, change: CalendarChange) => {
        if (!editable || event.pointerType === "touch" || event.button !== 0 || activeInteraction.current) return;
        movedRef.current = false;
        const position = eventDayMinute(schedule, day, zone);
        const originMinute = view === "week" ? minuteAt(event) : position.start;
        if (view === "week" && originMinute === undefined) return;
        const token = `drag:${schedule.id}:${event.pointerId}`;
        const target = event.target instanceof HTMLElement ? event.target.closest<HTMLElement>("a, .calendar-resize-handle") : null;
        const capture = target ?? (event.target === event.currentTarget && event.currentTarget instanceof HTMLElement ? event.currentTarget : null);
        if (capture && "setPointerCapture" in capture) { try { capture.setPointerCapture(event.pointerId); } catch { /* browser may reject a stale pointer */ } }
        const next = { x: event.clientX, y: event.clientY, change, moved: false, origin: { day, minute: originMinute ?? position.start }, token, pointerId: event.pointerId, capture } satisfies Gesture;
        activeInteraction.current = token;
        onInteractionChange(token);
        gestureRef.current = next;
        setGesture(next);
    };
    const move = (event: React.PointerEvent) => {
        const current = gestureRef.current;
        if (!current) return;
        if (!current.moved && Math.hypot(event.clientX - current.x, event.clientY - current.y) <= 6) return;
        if (!current.moved) {
            movedRef.current = true;
            const nextGesture = { ...current, moved: true };
            gestureRef.current = nextGesture;
            setGesture(nextGesture);
        }
        event.preventDefault();
        const targetDay = dayAt(event);
        if (!targetDay) { onPreview(null, null); return; }
        try {
            const next = view === "month"
                ? applyMonthChange(schedule, day, targetDay, current.change, zone)
                : current.change === "move"
                    ? (minuteAt(event) === undefined ? null : applyWeekMove(schedule, current.origin, { day: targetDay, minute: minuteAt(event)! }, zone))
                    : (minuteAt(event) === undefined ? null : applyWeekResize(schedule, { day: targetDay, minute: minuteAt(event)! }, current.change, zone));
            if (!next) { onPreview(null, null); return; }
            if (isValidChange(next, view === "week" && current.change !== "move" ? 15 : 0)) onPreview(previewText(schedule, next, zone), previewFor(schedule, next, targetDay, view, zone));
            else onPreview(null, null);
        } catch { onPreview("이 시간은 시간대 전환 때문에 사용할 수 없습니다. 시간 변경에서 다른 시간을 선택하세요.", null); }
    };
    const finish = (event: React.PointerEvent) => {
        const current = gestureRef.current;
        if (!current) return;
        const moved = current.moved || movedRef.current;
        if (!moved) { clear(event.pointerId); return; }
        const targetDay = dayAt(event);
        if (!targetDay) { clear(event.pointerId); return; }
        if (moved) {
            try {
                const next = view === "month"
                    ? applyMonthChange(schedule, day, targetDay, current.change, zone)
                    : current.change === "move"
                        ? (minuteAt(event) === undefined ? null : applyWeekMove(schedule, current.origin, { day: targetDay, minute: minuteAt(event)! }, zone))
                        : (minuteAt(event) === undefined ? null : applyWeekResize(schedule, { day: targetDay, minute: minuteAt(event)! }, current.change, zone));
                if (next && isValidChange(next, view === "week" && current.change !== "move" ? 15 : 0) && (Date.parse(next.startsAt) !== Date.parse(schedule.startsAt) || Date.parse(next.endsAt) !== Date.parse(schedule.endsAt))) onCommit(schedule, next, current.token);
            } catch { /* invalid local/DST drop is cancelled */ }
            event.preventDefault();
        }
        clear(event.pointerId);
    };
    const cancel = (event: React.PointerEvent | React.MouseEvent) => clear("pointerId" in event ? event.pointerId : undefined);
    const href = `/projects/${projectId}/schedules/${schedule.id}`;
    const label = view === "week" ? `${displayTime(schedule.startsAt, zone).slice(11)}–${displayTime(schedule.endsAt, zone).slice(11)} ${schedule.title}` : `${eventDayLabel(schedule as unknown as Rows[number], day, zone)} · ${schedule.title} · ${statusLabel(schedule.status)}`;
    const weekMinutes = (Date.parse(schedule.endsAt) - Date.parse(schedule.startsAt)) / 60000;
    const cornerGrip = view === "week" && weekMinutes < 30;
    return <div className={`calendar-event-wrapper ${gesture?.moved ? "is-dragging" : ""}`} onPointerDown={event => begin(event, "move")} onPointerMove={move} onPointerUp={finish} onPointerCancel={cancel} onContextMenu={cancel} onClick={event => { if (movedRef.current) { event.preventDefault(); event.stopPropagation(); movedRef.current = false; } }}>
        {startHandle && <span className={`calendar-resize-handle calendar-resize-handle--start ${cornerGrip ? "calendar-resize-handle--corner" : ""}`} aria-hidden="true" onPointerDown={event => { event.stopPropagation(); begin(event, "start"); }} />}
        <a href={`#${href}`} draggable={false} onDragStart={event => { if (gestureRef.current) event.preventDefault(); }} className={`${view === "month" ? `month-event ${eventStatusClass(schedule.status)}` : "week-event-link"}`}>{view === "month" ? <><span>{eventDayLabel(schedule as unknown as Rows[number], day, zone)} · </span><strong>{schedule.title} · </strong><span>{statusLabel(schedule.status)}</span></> : <>{label}<small>{statusLabel(schedule.status)}</small></>}</a>
        {endHandle && <span className={`calendar-resize-handle calendar-resize-handle--end ${cornerGrip ? "calendar-resize-handle--corner" : ""}`} aria-hidden="true" onPointerDown={event => { event.stopPropagation(); begin(event, "end"); }} />}
    </div>;
}

function ScheduleAgenda({ days, zone, rows, canEdit, locked, onEdit }: { days: string[]; zone: string; rows: ScheduleRow[]; canEdit: (schedule: CalendarSchedule) => boolean; locked: (id: string) => boolean; onEdit: (schedule: CalendarSchedule) => void }) {
    const groups = days.map(day => ({ day, events: sortEventsForDay(rows.filter(row => { const span = eventSpan(row.s, zone); return span.startDay <= day && span.endDay >= day; }), day, zone) })).filter(group => group.events.length);
    if (!groups.length) return <p className="schedule-empty">조건에 맞는 일정이 없습니다.</p>;
    return <div className="schedule-agenda" aria-label="날짜별 일정">
        {groups.map(group => <section key={group.day} aria-labelledby={`agenda-${group.day}`}><h3 id={`agenda-${group.day}`}><time dateTime={group.day}>{group.day}</time></h3><ul className="schedule-list">
            {group.events.map(({ s, ack }) => <li key={`${group.day}-${s.id}`}><Link to={`/projects/${s.projectId}/schedules/${s.id}`}><span>{eventDayLabel(s, group.day, zone)} · </span><strong>{s.title} · </strong><span>{statusLabel(s.status)}</span></Link><span>{ackLabel(ack)}</span>{canEdit(s) && <button type="button" aria-label={`시간 변경: ${s.title}`} disabled={locked(s.id)} onClick={() => onEdit(s)}>시간 변경</button>}</li>)}
        </ul></section>)}
    </div>;
}

export function Schedules({ id }: { id: string }) {
    const project = useProject(id);
    const me = useMe();
    const queryClient = useQueryClient();
    const [view, setView] = useState<"month" | "week">("month");
    const [anchor, setAnchor] = useState(() => dateInZone(new Date().toISOString(), "Asia/Seoul"));
    const [zoneInput, setZone] = useState("Asia/Seoul");
    const zone = validZone(zoneInput) ? zoneInput : "Asia/Seoul";
    const [page, setPage] = useState(0);
    const [text, setText] = useState("");
    const [status, setStatus] = useState("ALL");
    const [ack, setAck] = useState("ALL");
    const [mine, setMine] = useState(false);
    const [after, setAfter] = useState("");
    const [before, setBefore] = useState("");
    const [dialogSchedule, setDialogSchedule] = useState<CalendarSchedule | null>(null);
    const [dialogStart, setDialogStart] = useState("");
    const [dialogEnd, setDialogEnd] = useState("");
    const [dialogError, setDialogError] = useState<string | null>(null);
    const [pendingId, setPendingId] = useState<string | null>(null);
    const [lockedIds, setLockedIds] = useState<Set<string>>(() => new Set());
    const [overrides, setOverrides] = useState<Record<string, Rows[number]>>({});
    const [feedback, setFeedback] = useState<{ kind: "status" | "success" | "error"; text: string } | null>(null);
    const [recoverySchedule, setRecoverySchedule] = useState<CalendarSchedule | null>(null);
    const [recoveryIntent, setRecoveryIntent] = useState<{ startsAt: string; endsAt: string } | null>(null);
    const [preview, setPreview] = useState<string | null>(null);
    const [previewTarget, setPreviewTarget] = useState<DirectPreview | null>(null);
    const [activeToken, setActiveToken] = useState<string | null>(null);
    const [recoveringId, setRecoveringId] = useState<string | null>(null);
    const activeInteraction = useRef<string | null>(null);
    const dialogOriginal = useRef<{ startsAt: string; endsAt: string; startLocal: string; endLocal: string } | null>(null);
    const range = useMemo(() => viewWindow(anchor, view, zone), [anchor, view, zone]);
    const effectiveRange = useMemo(() => {
        if (!after && !before) return { from: range.from, to: range.to, error: "" };
        if (!after || !before) return { from: "", to: "", error: "시작일과 종료일을 모두 입력하세요." };
        if (before < after) return { from: "", to: "", error: "종료일은 시작일보다 빠를 수 없습니다." };
        try { return { from: civilDateBoundary(after, zone), to: civilDateBoundary(shiftDate(before, 1), zone), error: "" }; }
        catch (error) { return { from: "", to: "", error: error instanceof Error ? error.message : "날짜 범위를 확인하세요." }; }
    }, [after, before, range.from, range.to, zone]);
    const list = useQuery({ queryKey: [...keys.schedules(id), effectiveRange.from, effectiveRange.to, page], queryFn: () => api.schedules(id, effectiveRange.from, effectiveRange.to, page), enabled: project.isSuccess && !!project.data && !effectiveRange.error });
    const rows = useMemo(() => effectiveRange.error || !project.isSuccess || !project.data || !list.isSuccess ? [] : list.data.map(row => overrides[row.id] ?? row), [effectiveRange.error, project.isSuccess, project.data, list.isSuccess, list.data, overrides]);
    const enriched = useMemo<ScheduleRow[]>(() => rows.map(schedule => ({ s: schedule, ack: acknowledgement(schedule, me.data!.id) })), [rows, me.data!.id]);
    const filtered = useMemo(() => enriched.filter(row => (!text || row.s.title.toLocaleLowerCase().includes(text.toLocaleLowerCase()) || row.s.description?.toLocaleLowerCase().includes(text.toLocaleLowerCase())) && (status === "ALL" || row.s.status === status) && (ack === "ALL" || row.ack === ack) && (!mine || row.s.createdBy === me.data!.id)), [enriched, text, status, ack, mine, me.data!.id]);
    const eventsByDay = useMemo(() => new Map(range.days.map(day => [day, filtered.filter(row => overlapRow(row.s, day, zone))])), [filtered, range.days, zone]);
    const weekLayouts = useMemo(() => weekLaneLayout(filtered.map(row => row.s), range.days, zone), [range.days, zone, filtered]);
    const reportPreview = useCallback((value: string | null, target?: DirectPreview | null) => {
        setPreview(previous => previous === value ? previous : value);
        setPreviewTarget(previous => previous && target && previous.id === target.id && previous.view === target.view && previous.day === target.day && previous.start === target.start && previous.end === target.end ? previous : (target ?? null));
    }, []);
    const contextKey = `${id}|${view}|${anchor}|${zone}|${text}|${status}|${ack}|${mine}|${after}|${before}`;
    const priorContext = useRef(contextKey);
    useEffect(() => {
        if (priorContext.current === contextKey) return;
        priorContext.current = contextKey;
        if (activeInteraction.current?.startsWith("drag:") || activeInteraction.current?.startsWith("dialog:")) { activeInteraction.current = null; setActiveToken(null); }
        setPreview(null); setPreviewTarget(null); setDialogError(null);
        if (dialogSchedule) { dialogOriginal.current = null; setDialogSchedule(null); }
    }, [contextKey, dialogSchedule]);
    useEffect(() => {
        if (!list.isSuccess) return;
        setOverrides(previous => {
            const next = { ...previous };
            let changed = false;
            for (const row of list.data) if (next[row.id] && row.rowVersion >= next[row.id].rowVersion) { delete next[row.id]; changed = true; }
            return changed ? next : previous;
        });
    }, [list.data, list.isSuccess]);
    const locked = (scheduleId: string) => pendingId !== null || lockedIds.has(scheduleId) || recoveringId !== null || activeToken !== null;
    const directLocked = (scheduleId: string) => pendingId !== null || lockedIds.has(scheduleId) || recoveringId !== null || (activeToken !== null && !activeToken.startsWith(`drag:${scheduleId}:`));
    const canEdit = (schedule: CalendarSchedule) => !!me.data && !!project.data && capabilities(project.data, me.data.id, schedule as unknown as Parameters<typeof capabilities>[2]).edit;
    const recover = async (schedule: CalendarSchedule, context: ReturnType<typeof captureSession>, message: string, intended?: { startsAt: string; endsAt: string }) => {
        setRecoverySchedule(schedule);
        if (intended) setRecoveryIntent(intended);
        const intent = intended ?? recoveryIntent;
        setLockedIds(previous => new Set(previous).add(schedule.id));
        setRecoveringId(schedule.id);
        try {
            const [latest, membership] = await Promise.allSettled([accessRead(accessKey("schedule-write", id, schedule.id), () => api.detail(id, schedule.id)), project.refetch()]);
            if (!isSessionContextActive(context)) return;
            await list.refetch();
            if (!isSessionContextActive(context)) return;
            if (latest.status === "fulfilled" && membership.status === "fulfilled" && membership.value.isSuccess && membership.value.data) {
                setOverrides(previous => ({ ...previous, [schedule.id]: latest.value as unknown as Rows[number] }));
                setLockedIds(previous => { const next = new Set(previous); next.delete(schedule.id); return next; });
                clearAccessDenial(accessKey("schedule-write", id, schedule.id));
                setRecoverySchedule(null);
                const matches = intent && Date.parse(intent.startsAt) === Date.parse(latest.value.startsAt) && Date.parse(intent.endsAt) === Date.parse(latest.value.endsAt);
                setFeedback({ kind: "error", text: matches ? `서버에 변경이 반영되었습니다. 현재 저장된 시간은 ${previewText(latest.value, latest.value, zone)}입니다.` : `${message} 현재 저장된 시간은 ${previewText(latest.value, latest.value, zone)}입니다.` });
            } else setFeedback({ kind: "error", text: `${message} 현재 권한과 일정 상태를 확인할 수 없어 편집을 잠갔습니다.` });
        } finally {
            if (isSessionContextActive(context)) setRecoveringId(previous => previous === schedule.id ? null : previous);
        }
    };
    const saveChange = async (schedule: CalendarSchedule, change: { startsAt: string; endsAt: string }, ownerToken?: string) => {
        if ((activeInteraction.current && activeInteraction.current !== ownerToken) || pendingId || lockedIds.has(schedule.id) || !project.data || !me.data || !capabilities(project.data, me.data.id, schedule as unknown as Parameters<typeof capabilities>[2]).edit) return;
        if (Date.parse(change.startsAt) === Date.parse(schedule.startsAt) && Date.parse(change.endsAt) === Date.parse(schedule.endsAt)) {
            if (ownerToken && activeInteraction.current === ownerToken) { activeInteraction.current = null; setActiveToken(null); }
            return;
        }
        const writeToken = `write:${schedule.id}:${Date.now()}`;
        activeInteraction.current = writeToken; setActiveToken(writeToken);
        const context = captureSession(); setPendingId(schedule.id); setFeedback({ kind: "status", text: `${schedule.title} 저장 중…` });
        try {
            const updated = await api.edit(id, schedule.id, editBody(schedule, change));
            if (!isSessionContextActive(context)) return;
            setOverrides(previous => ({ ...previous, [schedule.id]: updated as unknown as Rows[number] }));
            await refreshSchedule(queryClient, id, schedule.id);
            if (!isSessionContextActive(context)) return;
            setOverrides(previous => { const next = { ...previous }; delete next[schedule.id]; return next; });
            setFeedback({ kind: "success", text: `${schedule.title} 일정을 ${previewText(schedule, change, zone)}으로 저장했습니다.` });
        } catch (error) {
            if (!isSessionContextActive(context)) return;
            setPendingId(null);
            if (error instanceof ApiError && error.status >= 400 && error.status < 500 && error.status !== 409) {
                setRecoverySchedule(schedule); setRecoveryIntent(null);
                setFeedback({ kind: "error", text: error.status === 403 ? "일정을 변경할 권한이 없습니다." : (error.problem.code ?? "일정 변경이 거부되었습니다.") });
                if (error.status === 403) { recordAccessDenial(accessKey("schedule-write", id, schedule.id)); setLockedIds(previous => new Set(previous).add(schedule.id)); setRecoverySchedule(schedule); }
            } else await recover(schedule, context, error instanceof ApiError && error.status === 409 ? "다른 사용자가 이 일정을 변경했습니다." : "저장 결과를 확인하지 못했습니다.", change);
        } finally { if (isSessionContextActive(context)) setPendingId(null); if (activeInteraction.current === writeToken) { activeInteraction.current = null; setActiveToken(null); } }
    };
    const retryRecovery = () => { if (recoverySchedule) void recover(recoverySchedule, captureSession(), "일정 상태를 다시 확인했습니다.", recoveryIntent ?? undefined); };
    const closeDialog = () => { if (activeInteraction.current?.startsWith("dialog:")) { activeInteraction.current = null; setActiveToken(null); } dialogOriginal.current = null; setDialogSchedule(null); setDialogError(null); };
    const openEdit = (schedule: CalendarSchedule) => {
        if (!canEdit(schedule) || locked(schedule.id) || activeInteraction.current) return;
        const startLocal = utcToLocalDateTime(schedule.startsAt, zone); const endLocal = utcToLocalDateTime(schedule.endsAt, zone);
        dialogOriginal.current = { startsAt: schedule.startsAt, endsAt: schedule.endsAt, startLocal, endLocal };
        activeInteraction.current = `dialog:${schedule.id}`; setActiveToken(activeInteraction.current);
        setDialogSchedule(schedule); setDialogStart(startLocal); setDialogEnd(endLocal); setDialogError(null); setFeedback(null);
    };
    const submitDialog = () => {
        if (!dialogSchedule) return;
        try {
            const original = dialogOriginal.current;
            const toUtc = (value: string, originalLocal: string | undefined, originalInstant: string | undefined) => {
                if (originalLocal && originalInstant && value === originalLocal) return originalInstant;
                return localDateTimeToUtc(value, zone);
            };
            const change = { startsAt: toUtc(dialogStart, original?.startLocal, original?.startsAt), endsAt: toUtc(dialogEnd, original?.endLocal, original?.endsAt) };
            if (!isValidChange(change)) { setDialogError("종료 시간은 시작 시간보다 뒤여야 합니다."); return; }
            setDialogError(null); setDialogSchedule(null); void saveChange(dialogSchedule, change, activeInteraction.current ?? undefined);
        } catch (error) { setDialogError(error instanceof Error ? error.message : "시간을 확인하세요."); }
    };
    if (!project.isSuccess) return <Shell><QueryState query={project} loadingMessage="프로젝트를 불러오는 중입니다." errorMessage="프로젝트를 불러오지 못했습니다." /></Shell>;
    if (!project.data) return <Shell><ProjectMissing onRetry={() => project.refetch()} isFetching={project.isFetching} /></Shell>;
    if (isAccessError(list.error)) return <Shell><h1>일정을 불러올 수 없습니다</h1><QueryState query={list} /></Shell>;
    const move = (value: string) => { if (/^\d{4}-\d{2}-\d{2}$/.test(value)) { setAnchor(value); setPage(0); } };
    const canCreate = list.isSuccess && capabilities(project.data, me.data!.id).create;
    return <Shell project={project.data}>
        <div className="schedule-screen schedule-listing">
            <header className="schedule-heading"><div><p className="eyebrow">{project.data.name}</p><h1>프로젝트 일정</h1><p className="lead">기간과 확인 상태를 살펴보고 일정 상세에서 필요한 작업을 처리하세요.</p></div><div className="schedule-actions">{canCreate && <Link className="button button-primary" to={`/projects/${id}/schedules/new`}>일정 만들기</Link>}</div></header>
            <section className="schedule-period" aria-label="일정 기간 탐색"><div className="schedule-view-toggle" role="group" aria-label="일정 보기"><button type="button" aria-pressed={view === "month"} onClick={() => { setView("month"); setPage(0); }}>월간 보기</button><button type="button" aria-pressed={view === "week"} onClick={() => { setView("week"); setPage(0); }}>주간 보기</button></div><div className="schedule-period-controls"><button type="button" aria-label="이전 기간" onClick={() => move(navigateDate(anchor, view, -1))}>이전 기간</button><label>기준 날짜<input type="date" value={anchor} onChange={event => move(event.target.value)} /></label><button type="button" aria-label="다음 기간" onClick={() => move(navigateDate(anchor, view, 1))}>다음 기간</button></div></section>
            {!validZone(zoneInput) && <p role="alert" id="schedule-zone-error" className="schedule-inline-alert">지원하지 않는 IANA 시간대입니다. Asia/Seoul로 표시합니다.</p>}
            <details className="schedule-filters"><summary>상세 필터 <span>검색·상태·확인·날짜 범위</span></summary><div className="schedule-filter-grid"><label>검색<input value={text} onChange={event => { setText(event.target.value); setPage(0); }} /></label><label>표시 시간대<input value={zoneInput} aria-invalid={!validZone(zoneInput)} aria-describedby={!validZone(zoneInput) ? "schedule-zone-error" : undefined} onChange={event => { setZone(event.target.value); setPage(0); }} /></label><label>날짜 이후<input type="date" value={after} aria-invalid={!!effectiveRange.error} aria-describedby={effectiveRange.error ? "schedule-range-error" : "schedule-range-help"} onChange={event => { setAfter(event.target.value); setPage(0); }} /></label><label>날짜 이전<input type="date" value={before} aria-invalid={!!effectiveRange.error} aria-describedby={effectiveRange.error ? "schedule-range-error" : "schedule-range-help"} onChange={event => { setBefore(event.target.value); setPage(0); }} /></label><label>상태<select value={status} onChange={event => setStatus(event.target.value)}>{filterOptions(["ALL", "DRAFT", "CONFIRMED", "CANCELLED"], statusLabels)}</select></label><label>확인 상태<select value={ack} onChange={event => setAck(event.target.value)}>{filterOptions(["ALL", "PENDING", "ACKNOWLEDGED", "NOT_REQUIRED"], ackLabels)}</select></label><label className="schedule-check"><input type="checkbox" checked={mine} onChange={event => setMine(event.target.checked)} />내가 만든 일정</label></div></details>
            <p id="schedule-range-help" className="schedule-help">현재 페이지의 최대 {PAGE_SIZE}개 일정에 필터를 적용합니다. 날짜 범위는 양 끝 날짜를 포함합니다.</p>
            {effectiveRange.error ? <p role="alert" id="schedule-range-error" className="schedule-inline-alert">{effectiveRange.error}</p> : <QueryState query={list} />}
            {list.isSuccess && !effectiveRange.error && <>
                <div className="schedule-period-summary"><div><p className="eyebrow">현재 범위</p><h2>{view === "month" ? "월간 일정" : "주간 일정"}</h2><p>{range.days[0]} ~ {range.days[range.days.length - 1]} · {zone}</p></div>{after && before && <p>조회 기간: {after} ~ {before} · 목록은 조회 기간 전체를 검색합니다.</p>}</div>
                <p className="schedule-direct-preview" aria-live="polite">{preview ?? " "}</p>
                {feedback && <div role={feedback.kind === "error" ? "alert" : undefined} className={`schedule-direct-feedback schedule-direct-feedback--${feedback.kind}`} aria-live="polite"><p>{feedback.text}</p>{recoverySchedule && feedback.kind === "error" && <><button type="button" disabled={recoveringId !== null} onClick={retryRecovery}>접근 상태 다시 확인</button><Link to={`/projects/${recoverySchedule.projectId}/schedules/${recoverySchedule.id}`}>일정 상세에서 수정</Link></>}</div>}
                <ScheduleAgenda days={range.days} zone={zone} rows={filtered} canEdit={canEdit} locked={locked} onEdit={openEdit} />
                <div className={`schedule-calendar ${view === "week" ? "schedule-calendar--week" : ""}`} style={view === "week" ? ({ "--calendar-content-width": `${Math.max(876, (Math.max(120, weekLayouts.globalLaneCount * 44) + 24) * 7 + 36)}px` } as React.CSSProperties) : undefined} aria-label={view === "month" ? "월간 일정" : "주간 일정"} role="grid"><div className="schedule-weekday-row">{weekdays.map(day => <div role="columnheader" key={day}>{day}</div>)}</div><div className={view === "month" ? "month-grid" : "week-grid"}>{range.days.map(day => { const events = eventsByDay.get(day) ?? []; const layout = view === "week" ? weekLayouts.byDay.find(item => item.day === day) : null; const ghost = previewTarget?.id && previewTarget.view === view && previewTarget.day === day; return <div role="gridcell" data-date={day} aria-label={day} key={day}><time dateTime={day}>{day.slice(5)}</time>{view === "month" ? <div className="month-events">{ghost && <div className="calendar-direct-ghost calendar-direct-ghost--month" aria-hidden="true"><span>미리 보기</span></div>}{sortEventsForDay(events, day, zone).map(row => <CalendarEvent key={row.s.id} schedule={row.s} day={day} days={range.days} zone={zone} view="month" projectId={id} activeInteraction={activeInteraction} onInteractionChange={setActiveToken} disabled={!canEdit(row.s) || directLocked(row.s.id)} onPreview={reportPreview} onCommit={saveChange} />)}</div> : <div className="day-events">{Array.from({ length: 24 }, (_, hour) => <span aria-hidden="true" className="hour-label" key={hour} style={{ top: `${hour / 24 * 100}%` }}>{String(hour).padStart(2, "0")}:00</span>)}{ghost && <div className="calendar-direct-ghost" aria-hidden="true" style={{ top: `${(previewTarget!.start / 1440) * 100}%`, height: `${Math.max(0, previewTarget!.end - previewTarget!.start) / 1440 * 100}%` }}><span>미리 보기</span></div>}{layout?.occurrences.map(({ schedule, start, end, lane }) => <div className="week-event" key={schedule.id} data-start-minute={start} data-duration-minute={Math.max(0, end - start)} style={{ top: `${start / 1440 * 100}%`, height: `${Math.max(0, end - start) / 1440 * 100}%`, left: `${lane / Math.max(1, weekLayouts.globalLaneCount) * 100}%`, width: `${100 / Math.max(1, weekLayouts.globalLaneCount)}%` }}><CalendarEvent schedule={schedule} day={day} days={range.days} zone={zone} view="week" projectId={id} activeInteraction={activeInteraction} onInteractionChange={setActiveToken} disabled={!canEdit(schedule) || directLocked(schedule.id)} resizeDisabled={(end - start) < 15} onPreview={reportPreview} onCommit={saveChange} /></div>)}</div>}</div>; })}</div></div>
                <section className="schedule-results" aria-labelledby="schedule-results-title"><div className="section-heading"><div><p className="eyebrow">현재 페이지</p><h2 id="schedule-results-title">일정 목록</h2></div><span className="schedule-help">{filtered.length}건 표시</span></div>{!filtered.length && <p className="schedule-empty">조건에 맞는 일정이 없습니다.</p>}<ul className="schedule-list">{filtered.map(row => <li key={row.s.id}><Link to={`/projects/${id}/schedules/${row.s.id}`}>{row.s.title}</Link><div><time dateTime={row.s.startsAt}>{displayTime(row.s.startsAt, zone)}</time><span>~ {displayTime(row.s.endsAt, zone)}</span></div><span>{statusLabel(row.s.status)}</span><span>{ackLabel(row.ack)}</span>{canEdit(row.s) && <button type="button" aria-label={`시간 변경: ${row.s.title}`} disabled={locked(row.s.id)} onClick={() => openEdit(row.s)}>시간 변경</button>}</li>)}</ul></section>
                <nav className="schedule-pagination" aria-label="일정 페이지 이동"><span>현재 {page + 1}페이지</span><button type="button" disabled={!page || list.isFetching} onClick={() => setPage(page - 1)}>이전 일정 페이지</button><button type="button" disabled={rows.length < PAGE_SIZE || list.isFetching || page >= 10000} onClick={() => setPage(page + 1)}>다음 일정 페이지</button></nav>
            </>}
        </div>
        <Dialog open={!!dialogSchedule} title="시간 변경" labelledBy="schedule-time-dialog-title" onClose={closeDialog}>
            <form onSubmit={event => { event.preventDefault(); submitDialog(); }}>
                <p className="schedule-dialog-zone">표시 시간대: {zone}</p>
                <label>시작<input type="datetime-local" value={dialogStart} onChange={event => setDialogStart(event.target.value)} /></label>
                <label>종료<input type="datetime-local" value={dialogEnd} onChange={event => setDialogEnd(event.target.value)} /></label>
                {dialogError && <p id="schedule-dialog-error" role="alert" className="schedule-inline-alert">{dialogError}</p>}
                <div className="schedule-form-actions"><button type="button" onClick={closeDialog}>취소</button><button type="submit" className="button-primary" disabled={!!pendingId}>변경 저장</button></div>
            </form>
        </Dialog>
    </Shell>;
}
