import { useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { accessKey, accessRead, accessReadCanClear, acknowledgement, calendarStatus, capabilities, clearAccessDenial, isAccessError, keys, recordAccessDenial, refreshSchedule, useAccessDenied, useMe, useProject } from "../state";
import { captureSession, isSessionContextActive } from "../session";
import { Link, Notice, ProjectMissing, QueryState, RetryButton, Shell } from "../ui";
import { displayTime } from "../time";
export function Detail({ id, scheduleId }: {
    id: string;
    scheduleId: string;
}) {
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
            const s = detail.data!;
            if (action === "confirm")
                return api.confirm(id, scheduleId, { rowVersion: s.rowVersion });
            if (action === "cancel")
                return api.cancel(id, scheduleId, { rowVersion: s.rowVersion });
            if (action === "ack")
                return api.acknowledge(id, scheduleId, { expectedBusinessRevision: s.businessRevision });
            return api.retryProjection(id, scheduleId);
        },
        onMutate: captureSession,
        onError: (error) => { if (isAccessError(error)) recordAccessDenial(writeAccessKey); },
        onSuccess: async (_value, _action, context) => { if (isSessionContextActive(context)) await refreshSchedule(qc, id, scheduleId); }
    });
    if (projectDenied)
        return <Shell><h1>프로젝트 접근 상태를 확인할 수 없습니다</h1><QueryState query={project} loadingMessage="프로젝트를 불러오는 중입니다." errorMessage="프로젝트를 불러오지 못했습니다."/><RetryButton onRetry={() => project.refetch()} isFetching={project.isFetching}>접근 상태 다시 확인</RetryButton><Link to="/">프로젝트 선택</Link></Shell>;
    if (!project.isSuccess)
        return <Shell><QueryState query={project} loadingMessage="프로젝트를 불러오는 중입니다." errorMessage="프로젝트를 불러오지 못했습니다."/></Shell>;
    if (!project.data)
        return <Shell><ProjectMissing onRetry={() => project.refetch()} isFetching={project.isFetching}/></Shell>;
    if (detailDenied || !detail.isSuccess)
        return <Shell project={project.data}><h1>일정 상세</h1><QueryState query={detail}/><Link to={`/projects/${id}/schedules`}>일정 목록으로</Link></Shell>;
    const s = detail.data;
    const caps = capabilities(project.data, me.data!.id, s, projectionDenied ? undefined : projection.data);
    const calendar = calendarStatus(connection.data, projectionDenied ? undefined : projection.data);
    const configurationRequired = connection.data?.configurationRequired || (!projectionDenied && projection.data?.retryClassification === "CONFIGURATION_REQUIRED");
    const denied = command.isError && isAccessError(command.error);
    const blocked = command.isPending || denied || writeDenied;
    const recoverAccess = async () => {
        const [membership, latest] = await Promise.all([project.refetch(), detail.refetch()]);
        if (membership.isSuccess && membership.data && latest.isSuccess) { clearAccessDenial(writeAccessKey); command.reset(); }
    };
    const run = (action: "confirm" | "cancel" | "ack" | "retry") => { if (!blocked)
        command.mutate(action); };
    return <Shell project={project.data}><h1>{s.title}</h1><p>{s.description}</p><p>상태 {s.status} · businessRevision {s.businessRevision} · rowVersion {s.rowVersion}</p><p><time dateTime={s.startsAt}>{displayTime(s.startsAt)}</time> ~ <time dateTime={s.endsAt}>{displayTime(s.endsAt)}</time> Asia/Seoul</p>
    {caps.edit && !blocked && <Link to={`/projects/${id}/schedules/${scheduleId}/edit`}>일정 수정</Link>}{caps.confirm && <button disabled={blocked} onClick={() => run("confirm")}>일정 확정</button>}{caps.cancel && <button disabled={blocked} onClick={() => run("cancel")}>일정 취소</button>}
    <h2>참여자 확인</h2><p>내 확인 상태: {acknowledgement(s, me.data!.id)}</p><h3>프로젝트 구성원</h3><ul>{s.participants.filter(p => p.memberUserId).map(p => <li key={p.memberUserId}>{p.memberUserId}: {s.businessRevision <= 0 ? "확인 대상 아님" : p.acknowledged ? "확인" : "대기"}</li>)}</ul>{!s.participants.some(p => p.memberUserId) && <p>내부 참석자가 없습니다.</p>}
    <h3>외부 참석자</h3><ul>{s.participants.filter(p => p.externalEmail).map(p => <li key={p.externalEmail}>{p.externalEmail} · Calendar 참석 대상 · 앱 변경 확인 대상 아님</li>)}</ul>{!s.participants.some(p => p.externalEmail) && <p>외부 참석자가 없습니다.</p>}
    {caps.ack && <button disabled={blocked} onClick={() => run("ack")}>변경 확인</button>}
    <h2>변경 이력</h2>{s.changes.length ? <ul>{s.changes.map((c, index) => <li key={index}>{c.businessRevision} · {c.type} · {c.changedBy} · <time dateTime={c.createdAt}>{displayTime(c.createdAt)}</time></li>)}</ul> : <p>변경 이력이 없습니다.</p>}<p>최신 변경 이력 최대 100개를 표시합니다.</p>
    <h2>Calendar 투영</h2><QueryState query={projection} label={projectionDenied ? "Calendar 접근 상태 다시 확인" : "투영 다시 시도"}/><QueryState query={connection} label="연결 다시 시도"/>
    {calendar && <p>Calendar {calendar}</p>}
    {connection.data && <p>연결: {connection.data.status}</p>}
    {!projectionDenied && projection.data && <p>투영: {projection.data.status} · revision {projection.data.businessRevision} · {projection.data.retryClassification}</p>}
    {configurationRequired ? <p>Calendar 연동이 구성되지 않았습니다.</p> : calendar === "REAUTH_REQUIRED" ? <Link to="/calendar">Calendar 다시 연결</Link> : !projectionDenied && caps.retry && <button disabled={blocked} onClick={() => run("retry")}>Calendar 재시도</button>}
    {command.isError && <><Notice error={command.error}/>{denied ? <RetryButton onRetry={recoverAccess} isFetching={project.isFetching || detail.isFetching}/> : <RetryButton onRetry={() => detail.refetch()} isFetching={detail.isFetching}>최신 일정 불러오기</RetryButton>}</>}{writeDenied && !command.isError && <><p>일정 변경 권한을 다시 확인해야 합니다.</p><RetryButton onRetry={recoverAccess} isFetching={project.isFetching || detail.isFetching}>접근 상태 다시 확인</RetryButton></>}{command.isSuccess && <p role="status">요청을 처리했습니다.</p>}
  </Shell>;
}
