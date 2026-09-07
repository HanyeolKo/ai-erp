import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  api,
  ApiError,
  type GoogleConnection,
  type MailDetail,
  type MailMessage,
  type MailSendReceipt,
} from "../api/client";
import { captureSession, isSessionContextActive } from "../session";
import { keys } from "../state";
import { Dialog, Link, Notice, QueryState, Shell } from "../ui";
import "./GoogleWorkspace.css";

const statusLabels: Record<string, string> = {
  CONNECTED: "연결됨",
  NOT_CONNECTED: "연결되지 않음",
  PERMISSION_REQUIRED: "권한 추가 필요",
  REAUTH_REQUIRED: "다시 인증 필요",
};

const featureLabels = {
  DRIVE: "Drive 파일 찾기",
  GMAIL: "Gmail 읽기 및 보내기",
  CALENDAR: "ERP 일정 동기화",
} as const;

function statusLabel(status: string) {
  return statusLabels[status] ?? "상태 확인 필요";
}

function isGoogleAccessProblem(error: unknown) {
  if (!(error instanceof ApiError)) return false;
  const code = error.problem.code ?? "";
  return (
    code === "GOOGLE_PERMISSION_REQUIRED" ||
    code === "GOOGLE_REAUTH_REQUIRED" ||
    [403, 404].includes(error.status)
  );
}

function isGoogleDriveUrl(value: string) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && url.hostname === "drive.google.com" && url.pathname === "/open";
  } catch {
    return false;
  }
}

function ConnectionCard({
  feature,
  status,
  onConnect,
  pending,
}: {
  feature: keyof typeof featureLabels;
  status: string;
  onConnect: (feature: keyof typeof featureLabels) => void;
  pending: boolean;
}) {
  const ready = status === "CONNECTED";
  return (
    <article className="google-service-card">
      <div>
        <p className="eyebrow">Google 서비스</p>
        <h2>{featureLabels[feature]}</h2>
        <p className="google-status" data-status={status}>
          {statusLabel(status)}
        </p>
      </div>
      {ready ? (
          <Link className="button button-secondary" to={feature === "DRIVE" ? "/account/drive" : feature === "GMAIL" ? "/account/mail" : "/account/google"}>
          {feature === "CALENDAR" ? "연결 상태 보기" : "열기"}
        </Link>
      ) : (
        <button className="button button-primary" type="button" disabled={pending} onClick={() => onConnect(feature)}>
          {pending ? "연결 준비 중…" : status === "NOT_CONNECTED" ? "Google 권한 연결" : "권한 다시 연결"}
        </button>
      )}
    </article>
  );
}

