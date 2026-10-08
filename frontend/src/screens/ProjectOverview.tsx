import { useEffect, useRef, useState, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, api, type Dashboard, type PlanItem, type PlanSnapshot, type Schedules } from "../api/client";
import { accessRead, capabilities, isAccessError, useMe, useProject } from "../state";
import { Link, ProjectMissing, RetryButton, Shell } from "../ui";
import { dateInZone, displayTime, shiftDate } from "../time";
import { currentSessionGeneration } from "../session";
import { overviewContext, overviewNextBoundary, overviewWindow, planProgress, readOverviewSchedules, scheduleOverlaps, selectGoals, selectMilestones, selectMyActiveTasks, selectRisks, updateOverviewContext, type OverviewPeriod } from "../project-overview";
import { ScheduleDashboardSection } from "./ScheduleWorkspace";
import "./project-overview.css";

const dateText = (value: string | null | undefined) => value ? value.slice(0, 10) : "날짜 없음";
const stateLabel: Record<string, string> = { BACKLOG: "대기", READY: "준비", IN_PROGRESS: "진행 중", BLOCKED: "차단됨", DONE: "완료", CANCELLED: "취소" };
const forecastLabel: Record<string, string> = { EMPTY: "예측 없음", UNDATED: "날짜 미정", INCOMPLETE: "일정 일부 미정", COMPLETE: "일정 계획 완료" };
function stateText(state: string) { return stateLabel[state] ?? "상태 확인 필요"; }
function isOverviewProjectAccessError(error: unknown) {
  if (!isAccessError(error) || !(error instanceof ApiError)) return false;
  return error.status === 403 || ["PROJECT_ACCESS_DENIED", "FORBIDDEN", "PROJECT_NOT_FOUND"].includes(error.problem.code ?? "");
}
function scheduleDate(row: Schedules[number], zone: string) { const startDate = dateInZone(row.startsAt, zone); const endDate = dateInZone(row.endsAt, zone); return `${startDate} ${displayTime(row.startsAt, zone).slice(11)} – ${endDate !== startDate ? `${endDate} ` : ""}${displayTime(row.endsAt, zone).slice(11)}`; }
function sectionCount(value: number, complete: boolean) { return complete ? `${value}건` : `${value}건 불러옴`; }

function ErrorState({ message, onRetry, fetching = false }: { message: string; onRetry?: () => unknown; fetching?: boolean }) {
  return <div className="overview-state" role="alert"><p>{message}</p>{onRetry && <RetryButton onRetry={onRetry} isFetching={fetching}>다시 시도</RetryButton>}</div>;
}
function PlanState({ query, children }: { query: { isPending: boolean; isError: boolean; error: unknown; isFetching: boolean; refetch: () => unknown; data?: PlanSnapshot }; children: (value: PlanSnapshot) => ReactNode }) {
  if (query.isPending) return <p className="overview-state" role="status">계획을 불러오는 중입니다.</p>;
  if (query.isError && !query.data) return <ErrorState message="계획을 불러오지 못했습니다." onRetry={() => query.refetch()} fetching={query.isFetching} />;
  return <>{query.isError && <ErrorState message="계획을 새로 고치지 못했습니다. 표시 중인 계획은 이전 읽기 결과입니다." onRetry={() => query.refetch()} fetching={query.isFetching} />}{query.isFetching && query.data && <p className="overview-state" role="status">계획을 새로 고치는 중입니다.</p>}{children(query.data as PlanSnapshot)}</>;
}

