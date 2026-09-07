import { useEffect, useRef, useState } from "react";
import {
  MutationCache,
  QueryCache,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import {
  Auth,
  Calendar,
  Invite,
  InviteCreate,
  JoinPreview,
  Members,
  Notifications,
  Projects,
  Account,
} from "./screens/Account";
import { Dashboard, Schedules } from "./screens/Schedules";
import { ScheduleForm } from "./screens/ScheduleForm";
import { Detail } from "./screens/Detail";
import { Drive, GoogleWorkspace, Gmail } from "./screens/GoogleWorkspace";
import { ProjectCalendarSettings, ProjectFiles } from "./screens/ProjectFiles";
import { Link, Shell } from "./ui";
import { ApiError } from "./api/client";
import { keys, resetAccessState, SESSION_EXPIRED_EVENT } from "./state";
import {
  beginSessionBoundary,
  isSessionActive,
  terminateSession,
} from "./session";
import "./app.css";
function Routes() {
  const [path, setPath] = useState(
    () => window.location.hash.replace(/^#/, "") || "/",
  );
  const previousPath = useRef(path);
  useEffect(() => {
    const change = () => setPath(window.location.hash.replace(/^#/, "") || "/");
    window.addEventListener("hashchange", change);
    return () => window.removeEventListener("hashchange", change);
  }, []);
  useEffect(() => {
    if (previousPath.current === path) return;
    previousPath.current = path;
    const focusHeading = () => {
      const heading = document.querySelector<HTMLElement>("main h1");
      if (!heading) return false;
      heading.tabIndex = -1;
      heading.focus();
      return true;
    };
    if (focusHeading()) return;
    const observer = new MutationObserver(() => {
      if (focusHeading()) observer.disconnect();
    });
    observer.observe(document.body, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, [path]);
  const invite = path.match(/^\/invitations\/([A-Za-z0-9_-]+)$/);
  const join = path.match(/^\/join\/([0-9A-HJKMNP-TV-Za-z]{16})$/i);
  const project = path.match(/^\/projects\/([A-Za-z0-9_-]+)(?:\/(.*))?$/);
  if (invite) return <Invite key={invite[1]} token={invite[1]} />;
  if (join)
    return (
      <JoinPreview key={join[1].toUpperCase()} code={join[1].toUpperCase()} />
    );
  if (path === "/account") return <Account />;
  if (path === "/account/google") return <GoogleWorkspace />;
  if (path === "/account/drive") return <Drive />;
  if (path === "/account/mail") return <Gmail />;
  if (path === "/notifications") return <Notifications />;
  if (path === "/calendar") return <Calendar />;
  if (project) {
    const [, id, rest = ""] = project;
    if (rest === "") return <Dashboard key={id} id={id} />;
    if (rest === "invitations/new") return <InviteCreate key={id} id={id} />;
    if (rest === "members") return <Members key={id} id={id} />;
    if (rest === "files") return <ProjectFiles key={id} id={id} />;
    if (rest === "calendar") return <ProjectCalendarSettings key={id} id={id} />;
    if (rest === "schedules") return <Schedules key={id} id={id} />;
    if (rest === "schedules/new") return <ScheduleForm key={id} id={id} />;
    const edit = rest.match(/^schedules\/([A-Za-z0-9_-]+)\/edit$/);
    if (edit)
      return <ScheduleForm key={id + edit[1]} id={id} scheduleId={edit[1]} />;
    const detail = rest.match(/^schedules\/([A-Za-z0-9_-]+)$/);
    if (detail)
      return <Detail key={id + detail[1]} id={id} scheduleId={detail[1]} />;
  }
  if (path === "/") return <Projects />;
  return (
    <Shell>
      <h1>화면을 찾을 수 없습니다.</h1>
      <Link to="/">프로젝트 선택</Link>
    </Shell>
  );
}
export default function App() {
  const [expired, setExpired] = useState(false);
  const [client] = useState(() => {
    beginSessionBoundary();
    resetAccessState();
    const onError = (error: unknown) => {
      if (
        error instanceof ApiError &&
        error.status === 401 &&
        (error.sessionGeneration === undefined ||
          isSessionActive(error.sessionGeneration))
      ) {
        terminateSession(error.sessionGeneration);
        setExpired(true);
      }
    };
    return new QueryClient({
      queryCache: new QueryCache({ onError }),
      mutationCache: new MutationCache({ onError }),
      defaultOptions: {
        queries: { retry: false, staleTime: 15000 },
        mutations: { retry: false },
      },
    });
  });
  const clearProtectedQueryData = () => {
    const protectedQueries = {
      predicate: (query: { queryKey: readonly unknown[] }) =>
        query.queryKey[0] !== keys.configuration[0],
    };
    return Promise.all([
      client.cancelQueries(protectedQueries),
      client.removeQueries(protectedQueries),
    ]).then(() => client.getMutationCache().clear());
  };
  useEffect(() => {
    const markExpired = () => {
      terminateSession();
      setExpired(true);
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, markExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, markExpired);
  }, []);
  useEffect(() => {
    if (!expired) return;
    void clearProtectedQueryData();
  }, [client, expired]);
  return (
    <QueryClientProvider client={client}>
      <Auth sessionExpired={expired}>
        <Routes />
      </Auth>
    </QueryClientProvider>
  );
}
