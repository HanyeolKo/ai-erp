import { useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type Schedule } from "../api/client";
import { accessKey, accessRead, accessReadCanClear, acknowledgement, calendarStatus, capabilities, clearAccessDenial, isAccessError, keys, recordAccessDenial, refreshSchedule, useAccessDenied, useMe, useProject } from "../state";
import { captureSession, isSessionContextActive } from "../session";
import { Link, Notice, ProjectMissing, QueryState, RetryButton, Shell } from "../ui";
import { displayTime } from "../time";
import "./schedules.css";

const statusLabels: Record<string, string> = { DRAFT: "초안", CONFIRMED: "확정", CANCELLED: "취소" };
const statusLabel = (value: string) => statusLabels[value] ?? "상태 확인 필요";
const projectionLabel = (value?: string) => value === "FAILED" || value === "REAUTH_REQUIRED" ? "연결 확인 필요" : value === "PENDING" ? "동기화 중" : value === "SYNCED" ? "동기화 완료" : value === "NOT_CONNECTED" ? "연결되지 않음" : "상태 확인 필요";
const participantLabel = (value: string) => value === "PENDING" ? "확인 대기" : value === "ACKNOWLEDGED" ? "확인 완료" : "확인 대상 아님";
const changeLabel = (value: string) => value === "SCHEDULE_CREATED" ? "일정 생성" : value === "SCHEDULE_CHANGED" ? "일정 변경" : value === "SCHEDULE_CONFIRMED" ? "일정 확정" : value === "SCHEDULE_CANCELLED" ? "일정 취소" : value === "SCHEDULE_ACKNOWLEDGED" ? "변경 확인" : "일정 변경";

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
    const calendarLabel = calendar ? projectionLabel(calendar) : (connection.data || projection.data) ? "상태 확인 필요" : undefined;
    const configurationRequired = connection.data?.configurationRequired || (!projectionDenied && projection.data?.retryClassification === "CONFIGURATION_REQUIRED");
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
    return <Shell project={project.data}>
        <div className="schedule-screen schedule-detail">
            <header className="schedule-heading schedule-detail-heading"><div><p className="eyebrow">{project.data.name} · 일정 상세</p><h1>{schedule.title}</h1><p className="lead">{schedule.description || "설명이 없는 일정입니다."}</p></div><Link className="button button-secondary" to={`/projects/${id}/schedules`}>일정 목록</Link></header>
            <section className="schedule-detail-summary" aria-label="일정 요약"><div><span>일정 상태</span><strong>{statusLabel(schedule.status)}</strong></div><div><span>일정 시간</span><strong><time dateTime={schedule.startsAt}>{displayTime(schedule.startsAt)}</time> ~ <time dateTime={schedule.endsAt}>{displayTime(schedule.endsAt)}</time></strong><small>Asia/Seoul 기준</small></div></section>
            <div className="schedule-detail-actions" aria-label="일정 작업">{caps.edit && !blocked && <Link className="button button-secondary" to={`/projects/${id}/schedules/${scheduleId}/edit`}>일정 수정</Link>}{caps.confirm && <button className="button button-primary" disabled={blocked} onClick={() => run("confirm")}>일정 확정</button>}{caps.cancel && <button className="button button-danger" disabled={blocked} onClick={() => run("cancel")}>일정 취소</button>}</div>
            <section className="schedule-detail-section" aria-labelledby="participants-title"><p className="eyebrow">참여자</p><h2 id="participants-title">참여자 확인</h2><p>내 확인 상태 · {participantLabel(ownAck)}</p><h3>프로젝트 구성원</h3>{schedule.participants.some(participant => participant.memberUserId) ? <ul className="schedule-participant-list">{schedule.participants.filter(participant => participant.memberUserId).map(participant => <li key={participant.memberUserId}><strong>{participantDisplay(participant)}</strong><span>{schedule.businessRevision <= 0 ? "확인 대상 아님" : participant.acknowledged ? "확인 완료" : "확인 대기"}</span></li>)}</ul> : <p>내부 참석자가 없습니다.</p>}<h3>외부 참석자</h3>{schedule.participants.some(participant => participant.externalEmail) ? <ul className="schedule-participant-list">{schedule.participants.filter(participant => participant.externalEmail).map(participant => <li key={participant.externalEmail}>{participant.externalEmail}<span>Calendar 참석 대상 · 앱 변경 확인 대상 아님</span></li>)}</ul> : <p>외부 참석자가 없습니다.</p>}{caps.ack && <button className="button button-primary" disabled={blocked} onClick={() => run("ack")}>변경 확인</button>}</section>
            <section className="schedule-detail-section schedule-history" aria-labelledby="history-title"><p className="eyebrow">기록</p><h2 id="history-title">변경 이력</h2>{schedule.changes.length ? <ol>{schedule.changes.map((change, index) => <li key={index}>{changeLabel(change.type)} · <time dateTime={change.createdAt}>{displayTime(change.createdAt)}</time></li>)}</ol> : <p>변경 이력이 없습니다.</p>}<p className="schedule-help">최근 변경을 최대 100개까지 표시합니다.</p></section>
            <section className="schedule-detail-section schedule-calendar-status" aria-labelledby="calendar-title"><p className="eyebrow">외부 연동</p><h2 id="calendar-title">Calendar 동기화</h2><QueryState query={projection} label={projectionDenied ? "Calendar 접근 상태 다시 확인" : "투영 다시 시도"} /><QueryState query={connection} label="연결 상태 다시 확인" />{calendarLabel && <p>동기화 상태 · <strong>{calendarLabel}</strong></p>}{connection.data && <p>계정 연결 상태 · {projectionLabel(connection.data.status)}</p>}{!projectionDenied && projection.data && <details><summary>오류 상세</summary><p>기술 진단 정보는 필요한 경우에만 확인하세요.</p><p>최근 변경 버전 · {projection.data.businessRevision}</p></details>}{configurationRequired ? <p>Calendar 연동이 구성되지 않았습니다.</p> : calendar === "REAUTH_REQUIRED" ? <Link to="/calendar">Calendar 다시 연결</Link> : !projectionDenied && caps.retry && <button className="button button-secondary" disabled={blocked} onClick={() => run("retry")}>Calendar 동기화 다시 시도</button>}</section>
            {command.isError && <section className="schedule-detail-feedback"><Notice error={command.error} />{denied ? <RetryButton onRetry={recoverAccess} isFetching={project.isFetching || detail.isFetching} /> : <RetryButton onRetry={() => detail.refetch()} isFetching={detail.isFetching}>최신 일정 불러오기</RetryButton>}</section>}{writeDenied && !command.isError && <section className="schedule-detail-feedback"><p>일정 변경 권한을 다시 확인해야 합니다.</p><RetryButton onRetry={recoverAccess} isFetching={project.isFetching || detail.isFetching}>접근 상태 다시 확인</RetryButton></section>}{command.isSuccess && <p className="schedule-success" role="status">요청을 처리했습니다.</p>}
        </div>
    </Shell>;
}
