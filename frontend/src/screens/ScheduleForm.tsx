import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError, type CreateBody, type Members, type Project, type Schedule, type ScheduleProperty } from "../api/client";
import { accessKey, accessRead, accessReadCanClear, capabilities, clearAccessDenial, isAccessError, keys, recordAccessDenial, refreshSchedule, useAccessDenied, useMe, useProject } from "../state";
import { captureSession, isSessionContextActive } from "../session";
import { go, Link, Notice, ProjectMissing, QueryState, RetryButton, Shell } from "../ui";
import { useUnsavedChanges } from "../unsaved-changes";
import { localDateTimeToUtc, utcToLocalDateTime, validZone } from "../time";
import "./schedules.css";
export function ScheduleForm({ id, scheduleId, queryString }: {
    id: string;
    scheduleId?: string;
    queryString?: string;
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
    return <Shell project={project.data} workspace><div className="schedule-screen schedule-editor-page"><header className="schedule-heading"><div><p className="eyebrow">{project.data.name} · 일정</p><h1>{scheduleId ? "일정 수정" : "일정 만들기"}</h1><p className="lead">제목과 시간부터 입력한 뒤 필요한 세부 정보를 펼쳐서 입력한다.</p></div></header><QueryState query={project} loadingMessage="프로젝트를 불러오는 중입니다." errorMessage="프로젝트를 불러오지 못했습니다."/>{scheduleId && <QueryState query={detail}/>}<QueryState query={members} label="구성원 다시 시도"/><Editor key={scheduleId ?? "new"} id={id} scheduleId={scheduleId} queryString={queryString} project={project.data} userId={me.data!.id} current={detail.data} members={members.data ?? []} membersReady={membersReady} accessReady={accessReady} writeDenied={writeDenied} onAccessRetry={recoverAccess}/>{membersReady && (memberPage > 0 || members.data.length >= 100) && <div className="schedule-member-pagination"><button disabled={!memberPage || members.isFetching} onClick={() => setMemberPage(memberPage - 1)}>이전 참석자 목록</button><button disabled={members.data.length < 100 || members.isFetching} onClick={() => setMemberPage(memberPage + 1)}>다음 참석자 목록</button></div>}</div></Shell>;
}

function localDate(value: Date) {
    return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(value);
}
function addLocalHour(value: string, zone: string) {
    try {
        const instant = new Date(Date.parse(localDateTimeToUtc(value, zone)) + 3600000).toISOString();
        return { local: utcToLocalDateTime(instant, zone), instant };
    }
    catch { return { local: value, instant: undefined }; }
}
function defaultStart() {
    const now = new Date();
    const date = localDate(now);
    const current = new Intl.DateTimeFormat("en-GB", { timeZone: "Asia/Seoul", hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(now);
    const [hour, minute] = current.split(":").map(Number);
    const next = Math.ceil((hour * 60 + minute + (now.getSeconds() || now.getMilliseconds() ? 1 : 0)) / 30) * 30;
    if (next < 24 * 60) return `${date}T${String(Math.floor(next / 60)).padStart(2, "0")}:${String(next % 60).padStart(2, "0")}`;
    const tomorrow = new Date(now.getTime() + 86400000);
    return `${localDate(tomorrow)}T00:00`;
}
function contextPrefill(queryString?: string) {
    const params = new URLSearchParams(queryString ?? "");
    const date = params.get("date");
    const startParam = params.get("start");
    const endParam = params.get("end");
    const zoneParam = params.get("zone");
    const zone = zoneParam && validZone(zoneParam) ? zoneParam : "Asia/Seoul";
    const validDate = (value: string | null) => {
        if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return undefined;
        const datePart = value.slice(0, 10);
        const instant = Date.parse(`${datePart}T12:00:00Z`);
        if (!Number.isFinite(instant) || new Date(instant).toISOString().slice(0, 10) !== datePart) return undefined;
        return value;
    };
    const validDateTime = (value: string | null) => {
        if (!value || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value) || !validDate(value.slice(0, 10))) return undefined;
        try { localDateTimeToUtc(value, zone); return value; } catch { return undefined; }
    };
    const contextDate = validDate(date);
    const validStart = validDateTime(startParam);
    const validEnd = validDateTime(endParam);
    const start = validStart ?? (contextDate ? `${contextDate}T09:00` : defaultStart());
    const automaticEnd = addLocalHour(start, zone);
    return { start, end: validEnd ?? automaticEnd.local, endInstant: validEnd ? undefined : automaticEnd.instant, zone };
}

function PropertyInput({ property, value, onChange }: { property: ScheduleProperty; value: string | number | boolean | null | undefined; onChange: (value: string | number | boolean | null) => void }) {
    const label = `${property.name}${property.archived ? " · Archived" : ""}`;
    if (property.type === "CHECKBOX") return <label className="schedule-member-option"><input type="checkbox" checked={value === true} onChange={event => onChange(event.target.checked)} />{label}<button type="button" onClick={() => onChange(null)}>설정 안 함</button></label>;
    if (property.type === "SINGLE_SELECT") return <label>{label}<select value={value == null ? "" : String(value)} onChange={event => onChange(event.target.value || null)}><option value="">Unset</option>{property.options.filter(option => !option.archived || option.id === value).map(option => <option key={option.id} value={option.id}>{option.label}{option.archived ? " · Archived" : ""}</option>)}</select></label>;
    const inputType = property.type === "NUMBER" ? "number" : property.type === "DATE" ? "date" : "text";
    return <label>{label}<input type={inputType} step={property.type === "NUMBER" ? "any" : undefined} value={value == null ? "" : String(value)} onChange={event => { const raw = event.target.value; if (!raw) onChange(null); else if (property.type === "NUMBER") onChange(Number(raw)); else onChange(raw); }} /></label>;
}

function Editor({ id, scheduleId, queryString, project, userId, current, members, membersReady, accessReady, writeDenied, onAccessRetry }: {
    id: string;
    scheduleId?: string;
    queryString?: string;
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
    const prefill = useMemo(() => contextPrefill(queryString), [queryString]);
    const [title, setTitle] = useState(snapshot?.title ?? "");
    const [description, setDescription] = useState(snapshot?.description ?? "");
    const [zone, setZone] = useState(snapshot ? "Asia/Seoul" : prefill.zone);
    const [start, setStart] = useState(() => snapshot ? utcToLocalDateTime(snapshot.startsAt, "Asia/Seoul") : prefill.start);
    const [end, setEnd] = useState(() => snapshot ? utcToLocalDateTime(snapshot.endsAt, "Asia/Seoul") : prefill.end);
    const endEdited = useRef(Boolean(snapshot));
    const rowVersionRef = useRef(snapshot?.rowVersion);
    const [conflict, setConflict] = useState<Schedule | null>(null);
    const [conflictReadFailed, setConflictReadFailed] = useState(false);
    const [ids, setIds] = useState<string[]>(() => snapshot?.participants.flatMap(p => p.memberUserId ? [p.memberUserId] : []) ?? []);
    const [external, setExternal] = useState(() => snapshot?.participants.flatMap(p => p.externalEmail ? [p.externalEmail] : []).join(", ") ?? "");
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [detailsOpen, setDetailsOpen] = useState(false);
    const workspaceAccessKey = accessKey("workspace", project.id);
    const workspace = useQuery({ queryKey: keys.workspace(project.id), queryFn: () => accessRead(workspaceAccessKey, () => api.workspace(project.id)), enabled: true });
    const workspaceRecord = useQuery({ queryKey: keys.workspaceRecord(project.id, snapshot?.id ?? ""), queryFn: () => accessRead(accessKey("workspace-record", project.id, snapshot!.id), () => api.workspaceRecord(project.id, snapshot!.id)), enabled: !!snapshot });
    const [values, setValues] = useState<Record<string, string | number | boolean | null>>({});
    const [valuesDirty, setValuesDirty] = useState(false);
    useEffect(() => { if (workspaceRecord.data && !valuesDirty) setValues(workspaceRecord.data.values); }, [valuesDirty, workspaceRecord.data]);
    const automaticEndInstant = useRef(snapshot ? undefined : prefill.endInstant);
    const instantBaseline = useRef(snapshot ? { zone: "Asia/Seoul", startLocal: utcToLocalDateTime(snapshot.startsAt, "Asia/Seoul"), endLocal: utcToLocalDateTime(snapshot.endsAt, "Asia/Seoul"), startsAt: snapshot.startsAt, endsAt: snapshot.endsAt } : null);
    const initialDraft = useRef({ title: snapshot?.title ?? "", description: snapshot?.description ?? "", zone: snapshot ? "Asia/Seoul" : prefill.zone, start: snapshot ? utcToLocalDateTime(snapshot.startsAt, "Asia/Seoul") : prefill.start, end: snapshot ? utcToLocalDateTime(snapshot.endsAt, "Asia/Seoul") : prefill.end, ids: snapshot?.participants.flatMap(p => p.memberUserId ? [p.memberUserId] : []) ?? [], external: snapshot?.participants.flatMap(p => p.externalEmail ? [p.externalEmail] : []).join(", ") ?? "" });
    const dirty = title !== initialDraft.current.title || description !== initialDraft.current.description || zone !== initialDraft.current.zone || start !== initialDraft.current.start || end !== initialDraft.current.end || ids.join(",") !== initialDraft.current.ids.join(",") || external !== initialDraft.current.external || valuesDirty;
    const { confirmDiscard, markSaved } = useUnsavedChanges(dirty);
    const previousQuery = useRef(queryString ?? "");
    useEffect(() => {
        const nextQuery = queryString ?? "";
        if (scheduleId || nextQuery === previousQuery.current) return;
        if (!confirmDiscard()) {
            const route = window.location.hash.replace(/^#/, "").split("?", 1)[0];
            window.history.replaceState(null, "", `#${route}${previousQuery.current ? `?${previousQuery.current}` : ""}`);
            window.dispatchEvent(new HashChangeEvent("hashchange"));
            return;
        }
        const next = contextPrefill(nextQuery);
        setTitle(""); setDescription(""); setZone(next.zone); setStart(next.start); setEnd(next.end); setIds([]); setExternal(""); setErrors({}); setDetailsOpen(false);
        endEdited.current = false;
        automaticEndInstant.current = next.endInstant;
        initialDraft.current = { title: "", description: "", zone: next.zone, start: next.start, end: next.end, ids: [], external: "" };
        previousQuery.current = nextQuery;
    }, [confirmDiscard, queryString, scheduleId]);
    useEffect(() => {
        if (!Object.keys(errors).length) return;
        requestAnimationFrame(() => document.querySelector<HTMLInputElement>("input[aria-invalid='true'], textarea[aria-invalid='true']")?.focus());
    }, [errors]);
    const qc = useQueryClient();
    const writeAccessKey = `schedule-write:${project.id}:${snapshot?.id ?? "new"}`;
    const save = useMutation({ mutationFn: (body: CreateBody) => {
        if (!workspace.data || (snapshot && !workspaceRecord.data)) throw new Error("프로젝트 속성 준비가 끝나지 않았습니다.");
        return snapshot ? api.workspaceRecordEdit(project.id, snapshot.id, { schedule: { ...body, rowVersion: rowVersionRef.current ?? snapshot.rowVersion }, values }) : api.workspaceRecordCreate(project.id, { schedule: body, values });
    }, onMutate: captureSession, onError: async (error, _body, context) => { if (!isSessionContextActive(context)) return; if (isAccessError(error)) recordAccessDenial(writeAccessKey); if (snapshot && error instanceof ApiError && error.status === 409) { try { const latest = await api.detail(project.id, snapshot.id); if (isSessionContextActive(context)) { setConflict(latest); setConflictReadFailed(false); } } catch { if (isSessionContextActive(context)) setConflictReadFailed(true); } } }, onSuccess: async (value, _body, context) => { if (!isSessionContextActive(context)) return; markSaved(); const record = (value as { schedule: Schedule }).schedule; await refreshSchedule(qc, project.id, record.id); if (!isSessionContextActive(context)) return; go(`/projects/${project.id}/schedules/${record.id}`); } });
    const serverErrors: Record<string, string[]> = {};
    const inputFields: Record<string, string> = { title: "title", description: "description", startsAt: "start", endsAt: "end", zone: "zone", timeZone: "zone", memberParticipantIds: "members", externalAttendeeEmails: "external" };
    if (save.error instanceof ApiError) {
        for (const error of save.error.problem.fieldErrors ?? []) {
            const key = inputFields[error.field.replace(/\[.*$|\..*$/g, "")];
            if (key)
                (serverErrors[key] ??= []).push(error.message);
        }
    }
    useEffect(() => { if (Object.keys(serverErrors).some(key => !["title", "start", "end"].includes(key))) setDetailsOpen(true); }, [save.error]);
    const fieldAttributes = (key: string) => ({
        "aria-invalid": !!errors[key] || !!serverErrors[key],
        "aria-describedby": [errors[key] ? `error-${key}` : "", serverErrors[key] ? `server-error-${key}` : ""].filter(Boolean).join(" ") || undefined
    });
    const caps = capabilities(project, userId, current);
    const editable = snapshot ? caps.edit : caps.create;
    const denied = save.isError && isAccessError(save.error);
    const metadataReady = workspace.isSuccess && (!snapshot || workspaceRecord.isSuccess);
    const disabled = !editable || save.isPending || denied || writeDenied || !metadataReady;
    const buildBody = () => {
        if (disabled || !membersReady || !accessReady)
            return null;
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
                startsAt = instantBaseline.current && zone === instantBaseline.current.zone && start === instantBaseline.current.startLocal ? new Date(instantBaseline.current.startsAt).toISOString() : localDateTimeToUtc(start, zone);
            }
            catch (error) {
                next.start = (error as Error).message;
            }
            try {
                endsAt = !endEdited.current && automaticEndInstant.current && end === utcToLocalDateTime(automaticEndInstant.current, zone)
                    ? automaticEndInstant.current
                    : instantBaseline.current && zone === instantBaseline.current.zone && end === instantBaseline.current.endLocal ? new Date(instantBaseline.current.endsAt).toISOString() : localDateTimeToUtc(end, zone);
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
        for (const property of workspace.data?.properties ?? []) {
            const value = values[property.id];
            if (property.type === "NUMBER" && typeof value === "number" && !Number.isFinite(value)) next[`property-${property.id}`] = `${property.name}에는 유효한 숫자를 입력하세요.`;
        }
        setErrors(next);
        if (Object.keys(next).length) setDetailsOpen(true);
        if (Object.keys(next).length) return null;
        return { title, description: snapshot && !description && snapshot.description == null ? snapshot.description : description, startsAt, endsAt, memberParticipantIds: ids, externalAttendeeEmails: emails } satisfies CreateBody;
    };
    const submit = (event: React.FormEvent) => {
        event.preventDefault();
        const body = buildBody();
        if (body) save.mutate(body);
    };
    const fieldError = (key: string) => <>{errors[key] && <p role="alert" id={`error-${key}`}>{errors[key]}</p>}{serverErrors[key] && <p role="alert" id={`server-error-${key}`}>{serverErrors[key].join(" ")}</p>}</>;
    const memberInfo = (member: Members[number]) => member as Members[number] & { displayName?: string; email?: string | null };
    const storedParticipantInfo = (id: string) => snapshot?.participants.find(participant => participant.memberUserId === id);
    const participantLabel = (id: string) => {
        const info = storedParticipantInfo(id);
        if (!info?.displayName && !info?.email) return "이름 없는 구성원 · 저장된 참석자";
        return `${info.displayName || "이름 없는 구성원"}${info.email ? ` · ${info.email}` : ""} · 저장된 참석자`;
    };
    if (workspace.isPending || (snapshot && workspaceRecord.isPending)) return <div className="schedule-screen schedule-editor"><p role="status">프로젝트 속성을 불러오는 중입니다…</p></div>;
    if (workspace.isError || workspaceRecord.isError) return <div className="schedule-screen schedule-editor" role="alert"><p>프로젝트 속성을 불러오지 못했습니다. 모든 초안을 유지했습니다.</p><button type="button" onClick={() => { void workspace.refetch(); if (snapshot) void workspaceRecord.refetch(); }}>다시 시도</button></div>;
    return <div className="schedule-screen schedule-editor">
        {!editable && <p className="schedule-inline-alert">현재 역할 또는 일정 상태로는 수정할 수 없습니다.</p>}
        <form noValidate onSubmit={submit}>
            <fieldset className="schedule-form-section" disabled={disabled}><legend>기본 정보</legend><label>제목<input required autoFocus={!scheduleId} value={title} disabled={disabled} {...fieldAttributes("title")} onChange={event => setTitle(event.target.value)} /></label>{fieldError("title")}</fieldset>
            <fieldset className="schedule-form-section" disabled={disabled}><legend>날짜와 시간</legend><div className="schedule-date-fields"><label>시작<input type="datetime-local" value={start} disabled={disabled} {...fieldAttributes("start")} onChange={event => { setStart(event.target.value); if (!endEdited.current) { const automatic = addLocalHour(event.target.value, zone); setEnd(automatic.local); automaticEndInstant.current = automatic.instant; } }} /></label>{fieldError("start")}<label>종료<input type="datetime-local" value={end} disabled={disabled} {...fieldAttributes("end")} onChange={event => { endEdited.current = true; automaticEndInstant.current = undefined; setEnd(event.target.value); }} /></label>{fieldError("end")}</div></fieldset>
            <details className="schedule-secondary" open={detailsOpen} onToggle={event => setDetailsOpen(event.currentTarget.open)}><summary>세부 정보 <span>시간대·설명·참석자·외부 이메일</span></summary><fieldset className="schedule-form-section schedule-zone-section" disabled={disabled}><legend>시간대</legend><label>시간대<input value={zone} disabled={disabled} {...fieldAttributes("zone")} onChange={event => { const nextZone = event.target.value; setZone(nextZone); if (!endEdited.current && validZone(nextZone)) { const automatic = addLocalHour(start, nextZone); setEnd(automatic.local); automaticEndInstant.current = automatic.instant; } }} /></label>{fieldError("zone")}</fieldset><fieldset className="schedule-form-section" disabled={disabled}><legend>설명</legend><label>설명<textarea rows={4} value={description} disabled={disabled} {...fieldAttributes("description")} onChange={event => setDescription(event.target.value)} /></label>{fieldError("description")}</fieldset><fieldset className="schedule-form-section" disabled={disabled || !membersReady} {...fieldAttributes("members")}><legend>참석자</legend><p className="schedule-help">프로젝트 구성원은 앱에서 일정 확인을 할 수 있습니다. 외부 참석자는 Calendar 일정에만 표시됩니다.</p><h3>프로젝트 구성원</h3>{membersReady && !members.length && <p>선택할 구성원이 없습니다.</p>}{members.map(member => { const info = memberInfo(member); return <label className="schedule-member-option" key={member.userId}><input type="checkbox" checked={ids.includes(member.userId)} onChange={event => setIds(old => event.target.checked ? [...old, member.userId] : old.filter(id => id !== member.userId))} /><span>{info.displayName || "이름 없는 구성원"}{info.email ? ` · ${info.email}` : ""}</span></label>; })}{ids.filter(id => !members.some(member => member.userId === id)).map(id => <label className="schedule-member-option" key={id}><input type="checkbox" checked onChange={() => setIds(old => old.filter(value => value !== id))} /><span>{participantLabel(id)}</span></label>)}{fieldError("members")}<label>외부 참석자 이메일<input value={external} disabled={disabled} {...fieldAttributes("external")} onChange={event => setExternal(event.target.value)} placeholder="guest@example.com, partner@example.com" /></label>{fieldError("external")}</fieldset><p className="schedule-help schedule-form-note">일정을 확정하면 Google Calendar에 단방향 투영합니다. 외부 참석자는 앱 접근권한이나 변경 확인 권한을 얻지 않습니다.</p></details>
            <QueryState query={workspace} loadingMessage="프로젝트 속성을 불러오는 중입니다." errorMessage="프로젝트 속성을 불러오지 못했습니다." />
            {!!workspace.data?.properties.length && <fieldset className="schedule-form-section" disabled={disabled}><legend>Project properties</legend>{workspace.data.properties.slice().sort((a, b) => a.position - b.position || a.id.localeCompare(b.id)).map(property => property.archived ? <p key={property.id}>{property.name} · 보관됨 · {values[property.id] == null ? "설정 안 함" : String(values[property.id])}<button type="button" disabled={disabled} onClick={() => { setValuesDirty(true); setValues(current => ({ ...current, [property.id]: null })); }}>지우기</button></p> : <PropertyInput key={property.id} property={property} value={values[property.id]} onChange={value => { setValuesDirty(true); setValues(current => ({ ...current, [property.id]: value })); }} />)}</fieldset>}
            {save.isError && <div className="schedule-form-feedback"><Notice error={save.error} /><p>입력한 내용은 유지됩니다. 충돌한 경우 아래 값을 비교한 뒤 처리하세요.</p>{conflict && <><dl className="schedule-conflict-comparison"><div><dt>내 입력</dt><dd>{title || "제목 없음"} · {start} ~ {end}</dd></div><div><dt>서버 최신값</dt><dd>{conflict.title} · {utcToLocalDateTime(conflict.startsAt, zone)} ~ {utcToLocalDateTime(conflict.endsAt, zone)}</dd></div></dl><div className="schedule-conflict-actions"><button type="button" onClick={() => { const body = buildBody(); if (!body) return; rowVersionRef.current = conflict.rowVersion; setConflict(null); save.reset(); save.mutate(body); }}>내 입력으로 즉시 재시도</button><button type="button" onClick={() => { const nextStart = utcToLocalDateTime(conflict.startsAt, zone); const nextEnd = utcToLocalDateTime(conflict.endsAt, zone); const nextIds = conflict.participants.flatMap(p => p.memberUserId ? [p.memberUserId] : []); const nextExternal = conflict.participants.flatMap(p => p.externalEmail ? [p.externalEmail] : []).join(", "); setTitle(conflict.title); setDescription(conflict.description ?? ""); setStart(nextStart); setEnd(nextEnd); setIds(nextIds); setExternal(nextExternal); rowVersionRef.current = conflict.rowVersion; instantBaseline.current = { zone, startLocal: nextStart, endLocal: nextEnd, startsAt: conflict.startsAt, endsAt: conflict.endsAt }; initialDraft.current = { title: conflict.title, description: conflict.description ?? "", zone, start: nextStart, end: nextEnd, ids: nextIds, external: nextExternal }; endEdited.current = true; automaticEndInstant.current = undefined; setConflict(null); save.reset(); }}>최신값 적용</button></div></>}{conflictReadFailed && <button type="button" onClick={async () => { if (!snapshot || disabled) return; try { const latest = await api.detail(project.id, snapshot.id); setConflict(latest); setConflictReadFailed(false); } catch { setConflictReadFailed(true); } }}>최신값 다시 불러오기</button>}{denied && <RetryButton onRetry={async () => { if (await onAccessRetry()) save.reset(); }} />}</div>}{writeDenied && !save.isError && <div className="schedule-form-feedback"><p>일정 저장 권한을 다시 확인해야 합니다. 입력한 내용은 유지됩니다.</p><RetryButton onRetry={async () => { if (await onAccessRetry()) save.reset(); }}>접근 상태 다시 확인</RetryButton></div>}
            <div className="schedule-form-actions"><Link className="button button-secondary" to={snapshot ? `/projects/${id}/schedules/${snapshot.id}` : `/projects/${id}/schedules`} onClick={event => { if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return; if (!confirmDiscard()) event.preventDefault(); }}>취소</Link><button className="button button-primary" type="submit" disabled={disabled || !membersReady}>일정 저장</button></div>
        </form>
    </div>;
}
