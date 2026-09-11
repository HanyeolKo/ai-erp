package com.aierp.calendarintegration;

import com.aierp.identity.api.GoogleAccess;
import com.aierp.project.api.ProjectAccess;
import com.aierp.schedule.api.ScheduleLookup;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Claims local projections briefly, calls Google outside the claim transaction, then CAS-completes the claim. */
@Component
public class CalendarDispatchWorker {
    private static final Duration LEASE = Duration.ofSeconds(60);
    private final ProjectCalendarRepository calendars;
    private final CalendarProjectionRepository projections;
    private final CalendarAdapter adapter;
    private final ScheduleLookup schedules;
    private final GoogleAccess google;
    private final ProjectAccess access;
    private final TransactionTemplate transactions;

    public CalendarDispatchWorker(ProjectCalendarRepository calendars, CalendarProjectionRepository projections,
                                  CalendarAdapter adapter, ScheduleLookup schedules, GoogleAccess google) {
        this(calendars, projections, adapter, schedules, google, null, null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public CalendarDispatchWorker(ProjectCalendarRepository calendars, CalendarProjectionRepository projections,
                                  CalendarAdapter adapter, ScheduleLookup schedules, GoogleAccess google, ProjectAccess access,
                                  PlatformTransactionManager transactionManager) {
        this.calendars = calendars; this.projections = projections; this.adapter = adapter; this.schedules = schedules; this.google = google;
        this.access = access;
        this.transactions = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${google.workspace.calendar.dispatch-delay-ms:1000}")
    public void scheduledDispatch() { dispatchOnce(); }

    public boolean dispatchOnce() {
        if (!adapter.configured()) return false;
        backfillOnce();
        var claim = claim();
        if (claim == null) return false;
        final long credentialGeneration;
        try {
            // Credential acquisition may refresh and therefore must occur outside the claim transaction.
            credentialGeneration = google.credential(claim.owner(), GoogleAccess.Feature.CALENDAR).generation();
        } catch (GoogleAccess.GoogleAccessException failure) {
            // A refresh/network failure is retryable. Preserve the provider category instead of
            // presenting every credential problem as a permanent permission denial.
            release(claim, googleFailureClassification(failure));
            return true;
        }
        claim = attachCredentialGeneration(claim, credentialGeneration);
        if (claim == null) return true;
        final var activeClaim = claim;
        // Re-read all local barriers immediately before the first provider request.
        if (!isCurrent(activeClaim)) { complete(activeClaim, null, new CalendarAdapter.TransientFailure()); return true; }
        CalendarAdapter.DeliveryResult result = null;
        RuntimeException failure = null;
        try {
            // This is intentionally after claim() returns: no provider HTTP is held in a DB transaction.
            result = adapter.deliver(activeClaim.owner(), activeClaim.calendarId(), activeClaim.schedule(), activeClaim.externalEventId(), () -> isCurrent(activeClaim));
        } catch (CalendarAdapter.ReauthorizationRequired | CalendarAdapter.PermissionRequired | CalendarAdapter.ConfigurationRequired e) {
            failure = e;
        } catch (CalendarAdapter.PermanentFailure | CalendarAdapter.TransientFailure | CalendarAdapter.StaleClaim e) {
            failure = e;
        } catch (RuntimeException e) {
            failure = new CalendarAdapter.TransientFailure();
        }
        complete(activeClaim, result, failure);
        return true;
    }

    private boolean isCurrent(Claim claim) {
        var row = projections.findById(claim.projectionId()).orElse(null);
        var calendar = row == null ? null : calendars.findById(row.projectCalendarId).orElse(null);
        if (row == null || !claim.claimToken().equals(row.claimToken) || calendar == null
            || !claim.owner().equals(calendar.bindingOwner) || !claim.calendarId().equals(calendar.externalCalendarId)
            || claim.bindingGeneration() != calendar.bindingGeneration) return false;
        if (access != null && !"MANAGER".equals(access.role(calendar.projectId, claim.owner()))) return false;
        var current = schedules.snapshot(calendar.projectId, claim.schedule().scheduleId());
        return current.businessRevision() == claim.schedule().businessRevision()
            && current.cancelled() == claim.schedule().cancelled()
            && google.isCurrent(claim.owner(), claim.credentialGeneration());
    }

    /** Materializes at most one persisted UUID-cursor page; this performs no provider I/O. */
    boolean backfillOnce() {
        if (transactions != null) return Boolean.TRUE.equals(transactions.execute(status -> backfillOnceInTransaction()));
        return backfillOnceInTransaction();
    }

    @Transactional
    boolean backfillOnceInTransaction() {
        // Read only a scalar candidate before locking. Do not retain a managed calendar entity
        // across the stable project lock: it can be a stale binding after concurrent disconnect.
        var projectId = calendars.findBackfillProjectIds(PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        if (projectId == null) return false;
        if (access != null) access.lockProject(projectId);
        var boundCalendar = calendars.findByProjectIdForUpdate(projectId).orElse(null);
        if (boundCalendar == null || boundCalendar.bindingOwner == null || boundCalendar.externalCalendarId == null) return false;
        var page = schedules.exportPage(boundCalendar.projectId, boundCalendar.backfillCursor, 100);
        for (var schedule : page.schedules()) {
            var projection = projections.findByScheduleIdAndProjectCalendarId(schedule.scheduleId(), boundCalendar.id).orElseGet(() -> {
                var p = new CalendarProjectionEntity(); p.id = UUID.randomUUID(); p.scheduleId = schedule.scheduleId(); p.projectCalendarId = boundCalendar.id; p.status = "PENDING"; return p;
            });
            if (schedule.businessRevision() >= projection.businessRevision) {
                projection.businessRevision = schedule.businessRevision(); projection.status = "PENDING"; projection.updatedAt = Instant.now(); projections.save(projection);
            }
        }
        boundCalendar.backfillCursor = page.nextCursor(); boundCalendar.backfillPending = page.hasNext(); calendars.saveAndFlush(boundCalendar);
        return true;
    }

    Claim claim() {
        if (transactions != null) return transactions.execute(status -> claimInTransaction());
        return claimInTransaction();
    }

    @Transactional
    Claim claimInTransaction() {
        var row = projections.claimable(Instant.now(), PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        if (row == null) return null;
        var calendar = calendars.findById(row.projectCalendarId).orElse(null);
        if (calendar == null || calendar.bindingOwner == null || calendar.externalCalendarId == null) {
            row.retryClassification = "CONFIGURATION_REQUIRED"; row.updatedAt = Instant.now(); projections.save(row); return null;
        }
        var owner = calendar.bindingOwner;
        if (access != null && !"MANAGER".equals(access.role(calendar.projectId, owner))) {
            row.retryClassification = "PERMISSION_REQUIRED"; row.updatedAt = Instant.now(); projections.save(row); return null;
        }
        final GoogleAccess.Connection connection;
        try {
            connection = google.status(owner);
        } catch (GoogleAccess.GoogleAccessException failure) {
            row.retryClassification = googleFailureClassification(failure);
            row.updatedAt = Instant.now();
            projections.save(row);
            return null;
        }
        var status = connection.status(GoogleAccess.Feature.CALENDAR);
        if (status != GoogleAccess.Status.CONNECTED) {
            row.retryClassification = status == GoogleAccess.Status.REAUTH_REQUIRED ? "REAUTH_REQUIRED" : "PERMISSION_REQUIRED";
            row.updatedAt = Instant.now(); projections.save(row); return null;
        }
        var now = Instant.now();
        var snapshot = schedules.snapshot(calendar.projectId, row.scheduleId);
        var scheduledCancellationAudit = "SYNCED".equals(row.status) && snapshot.cancelled()
            && row.reconcileUntil != null && row.nextReconcileAt != null && !row.nextReconcileAt.isAfter(now);
        if (row.claimToken != null && row.leaseUntil != null && row.leaseUntil.isBefore(now)) {
            row.claimToken = null;
            row.leaseUntil = null;
            if (!scheduledCancellationAudit) extendUncertainty(row, now, snapshot.cancelled());
        }
        if (snapshot.businessRevision() <= 0 || "DRAFT".equals(snapshot.status())) {
            row.status = "FAILED"; row.retryClassification = "NOT_EXPORTABLE"; row.claimToken = null; row.leaseUntil = null; row.updatedAt = Instant.now(); projections.save(row); return null;
        }
        // Persist the possibility of a provider write before credentials/HTTP are acquired. A
        // confirmed timeout can therefore be carried into a later cancellation audit. A scheduled
        // cancellation audit is a read/delete reconciliation and retains its original horizon.
        if (!scheduledCancellationAudit) {
            ensureUncertainty(row, now, snapshot.cancelled());
        }
        row.claimToken = UUID.randomUUID(); row.leaseUntil = now.plus(LEASE); row.lastAttemptAt = now;
        row.businessRevision = snapshot.businessRevision(); row.status = scheduledCancellationAudit ? "SYNCED" : "PENDING";
        row.updatedAt = Instant.now(); projections.saveAndFlush(row);
        return new Claim(row.id, row.claimToken, owner, calendar.externalCalendarId, calendar.bindingGeneration,
            0, snapshot, row.externalEventId, scheduledCancellationAudit);
    }

    Claim attachCredentialGeneration(Claim claim, long generation) {
        if (transactions != null) return transactions.execute(status -> attachCredentialGenerationInTransaction(claim, generation));
        return attachCredentialGenerationInTransaction(claim, generation);
    }

    @Transactional
    Claim attachCredentialGenerationInTransaction(Claim claim, long generation) {
        var row = projections.lockById(claim.projectionId()).orElse(null);
        var calendar = row == null ? null : calendars.findById(row.projectCalendarId).orElse(null);
        if (row == null || !claim.claimToken().equals(row.claimToken) || calendar == null
            || !claim.owner().equals(calendar.bindingOwner) || claim.bindingGeneration() != calendar.bindingGeneration) return null;
        return new Claim(claim.projectionId(), claim.claimToken(), claim.owner(), claim.calendarId(), claim.bindingGeneration(),
            generation, claim.schedule(), claim.externalEventId(), claim.audit());
    }

    void release(Claim claim, String classification) {
        Runnable operation = () -> {
            var row = projections.lockById(claim.projectionId()).orElse(null);
            if (row != null && claim.claimToken().equals(row.claimToken)) {
                var now = Instant.now();
                row.claimToken = null; row.leaseUntil = null; row.updatedAt = now;
                if (claim.audit()) {
                    if ("PENDING".equals(row.status)) {
                        // preserve explicit normal queue; keep state as-is.
                    } else {
                        row.status = "SYNCED";
                        preserveAuditFailureWindow(row, now);
                        row.retryClassification = classification;
                    }
                } else {
                    row.retryClassification = classification;
                }
                projections.save(row);
            }
        };
        if (transactions != null) transactions.executeWithoutResult(status -> operation.run()); else operation.run();
    }

    void complete(Claim claim, CalendarAdapter.DeliveryResult result, RuntimeException failure) {
        if (transactions != null) { transactions.executeWithoutResult(status -> completeInTransaction(claim, result, failure)); return; }
        completeInTransaction(claim, result, failure);
    }

    @Transactional
    void completeInTransaction(Claim claim, CalendarAdapter.DeliveryResult result, RuntimeException failure) {
        var row = projections.lockById(claim.projectionId()).orElse(null);
        if (row == null || !claim.claimToken().equals(row.claimToken)) return;
        var calendar = calendars.findById(row.projectCalendarId).orElse(null);
        var current = calendar == null ? null : schedules.snapshot(calendar.projectId, row.scheduleId);
        var generationCurrent = calendar != null && claim.owner().equals(calendar.bindingOwner)
            && claim.bindingGeneration() == calendar.bindingGeneration
            && calendar.projectId != null && (access == null || "MANAGER".equals(access.role(calendar.projectId, claim.owner())));
        var credentialCurrent = generationCurrent && google.isCurrent(claim.owner(), claim.credentialGeneration());
        var desiredCurrent = current != null && current.businessRevision() == claim.schedule().businessRevision()
            && current.cancelled() == claim.schedule().cancelled();
        if (claim.audit() && "PENDING".equals(row.status)) {
            row.claimToken = null;
            row.leaseUntil = null;
            row.updatedAt = Instant.now();
            projections.save(row);
            return;
        }
        if (!generationCurrent || !credentialCurrent || !desiredCurrent) {
            var now = Instant.now();
            row.claimToken = null; row.leaseUntil = null;
            if (claim.audit()) {
                if (current != null && !current.cancelled()) {
                    row.status = "PENDING";
                    clearAuditState(row, now, current);
                } else {
                    row.status = "SYNCED";
                    preserveAuditFailureWindow(row, now);
                }
            } else {
                row.status = "PENDING";
            }
            if (current != null && current.cancelled() && !claim.schedule().cancelled()) {
                ensureUncertainty(row, now, true); row.retryClassification = "CANCEL_RECONCILE";
            }
            row.updatedAt = now; projections.save(row); return;
        }
        row.claimToken = null; row.leaseUntil = null; row.updatedAt = Instant.now();
        if (failure == null) {
            row.externalEventId = result.eventId(); row.deliveredRevision = claim.schedule().businessRevision(); row.status = "SYNCED"; row.retryClassification = null;
            if (claim.schedule().cancelled() && row.reconcileUntil != null) {
                var now = Instant.now();
                if (now.isBefore(row.reconcileUntil)) row.nextReconcileAt = now.plusSeconds(30);
                else { row.reconcileUntil = null; row.nextReconcileAt = null; }
            } else row.nextReconcileAt = null;
        } else if (failure instanceof CalendarAdapter.ReauthorizationRequired) {
            row.status = claim.audit() ? "SYNCED" : "REAUTH_REQUIRED"; row.retryClassification = "REAUTH_REQUIRED";
            if (claim.audit()) preserveAuditFailureWindow(row, Instant.now());
        } else if (failure instanceof CalendarAdapter.PermissionRequired) {
            row.status = claim.audit() ? "SYNCED" : "FAILED"; row.retryClassification = "PERMISSION_REQUIRED";
            if (claim.audit()) preserveAuditFailureWindow(row, Instant.now());
        } else if (failure instanceof CalendarAdapter.ConfigurationRequired) {
            row.status = claim.audit() ? "SYNCED" : "PENDING"; row.retryClassification = "CONFIGURATION_REQUIRED";
            if (claim.audit()) preserveAuditFailureWindow(row, Instant.now());
        } else if (failure instanceof CalendarAdapter.PermanentFailure) {
            row.status = claim.audit() ? "SYNCED" : "FAILED"; row.retryClassification = "PERMANENT";
            if (claim.audit()) preserveAuditFailureWindow(row, Instant.now());
        } else {
            row.status = claim.audit() ? "SYNCED" : "PENDING"; row.retryClassification = "TRANSIENT";
            if (claim.audit()) preserveAuditFailureWindow(row, Instant.now());
        }
        projections.saveAndFlush(row);
    }

    private static void clearAuditState(CalendarProjectionEntity row, Instant now, ScheduleLookup.CalendarSnapshot snapshot) {
        if (!snapshot.cancelled()) {
            row.reconcileUntil = null;
            row.nextReconcileAt = null;
            return;
        }
        if (row.reconcileUntil != null && !row.reconcileUntil.isAfter(now)) {
            row.reconcileUntil = null;
            row.nextReconcileAt = null;
        } else if (row.nextReconcileAt != null && !row.nextReconcileAt.isAfter(now)) {
            row.nextReconcileAt = now.plusSeconds(30);
        }
    }

    private static void preserveAuditFailureWindow(CalendarProjectionEntity row, Instant now) {
        if (row.reconcileUntil != null && now.isBefore(row.reconcileUntil)) row.nextReconcileAt = now.plusSeconds(30);
        else { row.reconcileUntil = null; row.nextReconcileAt = null; }
    }

    private static void ensureUncertainty(CalendarProjectionEntity row, Instant now, boolean auditDue) {
        var horizon = now.plus(Duration.ofHours(6));
        // Reuse an active bounded window; only a new/expired uncertainty window may advance it.
        if (row.reconcileUntil == null || !now.isBefore(row.reconcileUntil)) row.reconcileUntil = horizon;
        if (auditDue) {
            if (row.nextReconcileAt == null || row.nextReconcileAt.isAfter(now)) row.nextReconcileAt = now;
        } else row.nextReconcileAt = null;
    }

    private static void extendUncertainty(CalendarProjectionEntity row, Instant now, boolean auditDue) {
        var horizon = now.plus(Duration.ofHours(6));
        if (row.reconcileUntil == null || row.reconcileUntil.isBefore(horizon)) row.reconcileUntil = horizon;
        if (auditDue && (row.nextReconcileAt == null || row.nextReconcileAt.isAfter(now))) row.nextReconcileAt = now;
        if (!auditDue) row.nextReconcileAt = null;
    }

    private static String googleFailureClassification(GoogleAccess.GoogleAccessException failure) {
        return switch (failure.category()) {
            case REAUTH_REQUIRED -> "REAUTH_REQUIRED";
            case PERMISSION_REQUIRED -> "PERMISSION_REQUIRED";
            case CONFIGURATION_REQUIRED -> "CONFIGURATION_REQUIRED";
            case TEMPORARY -> "TRANSIENT";
            case DEFINITIVE -> "PERMANENT";
        };
    }

    record Claim(UUID projectionId, UUID claimToken, UUID owner, String calendarId, long bindingGeneration,
                 long credentialGeneration, ScheduleLookup.CalendarSnapshot schedule, String externalEventId, boolean audit) {
        Claim(UUID projectionId, UUID claimToken, UUID owner, String calendarId, long bindingGeneration,
              long credentialGeneration, ScheduleLookup.CalendarSnapshot schedule, String externalEventId) {
            this(projectionId, claimToken, owner, calendarId, bindingGeneration, credentialGeneration, schedule, externalEventId, false);
        }
    }
}