export function GoogleWorkspace() {
  const qc = useQueryClient();
  const connection = useQuery({ queryKey: keys.googleConnection, queryFn: api.googleConnection });
  const [authorizationUrl, setAuthorizationUrl] = useState<string>();
  const [authorizationError, setAuthorizationError] = useState("");
  const safeAuthorizationUrl = (value: string) => {
    try {
      const url = new URL(value, window.location.origin);
      return url.origin === window.location.origin && url.pathname === "/oauth2/authorization/google" ? `${url.pathname}${url.search}` : "";
    } catch {
      return "";
    }
  };
  const connect = useMutation({
    mutationFn: (feature: "DRIVE" | "GMAIL" | "CALENDAR") => api.connectGoogle(feature),
    onMutate: captureSession,
    onSuccess: (value, _feature, context) => {
      if (!isSessionContextActive(context)) return;
      const safeUrl = safeAuthorizationUrl(value.authorizationUrl);
      setAuthorizationUrl(safeUrl || undefined);
      setAuthorizationError(safeUrl ? "" : "연결 주소를 확인하지 못했습니다. 다시 시도해 주세요.");
    },
  });
  const disconnect = useMutation({
    mutationFn: api.disconnectGoogle,
    onMutate: captureSession,
    onSuccess: async (_value, _variables, context) => {
      if (!isSessionContextActive(context)) return;
      setAuthorizationUrl(undefined);
      setAuthorizationError("");
      for (let index = sessionStorage.length - 1; index >= 0; index -= 1) {
        const key = sessionStorage.key(index);
        if (key?.startsWith("ai-erp.google-mail-send:")) sessionStorage.removeItem(key);
      }
      await Promise.all([
        qc.invalidateQueries({ queryKey: keys.googleConnection }),
        qc.removeQueries({ queryKey: keys.driveFiles }),
        qc.removeQueries({ queryKey: ["google-mail"] }),
        qc.removeQueries({ queryKey: keys.googleCalendars }),
      ]);
      if (!isSessionContextActive(context)) return;
    },
  });
  const data = connection.isSuccess && !connection.isFetching ? connection.data as GoogleConnection : undefined;
  const outcome = new URLSearchParams(window.location.hash.split("?")[1] || "").get("outcome");
  const outcomeLabels: Record<string, string> = { CONNECTED: "Google 서비스 연결이 완료되었습니다.", connected: "Google 서비스 연결이 완료되었습니다.", CANCELLED: "Google 권한 연결을 취소했습니다.", cancelled: "Google 권한 연결을 취소했습니다.", denied: "Google 권한 연결을 취소했습니다.", FAILED: "Google 서비스 연결을 완료하지 못했습니다.", failed: "Google 서비스 연결을 완료하지 못했습니다.", PERMISSION_REQUIRED: "필요한 Google 권한이 승인되지 않았습니다." };
  return (
    <Shell>
      <div className="google-page narrow-page">
        <p className="eyebrow">개인 Google</p>
        <h1>Google 서비스</h1>
        <p className="lead">이 연결은 내 계정에만 적용됩니다. 메일과 Drive 목록은 다른 프로젝트 구성원에게 보이지 않습니다.</p>
        {outcome && outcomeLabels[outcome] && <p className="google-connect-next" role="status">{outcomeLabels[outcome]}</p>}
        <QueryState query={connection} loadingMessage="Google 연결 상태를 확인하는 중…" />
        {data && !data.configurationRequired && (
          <>
            <section className="google-account-summary" aria-label="연결된 Google 계정">
              <span>연결 계정</span>
              <strong>{data.accountEmail || "이메일 비공개"}</strong>
              <button type="button" disabled={connection.isFetching} onClick={() => connection.refetch()}>연결 상태 새로고침</button>
            </section>
            <div className="google-service-list">
              {(["DRIVE", "GMAIL", "CALENDAR"] as const).map((feature) => (
                <ConnectionCard key={feature} feature={feature} status={data[feature.toLowerCase() as "drive" | "gmail" | "calendar"].status} onConnect={(value) => connect.mutate(value)} pending={connect.isPending} />
              ))}
            </div>
            {authorizationUrl && (
              <div className="google-connect-next" role="status">
                <p>Google 동의 화면을 열어 필요한 권한을 확인하세요.</p>
                <a className="button button-primary" href={authorizationUrl}>Google 동의 화면 열기</a>
              </div>
            )}
            <p className="google-note">Google 서비스 연결을 해제하면 AI ERP에 저장된 서비스 연결 정보와 개인 목록 캐시를 지웁니다. Google 로그인과 원본 파일·메일·캘린더는 유지됩니다.</p>
            <button className="button button-danger" type="button" disabled={disconnect.isPending} onClick={() => { if (window.confirm("AI ERP에 저장된 Google 서비스 연결 정보를 지울까요?")) disconnect.mutate(); }}>
              Google 서비스 연결 해제
            </button>
          </>
        )}
        {data?.configurationRequired && <p className="google-note">Google Workspace가 아직 구성되지 않았습니다.</p>}
        {connect.isError && <Notice error={connect.error} />}
        {authorizationError && <p className="field-error" role="alert">{authorizationError}</p>}
        {disconnect.isError && <Notice error={disconnect.error} />}
      </div>
    </Shell>
  );
}