function ProgressSection({ snapshot }: { snapshot: PlanSnapshot }) {
  const progress = planProgress(snapshot);
  const summary = snapshot.summary;
  const comparison = !snapshot.targetStart && !snapshot.targetEnd ? "비교할 수동 목표 없음" : !summary.forecastStart && !summary.forecastEnd ? "비교할 예측 기간 없음" : summary.outsideTarget ? `수동 목표 범위 밖 (outsideTarget)${summary.forecastState === "INCOMPLETE" ? " · 예측 일정 일부 미정" : ""}` : summary.forecastState === "INCOMPLETE" ? "예측 일정 일부 미정" : "수동 목표 안";
  return <section className="overview-progress" aria-labelledby="overview-progress-title">
    <div className="overview-section-heading"><div><p className="eyebrow">전체 계획</p><h2 id="overview-progress-title">프로젝트 진척</h2></div></div>
    <div className="overview-progress-main">
      <div><strong className="overview-ratio">{progress.empty ? "작업 없음" : progress.percent === null ? (snapshot.complete ? `${progress.doneCount}/${progress.taskCount}` : `${progress.doneCount}/${progress.taskCount}건 불러옴`) : `${progress.doneCount}/${progress.taskCount}`}</strong>{progress.percent !== null && <><span className="overview-percent-separator" aria-hidden="true">·</span><span className="overview-percent">{summary.progressPercent}%</span></>}<span className="overview-progress-label">작업 완료율 · 모든 작업 동일 비중 · 취소 제외</span></div>
      {!snapshot.complete && <p className="overview-partial">일부 계획만 불러왔습니다. 비율은 표시하지 않습니다.</p>}
      {progress.empty && <p className="overview-empty">작업이 없어 완료율을 계산할 수 없습니다.</p>}
      <p className="overview-as-of">계산 기준일: {snapshot.asOfDate}</p>
    </div>
    <dl className="overview-facts"><div><dt>수동 목표</dt><dd>{snapshot.targetStart || snapshot.targetEnd ? `${dateText(snapshot.targetStart)} – ${dateText(snapshot.targetEnd)}` : "목표 날짜 없음"}</dd></div><div><dt>예측</dt><dd>{forecastLabel[summary.forecastState] ?? summary.forecastState}{summary.forecastStart || summary.forecastEnd ? ` · 알려진 기간 ${dateText(summary.forecastStart)} – ${dateText(summary.forecastEnd)}` : " · 알려진 날짜 범위 없음"}</dd></div><div><dt>목표 비교</dt><dd>{comparison}</dd></div></dl>
  </section>;
}

function PlanList({ snapshot, projectId }: { snapshot: PlanSnapshot; projectId: string }) {
  const goals = selectGoals(snapshot); const risks = selectRisks(snapshot); const milestones = selectMilestones(snapshot);
  const goalCount = selectGoals(snapshot, Number.MAX_SAFE_INTEGER).length;
  const riskCount = selectRisks(snapshot, Number.MAX_SAFE_INTEGER).length;
  const milestoneCount = selectMilestones(snapshot, Number.MAX_SAFE_INTEGER).length;
  const list = (items: PlanItem[], render: (item: PlanItem & { path?: string[]; reasons?: string[]; date?: string | null; blocked?: boolean; overdue?: boolean }) => ReactNode, empty: string) => items.length ? <ul className="overview-list">{items.map(item => <li key={item.id}>{render(item as PlanItem & { path?: string[]; reasons?: string[]; date?: string | null; blocked?: boolean; overdue?: boolean })}</li>)}</ul> : <p className="overview-empty">{empty}</p>;
  return <>
    <section className="overview-domain-grid" aria-label="계획 요약">
      <section aria-labelledby="overview-goals-title"><div className="overview-section-heading"><div><p className="eyebrow">핵심 계획</p><h2 id="overview-goals-title">목표 <span className="overview-count">{sectionCount(goalCount, snapshot.complete)}</span></h2></div><Link to={`/projects/${projectId}/plan?view=hierarchy&types=EPIC,TOPIC`}>계획 계층 열기</Link></div>{list(goals, item => <><strong>{item.title}</strong><span className="overview-meta">작업 {item.summary.doneCount}/{item.summary.taskCount}{item.blocked ? " · 차단된 하위 작업" : ""}{item.overdue ? " · 기한 지난 하위 작업" : ""}</span></>, "표시할 목표가 없습니다.")}</section>
      <section aria-labelledby="overview-risks-title"><div className="overview-section-heading"><div><p className="eyebrow">주의할 작업</p><h2 id="overview-risks-title">확인이 필요한 작업 <span className="overview-count">{sectionCount(riskCount, snapshot.complete)}</span></h2></div><Link to={`/projects/${projectId}/plan?view=tasks&types=TASK`}>모든 작업 열기</Link></div>{list(risks, item => <><strong>{item.title}</strong><span className="overview-meta">{[...(item.reasons ?? []), stateText(item.state), dateText(item.deadline ?? item.targetEnd)].filter(Boolean).filter((value, index, values) => values.indexOf(value) === index).join(" · ")}</span><span className="overview-path">{item.path?.join(" / ") || "상위 경로 없음"}</span></>, "현재 확인할 위험 작업이 없습니다.")}</section>
      <section aria-labelledby="overview-milestones-title"><div className="overview-section-heading"><div><p className="eyebrow">주요 날짜</p><h2 id="overview-milestones-title">마일스톤 <span className="overview-count">{sectionCount(milestoneCount, snapshot.complete)}</span></h2></div><Link to={`/projects/${projectId}/plan?view=milestones&types=MILESTONE`}>마일스톤 열기</Link></div>{list(milestones, item => <><strong>{item.title}</strong><span className="overview-meta">{stateText(item.state)} · {item.date ? dateText(item.date) : "날짜 없음"}</span></>, "표시할 마일스톤이 없습니다.")}</section>
    </section>
  </>;
}

