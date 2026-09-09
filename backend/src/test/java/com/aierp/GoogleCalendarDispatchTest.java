package com.aierp.calendarintegration;

import com.aierp.calendarintegration.*;
import com.aierp.identity.api.GoogleAccess;
import com.aierp.schedule.api.ScheduleLookup;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Race-focused worker tests: stale claims are discarded and do not become SYNCED. */
class GoogleCalendarDispatchTest {
    @Test void lateCompletionAfterBindingGenerationChangeIsDiscarded() {
        var calendars = mock(ProjectCalendarRepository.class); var projections = mock(CalendarProjectionRepository.class);
        var adapter = mock(CalendarAdapter.class); var schedules = mock(ScheduleLookup.class); var google = mock(GoogleAccess.class);
        var worker = new CalendarDispatchWorker(calendars, projections, adapter, schedules, google);
        var project = UUID.randomUUID(); var scheduleId = UUID.randomUUID(); var owner = UUID.randomUUID();
        var calendar = new ProjectCalendarEntity(); calendar.id = UUID.randomUUID(); calendar.projectId = project; calendar.bindingOwner = owner; calendar.externalCalendarId = "primary"; calendar.bindingGeneration = 2;
        var row = new CalendarProjectionEntity(); row.id = UUID.randomUUID(); row.scheduleId = scheduleId; row.projectCalendarId = calendar.id; row.status = "PENDING";
        when(projections.lockById(row.id)).thenReturn(java.util.Optional.of(row));
        var snapshot = new ScheduleLookup.CalendarSnapshot(scheduleId, project, "Planning", null, Instant.parse("2026-09-07T10:00:00Z"), Instant.parse("2026-09-07T11:00:00Z"), "CONFIRMED", 2);
        var claim = new CalendarDispatchWorker.Claim(row.id, UUID.randomUUID(), owner, "primary", 1, 7, snapshot, null);
        row.claimToken = claim.claimToken();
        when(calendars.findById(calendar.id)).thenReturn(java.util.Optional.of(calendar));
        when(schedules.snapshot(project, scheduleId)).thenReturn(snapshot);
        when(google.isCurrent(owner, 7)).thenReturn(true);
        worker.complete(claim, new CalendarAdapter.DeliveryResult("stable", "etag"), null);
        assertThat(row.status).isNotEqualTo("SYNCED");
        verify(projections, never()).saveAndFlush(row);
    }

    @Test void supersededInsertCreatesBoundedCancelledAuditUntilProviderConfirmsAbsence() {
        var calendars = mock(ProjectCalendarRepository.class); var projections = mock(CalendarProjectionRepository.class);
        var adapter = mock(CalendarAdapter.class); var schedules = mock(ScheduleLookup.class); var google = mock(GoogleAccess.class);
        var worker = new CalendarDispatchWorker(calendars, projections, adapter, schedules, google);
        var project = UUID.randomUUID(); var scheduleId = UUID.randomUUID(); var owner = UUID.randomUUID();
        var calendar = new ProjectCalendarEntity(); calendar.id = UUID.randomUUID(); calendar.projectId = project; calendar.bindingOwner = owner; calendar.externalCalendarId = "primary"; calendar.bindingGeneration = 2;
        var row = new CalendarProjectionEntity(); row.id = UUID.randomUUID(); row.scheduleId = scheduleId; row.projectCalendarId = calendar.id; row.status = "PENDING"; row.claimToken = UUID.randomUUID();
        when(projections.lockById(row.id)).thenReturn(java.util.Optional.of(row)); when(calendars.findById(calendar.id)).thenReturn(java.util.Optional.of(calendar));
        var confirmed = new ScheduleLookup.CalendarSnapshot(scheduleId, project, "Planning", null, Instant.parse("2026-09-07T10:00:00Z"), Instant.parse("2026-09-07T11:00:00Z"), "CONFIRMED", 2);
        var cancelled = new ScheduleLookup.CalendarSnapshot(scheduleId, project, "Planning", null, confirmed.startsAt(), confirmed.endsAt(), "CANCELLED", 3);
        when(schedules.snapshot(project, scheduleId)).thenReturn(cancelled);
        var claim = new CalendarDispatchWorker.Claim(row.id, row.claimToken, owner, "primary", 2, 7, confirmed, null);
        worker.complete(claim, new CalendarAdapter.DeliveryResult("stable", "etag"), null);
        assertThat(row.status).isEqualTo("PENDING"); assertThat(row.retryClassification).isEqualTo("CANCEL_RECONCILE");
        assertThat(row.reconcileUntil).isNotNull(); assertThat(row.nextReconcileAt).isNotNull();
    }

