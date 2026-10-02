# Document management workflow

The router sends a parent-approved substantive document `save`, bounded `search`, or `archive` request to `librarian` with exact authorized paths and a complete document assignment. Short replies, raw output, temporary notes, and intermediate material remain exempt.

The local source is canonical. For a save, librarian writes only the parent-approved path and preserves accepted meaning, source facts, and revision. Documents may mention or quote technical work, screen plans, release history, or verdicts; return to the existing owner only when the requested operation changes executable code, tests, behavior-affecting configuration, screen-decision meaning, release permissions/commands, or a verdict itself.

For an archive operation, librarian searches the configured `AI 생성문서 관리` database for the same project, topic, purpose, and continuing work. It inspects the actual reading status before mutation: update a same-context `안읽음` page and retain `안읽음`; preserve `읽음` and `읽는중`; otherwise create only a substantive new page and explicitly set `안읽음`.

Unknown archive schema, reading status, permission, or external readiness blocks synchronization. Librarian preserves the local source, reports the exact reason and resume check, and never guesses, duplicates, shares, changes permissions, deletes, renames, or bulk-merges documents. An offline-contract-only installation check never proves live Notion success.

The parent accepts content and dispatches the assignment. Librarian returns the actual local path/revision or search results, archive link and synchronization date when available, source/status evidence, preserved-meaning confirmation, blocked or partial reason, and checks not run. Librarian does not route, review, approve, or delegate.
