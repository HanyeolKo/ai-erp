import { useMemo, useState } from "react";
import { useQueries, useQuery } from "@tanstack/react-query";
import { api, PAGE_SIZE, type Dashboard as DashboardData, type Schedules as Rows } from "../api/client";
import { acknowledgement, calendarStatus, capabilities, isAccessError, keys, useMe, useProject } from "../state";
import { Link, ProjectMissing, QueryState, Shell } from "../ui";
import { civilDateBoundary, dateInZone, displayTime, navigateDate, shiftDate, validZone, viewWindow } from "../time";
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
const calendarLabels: Record<string, string> = {
    ALL: "전체 Calendar 상태",
    REAUTH_REQUIRED: "연결 확인 필요",
    FAILED: "동기화 실패",
    PENDING: "동기화 중",
    SYNCED: "동기화 완료",
    NOT_CONNECTED: "연결되지 않음",
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

function eventPosition(schedule: Rows[number], day: string, zone: string) {
    const clock = (instant: string) => {
        const time = displayTime(instant, zone).slice(11);
        return Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
    };
    const start = dateInZone(schedule.startsAt, zone) < day ? 0 : clock(schedule.startsAt);
    const end = dateInZone(schedule.endsAt, zone) > day ? 1440 : clock(schedule.endsAt);
    return { start, duration: Math.max(1, end - start) };
}

function ScheduleAgenda({ days, zone, rows }: { days: string[]; zone: string; rows: Array<{ s: Rows[number]; ack: string; calendar?: string }> }) {
    const groups = days.map(day => ({ day, events: rows.filter(row => dateInZone(row.s.startsAt, zone) <= day && dateInZone(new Date(new Date(row.s.endsAt).getTime() - 1).toISOString(), zone) >= day) })).filter(group => group.events.length);
    if (!groups.length) return <p className="schedule-empty">조건에 맞는 일정이 없습니다.</p>;
    return <div className="schedule-agenda" aria-label="날짜별 일정">
        {groups.map(group => <section key={group.day} aria-labelledby={`agenda-${group.day}`}><h3 id={`agenda-${group.day}`}><time dateTime={group.day}>{group.day}</time></h3><ul className="schedule-list">
            {group.events.map(({ s, ack, calendar }) => <li key={`${group.day}-${s.id}`}><Link to={`/projects/${s.projectId}/schedules/${s.id}`}>{s.title}</Link><span>{displayTime(s.startsAt, zone).slice(11)}–{displayTime(s.endsAt, zone).slice(11)}</span><span>{statusLabel(s.status)}</span><span>{ackLabel(ack)}</span><span>{calendarLabels[calendar ?? ""] ?? "Calendar 확인 필요"}</span></li>)}
        </ul></section>)}
    </div>;
}

export function Schedules({ id }: { id: string }) {
    const project = useProject(id);
    const me = useMe();
    const [view, setView] = useState<"month" | "week">("month");
    const [anchor, setAnchor] = useState(() => dateInZone(new Date().toISOString(), "Asia/Seoul"));
    const [zoneInput, setZone] = useState("Asia/Seoul");
    const zone = validZone(zoneInput) ? zoneInput : "Asia/Seoul";
    const [page, setPage] = useState(0);
    const [text, setText] = useState("");
    const [status, setStatus] = useState("ALL");
    const [ack, setAck] = useState("ALL");
    const [calendar, setCalendar] = useState("ALL");
    const [mine, setMine] = useState(false);
    const [after, setAfter] = useState("");
    const [before, setBefore] = useState("");
    const range = useMemo(() => viewWindow(anchor, view, zone), [anchor, view, zone]);
    const effectiveRange = useMemo(() => {
        if (!after && !before) return { from: range.from, to: range.to, error: "" };
        if (!after || !before) return { from: "", to: "", error: "시작일과 종료일을 모두 입력하세요." };
        if (before < after) return { from: "", to: "", error: "종료일은 시작일보다 빠를 수 없습니다." };
        try { return { from: civilDateBoundary(after, zone), to: civilDateBoundary(shiftDate(before, 1), zone), error: "" }; }
        catch (error) { return { from: "", to: "", error: error instanceof Error ? error.message : "날짜 범위를 확인하세요." }; }
    }, [after, before, range.from, range.to, zone]);
    const list = useQuery({ queryKey: [...keys.schedules(id), effectiveRange.from, effectiveRange.to, page], queryFn: () => api.schedules(id, effectiveRange.from, effectiveRange.to, page), enabled: project.isSuccess && !!project.data && !effectiveRange.error });
    const connection = useQuery({ queryKey: keys.connection, queryFn: api.calendar, enabled: project.isSuccess && !!project.data });
    const rows = effectiveRange.error || !project.isSuccess || !project.data || !list.isSuccess ? [] : list.data;
    const projections = useQueries({ queries: rows.slice(0, PAGE_SIZE).map(schedule => ({ queryKey: keys.projection(id, schedule.id), queryFn: () => api.projection(id, schedule.id), staleTime: 30000 })) });
    const enriched = rows.map((schedule, index) => ({ s: schedule, projection: projections[index], ack: acknowledgement(schedule, me.data!.id), calendar: calendarStatus(connection.data, projections[index]?.data) }));
    const filtered = enriched.filter(row => (!text || row.s.title.toLocaleLowerCase().includes(text.toLocaleLowerCase()) || row.s.description?.toLocaleLowerCase().includes(text.toLocaleLowerCase())) && (status === "ALL" || row.s.status === status) && (ack === "ALL" || row.ack === ack) && (calendar === "ALL" || row.calendar === calendar) && (!mine || row.s.createdBy === me.data!.id));
    const overlap = (schedule: Rows[number], day: string) => dateInZone(schedule.startsAt, zone) <= day && dateInZone(new Date(new Date(schedule.endsAt).getTime() - 1).toISOString(), zone) >= day;
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
            <details className="schedule-filters"><summary>상세 필터 <span>검색·상태·확인·Calendar·날짜 범위</span></summary><div className="schedule-filter-grid"><label>검색<input value={text} onChange={event => { setText(event.target.value); setPage(0); }} /></label><label>표시 시간대<input value={zoneInput} aria-invalid={!validZone(zoneInput)} aria-describedby={!validZone(zoneInput) ? "schedule-zone-error" : undefined} onChange={event => { setZone(event.target.value); setPage(0); }} /></label><label>날짜 이후<input type="date" value={after} aria-invalid={!!effectiveRange.error} aria-describedby={effectiveRange.error ? "schedule-range-error" : "schedule-range-help"} onChange={event => { setAfter(event.target.value); setPage(0); }} /></label><label>날짜 이전<input type="date" value={before} aria-invalid={!!effectiveRange.error} aria-describedby={effectiveRange.error ? "schedule-range-error" : "schedule-range-help"} onChange={event => { setBefore(event.target.value); setPage(0); }} /></label><label>상태<select value={status} onChange={event => setStatus(event.target.value)}>{filterOptions(["ALL", "DRAFT", "CONFIRMED", "CANCELLED"], statusLabels)}</select></label><label>확인 상태<select value={ack} onChange={event => setAck(event.target.value)}>{filterOptions(["ALL", "PENDING", "ACKNOWLEDGED", "NOT_REQUIRED"], ackLabels)}</select></label><label>Calendar 상태<select value={calendar} onChange={event => setCalendar(event.target.value)}>{filterOptions(["ALL", "REAUTH_REQUIRED", "FAILED", "PENDING", "SYNCED", "NOT_CONNECTED"], calendarLabels)}</select></label><label className="schedule-check"><input type="checkbox" checked={mine} onChange={event => setMine(event.target.checked)} />내가 만든 일정</label></div></details>
            <p id="schedule-range-help" className="schedule-help">현재 페이지의 최대 {PAGE_SIZE}개 일정에 필터를 적용합니다. 날짜 범위는 양 끝 날짜를 포함합니다.</p>
            {effectiveRange.error ? <p role="alert" id="schedule-range-error" className="schedule-inline-alert">{effectiveRange.error}</p> : <QueryState query={list} />}<QueryState query={connection} label="연결 다시 시도" />
            {connection.data?.configurationRequired && <p>Calendar 연동이 구성되지 않았습니다.</p>}{connection.data?.status === "REAUTH_REQUIRED" && <Link to="/calendar">Calendar 다시 연결</Link>}
            {enriched.filter(row => row.projection?.isError).map(row => <div key={row.s.id} className="schedule-inline-alert"><p>{row.s.title} Calendar 상태를 불러오지 못했습니다.</p><QueryState query={row.projection} label="투영 다시 시도" /></div>)}
            {projections.some(query => query.isPending) && <p role="status">Calendar 투영을 불러오는 중입니다.</p>}
            {list.isSuccess && !effectiveRange.error && <>
                <div className="schedule-period-summary"><div><p className="eyebrow">현재 범위</p><h2>{view === "month" ? "월간 일정" : "주간 일정"}</h2><p>{range.days[0]} ~ {range.days[range.days.length - 1]} · {zone}</p></div>{after && before && <p>조회 기간: {after} ~ {before} · 목록은 조회 기간 전체를 검색합니다.</p>}</div>
                <ScheduleAgenda days={range.days} zone={zone} rows={filtered} />
                <div className="schedule-calendar" aria-label={view === "month" ? "월간 일정" : "주간 일정"} role="grid"><div className="schedule-weekday-row">{weekdays.map(day => <div role="columnheader" key={day}>{day}</div>)}</div><div className={view === "month" ? "month-grid" : "week-grid"}>{range.days.map(day => { const events = filtered.filter(row => overlap(row.s, day)); return <div role="gridcell" data-date={day} aria-label={day} key={day}><time dateTime={day}>{day.slice(5)}</time>{view === "month" ? <div className="month-signals"><span>{events.length}건</span><span>확인 대기 {events.filter(row => row.ack === "PENDING").length}</span><span>Calendar 위험 {events.filter(row => row.calendar === "FAILED" || row.calendar === "REAUTH_REQUIRED").length}</span></div> : <div className="day-events">{[0, 6, 12, 18].map(hour => <span aria-hidden="true" className="hour-label" key={hour} style={{ top: `${hour / 24 * 100}%` }}>{String(hour).padStart(2, "0")}:00</span>)}{events.map((row, index) => { const position = eventPosition(row.s, day, zone); return <div className="week-event" key={row.s.id} data-start-minute={position.start} data-duration-minute={position.duration} style={{ top: `${position.start / 1440 * 100}%`, minHeight: `${position.duration / 1440 * 100}%`, left: `${index / events.length * 100}%`, width: `${100 / events.length}%` }}><Link to={`/projects/${id}/schedules/${row.s.id}`}>{displayTime(row.s.startsAt, zone).slice(11)}–{displayTime(row.s.endsAt, zone).slice(11)} {row.s.title}</Link><small>{statusLabel(row.s.status)}</small></div>; })}</div>}</div>; })}</div></div>
                <section className="schedule-results" aria-labelledby="schedule-results-title"><div className="section-heading"><div><p className="eyebrow">현재 페이지</p><h2 id="schedule-results-title">일정 목록</h2></div><span className="schedule-help">{filtered.length}건 표시</span></div>{!filtered.length && <p className="schedule-empty">조건에 맞는 일정이 없습니다.</p>}<ul className="schedule-list">{filtered.map(row => <li key={row.s.id}><Link to={`/projects/${id}/schedules/${row.s.id}`}>{row.s.title}</Link><div><time dateTime={row.s.startsAt}>{displayTime(row.s.startsAt, zone)}</time><span>~ {displayTime(row.s.endsAt, zone)}</span></div><span>{statusLabel(row.s.status)}</span><span>{ackLabel(row.ack)}</span><span>{calendarLabels[row.calendar ?? ""] ?? "Calendar 확인 필요"}</span></li>)}</ul></section>
                <nav className="schedule-pagination" aria-label="일정 페이지 이동"><span>현재 {page + 1}페이지</span><button type="button" disabled={!page || list.isFetching} onClick={() => setPage(page - 1)}>이전 일정 페이지</button><button type="button" disabled={rows.length < PAGE_SIZE || list.isFetching || page >= 10000} onClick={() => setPage(page + 1)}>다음 일정 페이지</button></nav>
            </>}
        </div>
    </Shell>;
}
