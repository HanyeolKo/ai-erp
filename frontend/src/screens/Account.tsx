import { useEffect, useRef, useState, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../api/client";
import { accessKey, accessRead, accessReadCanClear, beginAccessRead, clearAccessDenial, completeAccessRead, isAccessError, keys, recordAccessDenial, useAccessDenied, useProject } from "../state";
import { captureSession, isSessionContextActive, terminateSession } from "../session";
import { go, Link, Shell, QueryState, ProjectMissing, Notice, invitationHash, internalLink } from "../ui";
import { ProjectStart } from "./ProjectStart";
function AuthShell({ children }: {
    children: ReactNode;
}) {
    return <main><header><Link to="/">AI ERP</Link></header><section>{children}</section></main>;
}
export function Auth({ children, sessionExpired = false }: {
    children: ReactNode;
    sessionExpired?: boolean;
}) {
    const config = useQuery({ queryKey: keys.configuration, queryFn: api.configuration });
    const me = useQuery({ queryKey: keys.me, queryFn: api.me, enabled: config.data?.login === "READY" && !sessionExpired });
    useEffect(() => {
        if (!me.isSuccess || sessionExpired)
            return;
        const saved = invitationHash(sessionStorage.getItem("ai-erp.invitation") ?? "");
        if (!window.location.hash || window.location.hash === "#/") {
            if (saved)
                window.location.hash = saved;
        }
        sessionStorage.removeItem("ai-erp.invitation");
    }, [me.isSuccess, sessionExpired]);
    if (!config.isSuccess)
        return <AuthShell><QueryState query={config}/></AuthShell>;
    if (config.data.login !== "READY" || !config.data.loginUrl)
        return <AuthShell><h1>AI ERP</h1><p>Google 로그인이 구성되지 않았습니다.</p></AuthShell>;
    if (sessionExpired || (me.isError && me.error instanceof ApiError && me.error.status === 401))
        return <AuthShell><h1>AI ERP</h1>{sessionExpired && <p role="status">로그인이 필요합니다. Google 계정으로 다시 로그인하세요.</p>}<a className="button" href={config.data.loginUrl} onClick={() => { const value = invitationHash(window.location.hash); if (value)
            sessionStorage.setItem("ai-erp.invitation", value); }}>Google로 로그인</a></AuthShell>;
    if (!me.isSuccess)
        return <AuthShell><QueryState query={me}/></AuthShell>;
    return <>{children}</>;
}

function AccountSwitch() {
    const logout = useMutation({ mutationFn: api.logout, onSuccess: () => {
        const continuation = invitationHash(window.location.hash);
        if (continuation) sessionStorage.setItem("ai-erp.invitation", continuation);
        else sessionStorage.removeItem("ai-erp.invitation");
        terminateSession();
    } });
    return <div className="account-recovery"><button type="button" disabled={logout.isPending} onClick={() => { if (!logout.isPending) logout.mutate(); }}>다른 계정으로 로그인</button>
        <p className="help">현재 AI ERP 세션을 종료한 뒤 Google에서 로그인할 계정을 선택하세요.</p>
        {logout.isPending && <p role="status">로그아웃하는 중입니다.</p>}
        {logout.isError && <Notice error={logout.error}/>}</div>;
}

function invitationPath(input: string) {
    const value = input.trim();
    if (/^#?\/invitations\/[A-Za-z0-9_-]+$/.test(value)) return value.replace(/^#/, "");
    try {
        const url = new URL(value);
        if (url.origin === window.location.origin && !url.username && !url.password && !url.search && url.pathname === "/" && invitationHash(url.hash)) return url.hash.slice(1);
    } catch { /* Invalid text stays in the form. */ }
    return undefined;
}

function InvitationEntry() {
    const [value, setValue] = useState("");
    const [invalid, setInvalid] = useState(false);
    const input = useRef<HTMLInputElement>(null);
    return <div className="recovery-panel"><h2>초대 링크로 프로젝트 참여</h2><p id="invitation-help">프로젝트 관리자에게 받은 초대 링크를 붙여 넣으세요. 초대 내용을 확인한 뒤 수락할 수 있습니다.</p>
        <form onSubmit={event => {
            event.preventDefault(); const path = invitationPath(value);
            if (path) { setInvalid(false); go(path); }
            else { setInvalid(true); input.current?.focus(); }
        }} noValidate>
            <label htmlFor="invitation-entry">초대 링크</label>
            <input id="invitation-entry" ref={input} type="text" value={value} onChange={event => { setValue(event.target.value); setInvalid(false); }} aria-invalid={invalid} aria-describedby={`invitation-help${invalid ? " invitation-error" : ""}`} autoComplete="off" spellCheck={false}/>
            {invalid && <p id="invitation-error" role="alert">초대 링크를 확인하세요. 현재 AI ERP 주소의 초대 링크 또는 #/invitations/로 시작하는 경로를 입력하세요.</p>}
            <button type="submit">초대 열기</button>
        </form></div>;
}

export function Projects() {
    const [page, setPage] = useState(0);
    const qc = useQueryClient();
    const me = useQuery({ queryKey: keys.me, queryFn: api.me });
    const projectsAccessKey = accessKey("project-list", String(page));
    const projectsDenied = useAccessDenied(projectsAccessKey);
    const projects = useQuery({ queryKey: page ? [...keys.projects, page] : keys.projects, queryFn: () => accessRead(projectsAccessKey, () => api.projects(page)), staleTime: projectsDenied ? 0 : 15000 });
    useEffect(() => { if (projects.isSuccess && !projects.isFetching && accessReadCanClear(projectsAccessKey)) clearAccessDenial(projectsAccessKey); }, [projectsAccessKey, projects.isFetching, projects.isSuccess]);
    const denied = projectsDenied || (projects.error instanceof ApiError && [401, 403, 404].includes(projects.error.status));
    const rows = denied ? undefined : projects.data;
    return <Shell><p className="account-id">계정 {me.data?.id}</p><h1>프로젝트</h1>
        <QueryState query={projects} loadingMessage="프로젝트를 불러오는 중입니다." errorMessage="프로젝트를 불러오지 못했습니다."/>
        {projects.isError && rows && <p>마지막으로 불러온 목록입니다. 현재 접근 상태를 다시 확인하세요.</p>}
        {projects.isSuccess && !rows?.length && <div className="empty-projects">
            <p>{page === 0 ? "참여 중인 프로젝트가 없습니다." : "이 페이지에 프로젝트가 없습니다."}</p>
            {page === 0 && <p>프로젝트를 만들거나 관리자에게 초대 링크를 받아 참여할 수 있습니다.</p>}
        </div>}
        {!!rows?.length && <ul className="project-list">{rows.map(p => <li key={p.id}><Link to={`/projects/${p.id}`}>{p.name}</Link><span>{p.role}</span></li>)}</ul>}
        {(page > 0 || (rows?.length ?? 0) >= 100) && <div className="toolbar" aria-label="프로젝트 페이지">
            <button disabled={!page || projects.isFetching} onClick={() => setPage(page - 1)}>이전 프로젝트</button>
            <button disabled={!projects.isSuccess || (rows?.length ?? 0) < 100 || projects.isFetching || page >= 10000} onClick={() => setPage(page + 1)}>다음 프로젝트</button>
        </div>}
        {!projects.isError && <button className="project-refresh" type="button" disabled={projects.isFetching} onClick={() => { if (!projects.isFetching) void qc.invalidateQueries({ queryKey: keys.projects }); }}>프로젝트 새로고침</button>}
        <ProjectStart accountId={me.data?.id}/><InvitationEntry/><AccountSwitch/>
    </Shell>;
}
export function Invite({ token }: {
    token: string;
}) {
    const qc = useQueryClient();
    const invitationAccessKey = accessKey("invitation", token);
    const invitationWriteKey = accessKey("invitation-write", token);
    const invitationDenied = useAccessDenied(invitationAccessKey);
    const invitationWriteDenied = useAccessDenied(invitationWriteKey);
    const invite = useQuery({ queryKey: keys.invitation(token), queryFn: async () => {
        const writeRevision = beginAccessRead(invitationWriteKey);
        const value = await accessRead(invitationAccessKey, () => api.invitation(token));
        completeAccessRead(invitationWriteKey, writeRevision);
        return value;
    }, staleTime: invitationDenied || invitationWriteDenied ? 0 : 15000 });
    useEffect(() => { if (invite.isSuccess && !invite.isFetching && accessReadCanClear(invitationAccessKey)) clearAccessDenial(invitationAccessKey); }, [invite.isFetching, invite.isSuccess, invitationAccessKey]);
    useEffect(() => { if (invite.isSuccess && !invite.isFetching && accessReadCanClear(invitationWriteKey)) clearAccessDenial(invitationWriteKey); }, [invite.isFetching, invite.isSuccess, invitationWriteKey]);
    const success = useRef<HTMLHeadingElement>(null);
    const resolve = useMutation({ mutationFn: (action: "accept" | "reject") => api.resolve(token, action), onMutate: captureSession, onError: error => { if (isAccessError(error)) recordAccessDenial(invitationWriteKey); }, onSuccess: async (value, _action, context) => {
            if (!isSessionContextActive(context)) return;
            qc.setQueryData(keys.invitation(token), value);
            await Promise.all([keys.invitation(token), keys.projects, keys.connection].map(queryKey => qc.invalidateQueries({ queryKey })));
            if (!isSessionContextActive(context)) return;
        } });
    const accepted = invite.data?.status === "ACCEPTED";
    const responseDenied = invitationWriteDenied || isAccessError(resolve.error);
    const recoveryNeeded = invitationWriteDenied || resolve.isError;
    useEffect(() => { if (accepted) success.current?.focus(); }, [accepted]);
    if (!invite.data || invitationDenied || isAccessError(invite.error) || (invite.error instanceof ApiError && invite.error.status === 401))
        return <Shell><h1>프로젝트 초대</h1><QueryState query={invite}/>{invite.error instanceof ApiError && invite.error.status === 403 && <><p>현재 Google 계정으로 이 초대에 접근할 수 없습니다. 초대받은 이메일과 로그인 계정이 같은지 확인하세요.</p><AccountSwitch/></>}<Link to="/">프로젝트 선택</Link></Shell>;
    const value = invite.data;
    const active = value.status === "PENDING" && new Date(value.expiresAt).getTime() > Date.now() && !resolve.isSuccess;
    return <Shell><h1>프로젝트 초대</h1><QueryState query={invite}/><p>이메일: {value.email}</p><p>상태: {value.status}</p><p>만료: <time dateTime={value.expiresAt}>{value.expiresAt}</time></p>{!responseDenied && <p>서버에서 현재 Google 계정의 인증된 이메일과 초대 이메일이 일치함을 확인했습니다.</p>}<p>초대를 수락하면 프로젝트에 참여할 수 있습니다. Calendar 공유 반영은 별도로 진행됩니다.</p>{active ? <><button disabled={resolve.isPending || !invite.isSuccess || responseDenied} onClick={() => { if (!resolve.isPending && invite.isSuccess && !responseDenied)
        resolve.mutate("accept"); }}>초대 수락</button><button disabled={resolve.isPending || !invite.isSuccess || responseDenied} onClick={() => { if (!resolve.isPending && invite.isSuccess && !responseDenied)
        resolve.mutate("reject"); }}>초대 거절</button></> : accepted ? <div className="recovery-panel"><h2 ref={success} tabIndex={-1}>프로젝트에 참여했습니다.</h2><Link to={`/projects/${value.projectId}`}>프로젝트 열기</Link></div> : <p>만료되었거나 이미 처리된 초대입니다. 참여하려면 프로젝트 관리자에게 새 초대 링크를 요청하세요.</p>}
        {resolve.isPending && <p role="status">초대 응답을 저장하고 있습니다.</p>}{resolve.isSuccess && <p role="status">초대 상태를 갱신했습니다.</p>}{recoveryNeeded && <>{resolve.isError && <Notice error={resolve.error}/>} {resolve.error instanceof ApiError && resolve.error.status === 403 && <AccountSwitch/>}<button disabled={invite.isFetching} onClick={async () => { if (!invite.isFetching) { const result = await invite.refetch(); if (result.isSuccess) { clearAccessDenial(invitationAccessKey); clearAccessDenial(invitationWriteKey); resolve.reset(); } } }}>최신 초대 불러오기</button></>}{<Link to="/">프로젝트 선택</Link>}</Shell>;
}
export function InviteCreate({ id }: {
    id: string;
}) {
    const project = useProject(id);
    const [email, setEmail] = useState("");
    const inviteAccessKey = accessKey("invite", id);
    const inviteDenied = useAccessDenied(inviteAccessKey);
    const invite = useMutation({ mutationFn: () => api.invite(project.data!.groupId, { projectId: id, email }), onMutate: captureSession, onError: (error) => { if (isAccessError(error)) { recordAccessDenial(inviteAccessKey); recordAccessDenial(accessKey("project", id)); } } });
    const accessDenied = invite.error instanceof ApiError && [403, 404].includes(invite.error.status);
    if (!project.isSuccess)
        return <Shell><QueryState query={project}/></Shell>;
    if (!project.data)
        return <Shell><ProjectMissing onRetry={() => project.refetch()} isFetching={project.isFetching}/></Shell>;
    return <Shell project={project.data}><h1>구성원 초대</h1>{project.data.role !== "MANAGER" ? <p>Manager만 초대를 만들 수 있습니다.</p> : <><form onSubmit={e => { e.preventDefault(); if (!invite.isPending && !accessDenied && !inviteDenied)
        invite.mutate(); }}><label>이메일<input type="email" required value={email} onChange={e => setEmail(e.target.value)}/></label><button disabled={invite.isPending || accessDenied || inviteDenied}>초대 만들기</button></form>{invite.isError && <Notice error={invite.error}/>}{(accessDenied || inviteDenied) && <button disabled={project.isFetching} onClick={async () => {
            if (!project.isFetching) { const result = await project.refetch(); if (result.isSuccess) { clearAccessDenial(inviteAccessKey); clearAccessDenial(accessKey("project", id)); invite.reset(); } }
        }}>접근 상태 다시 확인</button>}{invite.isSuccess && <div><p role="status">초대를 만들었습니다.</p><p>{invite.data.status} · 만료 {invite.data.expiresAt}</p><label>초대 링크<input readOnly value={window.location.href.split("#")[0] + "#/invitations/" + invite.data.token} onFocus={e => e.currentTarget.select()}/></label><Link to={`/invitations/${invite.data.token}`}>초대 열기</Link></div>}</>}</Shell>;
}
export function Notifications() {
    const qc = useQueryClient();
    const [page, setPage] = useState(0);
    const list = useQuery({ queryKey: [...keys.notifications, page], queryFn: () => api.notifications(page) });
    const read = useMutation({ mutationFn: api.readNotification, onMutate: captureSession, onSuccess: (_value, _id, context) => isSessionContextActive(context) ? qc.invalidateQueries({ queryKey: keys.notifications }) : undefined });
    return <Shell><h1>알림</h1><QueryState query={list}/>{list.isSuccess && <>{list.data.length ? <ul>{list.data.map(n => <li key={n.id}>{internalLink(n.link) ? <Link to={internalLink(n.link)!}>{n.type}</Link> : n.type} <time dateTime={n.createdAt}>{n.createdAt}</time> {n.readAt ? "읽음" : <button disabled={read.isPending} onClick={() => { if (!read.isPending)
        read.mutate(n.id); }}>읽음 처리</button>}</li>)}</ul> : <p>새 알림이 없습니다.</p>}<button disabled={!page} onClick={() => setPage(page - 1)}>이전 알림</button><button disabled={list.data.length < 100} onClick={() => setPage(page + 1)}>다음 알림</button></>}{read.isError && <Notice error={read.error}/>}{read.isSuccess && <p role="status">알림을 읽음으로 표시했습니다.</p>}</Shell>;
}
export function Members({ id }: {
    id: string;
}) {
    const project = useProject(id);
    const qc = useQueryClient();
    const [page, setPage] = useState(0);
    const membersAccessKey = accessKey("members", id);
    const membersDenied = useAccessDenied(membersAccessKey);
    const members = useQuery({ queryKey: [...keys.members(id), page], queryFn: () => accessRead(membersAccessKey, () => api.members(id, page)), staleTime: membersDenied ? 0 : 15000, enabled: !!project.data });
    useEffect(() => { if (members.isSuccess && !members.isFetching && accessReadCanClear(membersAccessKey)) clearAccessDenial(membersAccessKey); }, [membersAccessKey, members.isFetching, members.isSuccess]);
    const change = useMutation({ mutationFn: ({ userId, role }: {
            userId: string;
            role: string;
        }) => api.role(id, userId, { role }), onMutate: captureSession, onError: (error) => { if (isAccessError(error)) recordAccessDenial(accessKey("members", id)); }, onSettled: (_value, _error, _variables, context) => isSessionContextActive(context) ? Promise.all([qc.invalidateQueries({ queryKey: keys.members(id) }), qc.invalidateQueries({ queryKey: keys.projects })]) : undefined });
    if (!project.isSuccess)
        return <Shell><QueryState query={project}/></Shell>;
    if (!project.data)
        return <Shell><ProjectMissing onRetry={() => project.refetch()} isFetching={project.isFetching}/></Shell>;
    return <Shell project={project.data}><h1>구성원</h1><QueryState query={members}/>{membersDenied && <p>구성원 접근 권한을 다시 확인해야 합니다.</p>}{members.isSuccess && !membersDenied && !members.isFetching && <>{!members.data.length && <p>구성원이 없습니다.</p>}<ul>{members.data.map(m => <li key={m.userId}>{m.userId}{project.data!.role === "MANAGER" ? <label>{m.userId} 역할<select aria-label={`${m.userId} 역할`} value={m.role} disabled={change.isPending} onChange={e => { if (!change.isPending)
        change.mutate({ userId: m.userId, role: e.target.value }); }}><option>MANAGER</option><option>MEMBER</option><option>VIEWER</option></select></label> : <span>{m.role}</span>}</li>)}</ul><button disabled={!page} onClick={() => setPage(page - 1)}>이전 구성원</button><button disabled={members.data.length < 100} onClick={() => setPage(page + 1)}>다음 구성원</button></>}{change.isError && <Notice error={change.error}/>}{change.isSuccess && <p role="status">역할을 저장했습니다.</p>}</Shell>;
}
export function Calendar() {
    const qc = useQueryClient();
    const connection = useQuery({ queryKey: keys.connection, queryFn: api.calendar });
    const reconnect = useMutation({ mutationFn: api.reconnect, onMutate: captureSession, onSuccess: async (value, _variables, context) => { if (!isSessionContextActive(context)) return; qc.setQueryData(keys.connection, value); await qc.invalidateQueries({ queryKey: keys.connection }); if (!isSessionContextActive(context)) return; await qc.invalidateQueries({ queryKey: ["projection"] }); } });
    return <Shell><h1>Google Calendar</h1><QueryState query={connection}/>{connection.isSuccess && <><p>{connection.data.status}</p>{connection.data.configurationRequired ? <p>Calendar 연동이 구성되지 않았습니다.</p> : <button disabled={reconnect.isPending} onClick={() => { if (!reconnect.isPending)
        reconnect.mutate(); }}>Calendar 다시 연결</button>}</>}{reconnect.isError && <Notice error={reconnect.error}/>}{reconnect.isSuccess && <p role="status">연결 상태를 새로고침했습니다.</p>}</Shell>;
}