    @Test void expiredInsertTokenIsCarriedIntoLaterCancelledAuditAfter404() {
        var calendars = mock(ProjectCalendarRepository.class); var projections = mock(CalendarProjectionRepository.class);
        var adapter = mock(CalendarAdapter.class); var schedules = mock(ScheduleLookup.class); var google = mock(GoogleAccess.class);
        var worker = new CalendarDispatchWorker(calendars, projections, adapter, schedules, google);
        var project = UUID.randomUUID(); var scheduleId = UUID.randomUUID(); var owner = UUID.randomUUID();
        var calendar = new ProjectCalendarEntity(); calendar.id = UUID.randomUUID(); calendar.projectId = project;
        calendar.bindingOwner = owner; calendar.externalCalendarId = "primary"; calendar.bindingGeneration = 1;
        var row = new CalendarProjectionEntity(); row.id = UUID.randomUUID(); row.scheduleId = scheduleId;
        row.projectCalendarId = calendar.id; row.status = "PENDING"; var oldToken = UUID.randomUUID();
        row.claimToken = oldToken; row.leaseUntil = Instant.now().minusSeconds(1);
        var cancelled = new ScheduleLookup.CalendarSnapshot(scheduleId, project, "Planning", null,
            Instant.parse("2026-09-07T10:00:00Z"), Instant.parse("2026-09-07T11:00:00Z"), "CANCELLED", 3);
        when(projections.claimable(any(), any())).thenReturn(java.util.List.of(row));
        when(calendars.findById(calendar.id)).thenReturn(java.util.Optional.of(calendar));
        when(schedules.snapshot(project, scheduleId)).thenReturn(cancelled);
        when(google.status(owner)).thenReturn(new GoogleAccess.Connection("owner@example.test",
            Map.of(GoogleAccess.Feature.CALENDAR, GoogleAccess.Status.CONNECTED)));

        // Token B takes over after A's lease and must persist uncertainty before replacing A.
        var claimB = worker.claim();
        assertThat(claimB).isNotNull(); assertThat(claimB.claimToken()).isNotEqualTo(oldToken);
        assertThat(row.reconcileUntil).isAfter(Instant.now());

        when(projections.lockById(row.id)).thenReturn(java.util.Optional.of(row));
        when(google.isCurrent(owner, 0)).thenReturn(true);
        worker.complete(claimB, new CalendarAdapter.DeliveryResult(
            GoogleCalendarAdapter.deterministicEventId(project, scheduleId), null), null);
        assertThat(row.status).isEqualTo("SYNCED");
        var auditHorizon = row.reconcileUntil;
        assertThat(row.nextReconcileAt).isAfter(Instant.now());

        // The expired A completion arrives after B's provider GET returned 404. Its token mismatch
        // is ignored, and the bounded audit marker survives for a later cancellation reconciliation.
        var oldClaim = new CalendarDispatchWorker.Claim(row.id, oldToken, owner, "primary", 1, 0,
            new ScheduleLookup.CalendarSnapshot(scheduleId, project, "Planning", null, cancelled.startsAt(),
                cancelled.endsAt(), "CONFIRMED", 2), null);
        worker.complete(oldClaim, new CalendarAdapter.DeliveryResult("late", null), null);
        assertThat(row.reconcileUntil).isEqualTo(auditHorizon);
        assertThat(row.status).isEqualTo("SYNCED");

        // Once the audit timer is due, the repository's due-audit branch can claim it again.
        row.nextReconcileAt = Instant.now().minusSeconds(1); row.claimToken = null; row.leaseUntil = null;
        var auditClaim = worker.claim();
        assertThat(auditClaim).isNotNull(); assertThat(auditClaim.schedule().cancelled()).isTrue();
        assertThat(row.reconcileUntil).isEqualTo(auditHorizon);
    }

