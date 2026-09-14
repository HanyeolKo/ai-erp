# Production observation

- Date: 2026-09-14; runner: /root; mode: authenticated browser, read-only diagnosis.
- Target: https://ai-erp.duckdns.org/; no production data, read flags, mail, OAuth or settings were changed.
- GitHub: PR 13 is MERGED at 31f92ab473a38f786cf454cb117d3bc1b4b53eb2. Latest Deploy Production run 34794956458 succeeded at 49d79fe553bb4048a72671b503609e2d2c38ce58. Current local main is this SHA; app.css and GoogleWorkspace.css have no subsequent diff from PR 13. schedules.css includes later accepted calendar work.
- Actual browser: existing logged-in session; new agent tab, no user tab modified. UI uses v2 root 16px and accent #2458a6.
- Notification defect: project header is a link to #/notifications. Click changed the hash to #/notifications, removed project navigation, and rendered a full notification page. Opening did not mark notifications read. Browser Back returned to schedules.
- Date toolbar defect: actual CSS rectangles at default desktop viewport: previous top 272.640625px/height 40px; date input top 285.890625px/height 42.5px; next top 272.640625px/height 40px. Native date label contributes an unequal vertical offset. Month/week toggle is separate on the left; no Today control exists.
- Privacy: production account/project/notification content and screenshots are deliberately not persisted or published; only generic control geometry and behavior are recorded.
- Limits: workflow success is CI evidence; no server filesystem or live release manifest was inspected. Browser observation confirms current rendered CSS and defects. Date arithmetic failure is not claimed; the reproduced issue is presentation/alignment and missing navigation polish.
