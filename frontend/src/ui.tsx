import { useRef, useState, type ReactNode } from "react";
import { ApiError, type Project } from "./api/client";
export const go = (path: string) => { window.location.hash = `#${path}`; };
export const invitationHash = (value: string) => /^#\/invitations\/[A-Za-z0-9_-]+$/.test(value) ? value : undefined;
export const internalLink = (value?: string | null) => value && /^#?\/projects\/[A-Za-z0-9_-]+(?:\/schedules\/[A-Za-z0-9_-]+)?$/.test(value) ? value.replace(/^#/, "") : undefined;
export function Link({ to, children }: {
    to: string;
    children: ReactNode;
}) { return <a href={`#${to}`}>{children}</a>; }
export function Shell({ project, children }: {
    project?: Project;
    children: ReactNode;
}) {
    return <main><header><Link to="/">AI ERP</Link><nav aria-label="주 메뉴"><Link to="/">프로젝트 선택</Link><Link to="/notifications">알림</Link><Link to="/calendar">Calendar</Link>{project && <><Link to={`/projects/${project.id}`}>대시보드</Link><Link to={`/projects/${project.id}/schedules`}>일정</Link><Link to={`/projects/${project.id}/members`}>구성원</Link></>}</nav></header><section>{children}</section></main>;
}
export function Notice({ error, message }: {
    error: unknown;
    message?: string;
}) {
    const p = error instanceof ApiError ? error.problem : undefined;
    const status = error instanceof ApiError ? error.status : undefined;
    const explanation = status === 401 ? "로그인이 필요합니다. 다시 로그인해 주세요."
        : status === 403 ? "이 작업을 수행할 접근 권한이 없습니다. 프로젝트 선택 또는 접근 상태 다시 확인을 이용하세요."
        : status === 404 ? "요청한 항목을 찾을 수 없습니다. 삭제되었거나 더 이상 사용할 수 없는 항목입니다."
        : status === 409 ? "다른 변경이 먼저 저장되었습니다. 최신 내용을 불러온 뒤 다시 시도하세요."
        : status === 400 ? "입력한 내용을 확인해 주세요."
        : message ?? "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
    return <div role="alert" className="notice"><p>{explanation}</p>{p?.code && <small>{p.code}</small>}{p?.traceId && <p>traceId: {p.traceId}</p>}{p?.fieldErrors?.map(f => <p key={f.field}>{f.field}: {f.message}</p>)}</div>;
}
export function Loading({ message = "불러오는 중입니다." }: { message?: string }) { return <p role="status">{message}</p>; }
type State = {
    isPending: boolean;
    isError: boolean;
    isFetching?: boolean;
    data?: unknown;
    error: unknown;
    refetch: () => unknown;
};
export function QueryState({ query, label = "다시 시도", loadingMessage, errorMessage }: {
    query: State;
    label?: string;
    loadingMessage?: string;
    errorMessage?: string;
}) {
    const [retrying, setRetrying] = useState(false);
    const locked = useRef(false);
    if (query.isPending && !retrying) {
        return <Loading message={loadingMessage}/>;
    }
    if (query.isError || retrying)
        return <>{query.isError && <><Notice error={query.error} message={errorMessage}/>{query.data !== undefined && !(query.error instanceof ApiError && [401, 403, 404].includes(query.error.status)) && <p>표시된 내용이 최신 상태가 아닐 수 있습니다.</p>}</>}{(query.isFetching || retrying) && <Loading message={loadingMessage}/>}
            <button disabled={query.isFetching || retrying} onClick={async () => {
                if (locked.current || query.isFetching) return;
                locked.current = true; setRetrying(true);
                try { await query.refetch(); } finally { locked.current = false; setRetrying(false); }
            }}>{label}</button></>;
    if (query.isFetching)
        return <Loading message="최신 내용을 불러오는 중입니다."/>;
    return null;
}
export function RetryButton({ onRetry, isFetching = false, children = "접근 상태 다시 확인" }: {
    onRetry: () => unknown;
    isFetching?: boolean;
    children?: ReactNode;
}) {
    const [pending, setPending] = useState(false);
    const locked = useRef(false);
    return <>{(pending || isFetching) && <Loading message="접근 상태를 확인하는 중입니다."/>}<button type="button" disabled={pending || isFetching} onClick={async () => {
        if (locked.current || isFetching) return;
        locked.current = true; setPending(true);
        try { await onRetry(); } finally { locked.current = false; setPending(false); }
    }}>{children}</button></>;
}
export function ProjectMissing({ onRetry, isFetching }: { onRetry?: () => unknown; isFetching?: boolean }) {
    return <><h1>프로젝트에 접근할 수 없습니다</h1><p>선택한 프로젝트가 삭제되었거나 현재 계정에 접근 권한이 없습니다.</p><Link to="/">프로젝트 선택</Link>{onRetry && <RetryButton onRetry={onRetry} isFetching={isFetching}/>}</>;
}
