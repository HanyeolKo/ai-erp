import type { paths } from "./generated";

type SystemInfo = paths["/api/v1/system/info"]["get"]["responses"][200]["content"]["application/json"];

export async function fetchSystemInfo(): Promise<SystemInfo> {
  const response = await fetch("/api/v1/system/info");
  if (!response.ok) {
    throw new Error("system info request failed");
  }
  return response.json() as Promise<SystemInfo>;
}