    @Test void confirmedTransientClearsClaimButCarriesUncertaintyIntoLaterCancel() {
        var calendars = mock(ProjectCalendarRepository.class); var projections = mock(CalendarProjectionRepository.class);
        var adapter = mock(CalendarAdapter.class); var schedules = mock(ScheduleLookup.class); var google = mock(GoogleAccess.class);
        var worker = new CalendarDispatchWorker(calendars, projections, adapter, schedules, google);
        var project = UUID.randomUUID(); var scheduleId = UUID.randomUUID(); var owner = UUID.randomUUID();
        var calendar = new ProjectCalendarEntity(); calendar.id = UUID.randomUUID(); calendar.projectId = project;
        calendar.bindingOwner = owner; calendar.externalCalendarId = "primary"; calendar.bindingGeneration = 1;
        var row = new CalendarProjectionEntity(); row.id = UUID.randomUUID(); row.scheduleId = scheduleId;
        row.projectCalendarId = calendar.id; row.status = "PENDING";
        var confirmed = new ScheduleLookup.CalendarSnapshot(scheduleId, project, "Planning", null,
            Instant.parse("2026-09-07T10:00:00Z"), Instant.parse("2026-09-07T11:00:00Z"), "CONFIRMED", 2);
        var cancelled = new ScheduleLookup.CalendarSnapshot(scheduleId, project, "Planning", null,
            confirmed.startsAt(), confirmed.endsAt(), "CANCELLED", 3);
        when(projections.claimable(any(), any())).thenReturn(java.util.List.of(row));
        when(calendars.findById(calendar.id)).thenReturn(java.util.Optional.of(calendar));
        when(schedules.snapshot(project, scheduleId)).thenReturn(confirmed);
        when(google.status(owner)).thenReturn(new GoogleAccess.Connection("owner@example.test",
            Map.of(GoogleAccess.Feature.CALENDAR, GoogleAccess.Status.CONNECTED)));
        var claim = worker.claim();
        assertThat(row.reconcileUntil).isNotNull(); assertThat(row.nextReconcileAt).isNull();
        when(projections.lockById(row.id)).thenReturn(java.util.Optional.of(row));
        when(google.isCurrent(owner, 0)).thenReturn(true);
        worker.complete(claim, null, new CalendarAdapter.TransientFailure());
        assertThat(row.claimToken).isNull(); assertThat(row.leaseUntil).isNull();
        assertThat(row.status).isEqualTo("PENDING"); assertThat(row.reconcileUntil).isNotNull();
        assertThat(row.nextReconcileAt).isNull();

        when(schedules.snapshot(project, scheduleId)).thenReturn(cancelled);
        var cancellationClaim = worker.claim();
        assertThat(cancellationClaim).isNotNull();
        assertThat(row.nextReconcileAt).isBefore(Instant.now().plusSeconds(1));
    }

    @Test void confirmedSuccessDoesNotHotPollUncertaintyAudit() {
        var calendars = mock(ProjectCalendarRepository.class); var projections = mock(CalendarProjectionRepository.class);
        var adapter = mock(CalendarAdapter.class); var schedules = mock(ScheduleLookup.class); var google = mock(GoogleAccess.class);
        var worker = new CalendarDispatchWorker(calendars, projections, adapter, schedules, google);
        var project = UUID.randomUUID(); var scheduleId = UUID.randomUUID(); var owner = UUID.randomUUID();
        var calendar = new ProjectCalendarEntity(); calendar.id = UUID.randomUUID(); calendar.projectId = project;
        calendar.bindingOwner = owner; calendar.externalCalendarId = "primary"; calendar.bindingGeneration = 1;
        var row = new CalendarProjectionEntity(); row.id = UUID.randomUUID(); row.scheduleId = scheduleId;
        row.projectCalendarId = calendar.id; row.status = "PENDING";
        var confirmed = new ScheduleLookup.CalendarSnapshot(scheduleId, project, "Planning", null,
            Instant.parse("2026-09-07T10:00:00Z"), Instant.parse("2026-09-07T11:00:00Z"), "CONFIRMED", 2);
        when(projections.claimable(any(), any())).thenReturn(java.util.List.of(row));
        when(calendars.findById(calendar.id)).thenReturn(java.util.Optional.of(calendar));
        when(schedules.snapshot(project, scheduleId)).thenReturn(confirmed);
        when(google.status(owner)).thenReturn(new GoogleAccess.Connection("owner@example.test",
            Map.of(GoogleAccess.Feature.CALENDAR, GoogleAccess.Status.CONNECTED)));
        var claim = worker.claim();
        when(projections.lockById(row.id)).thenReturn(java.util.Optional.of(row));
        when(google.isCurrent(owner, 0)).thenReturn(true);
        worker.complete(claim, new CalendarAdapter.DeliveryResult("stable", "etag"), null);
        assertThat(row.status).isEqualTo("SYNCED");
        assertThat(row.reconcileUntil).isNotNull(); assertThat(row.nextReconcileAt).isNull();
    }

