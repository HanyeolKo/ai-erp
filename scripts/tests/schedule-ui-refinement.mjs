import fs from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

const root = process.env.SCHEDULE_UI_EVIDENCE_DIR || "D:/Project/Ai ERP/.tmp/evidence/schedule-ui-refinement-20260928/schedule/attempt3";
await fs.mkdir(root, { recursive: true });
const entry = process.env.PLAYWRIGHT_ENTRY || "C:/Users/hanyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.js";
const chrome = process.env.CHROME_EXECUTABLE || "C:/Program Files/Google/Chrome/Application/chrome.exe";
const playwright = await import(pathToFileURL(entry).href);
const chromium = playwright.default?.chromium ?? playwright.chromium;
const browser = await chromium.launch({ headless: true, executablePath: chrome });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();
page.setDefaultTimeout(10_000);
const base = process.env.SCHEDULE_UI_URL || "http://127.0.0.1:5173";
const measurements = {};
const ready = async (route, heading) => {
  await page.goto(base + "/#" + route, { waitUntil: "domcontentloaded" });
  if (heading) await page.getByRole("heading", { name: heading, exact: true }).waitFor();
  else await page.locator(".schedule-detail h1").waitFor();
  await page.waitForTimeout(150);
};
try {
  await ready("/projects/p1/schedules", "프로젝트 일정");
  measurements.desktopCalendarTop = await page.locator(".schedule-calendar").evaluate(node => node.getBoundingClientRect().top);
  measurements.desktopOverflow = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
  await page.screenshot({ path: path.join(root, "schedule-month-1440.png") });

  await page.setViewportSize({ width: 768, height: 900 });
  await page.getByRole("button", { name: "주간 보기" }).click();
  await page.getByRole("grid", { name: "주간 일정" }).waitFor();
  measurements.weekCanvasHeight = await page.locator(".day-events").first().evaluate(node => node.getBoundingClientRect().height);
  await page.screenshot({ path: path.join(root, "schedule-week-768.png") });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.getByRole("heading", { name: "프로젝트 일정", exact: true }).waitFor();
  measurements.mobileAgendaTop = await page.locator(".schedule-agenda").evaluate(node => node.getBoundingClientRect().top);
  measurements.mobileOverflow = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
  measurements.viewIconWidths = await page.locator(".schedule-view-toggle .schedule-view-icon").evaluateAll(nodes => nodes.map(node => node.getBoundingClientRect().width));
  measurements.shortTargets = await page.locator(".schedule-screen button:visible, .schedule-screen a.button:visible, .schedule-screen input:visible, .schedule-screen select:visible").evaluateAll(nodes => nodes.map(node => ({ label: node.getAttribute("aria-label") || node.textContent?.trim(), height: node.getBoundingClientRect().height })).filter(item => item.height < 43.5));
  await page.screenshot({ path: path.join(root, "schedule-agenda-390.png") });

  await page.setViewportSize({ width: 320, height: 844 });
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.getByRole("heading", { name: "프로젝트 일정", exact: true }).waitFor();
  measurements.mobile320AgendaTop = await page.locator(".schedule-agenda").evaluate(node => node.getBoundingClientRect().top);
  measurements.mobile320Overflow = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
  await page.screenshot({ path: path.join(root, "schedule-agenda-320.png") });

  await page.setViewportSize({ width: 1440, height: 900 });
  await ready("/projects/p1/schedules/new?date=2090-09-10&start=2090-09-10T09:00&zone=Asia%2FSeoul", "일정 만들기");
  await page.screenshot({ path: path.join(root, "schedule-form-1440.png") });
  await ready("/projects/p1/schedules/s1");
  await page.screenshot({ path: path.join(root, "schedule-detail-1440.png") });

  if (measurements.desktopCalendarTop > 360) throw new Error("desktop calendar starts at " + measurements.desktopCalendarTop + "px");
  if (measurements.mobileAgendaTop > 500) throw new Error("mobile agenda starts at " + measurements.mobileAgendaTop + "px");
  if (measurements.mobile320AgendaTop > 500) throw new Error("320px agenda starts at " + measurements.mobile320AgendaTop + "px");
  if (measurements.weekCanvasHeight !== 1152) throw new Error("week canvas height changed: " + measurements.weekCanvasHeight);
  if (measurements.viewIconWidths.some(width => width < 16)) throw new Error("view icons collapsed: " + JSON.stringify(measurements.viewIconWidths));
  if (measurements.desktopOverflow > 1 || measurements.mobileOverflow > 1 || measurements.mobile320Overflow > 1) throw new Error("page overflow: " + JSON.stringify(measurements));
  if (measurements.shortTargets.length) throw new Error("mobile targets below 44px: " + JSON.stringify(measurements.shortTargets));
  await fs.writeFile(path.join(root, "geometry.json"), JSON.stringify(measurements, null, 2), "utf8");
  console.log(JSON.stringify(measurements));
} finally {
  await browser.close();
}
