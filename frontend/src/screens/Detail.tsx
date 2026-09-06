import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { acknowledgement, calendarStatus, capabilities, keys, refreshSchedule, useMe, useProject } from "../state";
import { Link, Notice, ProjectMissing, QueryState, Shell } from "../ui";
import { displayTime } from "../time";
export function Detail({ id, scheduleId }: {
    id: string;
    scheduleId: string;
}) {
    const qc = useQueryClient();
    const project = useProject(id);
    const me = useMe();
    const detail = useQuery({ queryKey: keys.detail(id, scheduleId), queryFn: () => api.detail(id, scheduleId), enabled: !!project.data });
    const projection = useQuery({ queryKey: keys.projection(id, scheduleId), queryFn: () => api.projection(id, scheduleId), enabled: detail.isSuccess });
    const connection = useQuery({ queryKey: keys.connection, queryFn: api.calendar, enabled: detail.isSuccess });
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
        onSuccess: () => refreshSchedule(qc, id, scheduleId)
    });
    if (!project.isSuccess)
        return <Shell><QueryState query={project}/></Shell>;
    if (!project.data)
        return <Shell><ProjectMissing /></Shell>;
    if (!detail.isSuccess)
        return <Shell project={project.data}><QueryState query={detail}/></Shell>;
    const s = detail.data;
    const caps = capabilities(project.data, me.data!.id, s, projection.data);
    const run = (action: "confirm" | "cancel" | "ack" | "retry") => { if (!command.isPending)
        command.mutate(action); };
    return <Shell project={project.data}><h1>{s.title}</h1><p>{s.description}</p><p>상태 {s.status} · businessRevision {s.businessRevision} · rowVersion {s.rowVersion}</p><p><time dateTime={s.startsAt}>{displayTime(s.startsAt)}</time> ~ <time dateTime={s.endsAt}>{displayTime(s.endsAt)}</time> Asia/Seoul</p>
    {caps.edit && !command.isPending && <Link to={`/projects/${id}/schedules/${scheduleId}/edit`}>일정 수정</Link>}{caps.confirm && <button disabled={command.isPending} onClick={() => run("confirm")}>일정 확정</button>}{caps.cancel && <button disabled={command.isPending} onClick={() => run("cancel")}>일정 취소</button>}
    <h2>참여자 확인</h2><p>내 확인 상태: {acknowledgement(s, me.data!.id)}</p><h3>프로젝트 구성원</h3><ul>{s.participants.filter(p => p.memberUserId).map(p => <li key={p.memberUserId}>{p.memberUserId}: {s.businessRevision <= 0 ? "확인 대상 아님" : p.acknowledged ? "확인" : "대기"}</li>)}</ul>{!s.participants.some(p => p.memberUserId) && <p>내부 참석자가 없습니다.</p>}
    <h3>외부 참석자</h3><ul>{s.participants.filter(p => p.externalEmail).map(p => <li key={p.externalEmail}>{p.externalEmail} · Calendar 참석 대상 · 앱 변경 확인 대상 아님</li>)}</ul>{!s.participants.some(p => p.externalEmail) && <p>외부 참석자가 없습니다.</p>}
    {caps.ack && <button disabled={command.isPending} onClick={() => run("ack")}>변경 확인</button>}
    <h2>변경 이력</h2>{s.changes.length ? <ul>{s.changes.map((c, index) => <li key={index}>{c.businessRevision} · {c.type} · {c.changedBy} · <time dateTime={c.createdAt}>{displayTime(c.createdAt)}</time></li>)}</ul> : <p>변경 이력이 없습니다.</p>}<p>최신 변경 이력 최대 100개를 표시합니다.</p>
    <h2>Calendar 투영</h2><QueryState query={projection} label="투영 다시 시도"/><QueryState query={connection} label="연결 다시 시도"/>
    {projection.isSuccess && connection.isSuccess && <><p>Calendar {calendarStatus(connection.data, projection.data)}</p><p>투영: {projection.data.status} · revision {projection.data.businessRevision} · {projection.data.retryClassification}</p>{connection.data.configurationRequired || projection.data.retryClassification === "CONFIGURATION_REQUIRED" ? <p>Calendar 연동이 구성되지 않았습니다.</p> : calendarStatus(connection.data, projection.data) === "REAUTH_REQUIRED" ? <Link to="/calendar">Calendar 다시 연결</Link> : caps.retry && <button disabled={command.isPending} onClick={() => run("retry")}>Calendar 재시도</button>}</>}
    {command.isError && <><Notice error={command.error}/><button onClick={() => detail.refetch()}>최신 일정 불러오기</button></>}{command.isSuccess && <p role="status">요청을 처리했습니다.</p>}
  </Shell>;
}