function MyTasks({ snapshot, projectId, userId }: { snapshot: PlanSnapshot; projectId: string; userId: string }) {
  const tasks = selectMyActiveTasks(snapshot, userId);
  const taskCount = selectMyActiveTasks(snapshot, userId, Number.MAX_SAFE_INTEGER).length;
  return <section aria-labelledby="overview-my-tasks-title"><div className="overview-section-heading"><div><p className="eyebrow">내 작업</p><h2 id="overview-my-tasks-title">내 할 일 <span className="overview-count">{sectionCount(taskCount, snapshot.complete)}</span></h2></div><Link to={`/projects/${projectId}/plan?view=tasks&types=TASK&assigneeId=${encodeURIComponent(userId)}`}>내 담당 작업 모두 열기</Link></div>{tasks.length ? <ul className="overview-list">{tasks.map(task => <li key={task.id}><strong>{task.title}</strong><span className="overview-meta">{stateText(task.state)} · {dateText(task.deadline ?? task.targetEnd)}{task.blockerReason ? ` · ${task.blockerReason}` : ""}</span><span className="overview-path">{task.path.join(" / ") || "상위 경로 없음"}</span></li>)}</ul> : <p className="overview-empty">현재 맡은 활성 작업이 없습니다.</p>}</section>;
}

function ScheduleSection({ projectId, period, zone, date, weekStart, weekFrom, weekTo, weekEnd, scheduleQuery }: { projectId: string; period: OverviewPeriod; zone: string; date: string; weekStart: string; weekFrom: string; weekTo: string; weekEnd: string; scheduleQuery: { data?: { rows: Schedules; complete: boolean; partial: boolean; reason?: string }; isPending: boolean; isError: boolean; error: unknown; isFetching: boolean; refetch: () => unknown } }) {
  const loaded = scheduleQuery.data;
  const rows = loaded?.rows.filter(row => scheduleOverlaps(row, period === "today" ? civilStart(date, zone) : weekFrom, period === "today" ? civilEnd(date, zone) : weekTo)) ?? [];
  const label = period === "today" ? "오늘" : "이번 주";
  const calendar = `/projects/${projectId}/schedules?view=builtin-calendar&mode=week&date=${date}&zone=${encodeURIComponent(zone)}`;
  return <section aria-labelledby="overview-schedules-title"><div className="overview-section-heading"><div><p className="eyebrow">현재 일정</p><h2 id="overview-schedules-title">프로젝트 일정 <span className="overview-count">{loaded ? sectionCount(rows.length, loaded.complete) : ""}</span></h2></div><Link to={calendar}>캘린더 열기</Link></div><div className="overview-period-controls" role="group" aria-label="일정 기간"><button type="button" aria-pressed={period === "today"} onClick={() => updateOverviewContext("today", zone)}>오늘</button><button type="button" aria-pressed={period === "week"} onClick={() => updateOverviewContext("week", zone)}>이번 주</button></div><p className="overview-range">{label} · {period === "today" ? date : `${weekStart} – ${shiftDate(weekEnd, -1)}`} · {zone}</p>{scheduleQuery.isPending && <p className="overview-state" role="status">일정을 불러오는 중입니다.</p>}{scheduleQuery.isError && <ErrorState message={isOverviewProjectAccessError(scheduleQuery.error) ? "프로젝트 일정 접근 권한을 확인할 수 없습니다." : "일정을 불러오지 못했습니다."} onRetry={() => scheduleQuery.refetch()} fetching={scheduleQuery.isFetching} />}{loaded && !scheduleQuery.isError && <>{loaded.partial && <p className="overview-partial" role="status">{loaded.reason ?? "일부 일정만 불러왔습니다."}</p>}{rows.length ? <ul className="overview-list overview-schedule-list">{rows.map(row => <li key={row.id}><Link to={`/projects/${projectId}/schedules/${row.id}`}><strong>{row.title}</strong><span className="overview-meta">{scheduleDate(row, zone)} · {row.status}</span></Link></li>)}</ul> : <p className="overview-empty">{label}에 해당하는 일정이 없습니다.</p>}</>}</section>;
}

