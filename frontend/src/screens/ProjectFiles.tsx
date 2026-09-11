import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type DriveFile } from "../api/client";
import { captureSession, isSessionContextActive } from "../session";
import { capabilities, keys, useProject } from "../state";
import { Dialog, Link, Notice, ProjectMissing, QueryState, Shell } from "../ui";
import "./GoogleWorkspace.css";

function dateLabel(value: string) {
  return new Date(value).toLocaleString("ko-KR");
}

function isGoogleDriveUrl(value: string) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && url.hostname === "drive.google.com" && url.pathname === "/open";
  } catch {
    return false;
  }
}

export function ProjectFiles({ id }: { id: string }) {
  const project = useProject(id);
  const connection = useQuery({ queryKey: keys.googleConnection, queryFn: api.googleConnection });
  const connectionReady = connection.isSuccess && !connection.isFetching;
  const visibleConnection = connectionReady ? connection.data : undefined;
  const qc = useQueryClient();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [selected, setSelected] = useState<DriveFile>();
  const [searchDraft, setSearchDraft] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const files = useQuery({ queryKey: [...keys.projectFiles(id), page], queryFn: () => api.projectFiles(id, String(page)), enabled: !!project.data });
  const drive = useQuery({ queryKey: [...keys.driveFiles, search], queryFn: () => api.driveFiles(search), enabled: pickerOpen && connection.isSuccess && !connection.isFetching && connection.data.drive.status === "CONNECTED" });
  useEffect(() => {
    if (connectionReady && visibleConnection?.drive.status !== "CONNECTED") {
      setPickerOpen(false);
      setSelected(undefined);
    }
  }, [connectionReady, visibleConnection?.drive.status]);
  const attach = useMutation({
    mutationFn: (fileId: string) => api.attachProjectFile(id, fileId),
    onMutate: captureSession,
    onSuccess: async (_value, _fileId, context) => { if (!isSessionContextActive(context)) return; setSelected(undefined); setPickerOpen(false); await qc.invalidateQueries({ queryKey: keys.projectFiles(id) }); if (!isSessionContextActive(context)) return; },
  });
  const remove = useMutation({
    mutationFn: (referenceId: string) => api.removeProjectFile(id, referenceId),
    onMutate: captureSession,
    onSuccess: async (_value, _referenceId, context) => { if (!isSessionContextActive(context)) return; await qc.invalidateQueries({ queryKey: keys.projectFiles(id) }); if (!isSessionContextActive(context)) return; },
  });
  if (!project.isSuccess) return <Shell><QueryState query={project} /></Shell>;
  if (!project.data) return <Shell><ProjectMissing onRetry={() => project.refetch()} isFetching={project.isFetching} /></Shell>;
  const canAttach = capabilities(project.data, "", undefined).create && project.data.role !== "VIEWER" && visibleConnection?.drive.status === "CONNECTED";
  return <Shell project={project.data}>
    <div className="google-page project-files-page">
      <header className="page-heading"><div><p className="eyebrow">{project.data.name}</p><h1>프로젝트 파일</h1><p className="lead">선택한 Drive 파일의 이름과 링크만 프로젝트 참조로 보관합니다.</p></div>{canAttach && <button type="button" className="button button-primary" onClick={() => setPickerOpen(true)}>Drive 파일 첨부</button>}</header>
      <p className="google-note">파일 이름과 링크가 이 프로젝트의 구성원에게 표시됩니다. Google Drive의 파일 접근 권한은 변경되지 않습니다.</p>
      <QueryState query={connection} loadingMessage="Google 연결 상태를 확인하는 중…" />
      {visibleConnection && visibleConnection.drive.status !== "CONNECTED" && <div className="google-blocked"><p>Drive 권한을 연결하면 프로젝트에 파일 참조를 추가할 수 있습니다.</p><Link className="button button-secondary" to="/account/google">Google 연결 설정</Link></div>}
      <QueryState query={files} loadingMessage="프로젝트 파일을 불러오는 중…" />
      {files.isSuccess && (files.data.files.length ? <ul className="project-file-list">{files.data.files.map((file) => <li key={file.id}><div><strong>{file.name || "이름 없는 파일"}</strong><span>{file.mimeType}</span><span>첨부자 {file.attachedBy} · {dateLabel(file.attachedAt)}</span></div><div className="action-row">{isGoogleDriveUrl(file.url) && <a href={file.url} target="_blank" rel="noreferrer">Google Drive에서 열기</a>}{file.canRemove && <button type="button" disabled={remove.isPending} onClick={() => { if (window.confirm("프로젝트 참조만 제거하며 Drive 원본은 삭제하지 않습니다.")) remove.mutate(file.id); }}>첨부 제거</button>}</div></li>)}</ul> : <div className="empty-state"><h2>첨부된 프로젝트 파일이 없습니다</h2><p>필요한 파일을 선택해 프로젝트 참조로 추가하세요.</p></div>)}
      {files.isSuccess && <nav className="pagination" aria-label="프로젝트 파일 페이지 이동"><button type="button" disabled={page === 0 || files.isFetching} onClick={() => setPage(page - 1)}>이전</button><span>{page + 1} 페이지 · 최대 25개</span><button type="button" disabled={!files.data.hasNext || files.isFetching} onClick={() => setPage(page + 1)}>다음</button></nav>}
      {attach.isError && <Notice error={attach.error} />}{remove.isError && <Notice error={remove.error} />}
    </div>
    <Dialog open={pickerOpen} title={`${project.data.name}에 Drive 파일 첨부`} onClose={() => { setPickerOpen(false); setSelected(undefined); }} labelledBy="drive-picker-title">
      <p>파일 한 개를 선택하고 이름과 종류를 확인한 뒤 첨부하세요.</p><p className="google-note">Google Drive의 파일 접근 권한은 변경되지 않습니다.</p>
      {visibleConnection?.drive.status === "CONNECTED" ? <><form className="drive-search" onSubmit={(event) => { event.preventDefault(); setSearch(searchDraft.trim()); }}><label>파일 이름 검색<input value={searchDraft} onChange={(event) => setSearchDraft(event.target.value)} /></label><button type="submit">검색</button><button type="button" onClick={() => { setSearchDraft(""); setSearch(""); }}>검색 지우기</button></form><QueryState query={drive} loadingMessage="Drive 파일을 불러오는 중…" />{drive.isSuccess && !drive.isFetching && (drive.data.files.length ? <ul className="drive-file-list">{drive.data.files.map((file) => <li key={file.id} className={selected?.id === file.id ? "is-selected" : ""}><button type="button" onClick={() => setSelected(file)}><strong>{file.name || "이름 없는 파일"}</strong><span>{file.mimeType}</span><time dateTime={file.modifiedTime}>{dateLabel(file.modifiedTime)}</time></button></li>)}</ul> : <p>{search ? "검색 결과가 없습니다." : "Drive 파일이 없습니다."}</p>)}{selected && <div className="selection-summary"><strong>선택한 파일</strong><span>{selected.name || "이름 없는 파일"} · {selected.mimeType}</span><button className="button button-primary" type="button" disabled={attach.isPending} onClick={() => attach.mutate(selected.id)}>{attach.isPending ? "첨부하는 중…" : "첨부"}</button></div>}</> : <p>Drive 권한을 먼저 연결해 주세요.</p>}
    </Dialog>
  </Shell>;
}

