import { useEffect, useRef, useState, type ReactNode } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { api, ApiError, type Project } from "./api/client";
import {
  captureSession,
  isSessionContextActive,
  terminateSession,
} from "./session";
export const go = (path: string) => {
  window.location.hash = `#${path}`;
};
export const invitationHash = (value: string) =>
  /^#\/(?:invitations\/[A-Za-z0-9_-]+|join\/[0-9A-HJKMNP-TV-Z]{16})$/.test(
    value,
  )
    ? value
    : undefined;
export const internalLink = (value?: string | null) =>
  value &&
  /^#?\/projects\/[A-Za-z0-9_-]+(?:\/schedules\/[A-Za-z0-9_-]+)?$/.test(value)
    ? value.replace(/^#/, "")
    : undefined;
export function Link({
  to,
  children,
  className,
  ...props
}: {
  to: string;
  children: ReactNode;
  className?: string;
  "aria-current"?: "page";
}) {
  return (
    <a className={className} href={`#${to}`} {...props}>
      {children}
    </a>
  );
}
export function Shell({
  project,
  children,
}: {
  project?: Project;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [logoutError, setLogoutError] = useState<unknown>();
  const me = useQuery({ queryKey: ["me"], queryFn: api.me });
  const menuButton = useRef<HTMLButtonElement>(null);
  const current = window.location.hash.replace(/^#/, "") || "/";
  const logout = useMutation({
    mutationFn: api.logout,
    onMutate: () => {
      setLogoutError(undefined);
      const continuation = window.location.hash;
      if (/^#\/(?:invitations\/[A-Za-z0-9_-]+|join\/[0-9A-HJKMNP-TV-Z]{16})$/.test(continuation))
        sessionStorage.setItem("ai-erp.invitation", continuation);
      return captureSession();
    },
    onError: (error, _, context) => {
      if (isSessionContextActive(context)) setLogoutError(error);
    },
    onSuccess: (_, __, context) => {
      if (!isSessionContextActive(context)) return;
      terminateSession();
    },
  });
  useEffect(() => setOpen(false), [project?.id, window.location.hash]);
  useEffect(() => {
    if (!open) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      event.preventDefault();
      setOpen(false);
      menuButton.current?.focus();
    };
    document.addEventListener("keydown", closeOnEscape);
    return () => document.removeEventListener("keydown", closeOnEscape);
  }, [open]);
  return (
    <main
      className={project ? "app-shell has-project" : "app-shell lobby-shell"}
    >
      <button
        className="skip-link"
        type="button"
        onClick={() => {
          document.getElementById("main-content")?.focus();
        }}
      >
        본문으로 건너뛰기
      </button>
      <header className="topbar">
        <div className="topbar-primary">
          <Link to="/" className="brand">
            <span className="brand-mark" aria-hidden="true">AI</span>
            <span>AI ERP</span>
          </Link>
          <div className="topbar-actions">
            {project && <Link to="/notifications">알림</Link>}
            <details className="account-menu">
              <summary>{me.data?.displayName || "내 계정"}</summary>
              <div className="account-menu-panel">
                {me.data?.email && (
                  <span className="account-menu-email">{me.data.email}</span>
                )}
                <Link to="/account">계정 설정</Link>
                <button
                  type="button"
                  disabled={logout.isPending}
                  onClick={() => !logout.isPending && logout.mutate()}
                >
                  다른 계정으로 로그인
                </button>
              </div>
            </details>
            {logoutError !== undefined && <Notice error={logoutError} />}
          </div>
        </div>
        {project && (
          <div className="topbar-secondary">
            <button
              ref={menuButton}
              className="mobile-menu-button"
              type="button"
              aria-expanded={open}
              aria-controls="project-nav"
              onClick={() => setOpen((v) => !v)}
            >
              메뉴
            </button>
            <span className="mobile-project-title" title={project.name}>
              {project.name}
            </span>
          </div>
        )}
      </header>
      <div className={project ? "workspace" : "lobby-workspace"}>
        {project && (
          <aside
            id="project-nav"
            className={`project-sidebar ${open ? "is-open" : ""}`}
            aria-label="프로젝트 메뉴"
          >
            <div className="sidebar-project">
              <span className="eyebrow">현재 프로젝트</span>
              <strong title={project.name}>{project.name}</strong>
            </div>
            <nav aria-label="프로젝트 메뉴">
              <Link
                to={`/projects/${project.id}`}
                aria-current={
                  current === `/projects/${project.id}` ? "page" : undefined
                }
              >
                개요
              </Link>
              <Link
                to={`/projects/${project.id}/schedules`}
                aria-current={
                  current === `/projects/${project.id}/schedules`
                    ? "page"
                    : undefined
                }
              >
                일정
              </Link>
              <Link
                to={`/projects/${project.id}/members`}
                aria-current={
                  current === `/projects/${project.id}/members`
                    ? "page"
                    : undefined
                }
              >
                구성원
              </Link>
            </nav>
            <Link className="sidebar-return" to="/">
              프로젝트 선택
            </Link>
          </aside>
        )}
      <section id="main-content" className="content-area" tabIndex={-1}>
          {children}
        </section>
      </div>
    </main>
  );
}
export function Notice({
  error,
  message,
}: {
  error: unknown;
  message?: string;
}) {
  const p = error instanceof ApiError ? error.problem : undefined;
  const status = error instanceof ApiError ? error.status : undefined;
  const explanation =
    status === 401
      ? "로그인이 필요합니다. 다시 로그인해 주세요."
      : status === 403
        ? "이 작업을 수행할 접근 권한이 없습니다. 최신 접근 상태를 확인하세요."
        : status === 404
          ? "요청한 항목을 찾을 수 없습니다."
          : status === 409
            ? "다른 변경이 먼저 저장되었습니다. 최신 내용을 불러온 뒤 다시 시도하세요."
            : status === 400
              ? "입력한 내용을 확인해 주세요."
              : (message ??
                "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
  return (
    <div role="alert" className="notice">
      <p>{explanation}</p>
      {(p?.code || p?.traceId || p?.fieldErrors?.length) && (
        <details>
          <summary>오류 상세</summary>
          {p?.code && <p>{p.code}</p>}
          {p?.traceId && <p>{p.traceId}</p>}
          {p?.fieldErrors?.map((f) => (
            <p key={f.field}>
              {f.field}: {f.message}
            </p>
          ))}
        </details>
      )}
    </div>
  );
}
export function Loading({
  message = "불러오는 중입니다.",
}: {
  message?: string;
}) {
  return (
    <p role="status" className="loading">
      {message}
    </p>
  );
}
type State = {
  isPending: boolean;
  isError: boolean;
  isFetching?: boolean;
  data?: unknown;
  error: unknown;
  refetch: () => unknown;
};
export function QueryState({
  query,
  label = "다시 시도",
  loadingMessage,
  errorMessage,
}: {
  query: State;
  label?: string;
  loadingMessage?: string;
  errorMessage?: string;
}) {
  const [retrying, setRetrying] = useState(false);
  const locked = useRef(false);
  if (query.isPending && !retrying) return <Loading message={loadingMessage} />;
  if (query.isError || retrying)
    return (
      <>
        {query.isError && (
          <>
            <Notice error={query.error} message={errorMessage} />
            {query.data !== undefined &&
              !(
                query.error instanceof ApiError &&
                [401, 403, 404].includes(query.error.status)
              ) && (
                <p className="help">
                  표시된 내용이 최신 상태가 아닐 수 있습니다.
                </p>
              )}
          </>
        )}
        {(query.isFetching || retrying) && <Loading message={loadingMessage} />}
        <button
          disabled={query.isFetching || retrying}
          onClick={async () => {
            if (locked.current || query.isFetching) return;
            locked.current = true;
            setRetrying(true);
            try {
              await query.refetch();
            } finally {
              locked.current = false;
              setRetrying(false);
            }
          }}
        >
          {label}
        </button>
      </>
    );
  if (query.isFetching)
    return <Loading message="최신 내용을 불러오는 중입니다." />;
  return null;
}
export function RetryButton({
  onRetry,
  isFetching = false,
  children = "접근 상태 다시 확인",
}: {
  onRetry: () => unknown;
  isFetching?: boolean;
  children?: ReactNode;
}) {
  const [pending, setPending] = useState(false);
  const locked = useRef(false);
  return (
    <>
      {(pending || isFetching) && (
        <Loading message="접근 상태를 확인하는 중입니다." />
      )}
      <button
        type="button"
        disabled={pending || isFetching}
        onClick={async () => {
          if (locked.current || isFetching) return;
          locked.current = true;
          setPending(true);
          try {
            await onRetry();
          } finally {
            locked.current = false;
            setPending(false);
          }
        }}
      >
        {children}
      </button>
    </>
  );
}
export function ProjectMissing({
  onRetry,
  isFetching,
}: {
  onRetry?: () => unknown;
  isFetching?: boolean;
}) {
  return (
    <>
      <h1>프로젝트에 접근할 수 없습니다</h1>
      <p>선택한 프로젝트가 삭제되었거나 현재 계정에 접근 권한이 없습니다.</p>
      <Link to="/">프로젝트 선택</Link>
      {onRetry && <RetryButton onRetry={onRetry} isFetching={isFetching} />}
    </>
  );
}
export function Dialog({
  open,
  title,
  children,
  onClose,
  labelledBy,
}: {
  open: boolean;
  title: string;
  children: ReactNode;
  onClose: () => void;
  labelledBy?: string;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const caller = useRef<HTMLElement | null>(null);
  const wasOpen = useRef(false);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !wasOpen.current) {
      const active = document.activeElement;
      caller.current = active instanceof HTMLElement ? active : null;
    }
    if (open && !d.open) {
      if (typeof d.showModal === "function") d.showModal();
      else d.setAttribute("open", "");
      requestAnimationFrame(() => d.querySelector<HTMLElement>("h2")?.focus());
    } else if (!open && d.open) {
      if (typeof d.close === "function") d.close();
      else d.removeAttribute("open");
      requestAnimationFrame(() => caller.current?.focus());
    }
    wasOpen.current = open;
    return () => {
      if (d.open) {
        if (typeof d.close === "function") d.close();
        else d.removeAttribute("open");
      }
    };
  }, [open]);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    const h = (e: Event) => {
      if ((e as KeyboardEvent).key === "Escape") {
        e.preventDefault();
        onClose();
      }
    };
    d.addEventListener("cancel", h);
    return () => d.removeEventListener("cancel", h);
  }, [onClose]);
  return (
    <dialog
      ref={ref}
      className="modal"
      aria-labelledby={labelledBy}
      onClose={onClose}
    >
      <div className="modal-header">
        <h2 id={labelledBy} tabIndex={-1}>
          {title}
        </h2>
        <button
          className="icon-button"
          type="button"
          aria-label="닫기"
          onClick={onClose}
        >
          ×
        </button>
      </div>
      <div className="modal-body">{children}</div>
    </dialog>
  );
}