function civilStart(date: string, zone: string) { return overviewWindow(date, zone).from; }
function civilEnd(date: string, zone: string) { return overviewWindow(date, zone).to; }

type DashboardQuery = { data?: Dashboard; isPending: boolean; isError: boolean; isFetching: boolean; error: unknown; refetch: () => unknown };
function DashboardRegion({ projectId, role, zone, query }: { projectId: string; role: string; zone: string; query: DashboardQuery }) {
  if (query.isPending && !query.data) return <section className="overview-state" aria-live="polite"><p role="status">프로젝트 신호를 불러오는 중입니다.</p></section>;
  if (query.isError && !query.data) return <section className="overview-state"><ErrorState message="프로젝트 신호를 불러오지 못했습니다." onRetry={() => query.refetch()} fetching={query.isFetching} /></section>;
  if (!query.data) return null;
  const actionQueue = query.data.actionQueue as unknown as Schedules;
  return <>
    {query.isError && <section className="overview-state"><ErrorState message="프로젝트 신호를 새로 고치지 못했습니다. 이전 읽기 결과를 표시합니다." onRetry={() => query.refetch()} fetching={query.isFetching} /></section>}
    {query.isFetching && <p className="overview-state" role="status">프로젝트 신호를 새로 고치는 중입니다.</p>}
    <section aria-labelledby="overview-queue-title"><div className="overview-section-heading"><div><p className="eyebrow">확인 작업</p><h2 id="overview-queue-title">내 확인이 필요한 일정</h2></div></div>{role === "VIEWER" ? <p className="overview-empty">조회 권한에서는 확인 작업을 표시하지 않습니다.</p> : actionQueue.length ? <ul className="overview-list">{actionQueue.map(row => <li key={row.id}><Link to={`/projects/${projectId}/schedules/${row.id}`}><strong>{row.title}</strong><span className="overview-meta">확인 필요 · {scheduleDate(row, zone)}</span></Link></li>)}</ul> : <p className="overview-empty">처리할 확인 작업이 없습니다.</p>}</section>
    <section className="schedule-signal-section" aria-labelledby="overview-signals-title"><div className="section-heading"><div><p className="eyebrow">현재 신호</p><h2 id="overview-signals-title">이번 프로젝트의 흐름</h2></div></div><div className="schedule-radar"><div><span>전체 일정</span><strong>{query.data.scheduleCount}</strong></div><div><span>확인 대기</span><strong>{query.data.pendingAcknowledgementCount}</strong></div><div><span>Calendar 확인 필요</span><strong>{query.data.calendarRiskCount}</strong></div></div></section>
  </>;
}

