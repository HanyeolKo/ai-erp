import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type Schedule } from "../api/client";
import { accessKey, accessRead, accessReadCanClear, acknowledgement, calendarStatus, capabilities, clearAccessDenial, isAccessError, keys, recordAccessDenial, refreshSchedule, useAccessDenied, useMe, useProject } from "../state";
import { captureSession, isSessionContextActive } from "../session";
import { Link, Notice, ProjectMissing, QueryState, RetryButton, Shell } from "../ui";
import { Avatar, Icon, StatusBadge } from "../workspace-ui";
import { displayTime } from "../time";
import "./schedules.css";

const statusLabels: Record<string, string> = { DRAFT: "초안", CONFIRMED: "확정", CANCELLED: "취소" };
const statusLabel = (value: string) => statusLabels[value] ?? "상태 확인 필요";
const projectionLabel = (value?: string) => value === "FAILED" || value === "REAUTH_REQUIRED" ? "연결 확인 필요" : value === "PENDING" ? "동기화 중" : value === "SYNCED" ? "동기화 완료" : value === "NOT_CONNECTED" ? "연결되지 않음" : "상태 확인 필요";
const connectionStatusLabel = (value?: string) => value === "CONNECTED" ? "연결됨" : projectionLabel(value);
const compactCalendarLabels: Record<string, string> = { LOADING: "상태 확인 중", CONFIGURATION_REQUIRED: "구성 필요", ACCESS_DENIED: "접근 확인 필요", ERROR: "오류 확인 필요", REAUTH_REQUIRED: "다시 연결 필요", FAILED: "연결 확인 필요", PENDING: "동기화 중", SYNCED: "동기화 완료", NOT_CONNECTED: "연결되지 않음", UNKNOWN: "상태 확인 필요" };
const compactCalendarOutline = "M5.5 6.5A2 2 0 0 1 7.5 4.5h9a2 2 0 0 1 2 2v13h-13v-13zm3-3v3m7-3v3m-10 6h13";
const compactCalendarGlyphs: Record<string, string> = { LOADING: "M12 4a8 8 0 1 0 8 8", CONFIGURATION_REQUIRED: "M12 3.5l1.8 1.2 2.1-.2.8 2 1.7 1.4-.8 2 1.2 1.8-1.2 1.8.8 2-1.7 1.4-.8 2-2.1-.2L12 20.5l-1.8-1.2-2.1.2-.8-2-1.7-1.4.8-2L5.2 12l1.2-1.8-.8-2 1.7-1.4.8-2 2.1.2L12 3.5zm0 5.2v3.8m0 3h.01", REAUTH_REQUIRED: "M19 8V4m0 0h-4m4 0-3.1 3.1A7.5 7.5 0 1 0 19.2 14", FAILED: "M12 4v9m0 4h.01", PENDING: "M12 5v7l4 2", SYNCED: "M4 12.5l5 5L20 6.5", NOT_CONNECTED: "M5 12h14", UNKNOWN: "M9.6 9a2.5 2.5 0 1 1 4.2 1.8c-1.2 1-1.8 1.4-1.8 3.2m0 3h.01" };
const participantLabel = (value: string) => value === "PENDING" ? "확인 대기" : value === "ACKNOWLEDGED" ? "확인 완료" : "확인 대상 아님";
const changeLabel = (value: string) => value === "SCHEDULE_CREATED" ? "일정 생성" : value === "SCHEDULE_CHANGED" ? "일정 변경" : value === "SCHEDULE_CONFIRMED" ? "일정 확정" : value === "SCHEDULE_CANCELLED" ? "일정 취소" : value === "SCHEDULE_ACKNOWLEDGED" ? "변경 확인" : "일정 변경";

function CalendarGlyph({ state }: { state: string }) {
    const glyph = compactCalendarGlyphs[state] ?? compactCalendarGlyphs.FAILED;
    return <svg className="schedule-calendar-glyph" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d={compactCalendarOutline} fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" /><g transform="translate(12 12) scale(.55) translate(-12 -12)"><path d={glyph} fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" /></g></svg>;
}