export function Drive() {
  const connection = useQuery({ queryKey: keys.googleConnection, queryFn: api.googleConnection });
  const [draft, setDraft] = useState("");
  const [queryText, setQueryText] = useState("");
  const [pageToken, setPageToken] = useState("");
  const [pageHistory, setPageHistory] = useState<string[]>([""]);
  const connectionReady = connection.isSuccess && !connection.isFetching;
  const visibleConnection = connectionReady ? connection.data : undefined;
  const connected = connectionReady && connection.data.drive.status === "CONNECTED";
  const files = useQuery({ queryKey: [...keys.driveFiles, queryText, pageToken], queryFn: () => api.driveFiles(queryText, pageToken), enabled: connected });
  return <Shell><div className="google-page drive-page"><p className="eyebrow">개인 Google</p><h1>내 Drive</h1><p className="lead">개인 Drive 파일을 찾아보고 Google에서 열 수 있습니다. 이 화면을 방문해도 프로젝트에 공유되지 않습니다.</p>{visibleConnection && visibleConnection.drive.status !== "CONNECTED" && <div className="google-blocked"><p>Drive 파일을 보려면 Drive 메타데이터 권한이 필요합니다.</p><Link className="button button-secondary" to="/account/google">Google 연결 설정</Link></div>}{connected && <><form className="drive-search" onSubmit={(event) => { event.preventDefault(); setQueryText(draft.trim()); setPageToken(""); setPageHistory([""]); }}><label>파일 이름 검색<input value={draft} onChange={(event) => setDraft(event.target.value)} /></label><button type="submit">검색</button><button type="button" onClick={() => { setDraft(""); setQueryText(""); setPageToken(""); setPageHistory([""]); }}>검색 지우기</button></form><QueryState query={files} loadingMessage="Drive 파일을 불러오는 중…" />{files.isSuccess && !files.isFetching && (files.data.files.length ? <ul className="drive-file-list">{files.data.files.map((file) => <li key={file.id}>{isGoogleDriveUrl(file.url) ? <a href={file.url} target="_blank" rel="noreferrer"><strong>{file.name || "이름 없는 파일"}</strong><span>{file.mimeType}</span><time dateTime={file.modifiedTime}>{new Date(file.modifiedTime).toLocaleString("ko-KR")}</time></a> : <div><strong>{file.name || "이름 없는 파일"}</strong><span>안전한 Google Drive 링크를 확인할 수 없습니다.</span></div>}</li>)}</ul> : <div className="empty-state"><h2>{queryText ? "검색 결과가 없습니다" : "Drive 파일이 없습니다"}</h2><p>검색어를 바꾸거나 Google Drive에서 파일을 추가해 보세요.</p></div>)}{files.isSuccess && !files.isFetching && <nav className="pagination" aria-label="Drive 페이지 이동"><button type="button" disabled={pageHistory.length <= 1 || files.isFetching} onClick={() => { const next = pageHistory.slice(0, -1); setPageHistory(next); setPageToken(next[next.length - 1] || ""); }}>이전</button><span>개인 Drive</span><button type="button" disabled={!files.data.nextPageToken || files.isFetching} onClick={() => { const next = files.data.nextPageToken || ""; setPageHistory([...pageHistory, next]); setPageToken(next); }}>다음</button></nav>}</>}</div></Shell>;
}

function splitRecipients(value: string) {
  return value.split(/[;,\n]/).map((item) => item.trim()).filter(Boolean);
}

function validMail(value: string) {
  return /^[^\s@\r\n]+@[^\s@\r\n]+\.[^\s@\r\n]+$/.test(value);
}

function newRequestId() {
  return crypto.randomUUID();
}

function mailStatus(receipt?: MailSendReceipt) {
  if (!receipt) return null;
  if (receipt.status === "SENT") return "메일을 보냈습니다.";
  if (receipt.status === "SENDING") return "전송 결과를 확인하는 중…";
  if (receipt.status === "UNKNOWN") return "전송 결과를 확인하지 못했습니다. Gmail의 보낸편지함에서 확인해 주세요.";
  return "메일이 전송되지 않았습니다. 내용을 수정한 뒤 새로 확인해 주세요.";
}