export function ProjectOverview({ id }: { id: string }) {
  const project = useProject(id); const me = useMe();
  const queryClient = useQueryClient();
  const sessionGeneration = currentSessionGeneration();
  const sessionRef = useRef(sessionGeneration);
  const [context, setContext] = useState(() => overviewContext(window.location.hash.split("?", 2)[1] ?? ""));
  useEffect(() => { const update = () => setContext(overviewContext(window.location.hash.split("?", 2)[1] ?? "")); window.addEventListener("hashchange", update); return () => window.removeEventListener("hashchange", update); }, []);
  const [accessFailure, setAccessFailure] = useState(false);
  const [workspaceAccessFailure, setWorkspaceAccessFailure] = useState(false);
  const [overviewRevision, setOverviewRevision] = useState(0);
  const overviewQueryPrefix = (name: string) => [name, id, sessionGeneration, overviewRevision] as const;
  const isProjectProtectedQuery = (query: { queryKey: readonly unknown[] }) => query.queryKey[1] === id && ["overview-plan", "overview-dashboard", "overview-schedules", "schedule-workspace", "schedule-workspace-query", "schedule-workspace-record", "project-plan", "dashboard", "schedules"].includes(String(query.queryKey[0]));
  const clearOverviewQueries = () => {
    void queryClient.cancelQueries({ predicate: isProjectProtectedQuery });
    queryClient.removeQueries({ predicate: isProjectProtectedQuery });
  };
  useEffect(() => {
    if (sessionRef.current === sessionGeneration) return;
    sessionRef.current = sessionGeneration;
    clearOverviewQueries();
    setAccessFailure(true);
    setOverviewRevision(value => value + 1);
  }, [sessionGeneration]);
  useEffect(() => {
    let timer: number | undefined;
    const refresh = () => setContext(previous => {
      const next = overviewContext(`?period=${previous.period}&zone=${encodeURIComponent(previous.zone)}`, new Date());
      return next.date === previous.date && next.monday === previous.monday ? previous : next;
    });
    const schedule = () => { const boundary = overviewNextBoundary(new Date(), context.zone); timer = window.setTimeout(() => { refresh(); schedule(); }, Math.max(1000, Date.parse(boundary) - Date.now() + 50)); };
    const resume = () => { refresh(); if (timer !== undefined) window.clearTimeout(timer); schedule(); };
    document.addEventListener("visibilitychange", resume); window.addEventListener("focus", resume); schedule();
    return () => { document.removeEventListener("visibilitychange", resume); window.removeEventListener("focus", resume); if (timer !== undefined) window.clearTimeout(timer); };
  }, [context.zone]);
  const contextEpoch = `${context.date}:${context.zone}:${context.weekFrom}:${context.weekTo}`;
  const plan = useQuery({ queryKey: [...overviewQueryPrefix("overview-plan"), contextEpoch], queryFn: () => accessRead(`overview-plan:${id}:${sessionGeneration}:${overviewRevision}:${contextEpoch}`, () => api.plan(id)), enabled: project.isSuccess && !!project.data && !accessFailure });
  const dashboard = useQuery({ queryKey: [...overviewQueryPrefix("overview-dashboard"), contextEpoch], queryFn: () => accessRead(`overview-dashboard:${id}:${sessionGeneration}:${overviewRevision}:${contextEpoch}`, () => api.dashboard(id)), enabled: project.isSuccess && !!project.data && !accessFailure });
  const schedules = useQuery({ queryKey: [...overviewQueryPrefix("overview-schedules"), contextEpoch], queryFn: () => readOverviewSchedules(id, context.weekFrom, context.weekTo), enabled: project.isSuccess && !!project.data && !accessFailure });
  useEffect(() => {
    const isCurrentProtectedQuery = (query: { queryKey: readonly unknown[]; getObserversCount: () => number }) => {
      if (!isProjectProtectedQuery(query)) return false;
      const [name, projectId, generation, revision, epoch] = query.queryKey;
      if (String(name).startsWith("overview-")) return projectId === id && generation === sessionGeneration && revision === overviewRevision && epoch === contextEpoch;
      return query.getObserversCount() > 0;
    };
    const observe = () => {
      const denied = queryClient.getQueryCache().getAll().some(query => isCurrentProtectedQuery(query) && isOverviewProjectAccessError(query.state.error));
      if (denied) { clearOverviewQueries(); setWorkspaceAccessFailure(true); setAccessFailure(true); }
    };
    observe();
    return queryClient.getQueryCache().subscribe(observe);
  }, [contextEpoch, id, overviewRevision, queryClient, sessionGeneration]);
  const protectedAccessFailure = accessFailure || workspaceAccessFailure || (project.isError && isOverviewProjectAccessError(project.error)) || (plan.isError && isOverviewProjectAccessError(plan.error)) || (schedules.isError && isOverviewProjectAccessError(schedules.error)) || (dashboard.isError && isOverviewProjectAccessError(dashboard.error));
  useEffect(() => { if ((project.isError && isOverviewProjectAccessError(project.error)) || (plan.isError && isOverviewProjectAccessError(plan.error)) || (schedules.isError && isOverviewProjectAccessError(schedules.error)) || (dashboard.isError && isOverviewProjectAccessError(dashboard.error))) { clearOverviewQueries(); setOverviewRevision(value => value + 1); setAccessFailure(true); } }, [project.isError, project.error, plan.isError, plan.error, schedules.isError, schedules.error, dashboard.isError, dashboard.error]);
  const retry = async () => {
    clearOverviewQueries();
    const result = await project.refetch();
    if (result.isSuccess && result.data) { setOverviewRevision(value => value + 1); setWorkspaceAccessFailure(false); setAccessFailure(false); }
    else setAccessFailure(true);
  };
  if (protectedAccessFailure) return <Shell><ErrorState message="이 프로젝트의 개요에 접근할 수 없습니다. 보호된 내용을 숨겼습니다." onRetry={retry} fetching={project.isFetching || plan.isFetching || schedules.isFetching} /></Shell>;
  if (!project.isSuccess) return <Shell><QueryStateFallback query={project} /></Shell>;
  if (!project.data) return <Shell><ProjectMissing onRetry={() => project.refetch()} isFetching={project.isFetching} /></Shell>;
  const canCreate = capabilities(project.data, me.data?.id ?? "").create;
  const isEmpty = dashboard.isSuccess && dashboard.data.scheduleCount === 0 && !dashboard.data.upcomingSchedules.length && !dashboard.data.actionQueue.length;
  return <Shell project={project.data}><div className="schedule-screen project-overview-screen"><header className="schedule-heading"><div><p className="eyebrow">{project.data.name}</p><h1>프로젝트 개요</h1><p className="lead">전체 진척을 먼저 확인하고, 내 작업과 현재 일정을 이어서 살펴보세요.</p></div><div className="schedule-actions">{isEmpty && project.data.role === "MANAGER" && <Link className="button button-primary" to={`/projects/${id}/invitations/new`}>구성원 초대</Link>}{canCreate && <Link className={`button ${isEmpty && project.data.role === "MANAGER" ? "button-secondary" : "button-primary"}`} to={`/projects/${id}/schedules/new`}>일정 만들기</Link>}{!isEmpty && project.data.role === "MANAGER" && <Link className="button button-secondary" to={`/projects/${id}/invitations/new`}>구성원 초대</Link>}</div></header><PlanState query={plan}>{snapshot => <><div className="overview-macro"><ProgressSection snapshot={snapshot} /><PlanList snapshot={snapshot} projectId={id} /></div>{me.data && <MyTasks snapshot={snapshot} projectId={id} userId={me.data.id} />}</>}</PlanState><ScheduleSection projectId={id} period={context.period} zone={context.zone} date={context.date} weekStart={context.monday} weekFrom={context.weekFrom} weekTo={context.weekTo} weekEnd={context.weekEnd} scheduleQuery={schedules} /><DashboardRegion projectId={id} role={project.data.role} zone={context.zone} query={dashboard} /><ScheduleDashboardSection id={id} /></div></Shell>;
}

function QueryStateFallback({ query }: { query: { isPending: boolean; isError: boolean; refetch: () => unknown } }) { if (query.isPending) return <p role="status">프로젝트를 불러오는 중입니다.</p>; if (query.isError) return <ErrorState message="프로젝트를 불러오지 못했습니다." onRetry={() => query.refetch()} />; return null; }