    @Test void backfillUsesLockedBindingAfterScalarCandidateRead() {
        var calendars = mock(ProjectCalendarRepository.class); var projections = mock(CalendarProjectionRepository.class);
        var adapter = mock(CalendarAdapter.class); var schedules = mock(ScheduleLookup.class); var google = mock(GoogleAccess.class);
        var access = mock(com.aierp.project.api.ProjectAccess.class);
        var worker = new CalendarDispatchWorker(calendars, projections, adapter, schedules, google, access, null);
        var project = UUID.randomUUID();
        when(calendars.findBackfillProjectIds(any())).thenReturn(java.util.List.of(project));
        when(calendars.findByProjectIdForUpdate(project)).thenReturn(java.util.Optional.empty());
        assertThat(worker.backfillOnce()).isFalse();
        verify(access).lockProject(project);
        verify(calendars).findByProjectIdForUpdate(project);
        verifyNoInteractions(schedules, projections);
    }

    @Test void repeatedCancellationAuditsRetainHorizonAndNullNextIsNotDue() {
        var calendars = mock(ProjectCalendarRepository.class); var projections = mock(CalendarProjectionRepository.class);
        var adapter = mock(CalendarAdapter.class); var schedules = mock(ScheduleLookup.class); var google = mock(GoogleAccess.class);
        var worker = new CalendarDispatchWorker(calendars, projections, adapter, schedules, google);
        var project = UUID.randomUUID(); var scheduleId = UUID.randomUUID(); var owner = UUID.randomUUID();
        var calendar = new ProjectCalendarEntity(); calendar.id = UUID.randomUUID(); calendar.projectId = project;
        calendar.bindingOwner = owner; calendar.externalCalendarId = "primary"; calendar.bindingGeneration = 1;
        var row = new CalendarProjectionEntity(); row.id = UUID.randomUUID(); row.scheduleId = scheduleId;
        row.projectCalendarId = calendar.id; row.status = "SYNCED";
        var horizon = Instant.now().plusSeconds(3600); row.reconcileUntil = horizon;
        row.nextReconcileAt = Instant.now().minusSeconds(1);
        var cancelled = new ScheduleLookup.CalendarSnapshot(scheduleId, project, "Planning", null,
            Instant.parse("2026-09-07T10:00:00Z"), Instant.parse("2026-09-07T11:00:00Z"), "CANCELLED", 3);
        when(projections.claimable(any(), any())).thenReturn(java.util.List.of(row));
        when(calendars.findById(calendar.id)).thenReturn(java.util.Optional.of(calendar));
        when(schedules.snapshot(project, scheduleId)).thenReturn(cancelled);
        when(google.status(owner)).thenReturn(new GoogleAccess.Connection("owner@example.test",
            Map.of(GoogleAccess.Feature.CALENDAR, GoogleAccess.Status.CONNECTED)));
        var firstAudit = worker.claim();
        assertThat(firstAudit).isNotNull(); assertThat(row.reconcileUntil).isEqualTo(horizon);
        row.status = "SYNCED"; row.claimToken = null; row.leaseUntil = null; row.nextReconcileAt = Instant.now().minusSeconds(1);
        var secondAudit = worker.claim();
        assertThat(secondAudit).isNotNull(); assertThat(row.reconcileUntil).isEqualTo(horizon);
        row.status = "SYNCED"; row.claimToken = null; row.leaseUntil = null; row.nextReconcileAt = null;
        assertThat(row.nextReconcileAt).isNull();
    }

    @Test void nullNextReconcileAtIsExcludedFromDueAuditPredicate() throws Exception {
        var query = CalendarProjectionRepository.class.getMethod("claimable", Instant.class,
            org.springframework.data.domain.Pageable.class)
            .getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
        assertThat(query).contains("p.nextReconcileAt is not null")
            .doesNotContain("p.nextReconcileAt is null or");
    }

