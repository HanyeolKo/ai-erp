import { useEffect, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Auth, Calendar, Invite, InviteCreate, Members, Notifications, Projects } from "./screens/Account";
import { Dashboard, Schedules } from "./screens/Schedules";
import { ScheduleForm } from "./screens/ScheduleForm";
import { Detail } from "./screens/Detail";
import { Link, Shell } from "./ui";
import "./app.css";
function Routes() {
    const [path, setPath] = useState(() => window.location.hash.replace(/^#/, "") || "/");
    useEffect(() => { const change = () => setPath(window.location.hash.replace(/^#/, "") || "/"); window.addEventListener("hashchange", change); return () => window.removeEventListener("hashchange", change); }, []);
    const invite = path.match(/^\/invitations\/([A-Za-z0-9_-]+)$/);
    const project = path.match(/^\/projects\/([A-Za-z0-9_-]+)(?:\/(.*))?$/);
    if (invite)
        return <Invite key={invite[1]} token={invite[1]}/>;
    if (path === "/notifications")
        return <Notifications />;
    if (path === "/calendar")
        return <Calendar />;
    if (project) {
        const [, id, rest = ""] = project;
        if (rest === "")
            return <Dashboard key={id} id={id}/>;
        if (rest === "invitations/new")
            return <InviteCreate key={id} id={id}/>;
        if (rest === "members")
            return <Members key={id} id={id}/>;
        if (rest === "schedules")
            return <Schedules key={id} id={id}/>;
        if (rest === "schedules/new")
            return <ScheduleForm key={id} id={id}/>;
        const edit = rest.match(/^schedules\/([A-Za-z0-9_-]+)\/edit$/);
        if (edit)
            return <ScheduleForm key={id + edit[1]} id={id} scheduleId={edit[1]}/>;
        const detail = rest.match(/^schedules\/([A-Za-z0-9_-]+)$/);
        if (detail)
            return <Detail key={id + detail[1]} id={id} scheduleId={detail[1]}/>;
    }
    if (path === "/")
        return <Projects />;
    return <Shell><p>화면을 찾을 수 없습니다.</p><Link to="/">프로젝트 선택</Link></Shell>;
}
export default function App() {
    const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 15000 }, mutations: { retry: false } } }));
    return <QueryClientProvider client={client}><Auth><Routes /></Auth></QueryClientProvider>;
}