function MessageRow({ message, onOpen }: { message: MailMessage; onOpen: (id: string) => void }) {
  return (
    <li className={message.unread ? "mail-row is-unread" : "mail-row"}>
      <button type="button" onClick={() => onOpen(message.id)}>
        <strong>{message.subject || "(제목 없음)"}</strong>
        <span>{message.from || message.to.join(", ")}</span>
        <small>{message.snippet}</small>
      </button>
      <time dateTime={message.internalDate}>{new Date(message.internalDate).toLocaleString("ko-KR")}</time>
    </li>
  );
}

export function Gmail() {
  const me = useQuery({ queryKey: keys.me, queryFn: api.me });
  const connection = useQuery({ queryKey: keys.googleConnection, queryFn: api.googleConnection });
  const [folder, setFolder] = useState<"INBOX" | "SENT">("INBOX");
  const [searchDraft, setSearchDraft] = useState("");
  const [queryText, setQueryText] = useState("");
  const [pageToken, setPageToken] = useState("");
  const [pageHistory, setPageHistory] = useState<string[]>([""]);
  const [selectedId, setSelectedId] = useState<string>();
  const [composeOpen, setComposeOpen] = useState(false);
  const [review, setReview] = useState(false);
  const [formError, setFormError] = useState("");
  const [form, setForm] = useState({ to: "", cc: "", bcc: "", subject: "", body: "" });
  const connectionReady = connection.isSuccess && !connection.isFetching;
  const visibleConnection = connectionReady ? connection.data : undefined;
  const connected = connectionReady && connection.data.gmail.status === "CONNECTED";
  const messages = useQuery({ queryKey: [...keys.mail(folder), queryText, pageToken], queryFn: () => api.mailMessages(folder, queryText, pageToken), enabled: connected });
  const detail = useQuery({ queryKey: selectedId ? keys.mailDetail(selectedId) : ["google-mail-detail", "none"], queryFn: () => api.mailDetail(selectedId!), enabled: !!selectedId && connected });
  const storageKey = me.data?.id ? `ai-erp.google-mail-send:${me.data.id}` : "";
  const [requestId, setRequestId] = useState("");
  useEffect(() => { if (storageKey) setRequestId(sessionStorage.getItem(storageKey) || ""); }, [storageKey]);
  const send = useMutation({
    mutationFn: (body: { requestId: string; to: string[]; cc: string[]; bcc: string[]; subject: string; body: string }) => api.sendMail(body),
    onMutate: captureSession,
    onSuccess: (_value, variables, context) => { if (!isSessionContextActive(context)) return; setRequestId(variables.requestId); if (storageKey) sessionStorage.setItem(storageKey, variables.requestId); },
  });
  const receipt = useQuery({ queryKey: requestId ? keys.mailSend(requestId) : ["google-mail-send", "none"], queryFn: () => api.mailSendReceipt(requestId), enabled: !!requestId && connected && !send.isPending && !send.data });
  useEffect(() => {
    if ((!connectionReady && !connection.isError) || connected) return;
    setSelectedId(undefined);
    setComposeOpen(false);
    clearCompose();
    setRequestId("");
    if (storageKey) sessionStorage.removeItem(storageKey);
  }, [connected, connection.isError, connectionReady, storageKey]);
  const status = send.data?.status === "SENT" ? send.data : receipt.data ?? send.data;
  const connectionError = connection.isError && isGoogleAccessProblem(connection.error);
  const validate = () => {
    const to = splitRecipients(form.to);
    const cc = splitRecipients(form.cc);
    const bcc = splitRecipients(form.bcc);
    if (!to.length || [...to, ...cc, ...bcc].some((value) => !validMail(value))) return "받는 사람 주소를 확인해 주세요.";
    if ([...to, ...cc, ...bcc].length > 20) return "받는 사람은 최대 20명까지 입력할 수 있습니다.";
    if (!form.subject.trim() || form.subject.length > 200 || /[\r\n]/.test(form.subject)) return "제목을 입력해 주세요.";
    if (form.body.length > 100000) return "본문은 100,000자까지 입력할 수 있습니다.";
    return "";
  };
  const submitReview = () => { const error = validate(); setFormError(error); if (!error) setReview(true); };
  const submitSend = () => { const error = validate(); if (error) { setFormError(error); return; } const id = newRequestId(); const payload = { requestId: id, to: splitRecipients(form.to), cc: splitRecipients(form.cc), bcc: splitRecipients(form.bcc), subject: form.subject.trim(), body: form.body }; setFormError(""); setRequestId(id); if (storageKey) sessionStorage.setItem(storageKey, id); clearCompose(); setComposeOpen(false); send.mutate(payload); };
  const clearCompose = () => { setForm({ to: "", cc: "", bcc: "", subject: "", body: "" }); setReview(false); setFormError(""); };
  const startCompose = () => { clearCompose(); setRequestId(""); if (storageKey) sessionStorage.removeItem(storageKey); send.reset(); setComposeOpen(true); };
  const closeCompose = () => { if ((form.to || form.cc || form.bcc || form.subject || form.body) && !window.confirm("작성 중인 메일을 버릴까요?")) return; clearCompose(); setComposeOpen(false); };
  const resetSearch = () => { setSearchDraft(""); setQueryText(""); setPageToken(""); setPageHistory([""]); };
  const folderLabel = folder === "INBOX" ? "받은편지함" : "보낸편지함";
  return (
    <Shell>
      <div className="google-page mail-page">
        <header className="page-heading"><div><p className="eyebrow">개인 Google</p><h1>Gmail</h1><p className="lead">메일을 읽고 검토한 뒤 직접 보낼 수 있습니다. 목록을 열어도 읽음 상태는 바뀌지 않습니다.</p></div><button type="button" className="button button-primary" disabled={!connected} onClick={startCompose}>메일 작성</button></header>
        {connectionError && <div className="google-blocked"><p>Gmail 권한을 확인한 뒤 메일함을 열 수 있습니다.</p><Link className="button button-secondary" to="/account/google">Google 연결 설정</Link></div>}
        {!connectionError && visibleConnection && visibleConnection.gmail.status !== "CONNECTED" && <div className="google-blocked"><p>Gmail 읽기와 보내기 권한을 함께 연결해야 메일함을 사용할 수 있습니다.</p><Link className="button button-secondary" to="/account/google">Google 권한 연결</Link></div>}
        {connected && <>
          <div className="mail-toolbar"><div className="segmented" role="tablist" aria-label="메일함"><button type="button" role="tab" aria-selected={folder === "INBOX"} onClick={() => { setFolder("INBOX"); setPageToken(""); setPageHistory([""]); }}>받은편지함</button><button type="button" role="tab" aria-selected={folder === "SENT"} onClick={() => { setFolder("SENT"); setPageToken(""); setPageHistory([""]); }}>보낸편지함</button></div><form onSubmit={(event) => { event.preventDefault(); setQueryText(searchDraft.trim()); setPageToken(""); setPageHistory([""]); }}><label>메일 검색<input value={searchDraft} onChange={(event) => setSearchDraft(event.target.value)} /></label><button type="submit">검색</button><button type="button" onClick={resetSearch}>검색 지우기</button></form></div>
          <QueryState query={messages} loadingMessage="메일을 불러오는 중…" />
          {messages.isSuccess && !messages.isFetching && (messages.data.messages.length ? <ul className="mail-list" aria-label={folderLabel}>{messages.data.messages.map((message) => <MessageRow key={message.id} message={message} onOpen={setSelectedId} />)}</ul> : <div className="empty-state"><h2>{queryText ? "검색 결과가 없습니다" : `${folderLabel}에 메일이 없습니다`}</h2><p>{queryText ? "검색어를 바꾸거나 검색을 지워 보세요." : "새 메시지가 도착하면 여기에 표시됩니다."}</p></div>)}
          {messages.isSuccess && !messages.isFetching && <nav className="pagination" aria-label="메일 페이지 이동"><button type="button" disabled={pageHistory.length <= 1 || messages.isFetching} onClick={() => { const next = pageHistory.slice(0, -1); setPageHistory(next); setPageToken(next[next.length - 1] || ""); }}>이전</button><span>{folderLabel}</span><button type="button" disabled={!messages.data.nextPageToken || messages.isFetching} onClick={() => { const next = messages.data.nextPageToken || ""; setPageHistory([...pageHistory, next]); setPageToken(next); }}>다음</button></nav>}
          {status && <section className="mail-send-status" aria-live="polite"><strong>{mailStatus(status)}</strong>{(status.status === "UNKNOWN" || status.status === "SENDING") && <button type="button" disabled={receipt.isFetching} onClick={() => receipt.refetch()}>전송 결과 확인</button>}{status.status === "UNKNOWN" && <a href="https://mail.google.com/mail/u/0/#sent" target="_blank" rel="noreferrer">Gmail 보낸편지함 열기</a>}{status.status === "FAILED" && <button type="button" onClick={startCompose}>새 메일 작성</button>}</section>}
        </>}
      </div>
      <Dialog open={!!selectedId && connected} title={connected && detail.isSuccess && !detail.isFetching ? detail.data?.subject || "메일 상세" : "메일 상세"} onClose={() => setSelectedId(undefined)} labelledBy="mail-detail-title">
        {detail.isPending && <p role="status">메일을 불러오는 중…</p>}{detail.isError && <Notice error={detail.error} />}{connected && detail.isSuccess && !detail.isFetching && detail.data && <MailDetailView detail={detail.data} />}
      </Dialog>
      <Dialog open={composeOpen} title="메일 작성" onClose={closeCompose} labelledBy="mail-compose-title">
        {!review ? <form className="mail-compose" onSubmit={(event) => { event.preventDefault(); submitReview(); }}><label>To<input value={form.to} onChange={(event) => setForm({ ...form, to: event.target.value })} placeholder="person@example.com" /></label><label>Cc<input value={form.cc} onChange={(event) => setForm({ ...form, cc: event.target.value })} /></label><label>Bcc<input value={form.bcc} onChange={(event) => setForm({ ...form, bcc: event.target.value })} /></label><label>제목<input value={form.subject} onChange={(event) => setForm({ ...form, subject: event.target.value })} /></label><label>본문<textarea value={form.body} onChange={(event) => setForm({ ...form, body: event.target.value })} rows={10} /></label>{formError && <p className="field-error" role="alert">{formError}</p>}<button className="button button-primary" type="submit">보내기 확인</button></form> : <div className="mail-review"><p>발신자: {visibleConnection?.accountEmail || "연결 계정"}</p><p>To: {splitRecipients(form.to).join(", ")}</p>{form.cc && <p>Cc: {splitRecipients(form.cc).join(", ")}</p>}{form.bcc && <p>Bcc: {splitRecipients(form.bcc).join(", ")}</p>}<p>제목: {form.subject}</p><pre>{form.body}</pre>{formError && <p className="field-error" role="alert">{formError}</p>}<div className="action-row"><button type="button" onClick={() => setReview(false)}>작성으로 돌아가기</button><button className="button button-primary" type="button" disabled={send.isPending} onClick={submitSend}>{send.isPending ? "보내는 중…" : "메일 보내기"}</button></div></div>}
      </Dialog>
    </Shell>
  );
}

function MailDetailView({ detail }: { detail: MailDetail }) {
  return <article className="mail-detail"><p>보낸 사람: {detail.from}</p><p>받는 사람: {detail.to.join(", ")}</p>{detail.cc.length > 0 && <p>Cc: {detail.cc.join(", ")}</p>}<time dateTime={detail.date}>{new Date(detail.date).toLocaleString("ko-KR")}</time><pre>{detail.bodyText}</pre>{detail.truncated && <p className="google-note">본문 일부만 표시했습니다.</p>}</article>;
}