    @Test void temporaryCredentialFailureRemainsRetryableInsteadOfBecomingPermissionDenied() {
        var calendars = mock(ProjectCalendarRepository.class); var projections = mock(CalendarProjectionRepository.class);
        var adapter = mock(CalendarAdapter.class); var schedules = mock(ScheduleLookup.class); var google = mock(GoogleAccess.class);
        var worker = new CalendarDispatchWorker(calendars, projections, adapter, schedules, google);
        var project = UUID.randomUUID(); var scheduleId = UUID.randomUUID(); var owner = UUID.randomUUID();
        var calendar = new ProjectCalendarEntity(); calendar.id = UUID.randomUUID(); calendar.projectId = project;
        calendar.bindingOwner = owner; calendar.externalCalendarId = "primary"; calendar.bindingGeneration = 1;
        var row = new CalendarProjectionEntity(); row.id = UUID.randomUUID(); row.scheduleId = scheduleId;
        row.projectCalendarId = calendar.id; row.status = "PENDING";
        var snapshot = new ScheduleLookup.CalendarSnapshot(scheduleId, project, "Planning", null,
            Instant.parse("2026-09-07T10:00:00Z"), Instant.parse("2026-09-07T11:00:00Z"), "CONFIRMED", 2);
        when(adapter.configured()).thenReturn(true);
        when(projections.claimable(any(), any())).thenReturn(java.util.List.of(row));
        when(calendars.findById(calendar.id)).thenReturn(java.util.Optional.of(calendar));
        when(schedules.snapshot(project, scheduleId)).thenReturn(snapshot);
        when(google.status(owner)).thenReturn(new GoogleAccess.Connection("owner@example.test",
            Map.of(GoogleAccess.Feature.CALENDAR, GoogleAccess.Status.CONNECTED)));
        when(google.credential(owner, GoogleAccess.Feature.CALENDAR))
            .thenThrow(new GoogleAccess.GoogleAccessException("REFRESH_UNAVAILABLE"));
        when(projections.lockById(row.id)).thenReturn(java.util.Optional.of(row));

        assertThat(worker.dispatchOnce()).isTrue();
        assertThat(row.status).isEqualTo("PENDING");
        assertThat(row.retryClassification).isEqualTo("TRANSIENT");
        verify(adapter, never()).deliver(any(), any(), any(), any(), any());
    }

    @Test void successfulCancellationAuditPastItsHorizonStopsFurtherReconciliation() {
        var calendars = mock(ProjectCalendarRepository.class); var projections = mock(CalendarProjectionRepository.class);
        var adapter = mock(CalendarAdapter.class); var schedules = mock(ScheduleLookup.class); var google = mock(GoogleAccess.class);
        var worker = new CalendarDispatchWorker(calendars, projections, adapter, schedules, google);
        var project = UUID.randomUUID(); var scheduleId = UUID.randomUUID(); var owner = UUID.randomUUID();
        var calendar = new ProjectCalendarEntity(); calendar.id = UUID.randomUUID(); calendar.projectId = project;
        calendar.bindingOwner = owner; calendar.externalCalendarId = "primary"; calendar.bindingGeneration = 1;
        var row = new CalendarProjectionEntity(); row.id = UUID.randomUUID(); row.scheduleId = scheduleId;
        row.projectCalendarId = calendar.id; row.status = "SYNCED"; row.claimToken = UUID.randomUUID();
        row.reconcileUntil = Instant.now().minusSeconds(1); row.nextReconcileAt = Instant.now().minusSeconds(1);
        var cancelled = new ScheduleLookup.CalendarSnapshot(scheduleId, project, "Planning", null,
            Instant.parse("2026-09-07T10:00:00Z"), Instant.parse("2026-09-07T11:00:00Z"), "CANCELLED", 3);
        var claim = new CalendarDispatchWorker.Claim(row.id, row.claimToken, owner, "primary", 1, 7, cancelled, "stable");
        when(projections.lockById(row.id)).thenReturn(java.util.Optional.of(row));
        when(calendars.findById(calendar.id)).thenReturn(java.util.Optional.of(calendar));
        when(schedules.snapshot(project, scheduleId)).thenReturn(cancelled);
        when(google.isCurrent(owner, 7)).thenReturn(true);

        worker.complete(claim, new CalendarAdapter.DeliveryResult("stable", "etag"), null);

        assertThat(row.status).isEqualTo("SYNCED");
        assertThat(row.reconcileUntil).isNull();
        assertThat(row.nextReconcileAt).isNull();
    }
}
