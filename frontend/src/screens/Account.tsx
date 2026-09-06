import { useEffect, useState, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "../api/client";
import { keys, useProject } from "../state";
import { Link, Shell, QueryState, ProjectMissing, Notice, invitationHash, internalLink } from "../ui";
export function Auth({ children }: {
    children: ReactNode;
}) {
    const config = useQuery({ queryKey: keys.configuration, queryFn: api.configuration });
    const me = useQuery({ queryKey: keys.me, queryFn: api.me, enabled: config.data?.login === "READY" });
    useEffect(() => {
        if (!me.isSuccess)
            return;
        const saved = invitationHash(sessionStorage.getItem("ai-erp.invitation") ?? "");
        if (!window.location.hash || window.location.hash === "#/") {
            if (saved)
                window.location.hash = saved;
        }
        sessionStorage.removeItem("ai-erp.invitation");
    }, [me.isSuccess]);
    if (!config.isSuccess)
        return <Shell><QueryState query={config}/></Shell>;
    if (config.data.login !== "READY" || !config.data.loginUrl)
        return <Shell><h1>AI ERP</h1><p>Google 로그인이 구성되지 않았습니다.</p></Shell>;
    if (me.isError && me.error instanceof ApiError && me.error.status === 401)
        return <Shell><h1>AI ERP</h1><a href={config.data.loginUrl} onClick={() => { const value = invitationHash(window.location.hash); if (value)
            sessionStorage.setItem("ai-erp.invitation", value); }}>Google로 로그인</a></Shell>;
    if (!me.isSuccess)
        return <Shell><QueryState query={me}/></Shell>;
    return <>{children}</>;
}
export function Projects() {
    const [page, setPage] = useState(0);
    const me = useQuery({ queryKey: keys.me, queryFn: api.me });
    const projects = useQuery({ queryKey: page ? [...keys.projects, page] : keys.projects, queryFn: () => api.projects(page) });
    return <Shell><p>계정 {me.data?.id}</p><h1>프로젝트</h1><QueryState query={projects}/>{projects.isSuccess && <>{!projects.data.length && <p>참여 중인 프로젝트가 없습니다.</p>}<ul>{projects.data.map(p => <li key={p.id}><Link to={`/projects/${p.id}`}>{p.name}</Link> {p.role}</li>)}</ul><button disabled={!page} onClick={() => setPage(page - 1)}>이전 프로젝트</button><button disabled={projects.data.length < 100} onClick={() => setPage(page + 1)}>다음 프로젝트</button></>}</Shell>;
}
export function Invite({ token }: {
    token: string;
}) {
    const qc = useQueryClient();
    const invite = useQuery({ queryKey: keys.invitation(token), queryFn: () => api.invitation(token) });
    const resolve = useMutation({ mutationFn: (action: "accept" | "reject") => api.resolve(token, action), onSuccess: async (value) => {
            qc.setQueryData(keys.invitation(token), value);
            await Promise.all([keys.invitation(token), keys.projects, keys.connection].map(queryKey => qc.invalidateQueries({ queryKey })));
        } });
    if (!invite.isSuccess)
        return <Shell><QueryState query={invite}/>{invite.error instanceof ApiError && invite.error.status === 403 && <p>현재 Google 계정의 인증된 이메일이 초대 이메일과 일치하지 않습니다.</p>}</Shell>;
    const value = invite.data;
    const active = value.status === "PENDING" && new Date(value.expiresAt).getTime() > Date.now() && !resolve.isSuccess;
    return <Shell><h1>프로젝트 초대</h1><p>이메일: {value.email}</p><p>상태: {value.status}</p><p>만료: <time dateTime={value.expiresAt}>{value.expiresAt}</time></p><p>서버에서 현재 Google 계정의 인증된 이메일과 초대 이메일이 일치함을 확인했습니다.</p><p>수락 시 프로젝트 접근권한과 Calendar 공유 대상이 설정됩니다.</p>{active ? <><button disabled={resolve.isPending} onClick={() => { if (!resolve.isPending)
        resolve.mutate("accept"); }}>초대 수락</button><button disabled={resolve.isPending} onClick={() => { if (!resolve.isPending)
        resolve.mutate("reject"); }}>초대 거절</button></> : <p>만료되었거나 이미 처리된 초대입니다.</p>}{resolve.isSuccess && <p role="status">초대 상태를 갱신했습니다.</p>}{resolve.isError && <><Notice error={resolve.error}/><button onClick={() => invite.refetch()}>최신 초대 불러오기</button></>}<Link to="/">프로젝트 선택</Link></Shell>;
}
export function InviteCreate({ id }: {
    id: string;
}) {
    const project = useProject(id);
    const [email, setEmail] = useState("");
    const invite = useMutation({ mutationFn: () => api.invite(project.data!.groupId, { projectId: id, email }) });
    if (!project.isSuccess)
        return <Shell><QueryState query={project}/></Shell>;
    if (!project.data)
        return <Shell><ProjectMissing /></Shell>;
    return <Shell project={project.data}><h1>구성원 초대</h1>{project.data.role !== "MANAGER" ? <p>Manager만 초대를 만들 수 있습니다.</p> : <><form onSubmit={e => { e.preventDefault(); if (!invite.isPending)
        invite.mutate(); }}><label>이메일<input type="email" required value={email} onChange={e => setEmail(e.target.value)}/></label><button disabled={invite.isPending}>초대 만들기</button></form>{invite.isError && <Notice error={invite.error}/>}{invite.isSuccess && <div><p role="status">초대를 만들었습니다.</p><p>{invite.data.status} · 만료 {invite.data.expiresAt}</p><label>초대 링크<input readOnly value={window.location.href.split("#")[0] + "#/invitations/" + invite.data.token} onFocus={e => e.currentTarget.select()}/></label><Link to={`/invitations/${invite.data.token}`}>초대 열기</Link></div>}</>}</Shell>;
}
export function Notifications() {
    const qc = useQueryClient();
    const [page, setPage] = useState(0);
    const list = useQuery({ queryKey: [...keys.notifications, page], queryFn: () => api.notifications(page) });
    const read = useMutation({ mutationFn: api.readNotification, onSuccess: () => qc.invalidateQueries({ queryKey: keys.notifications }) });
    return <Shell><h1>알림</h1><QueryState query={list}/>{list.isSuccess && <>{list.data.length ? <ul>{list.data.map(n => <li key={n.id}>{internalLink(n.link) ? <Link to={internalLink(n.link)!}>{n.type}</Link> : n.type} <time dateTime={n.createdAt}>{n.createdAt}</time> {n.readAt ? "읽음" : <button disabled={read.isPending} onClick={() => { if (!read.isPending)
        read.mutate(n.id); }}>읽음 처리</button>}</li>)}</ul> : <p>새 알림이 없습니다.</p>}<button disabled={!page} onClick={() => setPage(page - 1)}>이전 알림</button><button disabled={list.data.length < 100} onClick={() => setPage(page + 1)}>다음 알림</button></>}{read.isError && <Notice error={read.error}/>}{read.isSuccess && <p role="status">알림을 읽음으로 표시했습니다.</p>}</Shell>;
}
export function Members({ id }: {
    id: string;
}) {
    const project = useProject(id);
    const qc = useQueryClient();
    const [page, setPage] = useState(0);
    const members = useQuery({ queryKey: [...keys.members(id), page], queryFn: () => api.members(id, page) });
    const change = useMutation({ mutationFn: ({ userId, role }: {
            userId: string;
            role: string;
        }) => api.role(id, userId, { role }), onSettled: () => Promise.all([qc.invalidateQueries({ queryKey: keys.members(id) }), qc.invalidateQueries({ queryKey: keys.projects })]) });
    if (!project.isSuccess)
        return <Shell><QueryState query={project}/></Shell>;
    if (!project.data)
        return <Shell><ProjectMissing /></Shell>;
    return <Shell project={project.data}><h1>구성원</h1><QueryState query={members}/>{members.isSuccess && <>{!members.data.length && <p>구성원이 없습니다.</p>}<ul>{members.data.map(m => <li key={m.userId}>{m.userId}{project.data!.role === "MANAGER" ? <label>{m.userId} 역할<select aria-label={`${m.userId} 역할`} value={m.role} disabled={change.isPending} onChange={e => { if (!change.isPending)
        change.mutate({ userId: m.userId, role: e.target.value }); }}><option>MANAGER</option><option>MEMBER</option><option>VIEWER</option></select></label> : <span>{m.role}</span>}</li>)}</ul><button disabled={!page} onClick={() => setPage(page - 1)}>이전 구성원</button><button disabled={members.data.length < 100} onClick={() => setPage(page + 1)}>다음 구성원</button></>}{change.isError && <Notice error={change.error}/>}{change.isSuccess && <p role="status">역할을 저장했습니다.</p>}</Shell>;
}
export function Calendar() {
    const qc = useQueryClient();
    const connection = useQuery({ queryKey: keys.connection, queryFn: api.calendar });
    const reconnect = useMutation({ mutationFn: api.reconnect, onSuccess: async (value) => { qc.setQueryData(keys.connection, value); await qc.invalidateQueries({ queryKey: keys.connection }); await qc.invalidateQueries({ queryKey: ["projection"] }); } });
    return <Shell><h1>Google Calendar</h1><QueryState query={connection}/>{connection.isSuccess && <><p>{connection.data.status}</p>{connection.data.configurationRequired ? <p>Calendar 연동이 구성되지 않았습니다.</p> : <button disabled={reconnect.isPending} onClick={() => { if (!reconnect.isPending)
        reconnect.mutate(); }}>Calendar 다시 연결</button>}</>}{reconnect.isError && <Notice error={reconnect.error}/>}{reconnect.isSuccess && <p role="status">연결 상태를 새로고침했습니다.</p>}</Shell>;
}
