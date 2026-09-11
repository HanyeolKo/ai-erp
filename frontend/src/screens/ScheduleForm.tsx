import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError, type CreateBody, type Members, type Project, type Schedule } from "../api/client";
import { accessKey, accessRead, accessReadCanClear, capabilities, clearAccessDenial, isAccessError, keys, recordAccessDenial, refreshSchedule, useAccessDenied, useMe, useProject } from "../state";
import { captureSession, isSessionContextActive } from "../session";
import { go, Link, Notice, ProjectMissing, QueryState, RetryButton, Shell } from "../ui";
import { localDateTimeToUtc, utcToLocalDateTime, validZone } from "../time";
import "./schedules.css";
export function ScheduleForm({ id, scheduleId }: {
    id: string;
    scheduleId?: string;
}) {
    const project = useProject(id);
    const me = useMe();
    const [memberPage, setMemberPage] = useState(0);
    const projectAccessKey = accessKey("project", id);
    const projectDenied = useAccessDenied(projectAccessKey);
    const membersAccessKey = accessKey("members", id);
    const detailAccessKey = accessKey("detail", id, scheduleId ?? "");
    const writeAccessKey = accessKey("schedule-write", id, scheduleId ?? "new");
    const membersDenied = useAccessDenied(membersAccessKey);
    const detailDenied = useAccessDenied(detailAccessKey);
    const writeDenied = useAccessDenied(writeAccessKey);
    const members = useQuery({ queryKey: [...keys.members(id), memberPage], queryFn: () => accessRead(membersAccessKey, () => api.members(id, memberPage)), staleTime: membersDenied || writeDenied ? 0 : 15000, enabled: !projectDenied && project.isSuccess && !!project.data });
    const detail = useQuery({ queryKey: keys.detail(id, scheduleId ?? ""), queryFn: () => accessRead(detailAccessKey, () => api.detail(id, scheduleId!)), staleTime: detailDenied || writeDenied ? 0 : 15000, enabled: !!scheduleId && !projectDenied && project.isSuccess && !!project.data });
    useEffect(() => { if (members.isSuccess && !members.isFetching && accessReadCanClear(membersAccessKey)) clearAccessDenial(membersAccessKey); }, [membersAccessKey, members.isFetching, members.isSuccess]);
    useEffect(() => { if (detail.isSuccess && !detail.isFetching && accessReadCanClear(detailAccessKey)) clearAccessDenial(detailAccessKey); }, [detailAccessKey, detail.isFetching, detail.isSuccess]);
    if (projectDenied)
        return <Shell><h1>프로젝트 접근 상태를 확인할 수 없습니다</h1><QueryState query={project} loadingMessage="프로젝트를 불러오는 중입니다." errorMessage="프로젝트를 불러오지 못했습니다."/><RetryButton onRetry={() => project.refetch()} isFetching={project.isFetching}>접근 상태 다시 확인</RetryButton><Link to="/">프로젝트 선택</Link></Shell>;
    if (!project.isSuccess && (!project.data || isAccessError(project.error)))
        return <Shell><QueryState query={project} loadingMessage="프로젝트를 불러오는 중입니다." errorMessage="프로젝트를 불러오지 못했습니다."/></Shell>;
    if (!project.data)
        return <Shell><ProjectMissing onRetry={() => project.refetch()} isFetching={project.isFetching}/></Shell>;
    if (scheduleId && (detailDenied || (!detail.isSuccess && (!detail.data || isAccessError(detail.error)))))
        return <Shell project={project.data}><h1>일정 수정</h1><QueryState query={detail}/><Link to={`/projects/${id}/schedules`}>일정 목록으로</Link></Shell>;
    if (membersDenied || isAccessError(members.error))
        return <Shell><h1>구성원을 불러올 수 없습니다</h1><QueryState query={members}/></Shell>;
    const recoverAccess = async () => {
        const recoveryContext = captureSession();
        const membership = await project.refetch();
        if (!isSessionContextActive(recoveryContext)) return false;
        if (!membership.isSuccess || !membership.data) return false;
        const [people, latest] = await Promise.all([members.refetch(), scheduleId ? detail.refetch() : Promise.resolve(undefined)]);
        if (!isSessionContextActive(recoveryContext)) return false;
        const recovered = people.isSuccess && (!scheduleId || latest?.isSuccess === true);
        if (recovered) clearAccessDenial(writeAccessKey);
        return recovered;
    };
    const membersReady = members.isSuccess && !membersDenied && !members.isFetching;
    const accessReady = project.isSuccess && !projectDenied && !writeDenied && membersReady && (!scheduleId || (detail.isSuccess && !detailDenied && !detail.isFetching));
    return <Shell project={project.data}><h1>{scheduleId ? "일정 수정" : "일정 만들기"}</h1><QueryState query={project} loadingMessage="프로젝트를 불러오는 중입니다." errorMessage="프로젝트를 불러오지 못했습니다."/>{scheduleId && <QueryState query={detail}/>}<QueryState query={members} label="구성원 다시 시도"/><Editor key={scheduleId ?? "new"} id={id} scheduleId={scheduleId} project={project.data} userId={me.data!.id} current={detail.data} members={members.data ?? []} membersReady={membersReady} accessReady={accessReady} writeDenied={writeDenied} onAccessRetry={recoverAccess}/>{membersReady && <><button disabled={!memberPage || members.isFetching} onClick={() => setMemberPage(memberPage - 1)}>이전 참석자 목록</button><button disabled={members.data.length < 100 || members.isFetching} onClick={() => setMemberPage(memberPage + 1)}>다음 참석자 목록</button></>}</Shell>;
}
function Editor({ id, scheduleId, project, userId, current, members, membersReady, accessReady, writeDenied, onAccessRetry }: {
    id: string;
    scheduleId?: string;
    project: Project;
    userId: string;
    current?: Schedule;
    members: Members;
    membersReady: boolean;
    accessReady: boolean;
    writeDenied: boolean;
    onAccessRetry: () => Promise<boolean>;
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
    const writeAccessKey = `schedule-write:${project.id}:${snapshot?.id ?? "new"}`;
    const save = useMutation({ mutationFn: (body: CreateBody) => snapshot ? api.edit(project.id, snapshot.id, { ...body, rowVersion: snapshot.rowVersion }) : api.create(project.id, body), onMutate: captureSession, onError: (error, _body, context) => { if (isSessionContextActive(context) && isAccessError(error)) recordAccessDenial(writeAccessKey); }, onSuccess: async (value, _body, context) => { if (!isSessionContextActive(context)) return; await refreshSchedule(qc, project.id, value.id); if (!isSessionContextActive(context)) return; go(`/projects/${project.id}/schedules/${value.id}`); } });
    const serverErrors: Record<string, string[]> = {};
    const inputFields: Record<string, string> = { title: "title", description: "description", startsAt: "start", endsAt: "end", memberParticipantIds: "members", externalAttendeeEmails: "external" };
    if (save.error instanceof ApiError) {
        for (const error of save.error.problem.fieldErrors ?? []) {
            const key = inputFields[error.field.replace(/\[.*$|\..*$/g, "")];
            if (key)
                (serverErrors[key] ??= []).push(error.message);
        }
    }
    const fieldAttributes = (key: string) => ({
        "aria-invalid": !!errors[key] || !!serverErrors[key],
        "aria-describedby": [errors[key] ? `error-${key}` : "", serverErrors[key] ? `server-error-${key}` : ""].filter(Boolean).join(" ") || undefined
    });
    const caps = capabilities(project, userId, current);
    const editable = snapshot ? caps.edit : caps.create;
    const denied = save.isError && isAccessError(save.error);
    const disabled = !editable || save.isPending || denied || writeDenied;
    const submit = (event: React.FormEvent) => {
        event.preventDefault();
        if (disabled || !membersReady || !accessReady)
            return;
        save.reset();
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
        if (Object.keys(next).length) requestAnimationFrame(() => document.querySelector<HTMLInputElement>("input[aria-invalid='true'], textarea[aria-invalid='true']")?.focus());
        if (!Object.keys(next).length)
            save.mutate({ title, description: snapshot && !description && snapshot.description == null ? snapshot.description : description, startsAt, endsAt, memberParticipantIds: ids, externalAttendeeEmails: emails });
    };
    const fieldError = (key: string) => <>{errors[key] && <p role="alert" id={`error-${key}`}>{errors[key]}</p>}{serverErrors[key] && <p role="alert" id={`server-error-${key}`}>{serverErrors[key].join(" ")}</p>}</>;
    const memberInfo = (member: Members[number]) => member as Members[number] & { displayName?: string; email?: string | null };
    const storedParticipantInfo = (id: string) => snapshot?.participants.find(participant => participant.memberUserId === id);
    const participantLabel = (id: string) => {
        const info = storedParticipantInfo(id);
        if (!info?.displayName && !info?.email) return "이름 없는 구성원 · 저장된 참석자";
        return `${info.displayName || "이름 없는 구성원"}${info.email ? ` · ${info.email}` : ""} · 저장된 참석자`;
    };
    return <div className="schedule-screen schedule-editor">
        {!editable && <p className="schedule-inline-alert">현재 역할 또는 일정 상태로는 수정할 수 없습니다.</p>}
        <form noValidate onSubmit={submit}>
            <fieldset className="schedule-form-section" disabled={disabled}><legend>기본 정보</legend><label>제목<input required autoFocus={!scheduleId} value={title} disabled={disabled} {...fieldAttributes("title")} onChange={event => setTitle(event.target.value)} /></label>{fieldError("title")}<label>설명<textarea rows={4} value={description} disabled={disabled} {...fieldAttributes("description")} onChange={event => setDescription(event.target.value)} /></label>{fieldError("description")}</fieldset>
            <fieldset className="schedule-form-section" disabled={disabled}><legend>날짜와 시간</legend><p className="schedule-help">입력한 현지 시간을 선택한 IANA 시간대 기준으로 저장합니다.</p><label>시간대<input value={zone} disabled={disabled} {...fieldAttributes("zone")} onChange={event => setZone(event.target.value)} /></label>{fieldError("zone")}<div className="schedule-date-fields"><label>시작<input type="datetime-local" value={start} disabled={disabled} {...fieldAttributes("start")} onChange={event => setStart(event.target.value)} /></label>{fieldError("start")}<label>종료<input type="datetime-local" value={end} disabled={disabled} {...fieldAttributes("end")} onChange={event => setEnd(event.target.value)} /></label>{fieldError("end")}</div></fieldset>
            <fieldset className="schedule-form-section" disabled={disabled || !membersReady} {...fieldAttributes("members")}><legend>참석자</legend><p className="schedule-help">프로젝트 구성원은 앱에서 일정 확인을 할 수 있습니다. 외부 참석자는 Calendar 일정에만 표시됩니다.</p><h3>프로젝트 구성원</h3>{membersReady && !members.length && <p>선택할 구성원이 없습니다.</p>}{members.map(member => { const info = memberInfo(member); return <label className="schedule-member-option" key={member.userId}><input type="checkbox" checked={ids.includes(member.userId)} onChange={event => setIds(old => event.target.checked ? [...old, member.userId] : old.filter(id => id !== member.userId))} /><span>{info.displayName || "이름 없는 구성원"}{info.email ? ` · ${info.email}` : ""}</span></label>; })}{ids.filter(id => !members.some(member => member.userId === id)).map(id => <label className="schedule-member-option" key={id}><input type="checkbox" checked onChange={() => setIds(old => old.filter(value => value !== id))} /><span>{participantLabel(id)}</span></label>)}{fieldError("members")}<label>외부 참석자 이메일<input value={external} disabled={disabled} {...fieldAttributes("external")} onChange={event => setExternal(event.target.value)} placeholder="guest@example.com, partner@example.com" /></label>{fieldError("external")}</fieldset>
            <p className="schedule-help schedule-form-note">일정을 확정하면 Google Calendar에 단방향 투영합니다. 외부 참석자는 앱 접근권한이나 변경 확인 권한을 얻지 않습니다.</p>
            {save.isError && <div className="schedule-form-feedback"><Notice error={save.error} /><p>입력한 내용은 유지됩니다. 충돌한 경우 최신 버전을 확인한 뒤 다시 편집하세요.</p>{denied && <RetryButton onRetry={async () => { if (await onAccessRetry()) save.reset(); }} />}</div>}{writeDenied && !save.isError && <div className="schedule-form-feedback"><p>일정 저장 권한을 다시 확인해야 합니다. 입력한 내용은 유지됩니다.</p><RetryButton onRetry={async () => { if (await onAccessRetry()) save.reset(); }}>접근 상태 다시 확인</RetryButton></div>}
            <div className="schedule-form-actions"><Link className="button button-secondary" to={snapshot ? `/projects/${id}/schedules/${snapshot.id}` : `/projects/${id}/schedules`}>취소</Link><button className="button button-primary" type="submit" disabled={disabled || !membersReady}>일정 저장</button></div>
        </form>
    </div>;
}