function CalendarStatus({ calendar, connection, projection, projectionDenied, configurationRequired, caps, blocked, run }: { calendar?: string; connection: { isPending: boolean; isError: boolean; isFetching?: boolean; data?: { status?: string }; error: unknown; refetch: () => unknown }; projection: { isPending: boolean; isError: boolean; isFetching?: boolean; data?: { status?: string; businessRevision?: number }; error: unknown; refetch: () => unknown }; projectionDenied: boolean; configurationRequired: boolean; caps: { retry: boolean }; blocked: boolean; run: (action: "retry") => void }) {
    const triggerRef = useRef<HTMLButtonElement>(null);
    const [open, setOpen] = useState(false);
    const hasQueryError = projection.isError || connection.isError;
    const state = projectionDenied ? "ACCESS_DENIED" : hasQueryError ? "ERROR" : configurationRequired ? "CONFIGURATION_REQUIRED" : (projection.isPending && !projection.data) || (connection.isPending && !connection.data) ? "LOADING" : calendar ?? "UNKNOWN";
    const label = compactCalendarLabels[state] ?? compactCalendarLabels.UNKNOWN;
    const toggle = () => setOpen(value => !value);
    useEffect(() => {
        if (!open) return;
        const closeOnEscape = (event: KeyboardEvent) => {
            if (event.key !== "Escape") return;
            event.preventDefault();
            setOpen(false);
            triggerRef.current?.focus();
        };
        document.addEventListener("keydown", closeOnEscape);
        return () => document.removeEventListener("keydown", closeOnEscape);
    }, [open]);
    return <div className={`schedule-calendar-inline schedule-calendar-inline--${state.toLowerCase()}`}>
        <div className="schedule-calendar-control-row">
            <button ref={triggerRef} className="schedule-calendar-trigger" type="button" aria-label={`Calendar: ${label}`} aria-expanded={open} aria-controls="calendar-status-disclosure" onClick={toggle}><CalendarGlyph state={state} /></button>
            <div className="schedule-calendar-recovery">
                {(projection.isError || (!projection.isPending && !(projection.isFetching && projection.data))) && <QueryState query={projection} label={projectionDenied ? "Calendar 접근 상태 다시 확인" : "투영 다시 시도"} />}
                {(connection.isError || (!connection.isPending && !(connection.isFetching && connection.data))) && <QueryState query={connection} label="연결 상태 다시 확인" />}
                {configurationRequired && <p>Calendar 연동이 구성되지 않았습니다.</p>}
                {!configurationRequired && calendar === "REAUTH_REQUIRED" && <Link to="/calendar">Calendar 다시 연결</Link>}
                {!configurationRequired && calendar !== "REAUTH_REQUIRED" && !projectionDenied && caps.retry && <button className="button button-secondary" disabled={blocked} onClick={() => run("retry")}>Calendar 동기화 다시 시도</button>}
            </div>
        </div>
        {open && <div id="calendar-status-disclosure" className="schedule-calendar-disclosure" role="region" aria-label="Calendar 상태 상세"><p><strong>{label}</strong></p>{connection.data && <p>계정 연결 상태 · {connectionStatusLabel(connection.data.status)}</p>}{!projectionDenied && projection.data && <details><summary>진단 정보</summary><p>최근 변경 버전 · {projection.data.businessRevision}</p></details>}</div>}
    </div>;
}

