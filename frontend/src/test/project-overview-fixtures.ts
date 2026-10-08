import { json, schedule, workspace, workspaceQuery } from "./http";

type TestServer = { on: (method: string, path: string, handler: (body?: any, url?: URL) => Response | Promise<Response>) => void };

const overviewPlan = (projectId: string) => ({ projectId, rowVersion: 1, targetStart: null, targetEnd: null, asOfDate: "2090-09-10", items: [], matchedIds: [], summary: { taskCount: 0, doneCount: 0, blockedCount: 0, overdueCount: 0, unplannedCount: 0, unassignedCount: 0, progressPercent: null, forecastStart: null, forecastEnd: null, forecastState: "EMPTY", outsideTarget: false }, complete: true, totalCount: 0 });
const overviewSchedule = (projectId: string) => ({ ...schedule, projectId });
const overviewDashboard = (projectId: string) => ({ projectId, memberCount: 2, scheduleCount: 1, pendingAcknowledgementCount: 1, calendarRiskCount: 1, upcomingSchedules: [overviewSchedule(projectId)], actionQueue: [overviewSchedule(projectId)] });

/** Opt-in reads for tests whose project route now mounts ProjectOverview. */
export function installOverviewFixtures(server: TestServer, projectId = "p1") {
  server.on("GET", `/api/v1/projects/${projectId}/plan`, () => json(overviewPlan(projectId)));
  server.on("GET", `/api/v1/projects/${projectId}/dashboard`, () => json(overviewDashboard(projectId)));
  // p1 already has the shared schedule/workspace handlers. Only synthetic project ids
  // need an explicitly scoped schedule response; no helper may replace those defaults.
  if (projectId !== "p1") {
    server.on("GET", `/api/v1/projects/${projectId}/schedules`, () => json([overviewSchedule(projectId)]));
    server.on("GET", `/api/v1/projects/${projectId}/schedule-workspace`, () => json(workspace));
    server.on("POST", `/api/v1/projects/${projectId}/schedule-workspace/query`, body => json({ ...workspaceQuery(body?.config), records: [{ schedule: overviewSchedule(projectId), values: {} }] }));
  }
}
