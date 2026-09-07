import { useEffect, useRef, useState, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError, type ShareInvitation } from "../api/client";
import {
  accessKey,
  accessRead,
  accessReadCanClear,
  clearAccessDenial,
  clearShareAttempt,
  clearShareRecovery,
  beginShareRecovery,
  beginShareRead,
  getShareAttempt,
  getShareReadRevision,
  invalidateShareRecovery,
  isCurrentShareRecovery,
  startShareAttempt,
  isAccessError,
  keys,
  recordAccessDenial,
  setShareAttempt,
  useAccessDenied,
  useProject,
  useShareRecovery,
  useShareAttempt,
} from "../state";
import {
  captureSession,
  isSessionContextActive,
  terminateSession,
} from "../session";
import {
  Dialog,
  go,
  internalLink,
  Link,
  Notice,
  ProjectMissing,
  QueryState,
  Shell,
} from "../ui";
import { ProjectStart } from "./ProjectStart";

function AuthShell({ children }: { children: ReactNode }) {
  return (
    <main className="auth-shell">
      <header className="topbar">
        <Link to="/" className="brand">
          <span className="brand-mark" aria-hidden="true">AI</span>
          <span>AI ERP</span>
        </Link>
      </header>
      <section className="content-area">{children}</section>
    </main>
  );
}
export function Auth({
  children,
  sessionExpired = false,
}: {
  children: ReactNode;
  sessionExpired?: boolean;
}) {
  const config = useQuery({
    queryKey: keys.configuration,
    queryFn: api.configuration,
  });
  const me = useQuery({
    queryKey: keys.me,
    queryFn: api.me,
    enabled: config.data?.login === "READY" && !sessionExpired,
  });
  useEffect(() => {
    if (!me.isSuccess || sessionExpired) return;
    const saved = sessionStorage.getItem("ai-erp.invitation") || "";
    const safe =
      /^#\/(?:invitations\/[A-Za-z0-9_-]+|join\/[0-9A-HJKMNP-TV-Z]{16})$/.test(
        saved,
      )
        ? saved
        : undefined;
    if ((!window.location.hash || window.location.hash === "#/") && safe)
      window.location.hash = safe;
    sessionStorage.removeItem("ai-erp.invitation");
  }, [me.isSuccess, sessionExpired]);
  if (!config.isSuccess)
    return (
      <AuthShell>
        <QueryState query={config} />
      </AuthShell>
    );
  if (config.data.login !== "READY" || !config.data.loginUrl)
    return (
      <AuthShell>
        <h1>AI ERP</h1>
        <p>Google 로그인이 구성되지 않았습니다.</p>
      </AuthShell>
    );
  if (
    sessionExpired ||
    (me.isError && me.error instanceof ApiError && me.error.status === 401)
  )
    return (
      <AuthShell>
        <h1>다시 로그인해 주세요</h1>
        {sessionExpired && <p role="status">로그인이 필요합니다.</p>}
        <a
          className="button button-primary"
          href={config.data.loginUrl}
          onClick={() => {
            const value = window.location.hash;
            if (
              /^#\/(?:invitations\/[A-Za-z0-9_-]+|join\/[0-9A-HJKMNP-TV-Z]{16})$/.test(
                value,
              )
            )
              sessionStorage.setItem("ai-erp.invitation", value);
          }}
        >
          Google로 로그인
        </a>
      </AuthShell>
    );
  if (!me.isSuccess)
    return (
      <AuthShell>
        <QueryState query={me} />
      </AuthShell>
    );
  return <>{children}</>;
}
function AccountSwitch() {
  const [logoutError, setLogoutError] = useState<unknown>();
  const logout = useMutation({
    mutationFn: api.logout,
    onMutate: () => {
      setLogoutError(undefined);
      const continuation = window.location.hash;
      if (
        /^#\/(?:invitations\/[A-Za-z0-9_-]+|join\/[0-9A-HJKMNP-TV-Z]{16})$/.test(
          continuation,
        )
      )
        sessionStorage.setItem("ai-erp.invitation", continuation);
      return captureSession();
    },
    onError: (error, _, ctx) => {
      if (isSessionContextActive(ctx)) setLogoutError(error);
    },
    onSuccess: (_, __, ctx) => {
      if (!isSessionContextActive(ctx)) return;
      terminateSession();
    },
  });
  return (
    <div className="account-recovery">
      <button
        type="button"
        disabled={logout.isPending}
        onClick={() => !logout.isPending && logout.mutate()}
      >
        다른 계정으로 로그인
      </button>
      {logout.isPending && <p role="status">로그아웃하는 중입니다.</p>}
      {logoutError !== undefined && <Notice error={logoutError} />}
    </div>
  );
}
function legacyInvitationPath(value: string) {
  const s = value.trim();
  if (/^#?[/]invitations[/][A-Za-z0-9_-]+$/.test(s)) return s.replace(/^#/, "");
  try {
    const u = new URL(s);
    if (
      u.origin === window.location.origin &&
      !u.username &&
      !u.password &&
      !u.search &&
      u.pathname === "/" &&
      /^#[/]invitations[/][A-Za-z0-9_-]+$/.test(u.hash)
    )
      return u.hash.slice(1);
  } catch {}
  return undefined;
}
function normalizeCode(value: string) {
  const s = value.trim();
  try {
    const u = new URL(s);
    if (
      u.origin !== window.location.origin ||
      u.username ||
      u.password ||
      u.search ||
      u.pathname !== "/" ||
      s.length > 2048
    )
      return;
    const m = u.hash.match(/^#\/join\/([0-9A-HJKMNP-TV-Za-hjkmnp-tv-z -]+)$/);
    if (m) return normalizeCode(m[1]);
  } catch {}
  if (s.length > 64 || !/^[\x00-\x7F]*$/.test(s)) return;
  const code = s
    .replace(/^#?\/join\//i, "")
    .replace(/[ -]/g, "")
    .toUpperCase();
  return /^[0-9A-HJKMNP-TV-Z]{16}$/.test(code) ? code : undefined;
}
function InvitationEntry() {
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState("");
  const [invalid, setInvalid] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  return (
    <>
      <button
        className="button button-secondary"
        type="button"
        onClick={() => setOpen(true)}
      >
        초대로 참여
      </button>
      <Dialog
        open={open}
        title="초대로 참여"
        labelledBy="join-dialog-title"
        onClose={() => setOpen(false)}
      >
        <p className="help">
          초대 링크 또는 16자리 코드를 입력하세요. 내용을 확인한 뒤 참여합니다.
        </p>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            const code = normalizeCode(value);
            const legacy = legacyInvitationPath(value);
            if (!code && !legacy) {
              setInvalid(true);
              input.current?.focus();
              return;
            }
            setInvalid(false);
            setOpen(false);
            go(legacy || `/join/${code}`);
          }}
          noValidate
        >
          <label htmlFor="invite-code">초대 링크 또는 코드</label>
          <input
            id="invite-code"
            data-dialog-autofocus
            ref={input}
            value={value}
            onChange={(e) => {
              setValue(e.target.value);
              setInvalid(false);
            }}
            aria-invalid={invalid}
            aria-describedby={invalid ? "invite-code-error" : undefined}
            autoComplete="off"
            spellCheck={false}
          />
          {invalid && (
            <p id="invite-code-error" role="alert">
              현재 서비스에서 발행한 초대 링크 또는 코드를 입력하세요.
            </p>
          )}
          <div className="modal-actions">
            <button
              type="button"
              className="button button-secondary"
              onClick={() => setOpen(false)}
            >
              취소
            </button>
            <button type="submit" className="button button-primary">
              초대 확인
            </button>
          </div>
        </form>
      </Dialog>
    </>
  );
}
export function Projects() {
  const [page, setPage] = useState(0);
  const qc = useQueryClient();
  const me = useQuery({ queryKey: keys.me, queryFn: api.me });
  const ak = accessKey("project-list", String(page));
  const denied = useAccessDenied(ak);
  const projects = useQuery({
    queryKey: page ? [...keys.projects, page] : keys.projects,
    queryFn: () => accessRead(ak, () => api.projects(page)),
    staleTime: denied ? 0 : 15000,
  });
  useEffect(() => {
    if (projects.isSuccess && !projects.isFetching && accessReadCanClear(ak))
      clearAccessDenial(ak);
  }, [ak, projects.isFetching, projects.isSuccess]);
  const rows = denied ? undefined : projects.data;
  const identity = me.data;
  return (
    <Shell>
      <div className="page-heading">
        <div>
          <p className="eyebrow">워크스페이스</p>
          <h1>프로젝트 선택</h1>
          <p className="lead">
            작업할 프로젝트를 선택하거나 새 프로젝트를 만들어 시작하세요.
          </p>
        </div>
        <div className="lobby-actions">
          <ProjectStart accountId={identity?.id} />
          <InvitationEntry />
        </div>
      </div>
      <QueryState
        query={projects}
        loadingMessage="프로젝트를 불러오는 중…"
        errorMessage="프로젝트를 불러오지 못했습니다."
      />
      {projects.isError && rows && (
        <p className="help">마지막으로 불러온 목록을 표시하고 있습니다.</p>
      )}
      {projects.isSuccess && !rows?.length && (
        <div className="empty-state">
          <div className="empty-icon">＋</div>
          <h2>
            {page === 0
              ? "아직 프로젝트가 없습니다"
              : "이 페이지에 프로젝트가 없습니다"}
          </h2>
          <p>
            {page === 0
              ? "새 프로젝트를 만들거나 초대 코드로 참여해 보세요."
              : "이전 페이지에서 프로젝트를 확인하세요."}
          </p>
        </div>
      )}
      {!!rows?.length && (
        <ul className="project-cards" aria-label="프로젝트 목록">
          {rows.map((p) => (
            <li key={p.id}>
              <Link to={`/projects/${p.id}`}>
                <span className="project-card-name">{p.name}</span>
                <span className="project-card-meta">
                  {p.role === "MANAGER"
                    ? "관리자"
                    : p.role === "MEMBER"
                      ? "구성원"
                    : "조회자"}
                </span>
                <span className="project-card-open">열기 →</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
      {(page > 0 || (rows?.length ?? 0) >= 100) && (
        <div className="pagination">
          <button
            disabled={!page || projects.isFetching}
            onClick={() => setPage(page - 1)}
          >
            이전 프로젝트
          </button>
          <span>{page + 1} 페이지</span>
          <button
            disabled={
              !projects.isSuccess ||
              (rows?.length ?? 0) < 100 ||
              projects.isFetching
            }
            onClick={() => setPage(page + 1)}
          >
            다음 프로젝트
          </button>
        </div>
      )}
      {!projects.isError && (
        <button
          className="text-button"
          type="button"
          disabled={projects.isFetching}
          onClick={() => qc.invalidateQueries({ queryKey: keys.projects })}
        >
          목록 새로고침
        </button>
      )}
    </Shell>
  );
}
function dateLabel(value: string | null | undefined) {
  return value
    ? new Intl.DateTimeFormat("ko-KR", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(value))
    : "-";
}
export function copyValue(value: string, setMessage: (s: string) => void) {
  const clipboard = window.navigator.clipboard;
  if (!clipboard?.writeText) {
    setMessage(
      "자동 복사를 사용할 수 없습니다. 위 값을 선택해 직접 복사하세요.",
    );
    return;
  }
  clipboard.writeText(value).then(
    () => setMessage("복사했습니다."),
    () =>
      setMessage(
        "자동 복사를 사용할 수 없습니다. 위 값을 선택해 직접 복사하세요.",
      ),
  );
}
export function ShareInvitationDialog({
  id,
  projectName,
  open,
  onClose,
}: {
  id: string;
  projectName?: string;
  open: boolean;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const [message, setMessage] = useState("");
  const [now, setNow] = useState(() => Date.now());
  const attempt = useShareAttempt(id);
  const recovery = useShareRecovery(id);
  const shareKey = accessKey("share", id);
  const denied = useAccessDenied(shareKey);
  const q = useQuery({
    queryKey: ["share-invitation", id],
    queryFn: () => {
      beginShareRead(id);
      return accessRead(shareKey, () => api.shareInvitation(id));
    },
    enabled: open,
    staleTime: 0,
  });
  useEffect(() => {
    if (q.isSuccess && !q.isFetching && accessReadCanClear(shareKey))
      clearAccessDenial(shareKey);
  }, [q.isFetching, q.isSuccess, shareKey]);
  useEffect(() => {
    if (
      attempt?.completed &&
      q.isSuccess &&
      !q.isFetching &&
      getShareReadRevision(id) > (attempt.completedReadRevision ?? 0)
    )
      clearShareAttempt(id);
  }, [attempt?.completed, attempt?.completedReadRevision, id, q.isFetching, q.isSuccess]);
  const mut = useMutation({
    mutationFn: async () => {
      const ctx = captureSession();
      try {
        const value = await api.createShareInvitation(id);
        if (isSessionContextActive(ctx)) {
          const current = getShareAttempt(id);
          if (current?.pending)
            setShareAttempt(id, {
              ...current,
              completed: true,
              completedReadRevision: getShareReadRevision(id),
            });
        }
        return value;
      } catch (error) {
        if (isSessionContextActive(ctx)) {
          const current = getShareAttempt(id);
          if (current?.pending)
            setShareAttempt(id, {
              ...current,
              pending: false,
              uncertain: !(error instanceof ApiError && [400, 409].includes(error.status)),
              completed: false,
            });
        }
        throw error;
      }
    },
    onMutate: () => {
      const ctx = captureSession();
      invalidateShareRecovery(id);
      const revision = startShareAttempt(id);
      setShareAttempt(id, { pending: true, uncertain: false, revision });
      return { ...ctx, revision };
    },
    onSuccess: async (v, _, ctx) => {
      if (!isSessionContextActive(ctx)) return;
      await qc.cancelQueries({ queryKey: ["share-invitation", id] });
      if (!isSessionContextActive(ctx)) return;
      clearShareAttempt(id);
      qc.setQueryData(["share-invitation", id], v);
    },
    onError: (error, __, ctx) => {
      if (isSessionContextActive(ctx)) {
        if (isAccessError(error)) recordAccessDenial(shareKey);
        setShareAttempt(id, {
          pending: false,
          uncertain: true,
          revision: (ctx as { revision: number }).revision,
          completed: false,
        });
        void q.refetch();
      }
    },
  });
  const revoke = useMutation({
    mutationFn: async () => {
      const ctx = captureSession();
      try {
        const value = await api.revokeShareInvitation(id);
        if (isSessionContextActive(ctx)) {
          const current = getShareAttempt(id);
          if (current?.pending)
            setShareAttempt(id, {
              ...current,
              completed: true,
              completedReadRevision: getShareReadRevision(id),
            });
        }
        return value;
      } catch (error) {
        if (isSessionContextActive(ctx)) {
          const current = getShareAttempt(id);
          if (current?.pending)
            setShareAttempt(id, {
              ...current,
              pending: false,
              uncertain: !(error instanceof ApiError && [400, 409].includes(error.status)),
              completed: false,
            });
        }
        throw error;
      }
    },
    onMutate: () => {
      const ctx = captureSession();
      invalidateShareRecovery(id);
      const revision = startShareAttempt(id);
      setShareAttempt(id, { pending: true, uncertain: false, revision });
      return { ...ctx, revision };
    },
    onSuccess: async (_, __, ctx) => {
      if (!isSessionContextActive(ctx)) return;
      await qc.cancelQueries({ queryKey: ["share-invitation", id] });
      if (!isSessionContextActive(ctx)) return;
      clearShareAttempt(id);
      qc.setQueryData(["share-invitation", id], {
        state: "REVOKED",
        code: null,
        expiresAt: null,
      });
      void q.refetch();
    },
    onError: (error, __, ctx) => {
      if (isSessionContextActive(ctx)) {
        if (isAccessError(error)) recordAccessDenial(shareKey);
        setShareAttempt(id, {
          pending: false,
          uncertain: true,
          revision: (ctx as { revision: number }).revision,
          completed: false,
        });
        void q.refetch();
      }
    },
  });
  const recover = async () => {
    if (attempt?.pending) return;
    const recoveryRevision = beginShareRecovery(id);
    if (recoveryRevision === undefined) return;
    const ctx = captureSession();
    try {
      await qc.cancelQueries({ queryKey: ["share-invitation", id] });
      if (!isSessionContextActive(ctx)) return;
      let result: ShareInvitation;
      try {
        result = await accessRead(shareKey, () => api.shareInvitation(id));
      } catch {
        return;
      }
      if (
        !isSessionContextActive(ctx) ||
        !isCurrentShareRecovery(id, recoveryRevision)
      )
        return;
      qc.setQueryData(["share-invitation", id], result);
      if (accessReadCanClear(shareKey)) clearAccessDenial(shareKey);
      clearShareAttempt(id);
    } finally {
      if (isSessionContextActive(ctx)) clearShareRecovery(id, recoveryRevision);
    }
  };
  const data =
    denied || attempt?.pending || attempt?.uncertain || attempt?.completed
      ? undefined
      : (q.data as ShareInvitation | undefined);
  const active =
    data?.state === "ACTIVE" &&
    !!data.expiresAt &&
    new Date(data.expiresAt).getTime() > now;
  useEffect(() => {
    if (!data?.expiresAt) return;
    const expiresAt = new Date(data.expiresAt).getTime();
    const delay = expiresAt - Date.now();
    if (delay <= 0) {
      setNow(Date.now());
      return;
    }
    if (delay > 2_147_483_647) return;
    const timer = window.setTimeout(() => setNow(Date.now()), delay + 1);
    return () => window.clearTimeout(timer);
  }, [data?.expiresAt]);
  const link = active && data.code
    ? `${window.location.origin}/#/join/${data.code}`
    : "";
  const locked = !!attempt?.pending || !!attempt?.uncertain || !!attempt?.completed;
  const canChange =
    q.isSuccess && !q.isFetching && !denied && !locked && !recovery;
  const mutate = () => {
    if (canChange) mut.mutate();
  };
  const revokeInvite = () => {
    if (canChange) revoke.mutate();
  };
  return (
    <Dialog
      open={open}
      title={projectName ? `${projectName}에 구성원 초대` : "구성원 초대"}
      labelledBy="share-dialog-title"
      onClose={onClose}
    >
      <p className="help">
        링크 또는 코드를 받은 사람은 로그인 후 구성원으로 참여합니다. 초대는 7일
        동안 유효합니다.
      </p>
      <QueryState query={q} loadingMessage="초대 상태를 확인하는 중…" />
      {data?.state === "ACTIVE" && data.expiresAt && !active && (
        <div className="invite-expired" role="status">
          <p>이 초대는 만료되었습니다. 새 초대를 발행하세요.</p>
          <button
            type="button"
            className="button button-primary"
            disabled={!canChange}
            onClick={() => mutate()}
          >
            {attempt?.pending ? "만드는 중…" : "새 초대 만들기"}
          </button>
        </div>
      )}
      {denied && (
        <button
          type="button"
          disabled={q.isFetching || !!attempt?.pending || !!recovery}
          onClick={() => void recover()}
        >
          {recovery ? "확인하는 중…" : "초대 상태 다시 확인"}
        </button>
      )}
      {active && data.code ? (
        <div className="invite-details">
          <label>
            초대 링크
            <input
              readOnly
              value={link}
              onFocus={(e) => e.currentTarget.select()}
            />
          </label>
          <button
            type="button"
            className="button button-secondary"
            onClick={() => copyValue(link, setMessage)}
          >
            링크 복사
          </button>
          <label>
            초대 코드
            <input
              readOnly
              value={data.code.match(/.{1,4}/g)?.join("-") ?? data.code}
              onFocus={(e) => e.currentTarget.select()}
            />
          </label>
          <button
            type="button"
            className="button button-secondary"
            onClick={() => copyValue(data.code!, setMessage)}
          >
            코드 복사
          </button>
          {navigator.share && (
            <button
              type="button"
              className="button button-secondary"
              onClick={async () => {
                try {
                  await navigator.share({
                    title: "AI ERP 프로젝트 초대",
                    text: link,
                  });
                } catch (error) {
                  if (error instanceof DOMException && error.name === "AbortError")
                    return;
                  setMessage(
                    "공유를 완료하지 못했습니다. 링크 복사로 초대해 주세요.",
                  );
                }
              }}
            >
              공유
            </button>
          )}
          <p>만료 {dateLabel(data.expiresAt)}</p>
          <div className="modal-actions">
            <button
              type="button"
              className="button button-danger"
              disabled={!canChange}
              onClick={() => {
                if (window.confirm("현재 초대 링크를 사용할 수 없게 할까요?"))
                  revokeInvite();
              }}
            >
              초대 사용 중지
            </button>
            <button
              type="button"
              className="button button-secondary"
              disabled={!canChange}
              onClick={() => {
                if (window.confirm("기존 링크를 폐기하고 새 초대를 만들까요?"))
                  mutate();
              }}
            >
              새 초대로 바꾸기
            </button>
          </div>
        </div>
      ) : data?.state === "ACTIVE" && data.expiresAt ? null : (
        <button
          type="button"
          className="button button-primary"
          disabled={!canChange}
          onClick={() => mutate()}
        >
          {attempt?.pending
            ? "만드는 중…"
            : attempt?.uncertain
              ? "상태 확인 필요"
              : "초대 만들기"}
          </button>
      )}
      {attempt?.uncertain && !denied && (
        <div className="uncertain-panel">
          <p role="alert">
            초대 변경 결과를 확인하지 못했습니다. 새 발행을 시작하지 않고
            현재 상태를 다시 확인합니다.
          </p>
          <button
            type="button"
            disabled={q.isFetching || !!attempt?.pending || !!recovery}
            onClick={() => void recover()}
          >
            {recovery ? "확인하는 중…" : "초대 상태 다시 확인"}
          </button>
        </div>
      )}
      {message && (
        <p role="status" aria-live="polite">
          {message}
        </p>
      )}
      {mut.isError && <Notice error={mut.error} />}
    </Dialog>
  );
}

export function JoinPreview({ code }: { code: string }) {
  const qc = useQueryClient();
  const [now, setNow] = useState(() => Date.now());
  const [joinConflict, setJoinConflict] = useState(false);
  const [conflictReadFailed, setConflictReadFailed] = useState(false);
  const q = useQuery({
    queryKey: ["project-invitation", code],
    queryFn: () => api.projectInvitation(code),
    staleTime: 0,
  });
  const join = useMutation({
    mutationFn: () => api.joinProjectInvitation(code),
    onMutate: captureSession,
    onError: async (error, _, ctx) => {
      if (!isSessionContextActive(ctx) || !(error instanceof ApiError) || error.status !== 409)
        return;
      setJoinConflict(true);
      setConflictReadFailed(false);
      const refreshed = await q.refetch();
      if (isSessionContextActive(ctx) && refreshed.isSuccess)
        setJoinConflict(false);
    },
    onSuccess: async (p, _, ctx) => {
      if (!isSessionContextActive(ctx)) return;
      await qc.invalidateQueries({ queryKey: keys.projects });
      if (isSessionContextActive(ctx)) go(`/projects/${p.id}`);
    },
  });
  useEffect(() => {
    if (joinConflict && q.isError) {
      setConflictReadFailed(true);
    } else if (joinConflict && conflictReadFailed && q.isSuccess) {
      setJoinConflict(false);
    }
  }, [conflictReadFailed, joinConflict, q.isError, q.isSuccess]);
  useEffect(() => {
    if (!q.data?.expiresAt) return;
    const expiresAt = new Date(q.data.expiresAt).getTime();
    const delay = expiresAt - Date.now();
    if (delay <= 0) {
      setNow(Date.now());
      return;
    }
    if (delay > 2_147_483_647) return;
    const timer = window.setTimeout(() => setNow(Date.now()), delay + 1);
    return () => window.clearTimeout(timer);
  }, [q.data?.expiresAt]);
  const active =
    !joinConflict &&
    q.data?.state === "ACTIVE" &&
    q.data.expiresAt &&
    new Date(q.data.expiresAt).getTime() > now;
  const runJoin = () => {
    if (!active || !q.data?.expiresAt || new Date(q.data.expiresAt).getTime() <= Date.now()) {
      setNow(Date.now());
      return;
    }
    join.mutate();
  };
  return (
    <Shell>
      <div className="narrow-page">
        <p className="eyebrow">공유 초대</p>
        <h1>프로젝트 참여</h1>
        <QueryState query={q} loadingMessage="초대 정보를 확인하는 중…" />
        {q.isSuccess && q.data && active ? (
          <div className="preview-card">
            <h2>{q.data.projectName}</h2>
            <dl>
              <div>
                <dt>초대한 사람</dt>
                <dd>{q.data.inviterName || "프로젝트 관리자"}</dd>
              </div>
              {!q.data.alreadyMember && (
                <div>
                  <dt>받게 될 역할</dt>
                  <dd>구성원</dd>
                </div>
              )}
              <div>
                <dt>만료</dt>
                <dd>{dateLabel(q.data.expiresAt)}</dd>
              </div>
            </dl>
            {q.data.alreadyMember ? (
              <>
                <p>
                  이미 참여 중인 프로젝트입니다. 기존 역할은 그대로 유지됩니다.
                </p>
                <Link
                  className="button button-primary"
                  to={`/projects/${q.data.projectId}`}
                >
                  프로젝트 열기
                </Link>
              </>
            ) : (
              <button
                className="button button-primary"
                disabled={join.isPending}
                onClick={runJoin}
              >
                {join.isPending ? "참여하는 중…" : "프로젝트 참여"}
              </button>
            )}
          </div>
        ) : (
          q.isSuccess && (
            <div className="empty-state">
              <h2>사용할 수 없는 초대입니다</h2>
              <p>새 초대를 받아 입력해 주세요.</p>
              <Link className="button button-secondary" to="/">
                프로젝트 선택
              </Link>
            </div>
          )
        )}
        {join.isError && <Notice error={join.error} />}
        <p>
          <Link to="/">프로젝트 선택</Link>
        </p>
      </div>
    </Shell>
  );
}

export function Invite({ token }: { token: string }) {
  const qc = useQueryClient();
  const [resolveMessage, setResolveMessage] = useState("");
  const readKey = accessKey("invitation", token);
  const writeKey = accessKey("invitation-write", token);
  const readDenied = useAccessDenied(readKey);
  const writeDenied = useAccessDenied(writeKey);
  const denied = readDenied || writeDenied;
  const q = useQuery({
    queryKey: keys.invitation(token),
    queryFn: () => accessRead(readKey, () => api.invitation(token)),
    staleTime: denied ? 0 : 15000,
  });
  useEffect(() => {
    if (q.isSuccess && !q.isFetching && accessReadCanClear(readKey))
      clearAccessDenial(readKey);
  }, [q.isSuccess, q.isFetching, readKey]);
  const resolve = useMutation({
    mutationFn: (action: "accept" | "reject") => api.resolve(token, action),
    onMutate: captureSession,
    onError: (e, _, ctx) => {
      if (isSessionContextActive(ctx) && isAccessError(e))
        recordAccessDenial(writeKey);
    },
    onSuccess: async (value, _, ctx) => {
      if (!isSessionContextActive(ctx)) return;
      setResolveMessage("초대 상태를 갱신했습니다.");
      qc.setQueryData(keys.invitation(token), value);
      await qc.invalidateQueries({ queryKey: keys.invitation(token) });
      if (!isSessionContextActive(ctx)) return;
      await qc.invalidateQueries({ queryKey: keys.projects });
      if (!isSessionContextActive(ctx)) return;
    },
  });
  const blocked = denied || (resolve.isError && isAccessError(resolve.error));
  const expired =
    !!q.data?.expiresAt && new Date(q.data.expiresAt).getTime() <= Date.now();
  const recover = async () => {
    const ctx = captureSession();
    const result = await q.refetch();
    if (isSessionContextActive(ctx) && result.isSuccess) {
      clearAccessDenial(readKey);
      clearAccessDenial(writeKey);
      resolve.reset();
    }
  };
  return (
    <Shell>
      <div className="narrow-page">
        <h1>프로젝트 초대</h1>
        <QueryState query={q} />
        {q.data && !denied && (
          <>
            <p>기존 이메일 초대입니다. 초대받은 계정으로 확인해 주세요.</p>
            {q.data.email && <p>초대받은 이메일: {q.data.email}</p>}
            <p>상태: {q.data.status}</p>
            <p>만료 {dateLabel(q.data.expiresAt)}</p>
            {q.data.status === "PENDING" && !expired ? (
              <div className="action-row">
                <button
                  className="button button-primary"
                  disabled={resolve.isPending || blocked}
                  onClick={() => resolve.mutate("accept")}
                >
                  초대 수락
                </button>
                <button
                  className="button button-secondary"
                  disabled={resolve.isPending || blocked}
                  onClick={() => resolve.mutate("reject")}
                >
                  초대 거절
                </button>
              </div>
            ) : q.data.status === "ACCEPTED" ? (
              <>
                <p role="status">프로젝트에 참여했습니다.</p>
                <Link
                  className="button button-primary"
                  to={`/projects/${q.data.projectId}`}
                >
                  프로젝트 열기
                </Link>
              </>
            ) : (
              <p>만료되었거나 이미 처리된 초대입니다.</p>
            )}
            {resolveMessage && <p role="status">{resolveMessage}</p>}
            {resolve.isError && <Notice error={resolve.error} />}{" "}
            {resolve.error instanceof ApiError &&
              resolve.error.status === 403 && <AccountSwitch />}
            <p>
              <Link to="/">프로젝트 선택</Link>
            </p>
          </>
        )}
        {(denied || (resolve.isError && isAccessError(resolve.error))) && (
          <>
            {resolve.isError && <Notice error={resolve.error} />}
            {((q.error instanceof ApiError && q.error.status === 403) ||
              (resolve.error instanceof ApiError && resolve.error.status === 403)) && (
              <p>현재 Google 계정의 인증된 이메일과 초대 이메일이 일치하는지 확인해 주세요.</p>
            )}
            {resolve.error instanceof ApiError && resolve.error.status === 403 && <AccountSwitch />}
            <button type="button" disabled={q.isFetching} onClick={recover}>최신 초대 불러오기</button>
          </>
        )}
      </div>
    </Shell>
  );
}

export function Members({
  id,
  inviteInitially = false,
}: {
  id: string;
  inviteInitially?: boolean;
}) {
  const project = useProject(id);
  const me = useQuery({ queryKey: keys.me, queryFn: api.me });
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const [inviteOpen, setInviteOpen] = useState(inviteInitially);
  const [roleDrafts, setRoleDrafts] = useState<Record<string, string>>({});
  const access = accessKey("members", id);
  const denied = useAccessDenied(access);
  const members = useQuery({
    queryKey: [...keys.members(id), page],
    queryFn: () => accessRead(access, () => api.members(id, page)),
    enabled: !!project.data,
    staleTime: denied ? 0 : 15000,
  });
  useEffect(() => {
    if (members.isSuccess && !members.isFetching && accessReadCanClear(access))
      clearAccessDenial(access);
  }, [access, members.isFetching, members.isSuccess]);
  const change = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: string }) =>
      api.role(id, userId, { role }),
    onMutate: captureSession,
    onError: (_error, variables, context) => {
      if (!isSessionContextActive(context)) return;
      setRoleDrafts((drafts) => {
        const next = { ...drafts };
        delete next[variables.userId];
        return next;
      });
    },
    onSettled: (_data, _error, _variables, ctx) =>
      isSessionContextActive(ctx)
        ? Promise.all([
            qc.invalidateQueries({ queryKey: keys.members(id) }),
            qc.invalidateQueries({ queryKey: keys.projects }),
          ])
        : undefined,
  });
  useEffect(() => {
    if (!project.isSuccess || project.data?.role === "MANAGER") return;
    setInviteOpen(false);
    qc.removeQueries({ queryKey: ["share-invitation", id] });
  }, [id, project.data?.role, project.isSuccess, qc]);
  if (!project.isSuccess)
    return (
      <Shell>
        <QueryState query={project} />
      </Shell>
    );
  if (!project.data)
    return (
      <Shell>
        <ProjectMissing
          onRetry={() => project.refetch()}
          isFetching={project.isFetching}
        />
      </Shell>
    );
  const manager = project.data.role === "MANAGER";
  return (
    <Shell project={project.data}>
      <div className="page-heading">
        <div>
          <p className="eyebrow">프로젝트</p>
          <h1>구성원</h1>
          <p className="lead">
            프로젝트에 접근할 수 있는 사람과 역할을 관리합니다.
          </p>
        </div>
        {manager && (
          <button
            className="button button-primary"
            onClick={() => setInviteOpen(true)}
          >
            구성원 초대
          </button>
        )}
      </div>
      <QueryState query={members} loadingMessage="구성원을 불러오는 중…" />
      {denied && <p>구성원 접근 권한을 다시 확인해야 합니다.</p>}
      {members.isSuccess && !denied && (
        <div className="member-list">
          {members.data.map((m) => {
            const draft = roleDrafts[m.userId] ?? m.role;
            const changed = draft !== m.role;
            return (
              <div className="member-row" key={m.userId}>
                <div>
                  <strong>
                    {m.displayName || "이름 없는 구성원"}
                    {me.data && m.userId === me.data.id && <span> · 나</span>}
                  </strong>
                  <span>{m.email || "이메일 비공개"}</span>
                </div>
                <div className="member-role">
                  <label htmlFor={`role-${m.userId}`}>역할</label>
                  <select
                    id={`role-${m.userId}`}
                    aria-label={`${m.displayName || "구성원"} 역할`}
                    value={draft}
                    disabled={!manager || change.isPending}
                    onChange={(e) =>
                      setRoleDrafts((v) => ({
                        ...v,
                        [m.userId]: e.target.value,
                      }))
                    }
                  >
                    <option value="MANAGER">관리자</option>
                    <option value="MEMBER">구성원</option>
                    <option value="VIEWER">조회자</option>
                  </select>
                  {changed && (
                    <span className="row-actions">
                      <button
                        type="button"
                        disabled={!manager || change.isPending}
                        onClick={() => {
                          if (manager && !change.isPending)
                            change.mutate({ userId: m.userId, role: draft });
                        }}
                      >
                        저장
                      </button>
                      <button
                        type="button"
                        className="text-button"
                        onClick={() =>
                          setRoleDrafts((v) => ({ ...v, [m.userId]: m.role }))
                        }
                      >
                        취소
                      </button>
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
      {members.isSuccess && (
        <div className="pagination">
          <button
            disabled={!page || members.isFetching}
            onClick={() => setPage(page - 1)}
          >
            이전
          </button>
          <span>{page + 1} 페이지</span>
          <button
            disabled={members.data.length < 100 || members.isFetching}
            onClick={() => setPage(page + 1)}
          >
            다음
          </button>
        </div>
      )}
      {change.isError && <Notice error={change.error} />}
      <ShareInvitationDialog
        id={id}
        projectName={project.data.name}
        open={inviteOpen && manager}
        onClose={() => {
          setInviteOpen(false);
          if (inviteInitially) go(`/projects/${id}/members`);
        }}
      />
    </Shell>
  );
}
export function InviteCreate({ id }: { id: string }) {
  return <Members id={id} inviteInitially />;
}
const notificationLabels: Record<string, string> = {
  SCHEDULE_CREATED: "일정 생성",
  SCHEDULE_UPDATED: "일정 변경",
  SCHEDULE_CONFIRMED: "일정 확정",
  SCHEDULE_CANCELLED: "일정 취소",
  SCHEDULE_ACKNOWLEDGED: "참여자 확인",
  CALENDAR_SYNCED: "Calendar 동기화 완료",
  CALENDAR_SYNC_FAILED: "Calendar 동기화 실패",
  INVITATION_CREATED: "초대 생성",
  INVITATION_ACCEPTED: "초대 수락",
  INVITATION_REJECTED: "초대 거절",
};
export function Notifications() {
  const [page, setPage] = useState(0);
  const qc = useQueryClient();
  const list = useQuery({
    queryKey: [...keys.notifications, page],
    queryFn: () => api.notifications(page),
  });
  const read = useMutation({
    mutationFn: api.readNotification,
    onMutate: captureSession,
    onSuccess: (_, __, ctx) =>
      isSessionContextActive(ctx)
        ? qc.invalidateQueries({ queryKey: keys.notifications })
        : undefined,
  });
  return (
    <Shell>
      <div className="narrow-page">
        <p className="eyebrow">모든 프로젝트</p>
        <h1>알림</h1>
        <QueryState query={list} />
        {list.isSuccess &&
          (list.data.length ? (
            <ul className="notification-list">
              {list.data.map((n) => (
                <li key={n.id}>
                  <div>
                    <strong>
                      {notificationLabels[n.type] ?? "프로젝트 알림"}
                    </strong>
                    <time dateTime={n.createdAt}>{dateLabel(n.createdAt)}</time>
                  </div>
                  {internalLink(n.link) && (
                    <Link to={internalLink(n.link)!}>
                      {n.link?.includes("/schedules/")
                        ? "관련 일정 보기"
                        : "관련 프로젝트 보기"}
                    </Link>
                  )}
                  {n.readAt ? (
                    "읽음"
                  ) : (
                    <button
                      type="button"
                      disabled={read.isPending}
                      onClick={() => !read.isPending && read.mutate(n.id)}
                    >
                      읽음 처리
                    </button>
                  )}
                </li>
              ))}
            </ul>
          ) : (
            <div className="empty-state">
              <h2>새 알림이 없습니다</h2>
              <p>새로운 프로젝트 활동이 생기면 여기에 표시됩니다.</p>
            </div>
          ))}
        {read.isError && <Notice error={read.error} />}
        <div className="pagination">
          <button
            disabled={!page || list.isFetching}
            onClick={() => setPage(page - 1)}
          >
            이전
          </button>
          <span>{page + 1} 페이지</span>
          <button
            disabled={
              !list.isSuccess || list.data.length < 100 || list.isFetching
            }
            onClick={() => setPage(page + 1)}
          >
            다음
          </button>
        </div>
      </div>
    </Shell>
  );
}
export function Calendar() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: keys.connection, queryFn: api.calendar });
  const m = useMutation({
    mutationFn: api.reconnect,
    onMutate: captureSession,
    onSuccess: async (v, _, ctx) => {
      if (isSessionContextActive(ctx)) {
        qc.setQueryData(keys.connection, v);
        await qc.invalidateQueries({ queryKey: keys.connection });
        if (!isSessionContextActive(ctx)) return;
        await qc.invalidateQueries({ queryKey: ["projection"] });
      }
    },
  });
  const statusLabels: Record<string, string> = {
    REAUTH_REQUIRED: "다시 인증 필요",
    FAILED: "동기화 실패",
    PENDING: "동기화 대기 중",
    SYNCED: "동기화 완료",
    NOT_CONNECTED: "연결되지 않음",
  };
  return (
    <Shell>
      <div className="narrow-page">
        <p className="eyebrow">계정 전체</p>
        <h1>Calendar 연결</h1>
        <p className="lead">이 연결은 모든 프로젝트 일정에 적용됩니다.</p>
        <QueryState query={q} />
        {q.data && (
          <div className="status-card">
            <span>연결 상태</span>
            <strong>
              {q.data.configurationRequired
                ? "Calendar 연동이 구성되지 않았습니다."
                : statusLabels[q.data.status] ?? "상태 확인 필요"}
            </strong>
            <button
              type="button"
              className="button button-secondary"
              disabled={m.isPending || q.data.configurationRequired}
              onClick={() => m.mutate()}
            >
              {m.isPending ? "확인하는 중…" : "연결 상태 새로고침"}
            </button>
          </div>
        )}
        {m.isError && <Notice error={m.error} />}
      </div>
    </Shell>
  );
}
export function Account() {
  const me = useQuery({ queryKey: keys.me, queryFn: api.me });
  return (
    <Shell>
      <div className="narrow-page">
        <p className="eyebrow">계정 전체</p>
        <h1>내 계정</h1>
        <QueryState query={me} />
        {me.data && (
          <div className="account-card">
            <strong>{me.data.displayName || "이름 없는 계정"}</strong>
            <span>{me.data.email || "이메일 비공개"}</span>
            <Link className="button button-primary" to="/account/google">Google 서비스 연결</Link>
            <div className="account-service-links">
              <Link to="/account/drive">내 Drive</Link>
              <Link to="/account/mail">Gmail</Link>
            </div>
            <Link className="button button-secondary" to="/calendar">Calendar 연결 설정</Link>
            <AccountSwitch />
          </div>
        )}
      </div>
    </Shell>
  );
}
