import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type CreateBody, type Members, type Project, type Schedule } from "../api/client";
import { capabilities, keys, refreshSchedule, useMe, useProject } from "../state";
import { go, Notice, ProjectMissing, QueryState, Shell } from "../ui";
import { localDateTimeToUtc, utcToLocalDateTime, validZone } from "../time";
export function ScheduleForm({ id, scheduleId }: {
    id: string;
    scheduleId?: string;
}) {
    const project = useProject(id);
    const me = useMe();
    const [memberPage, setMemberPage] = useState(0);
    const members = useQuery({ queryKey: [...keys.members(id), memberPage], queryFn: () => api.members(id, memberPage), enabled: !!project.data });
    const detail = useQuery({ queryKey: keys.detail(id, scheduleId ?? ""), queryFn: () => api.detail(id, scheduleId!), enabled: !!scheduleId && !!project.data });
    if (!project.isSuccess)
        return <Shell><QueryState query={project}/></Shell>;
    if (!project.data)
        return <Shell><ProjectMissing /></Shell>;
    if (scheduleId && !detail.isSuccess)
        return <Shell project={project.data}><QueryState query={detail}/></Shell>;
    return <Shell project={project.data}><h1>{scheduleId ? "일정 수정" : "일정 만들기"}</h1><QueryState query={members} label="구성원 다시 시도"/><Editor key={scheduleId ?? "new"} project={project.data} userId={me.data!.id} current={detail.data} members={members.data ?? []} membersReady={members.isSuccess}/>{members.isSuccess && <><button disabled={!memberPage} onClick={() => setMemberPage(memberPage - 1)}>이전 참석자 목록</button><button disabled={members.data.length < 100} onClick={() => setMemberPage(memberPage + 1)}>다음 참석자 목록</button></>}</Shell>;
}
function Editor({ project, userId, current, members, membersReady }: {
    project: Project;
    userId: string;
    current?: Schedule;
    members: Members;
    membersReady: boolean;
}) {
    // The editor mounts only after detail succeeds. Query refreshes never overwrite an unsaved draft.
    const [snapshot] = useState(current);
    const [title, setTitle] = useState(snapshot?.title ?? "");
    const [description, setDescription] = useState(snapshot?.description ?? "");
    const [zone, setZone] = useState("Asia/Seoul");
    const [start, setStart] = useState(() => snapshot ? utcToLocalDateTime(snapshot.startsAt, "Asia/Seoul") : "");
    const [end, setEnd] = useState(() => snapshot ? utcToLocalDateTime(snapshot.endsAt, "Asia/Seoul") : "");
    const [ids, setIds] = useState<string[]>(() => snapshot?.participants.flatMap(p => p.memberUserId ? [p.memberUserId] : []) ?? []);
    const [external, setExternal] = useState(() => snapshot?.participants.flatMap(p => p.externalEmail ? [p.externalEmail] : []).join(", ") ?? "");
    const [errors, setErrors] = useState<Record<string, string>>({});
    const qc = useQueryClient();
    const save = useMutation({ mutationFn: (body: CreateBody) => snapshot ? api.edit(project.id, snapshot.id, { ...body, rowVersion: snapshot.rowVersion }) : api.create(project.id, body), onSuccess: async (value) => { await refreshSchedule(qc, project.id, value.id); go(`/projects/${project.id}/schedules/${value.id}`); } });
    const caps = capabilities(project, userId, current);
    const editable = snapshot ? caps.edit : caps.create;
    const disabled = !editable || save.isPending;
    const submit = (event: React.FormEvent) => {
        event.preventDefault();
        if (disabled || !membersReady)
            return;
        const next: Record<string, string> = {};
        if (!title.trim())
            next.title = "제목을 입력하세요.";
        if (!validZone(zone))
            next.zone = "지원하지 않는 IANA 시간대입니다.";
        let startsAt = "";
        let endsAt = "";
        if (!next.zone) {
            try {
                startsAt = snapshot && zone === "Asia/Seoul" && start === utcToLocalDateTime(snapshot.startsAt, zone) ? new Date(snapshot.startsAt).toISOString() : localDateTimeToUtc(start, zone);
            }
            catch (error) {
                next.start = (error as Error).message;
            }
            try {
                endsAt = snapshot && zone === "Asia/Seoul" && end === utcToLocalDateTime(snapshot.endsAt, zone) ? new Date(snapshot.endsAt).toISOString() : localDateTimeToUtc(end, zone);
            }
            catch (error) {
                next.end = (error as Error).message;
            }
            if (startsAt && endsAt && endsAt <= startsAt)
                next.end = "종료 시간은 시작 시간보다 뒤여야 합니다.";
        }
        const emails = external.split(",").map(s => s.trim()).filter(Boolean);
        if (emails.some(s => !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s)))
            next.external = "올바른 외부 참석자 이메일을 입력하세요.";
        setErrors(next);
        if (!Object.keys(next).length)
            save.mutate({ title, description: snapshot && !description && snapshot.description == null ? snapshot.description : description, startsAt, endsAt, memberParticipantIds: ids, externalAttendeeEmails: emails });
    };
    const fieldError = (key: string) => errors[key] ? <p role="alert" id={`error-${key}`}>{errors[key]}</p> : null;
    return <>{!editable && <p>현재 역할 또는 일정 상태로는 수정할 수 없습니다.</p>}<form noValidate onSubmit={submit}>
    <label>제목<input required value={title} disabled={disabled} aria-invalid={!!errors.title} onChange={e => setTitle(e.target.value)}/></label>{fieldError("title")}
    <label>설명<textarea value={description} disabled={disabled} onChange={e => setDescription(e.target.value)}/></label>
    <label>시간대<input value={zone} disabled={disabled} aria-invalid={!!errors.zone} onChange={e => setZone(e.target.value)}/></label>{fieldError("zone")}
    <label>시작<input type="datetime-local" value={start} disabled={disabled} aria-invalid={!!errors.start} onChange={e => setStart(e.target.value)}/></label>{fieldError("start")}
    <label>종료<input type="datetime-local" value={end} disabled={disabled} aria-invalid={!!errors.end} onChange={e => setEnd(e.target.value)}/></label>{fieldError("end")}
    <fieldset disabled={disabled || !membersReady}><legend>프로젝트 구성원 참석자</legend>{membersReady && !members.length && <p>선택할 구성원이 없습니다.</p>}{members.map(m => <label key={m.userId}><input type="checkbox" checked={ids.includes(m.userId)} onChange={e => setIds(old => e.target.checked ? [...old, m.userId] : old.filter(id => id !== m.userId))}/>{m.userId}</label>)}{ids.filter(id => !members.some(m => m.userId === id)).map(id => <label key={id}><input type="checkbox" checked onChange={() => setIds(old => old.filter(v => v !== id))}/>{id} (저장된 참석자)</label>)}</fieldset>
    <label>외부 참석자 이메일<input value={external} disabled={disabled} aria-invalid={!!errors.external} onChange={e => setExternal(e.target.value)}/></label>{fieldError("external")}
    <p>일정을 확정하면 Google Calendar에 단방향 투영합니다. 이후 수정·취소도 Calendar에 반영하며 외부 전송 실패는 일정 저장을 취소하지 않습니다. 외부 참석자는 앱 접근권한이나 변경 확인 권한을 얻지 않습니다.</p><p>IANA 시간대는 입력 시간을 UTC로 변환하는 데 사용합니다. 시간대를 바꾸면 입력한 현지 시각의 의미가 바뀝니다. 현재 API는 시간대 이름을 저장하지 않습니다.</p>
    {save.isError && <><Notice error={save.error}/><p>입력한 내용은 유지됩니다. 충돌한 경우 상세 화면에서 최신 버전을 확인한 뒤 다시 편집하세요.</p></>}<button disabled={disabled || !membersReady}>일정 저장</button>
  </form></>;
}
