import { useMemo, useState } from "react";
import { useQueries, useQuery } from "@tanstack/react-query";
import { api, PAGE_SIZE, type Schedules as Rows, type Dashboard as DashboardData } from "../api/client";
import { acknowledgement, calendarStatus, capabilities, keys, useMe, useProject } from "../state";
import { Link, ProjectMissing, QueryState, Shell } from "../ui";
import { civilDateBoundary, dateInZone, displayTime, navigateDate, shiftDate, validZone, viewWindow } from "../time";
function TimedList({ rows }: {
    rows: DashboardData["upcomingSchedules"];
}) {
    return rows.length ? <ul className="schedule-list">{rows.map(s => <li key={s.id}><Link to={`/projects/${s.projectId}/schedules/${s.id}`}>{s.title}</Link><span>{s.status}</span><time dateTime={s.startsAt}>{displayTime(s.startsAt)}</time><span>~</span><time dateTime={s.endsAt}>{displayTime(s.endsAt)}</time></li>)}</ul> : <p>예정된 일정이 없습니다.</p>;
}
export function Dashboard({ id }: {
    id: string;
}) {
    const project = useProject(id);
    const me = useMe();
    const dashboard = useQuery({ queryKey: keys.dashboard(id), queryFn: () => api.dashboard(id), enabled: !!project.data });
    if (!project.isSuccess)
        return <Shell><QueryState query={project}/></Shell>;
    if (!project.data)
        return <Shell><ProjectMissing /></Shell>;
    return <Shell project={project.data}><h1>{project.data.name} 대시보드</h1><QueryState query={dashboard}/>{dashboard.isSuccess && <><div className="radar"><strong>일정 {dashboard.data.scheduleCount}</strong><strong>확인 대기 {dashboard.data.pendingAcknowledgementCount}</strong><strong>Calendar 위험 {dashboard.data.calendarRiskCount}</strong></div><h2>2주 일정</h2><TimedList rows={dashboard.data.upcomingSchedules}/><h2>처리 대기</h2>{dashboard.data.actionQueue.length ? <TimedList rows={dashboard.data.actionQueue}/> : <p>처리할 항목이 없습니다.</p>}</>}{capabilities(project.data, me.data!.id).create && <Link to={`/projects/${id}/schedules/new`}>일정 만들기</Link>}{project.data.role === "MANAGER" && <Link to={`/projects/${id}/invitations/new`}>구성원 초대</Link>}</Shell>;
}
const weekdays = ["월", "화", "수", "목", "금", "토", "일"];
function eventPosition(s: Rows[number], day: string, zone: string) {
    const clock = (instant: string) => { const time = displayTime(instant, zone).slice(11); return Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5)); };
    const start = dateInZone(s.startsAt, zone) < day ? 0 : clock(s.startsAt);
    const end = dateInZone(s.endsAt, zone) > day ? 1440 : clock(s.endsAt);
    return { start, duration: Math.max(1, end - start) };
}
export function Schedules({ id }: {
    id: string;
}) {
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
        if (!after && !before)
            return { from: range.from, to: range.to, error: "" };
        if (!after || !before)
            return { from: "", to: "", error: "시작일과 종료일을 모두 입력하세요." };
        if (before < after)
            return { from: "", to: "", error: "종료일은 시작일보다 빠를 수 없습니다." };
        try {
            return { from: civilDateBoundary(after, zone), to: civilDateBoundary(shiftDate(before, 1), zone), error: "" };
        } catch (error) {
            return { from: "", to: "", error: error instanceof Error ? error.message : "날짜 범위를 확인하세요." };
        }
    }, [after, before, range.from, range.to, zone]);
    const list = useQuery({ queryKey: [...keys.schedules(id), effectiveRange.from, effectiveRange.to, page], queryFn: () => api.schedules(id, effectiveRange.from, effectiveRange.to, page), enabled: !!project.data && !effectiveRange.error });
    const connection = useQuery({ queryKey: keys.connection, queryFn: api.calendar, enabled: !!project.data });
    // The server page is bounded to 20; only that page's projections are observed.
    const rows = effectiveRange.error ? [] : list.data ?? [];
    const projections = useQueries({ queries: rows.slice(0, PAGE_SIZE).map(s => ({ queryKey: keys.projection(id, s.id), queryFn: () => api.projection(id, s.id), staleTime: 30000 })) });
    const enriched = rows.map((s, i) => ({ s, projection: projections[i], ack: acknowledgement(s, me.data!.id), calendar: calendarStatus(connection.data, projections[i]?.data) }));
    const filtered = enriched.filter(r => (!text || r.s.title.toLocaleLowerCase().includes(text.toLocaleLowerCase()) || r.s.description?.toLocaleLowerCase().includes(text.toLocaleLowerCase())) && (status === "ALL" || r.s.status === status) && (ack === "ALL" || r.ack === ack) && (calendar === "ALL" || r.calendar === calendar) && (!mine || r.s.createdBy === me.data!.id));
    const overlap = (s: Rows[number], day: string) => dateInZone(s.startsAt, zone) <= day && dateInZone(new Date(new Date(s.endsAt).getTime() - 1).toISOString(), zone) >= day;
    if (!project.isSuccess)
        return <Shell><QueryState query={project}/></Shell>;
    if (!project.data)
        return <Shell><ProjectMissing /></Shell>;
    const move = (value: string) => { if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
        setAnchor(value);
        setPage(0);
    } };
    return <Shell project={project.data}><h1>프로젝트 일정</h1><div className="toolbar">
    <button aria-pressed={view === "month"} onClick={() => { setView("month"); setPage(0); }}>월간 보기</button><button aria-pressed={view === "week"} onClick={() => { setView("week"); setPage(0); }}>주간 보기</button>
    <button onClick={() => move(navigateDate(anchor, view, -1))}>이전 기간</button><label>기준 날짜<input type="date" value={anchor} onChange={e => move(e.target.value)}/></label><button onClick={() => move(navigateDate(anchor, view, 1))}>다음 기간</button>
    <label>표시 시간대<input value={zoneInput} onChange={e => { setZone(e.target.value); setPage(0); }}/></label></div>
    {!validZone(zoneInput) && <p role="alert">지원하지 않는 IANA 시간대입니다. Asia/Seoul로 표시합니다.</p>}
    <div className="toolbar"><label>검색<input value={text} onChange={e => setText(e.target.value)}/></label><label>날짜 이후<input type="date" value={after} aria-invalid={!!effectiveRange.error} aria-describedby={effectiveRange.error ? "schedule-range-error" : "schedule-range-help"} onChange={e => { setAfter(e.target.value); setPage(0); }}/></label><label>날짜 이전<input type="date" value={before} aria-invalid={!!effectiveRange.error} aria-describedby={effectiveRange.error ? "schedule-range-error" : "schedule-range-help"} onChange={e => { setBefore(e.target.value); setPage(0); }}/></label>
    <label>상태<select value={status} onChange={e => setStatus(e.target.value)}>{["ALL", "DRAFT", "CONFIRMED", "CANCELLED"].map(v => <option key={v}>{v}</option>)}</select></label>
    <label>확인<select value={ack} onChange={e => setAck(e.target.value)}>{["ALL", "PENDING", "ACKNOWLEDGED", "NOT_REQUIRED"].map(v => <option key={v}>{v}</option>)}</select></label>
    <label>Calendar 상태<select value={calendar} onChange={e => setCalendar(e.target.value)}>{["ALL", "REAUTH_REQUIRED", "FAILED", "PENDING", "SYNCED", "NOT_CONNECTED"].map(v => <option key={v}>{v}</option>)}</select></label><label><input type="checkbox" checked={mine} onChange={e => setMine(e.target.checked)}/>내가 만든 일정</label></div>
    <p id="schedule-range-help">날짜 검색은 시작일과 종료일을 모두 입력하며 양 끝 날짜를 포함합니다. 비워 두면 현재 달력 기간을 조회합니다.</p>
    {effectiveRange.error ? <p role="alert" id="schedule-range-error">{effectiveRange.error}</p> : <QueryState query={list}/>}<QueryState query={connection} label="연결 다시 시도"/>
    {connection.data?.configurationRequired && <p>Calendar 연동이 구성되지 않았습니다.</p>}{connection.data?.status === "REAUTH_REQUIRED" && <Link to="/calendar">Calendar 다시 연결</Link>}
    {enriched.filter(r => r.projection?.isError).map(r => <div key={r.s.id}><p>{r.s.title} Calendar 상태를 불러오지 못했습니다.</p><QueryState query={r.projection} label="투영 다시 시도"/></div>)}
    {projections.some(p => p.isPending) && <p role="status">Calendar 투영을 불러오는 중입니다.</p>}
    {list.isSuccess && !effectiveRange.error && <><h2>{view === "month" ? "월간 신호 보기" : "주간 실제 일정"}</h2><p>{range.days[0]} ~ {shiftDate(range.days[range.days.length - 1], 0)} · {zone}</p>{after && before && <p>조회 기간: {after} ~ {before} · {zone}. 목록은 조회 기간 전체를 검색하며 달력은 기준 날짜가 속한 기간을 표시합니다.</p>}
    <div role="grid" aria-label={view === "month" ? "월간 일정" : "주간 일정"} className={view === "month" ? "month-grid" : "week-grid"}>
      {weekdays.map(d => <div role="columnheader" key={d}>{d}</div>)}
      {range.days.map(day => {
                const events = filtered.filter(r => overlap(r.s, day));
                return <div role="gridcell" data-date={day} aria-label={day} key={day}>
          <time dateTime={day}>{day.slice(5)}</time>
          {view === "month" ? <><span>{events.length}건</span><span>확인 대기 {events.filter(r => r.ack === "PENDING").length}</span><span>Calendar 위험 {events.filter(r => r.calendar === "FAILED" || r.calendar === "REAUTH_REQUIRED").length}</span></> :
                        <div className="day-events">
              {[0, 6, 12, 18].map(hour => <span aria-hidden="true" className="hour-label" key={hour} style={{ top: `${hour / 24 * 100}%` }}>{String(hour).padStart(2, "0")}:00</span>)}
              {events.map((r, index) => {
                                const position = eventPosition(r.s, day, zone);
                                return <div className="week-event" key={r.s.id} data-start-minute={position.start} data-duration-minute={position.duration} style={{ top: `${position.start / 1440 * 100}%`, minHeight: `${position.duration / 1440 * 100}%`, left: `${index / events.length * 100}%`, width: `${100 / events.length}%` }}>
                  <Link to={`/projects/${id}/schedules/${r.s.id}`}>{displayTime(r.s.startsAt, zone).slice(11)}–{displayTime(r.s.endsAt, zone).slice(11)} {r.s.title}</Link><small>{r.s.status}</small>
                </div>;
                            })}
            </div>}
        </div>;
            })}
    </div><h2>일정 목록</h2>{!filtered.length && <p>조건에 맞는 일정이 없습니다.</p>}<ul className="schedule-list">{filtered.map(r => <li key={r.s.id}><Link to={`/projects/${id}/schedules/${r.s.id}`}>{r.s.title}</Link><time dateTime={r.s.startsAt}>{displayTime(r.s.startsAt, zone)}</time><span>~ {displayTime(r.s.endsAt, zone)}</span><span>작성자 {r.s.createdBy}</span><span>상태 {r.s.status}</span><span>확인 {r.ack}</span><span>Calendar {r.calendar ?? (r.projection?.isError || connection.isError ? "불러오기 실패" : "불러오는 중")}</span></li>)}</ul>
    <p>현재 {page + 1}페이지의 최대 {PAGE_SIZE}개 일정에 필터와 달력 신호를 적용합니다.</p><button disabled={!page || list.isFetching} onClick={() => setPage(page - 1)}>이전 일정 페이지</button><button disabled={rows.length < PAGE_SIZE || list.isFetching || page >= 10000} onClick={() => setPage(page + 1)}>다음 일정 페이지</button></>}
    {capabilities(project.data, me.data!.id).create && <Link to={`/projects/${id}/schedules/new`}>일정 만들기</Link>}
  </Shell>;
}
