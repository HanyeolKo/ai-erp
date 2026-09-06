import type { ReactNode } from "react";
import { ApiError, type Project } from "./api/client";
export const go = (path: string) => { window.location.hash = `#${path}`; };
export const invitationHash = (value: string) => /^#\/invitations\/[A-Za-z0-9_-]+$/.test(value) ? value : undefined;
export const internalLink = (value?: string | null) => value && /^#?\/projects\/[A-Za-z0-9_-]+\/schedules\/[A-Za-z0-9_-]+$/.test(value) ? value.replace(/^#/, "") : undefined;
export function Link({ to, children }: {
    to: string;
    children: ReactNode;
}) { return <a href={`#${to}`}>{children}</a>; }
export function Shell({ project, children }: {
    project?: Project;
    children: ReactNode;
}) {
    return <main><header><Link to="/">AI ERP</Link><nav aria-label="주 메뉴"><Link to="/notifications">알림</Link><Link to="/calendar">Calendar</Link>{project && <><Link to={`/projects/${project.id}`}>대시보드</Link><Link to={`/projects/${project.id}/schedules`}>일정</Link><Link to={`/projects/${project.id}/members`}>구성원</Link></>}</nav></header><section>{children}</section></main>;
}
export function Notice({ error }: {
    error: unknown;
}) {
    const p = error instanceof ApiError ? error.problem : undefined;
    return <div role="alert" className="notice">{p?.code ?? "요청을 처리하지 못했습니다."}{p?.traceId && <p>traceId: {p.traceId}</p>}{p?.fieldErrors?.map(f => <p key={f.field}>{f.field}: {f.message}</p>)}{error instanceof ApiError && error.status === 409 && <p>다른 변경이 먼저 저장되었습니다. 최신 내용을 불러온 뒤 다시 시도하세요.</p>}</div>;
}
export function Loading() { return <p role="status">불러오는 중입니다.</p>; }
type State = {
    isPending: boolean;
    isError: boolean;
    error: unknown;
    refetch: () => unknown;
};
export function QueryState({ query, label = "다시 시도" }: {
    query: State;
    label?: string;
}) {
    if (query.isPending)
        return <Loading />;
    if (query.isError)
        return <><Notice error={query.error}/><button onClick={() => query.refetch()}>{label}</button></>;
    return null;
}
export function ProjectMissing() { return <p>프로젝트를 찾을 수 없습니다. <Link to="/">프로젝트 선택</Link></p>; }
