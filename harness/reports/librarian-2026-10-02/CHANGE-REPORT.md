# Librarian role installation change report

The implementation adds one parent-controlled `librarian` role for substantive document save, bounded retrieval, and archive requests. Local files remain canonical. Archive operations search the same context and inspect the actual reading status before mutation: `안읽음` pages are updated in place, `읽음` and `읽는중` pages are preserved, and a new substantive page is explicitly initialized as `안읽음` only when no suitable unread page exists. Unknown status, permission, schema, or readiness defers synchronization and preserves the local result.

Documents may mention or quote technical work, screen plans, release history, or verdicts. A request that changes executable code, tests, behavior configuration, screen-decision meaning, release permissions/commands, or a verdict returns to its existing owner and gate. Storing accepted content does not fabricate a UI review.

The selected fluent-korean guidance is pinned byte-for-byte at `vendor/writing-guidance/fluent-korean.md`; source and vendor SHA-256 are `3F1962FB848801482A1E7B99D483DB7EF366C5AFEF8F662B78D1B6C7CA099AA0` with `6365` bytes. Live Notion archive execution and the full regression suite were deferred to the parent for the combined final candidate. Independent Astra/high review remains required.