export function Detail({ id, scheduleId }: { id: string; scheduleId: string }) {
    const qc = useQueryClient();
    const project = useProject(id);
    const me = useMe();
    const projectDenied = useAccessDenied(accessKey("project", id));
    const detailAccessKey = accessKey("detail", id, scheduleId);
    const projectionAccessKey = accessKey("projection", id, scheduleId);
    const writeAccessKey = accessKey("schedule-write", id, scheduleId);
    const detailDenied = useAccessDenied(detailAccessKey);
    const projectionDenied = useAccessDenied(projectionAccessKey);
    const writeDenied = useAccessDenied(writeAccessKey);
    const detail = useQuery({ queryKey: keys.detail(id, scheduleId), queryFn: () => accessRead(detailAccessKey, () => api.detail(id, scheduleId)), staleTime: detailDenied || writeDenied ? 0 : 15000, enabled: !projectDenied && project.isSuccess && !!project.data });
    const projection = useQuery({ queryKey: keys.projection(id, scheduleId), queryFn: () => accessRead(projectionAccessKey, () => api.projection(id, scheduleId)), staleTime: projectionDenied ? 0 : 15000, enabled: !projectDenied && project.isSuccess && !!project.data && detail.isSuccess });
    const connection = useQuery({ queryKey: keys.connection, queryFn: api.calendar, enabled: !projectDenied && project.isSuccess && !!project.data && detail.isSuccess });
    useEffect(() => { if (detail.isSuccess && !detail.isFetching && accessReadCanClear(detailAccessKey)) clearAccessDenial(detailAccessKey); }, [detailAccessKey, detail.isFetching, detail.isSuccess]);
    useEffect(() => { if (projection.isSuccess && !projection.isFetching && accessReadCanClear(projectionAccessKey)) clearAccessDenial(projectionAccessKey); }, [projectionAccessKey, projection.isFetching, projection.isSuccess]);
    const command = useMutation({
        mutationFn: async (action: "confirm" | "cancel" | "ack" | "retry") => {
            const schedule = detail.data!;
            if (action === "confirm") return api.confirm(id, scheduleId, { rowVersion: schedule.rowVersion });
            if (action === "cancel") return api.cancel(id, scheduleId, { rowVersion: schedule.rowVersion });
            if (action === "ack") return api.acknowledge(id, scheduleId, { expectedBusinessRevision: schedule.businessRevision });
            return api.retryProjection(id, scheduleId);
        },
        onMutate: captureSession,
        onError: (error, _action, context) => { if (isSessionContextActive(context) && isAccessError(error)) recordAccessDenial(writeAccessKey); },
        onSuccess: async (_value, _action, context) => { if (isSessionContextActive(context)) await refreshSchedule(qc, id, scheduleId); },
    });
    if (projectDenied) return <Shell><h1>프로젝트 접근 상태를 확인할 수 없습니다</h1><QueryState query={project} loadingMessage="프로젝트를 불러오는 중입니다." errorMessage="프로젝트를 불러오지 못했습니다." /><RetryButton onRetry={() => project.refetch()} isFetching={project.isFetching}>접근 상태 다시 확인</RetryButton><Link to="/">프로젝트 선택</Link></Shell>;
    if (!project.isSuccess) return <Shell><QueryState query={project} loadingMessage="프로젝트를 불러오는 중입니다." errorMessage="프로젝트를 불러오지 못했습니다." /></Shell>;
    if (!project.data) return <Shell><ProjectMissing onRetry={() => project.refetch()} isFetching={project.isFetching} /></Shell>;
    if (detailDenied || !detail.isSuccess) return <Shell project={project.data}><h1>일정 상세</h1><QueryState query={detail} /><RetryButton onRetry={() => detail.refetch()} isFetching={detail.isFetching}>일정 다시 확인</RetryButton><Link to={`/projects/${id}/schedules`}>일정 목록으로</Link></Shell>;
    const schedule = detail.data;
    const caps = capabilities(project.data, me.data!.id, schedule, projectionDenied ? undefined : projection.data);
    const calendar = calendarStatus(connection.data, projectionDenied ? undefined : projection.data);
    const configurationRequired = Boolean(connection.data?.configurationRequired || (!projectionDenied && projection.data?.retryClassification === "CONFIGURATION_REQUIRED"));
    const denied = command.isError && isAccessError(command.error);
    const blocked = command.isPending || denied || writeDenied;
    const recoverAccess = async () => { const recoveryContext = captureSession(); const [membership, latest] = await Promise.all([project.refetch(), detail.refetch()]); if (!isSessionContextActive(recoveryContext)) return; if (membership.isSuccess && membership.data && latest.isSuccess) { clearAccessDenial(writeAccessKey); command.reset(); } };
    const run = (action: "confirm" | "cancel" | "ack" | "retry") => { if (!blocked) command.mutate(action); };
    const ownAck = acknowledgement(schedule, me.data!.id);
    const participantDisplay = (participant: Schedule["participants"][number]) => {
        if (participant.memberUserId === me.data!.id) return "나";
        if (!participant.displayName && !participant.email) return "이름 없는 구성원 · 구성원 정보를 확인할 수 없습니다.";
        return `${participant.displayName || "이름 없는 구성원"}${participant.email ? ` · ${participant.email}` : ""}`;
    };
    const firstMember = schedule.participants.find(participant => participant.memberUserId);
    return <Shell project={project.data} workspace>
        <div className="schedule-screen schedule-detail">
            <header className="schedule-heading schedule-detail-heading"><div><p className="eyebrow">{project.data.name} · 일정 상세</p><h1>{schedule.title}</h1></div><Link className="button button-secondary" to={`/projects/${id}/schedules`}><Icon name="list" />일정 목록</Link></header>
            <section className="schedule-detail-summary" aria-label="일정 요약"><div><span>일정 상태</span><StatusBadge tone={schedule.status === "CONFIRMED" ? "green" : schedule.status === "CANCELLED" ? "red" : "neutral"} icon={schedule.status === "CONFIRMED" ? "check" : schedule.status === "CANCELLED" ? "cancel" : "draft"}>{statusLabel(schedule.status)}</StatusBadge></div><div><span>일정 시간</span><strong><time dateTime={schedule.startsAt}>{displayTime(schedule.startsAt)}</time> ~ <time dateTime={schedule.endsAt}>{displayTime(schedule.endsAt)}</time></strong><small>Asia/Seoul 기준</small></div><div><span>참석자</span><strong>{schedule.participants.length}명</strong>{firstMember && <Avatar name={firstMember.displayName || firstMember.email || "이름 확인 불가"} />}</div></section>
            <div className="schedule-detail-actions" aria-label="일정 작업">{caps.edit && !blocked && <Link className="button button-secondary" to={`/projects/${id}/schedules/${scheduleId}/edit`}><Icon name="edit" />일정 수정</Link>}{caps.confirm && <button className="button button-primary" disabled={blocked} onClick={() => run("confirm")}><Icon name="check" />일정 확정</button>}{caps.cancel && <button className="button button-danger" disabled={blocked} onClick={() => run("cancel")}><Icon name="cancel" />일정 취소</button>}{caps.ack && <button className="button button-secondary" disabled={blocked} onClick={() => run("ack")}><Icon name="check" />변경 확인</button>}</div>
            <CalendarStatus calendar={calendar} connection={connection} projection={projection} projectionDenied={projectionDenied} configurationRequired={configurationRequired} caps={caps} blocked={blocked} run={run} />
            <p className="schedule-own-ack"><strong>내 확인 상태</strong> · {participantLabel(ownAck)}</p>
            <details className="schedule-detail-disclosure"><summary><Icon name="users" />설명과 참석자 <span>{schedule.participants.length}명</span></summary><section className="schedule-detail-section" aria-labelledby="participants-title"><h2>일정 설명</h2><p>{schedule.description || "설명이 없는 일정입니다."}</p><h2 id="participants-title">전체 참석자</h2><h3>프로젝트 구성원</h3>{schedule.participants.some(participant => participant.memberUserId) ? <ul className="schedule-participant-list">{schedule.participants.filter(participant => participant.memberUserId).map(participant => <li key={participant.memberUserId}><strong>{participantDisplay(participant)}</strong><span>{schedule.businessRevision <= 0 ? "확인 대상 아님" : participant.acknowledged ? "확인 완료" : "확인 대기"}</span></li>)}</ul> : <p>내부 참석자가 없습니다.</p>}<h3>외부 참석자</h3>{schedule.participants.some(participant => participant.externalEmail) ? <ul className="schedule-participant-list">{schedule.participants.filter(participant => participant.externalEmail).map(participant => <li key={participant.externalEmail}>{participant.externalEmail}<span>Calendar 참석 대상 · 앱 변경 확인 대상 아님</span></li>)}</ul> : <p>외부 참석자가 없습니다.</p>}</section></details>
            <details className="schedule-detail-disclosure"><summary><Icon name="history" />변경 이력 <span>{schedule.changes.length}건</span></summary><section className="schedule-detail-section schedule-history" aria-labelledby="history-title"><h2 id="history-title">변경 이력</h2>{schedule.changes.length ? <ol>{schedule.changes.map((change, index) => <li key={index}>{changeLabel(change.type)} · <time dateTime={change.createdAt}>{displayTime(change.createdAt)}</time></li>)}</ol> : <p>변경 이력이 없습니다.</p>}<p className="schedule-help">최근 변경을 최대 100개까지 표시합니다.</p></section></details>
            {command.isError && <section className="schedule-detail-feedback"><Notice error={command.error} />{denied ? <RetryButton onRetry={recoverAccess} isFetching={project.isFetching || detail.isFetching} /> : <RetryButton onRetry={() => detail.refetch()} isFetching={detail.isFetching}>최신 일정 불러오기</RetryButton>}</section>}{writeDenied && !command.isError && <section className="schedule-detail-feedback"><p>일정 변경 권한을 다시 확인해야 합니다.</p><RetryButton onRetry={recoverAccess} isFetching={project.isFetching || detail.isFetching}>접근 상태 다시 확인</RetryButton></section>}{command.isSuccess && <p className="schedule-success" role="status">요청을 처리했습니다.</p>}
        </div>
    </Shell>;
}