const calendarStatusLabels: Record<string, string> = { NOT_BOUND: "연결되지 않음", BOUND: "연결됨", REAUTH_REQUIRED: "다시 인증 필요" };

export function ProjectCalendarSettings({ id }: { id: string }) {
  const project = useProject(id);
  const connection = useQuery({ queryKey: keys.googleConnection, queryFn: api.googleConnection });
  const connectionReady = connection.isSuccess && !connection.isFetching;
  const calendar = useQuery({ queryKey: keys.projectCalendar(id), queryFn: () => api.projectCalendar(id), enabled: !!project.data });
  const calendars = useQuery({ queryKey: keys.googleCalendars, queryFn: () => api.googleCalendars(), enabled: calendar.data?.canManage === true && calendar.data.status === "NOT_BOUND" && connectionReady && connection.data.calendar.status === "CONNECTED" });
  const qc = useQueryClient();
  const [selected, setSelected] = useState("");
  const [disconnectOpen, setDisconnectOpen] = useState(false);
  const bind = useMutation({ mutationFn: (calendarId: string) => api.bindProjectCalendar(id, calendarId), onMutate: captureSession, onSuccess: async (_value, _id, context) => { if (!isSessionContextActive(context)) return; await qc.invalidateQueries({ queryKey: keys.projectCalendar(id) }); if (!isSessionContextActive(context)) return; } });
  const unbind = useMutation({ mutationFn: () => api.unbindProjectCalendar(id), onMutate: captureSession, onSuccess: async (_value, _id, context) => { if (!isSessionContextActive(context)) return; await qc.invalidateQueries({ queryKey: keys.projectCalendar(id) }); if (!isSessionContextActive(context)) return; } });
  if (!project.isSuccess) return <Shell><QueryState query={project} /></Shell>;
  if (!project.data) return <Shell><ProjectMissing onRetry={() => project.refetch()} isFetching={project.isFetching} /></Shell>;
  const visibleConnection = connectionReady ? connection.data : undefined;
  return <Shell project={project.data}><div className="google-page project-calendar-page"><p className="eyebrow">{project.data.name}</p><h1>프로젝트 Calendar 설정</h1><p className="lead">ERP에서 확정한 일정을 Google Calendar에 표시합니다.</p><QueryState query={calendar} loadingMessage="프로젝트 Calendar 설정을 불러오는 중…" />{calendar.data && <section className="calendar-binding-card"><div><span>현재 상태</span><strong>{calendarStatusLabels[calendar.data.status] ?? "상태 확인 필요"}</strong>{calendar.data.calendarName && <p>대상 Calendar: {calendar.data.calendarName}</p>}{calendar.data.ownerName && <p>연결 담당자: {calendar.data.ownerName}</p>}{calendar.data.backfillPending && <p role="status">기존 확정 일정을 반영하는 중…</p>}{calendar.data.status === "REAUTH_REQUIRED" && <p>Google Calendar 권한을 다시 연결한 뒤 상태를 확인해 주세요. <Link to="/account/google">Google 연결 설정</Link></p>}</div>{(calendar.data.status === "BOUND" || calendar.data.status === "REAUTH_REQUIRED") && calendar.data.canManage && <button type="button" disabled={unbind.isPending} onClick={() => setDisconnectOpen(true)}>Calendar 연결 해제</button>}</section>}{calendar.data?.status === "NOT_BOUND" && calendar.data.canManage && <section className="calendar-picker"><h2>연결할 Calendar 선택</h2><p>연결하면 이 프로젝트의 기존 확정 일정과 앞으로 확정하는 일정이 선택한 Google Calendar에 반영됩니다. Google에서 수정한 내용은 ERP에 반영되지 않습니다.</p>{visibleConnection?.calendar.status !== "CONNECTED" ? <p>Google Calendar 권한을 먼저 연결해 주세요. <Link to="/account/google">연결 설정</Link></p> : <><QueryState query={calendars} loadingMessage="쓰기 가능한 Calendar를 불러오는 중…" />{calendars.isSuccess && <><label>Calendar<select value={selected} onChange={(event) => setSelected(event.target.value)}><option value="">Calendar를 선택하세요</option>{calendars.data.calendars.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><button className="button button-primary" type="button" disabled={!selected || bind.isPending} onClick={() => bind.mutate(selected)}>{bind.isPending ? "연결하는 중…" : "이 Calendar 연결"}</button></>}</>}</section>}{bind.isError && <Notice error={bind.error} />}{unbind.isError && <Notice error={unbind.error} />}</div><Dialog open={disconnectOpen} title="Calendar 연결 해제" onClose={() => setDisconnectOpen(false)} labelledBy="project-calendar-disconnect-title"><p>기존 Google Calendar 일정은 그대로 남습니다. 연결 해제 후 새 동기화는 시작하지 않습니다. 이미 Google에 보낸 요청은 해제 후에도 완료될 수 있습니다.</p><div className="action-row"><button type="button" onClick={() => setDisconnectOpen(false)}>취소</button><button className="button button-danger" type="button" disabled={unbind.isPending} onClick={() => { unbind.mutate(); setDisconnectOpen(false); }}>{unbind.isPending ? "해제하는 중…" : "연결 해제"}</button></div></Dialog></Shell>;
}
