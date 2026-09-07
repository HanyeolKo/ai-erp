package com.aierp.calendarintegration;

import com.aierp.calendarintegration.api.CalendarController;
import com.aierp.identity.api.GoogleAccess;
import com.aierp.identity.api.IdentityProfiles;
import com.aierp.project.api.ProjectAccess;
import com.aierp.schedule.api.ScheduleLookup;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Local Calendar binding and projection state. Provider calls are deliberately outside DB transactions. */
@Service
public class CalendarService {
    private final CalendarConnectionRepository connections;
    private final ProjectCalendarRepository calendars;
    private final CalendarProjectionRepository projections;
    private final CalendarAdapter adapter;
    private final ProjectAccess access;
    private final ScheduleLookup schedules;
    private final GoogleAccess google;
    private final IdentityProfiles profiles;
    private final TransactionTemplate transactions;

    public CalendarService(CalendarConnectionRepository connections, ProjectCalendarRepository calendars,
                            CalendarProjectionRepository projections, CalendarAdapter adapter, ProjectAccess access,
                            ScheduleLookup schedules) {
        this(connections, calendars, projections, adapter, access, schedules, null, null, null);
    }

    public CalendarService(CalendarConnectionRepository connections, ProjectCalendarRepository calendars,
                            CalendarProjectionRepository projections, CalendarAdapter adapter, ProjectAccess access,
                            ScheduleLookup schedules, GoogleAccess google) {
        this(connections, calendars, projections, adapter, access, schedules, google, null, null);
    }

    @Autowired
    public CalendarService(CalendarConnectionRepository connections, ProjectCalendarRepository calendars,
                            CalendarProjectionRepository projections, CalendarAdapter adapter, ProjectAccess access,
                              ScheduleLookup schedules, GoogleAccess google,
                              IdentityProfiles profiles,
                              PlatformTransactionManager transactionManager) {
        this.connections = connections; this.calendars = calendars; this.projections = projections;
        this.adapter = adapter; this.access = access; this.schedules = schedules; this.google = google; this.profiles = profiles;
        this.transactions = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    public CalendarController.Connection connection(UUID user) {
        var status = connections.findByUserAccountId(user).map(c -> c.status).orElse("NOT_CONNECTED");
        return new CalendarController.Connection(status, !adapter.configured());
    }

    @Transactional public CalendarController.Connection reconnect(UUID user) {
        var c = connections.findByUserAccountId(user).orElseGet(() -> {
            var n = new CalendarConnectionEntity(); n.id = UUID.randomUUID(); n.userAccountId = user; return n;
        });
        c.status = !adapter.configured() || google == null ? "NOT_CONNECTED" : googleStatus(google.status(user).status(GoogleAccess.Feature.CALENDAR));
        c.updatedAt = Instant.now(); connections.save(c);
        return new CalendarController.Connection(c.status, !adapter.configured());
    }

    public CalendarController.CalendarPage listCalendars(UUID user, String pageToken) {
        return map(adapter.calendars(user, pageToken));
    }

    public CalendarController.ProjectCalendar projectCalendar(UUID projectId, UUID user) {
        var role = access.role(projectId, user);
        var current = calendars.findByProjectId(projectId).orElse(null);
        if (current == null || current.externalCalendarId == null || current.bindingOwner == null)
            return new CalendarController.ProjectCalendar("NOT_BOUND", null, null, false, "MANAGER".equals(role), false);
        var state = google == null ? GoogleAccess.Status.NOT_CONNECTED : google.status(current.bindingOwner).status(GoogleAccess.Feature.CALENDAR);
        boolean ownerManager;
        try { ownerManager = "MANAGER".equals(access.role(projectId, current.bindingOwner)); }
        catch (RuntimeException demoted) { ownerManager = false; }
        var status = state == GoogleAccess.Status.CONNECTED && ownerManager ? "BOUND" : "REAUTH_REQUIRED";
        var profile = profiles == null ? null : profiles.find(java.util.List.of(current.bindingOwner)).get(current.bindingOwner);
        var ownerName = profile == null ? null : (profile.displayName() == null || profile.displayName().isBlank() ? profile.email() : profile.displayName());
        return new CalendarController.ProjectCalendar(status, current.calendarName, ownerName,
            current.bindingOwner.equals(user), "MANAGER".equals(role), current.backfillPending);
    }

    public CalendarController.ProjectCalendar bind(UUID projectId, UUID user, String calendarId) {
        access.requireManager(projectId, user);
        var existing = calendars.findByProjectId(projectId).orElse(null);
        if (existing != null && existing.externalCalendarId != null) {
            if (existing.bindingOwner != null && existing.bindingOwner.equals(user) && existing.externalCalendarId.equals(calendarId))
                return projectCalendar(projectId, user);
            throw new IllegalStateException("CALENDAR_ALREADY_BOUND");
        }
        var target = adapter.writableCalendar(user, calendarId);
        if (target == null) target = new CalendarAdapter.CalendarInfo(calendarId, null);
        return saveBinding(projectId, user, target);
    }

    @Transactional
    CalendarController.ProjectCalendar saveBinding(UUID projectId, UUID user, CalendarAdapter.CalendarInfo target) {
        if (transactions != null) return transactions.execute(status -> saveBindingInTransaction(projectId, user, target));
        return saveBindingInTransaction(projectId, user, target);
    }

    @Transactional
    CalendarController.ProjectCalendar saveBindingInTransaction(UUID projectId, UUID user, CalendarAdapter.CalendarInfo target) {
        access.lockProject(projectId);
        access.requireManager(projectId, user);
        var c = lockedCalendar(projectId).orElseGet(() -> { var n = new ProjectCalendarEntity(); n.id = UUID.randomUUID(); n.projectId = projectId; return n; });
        if (c.externalCalendarId != null) {
            if (user.equals(c.bindingOwner) && c.externalCalendarId.equals(target.id())) return projectCalendar(projectId, user);
            throw new IllegalStateException("CALENDAR_ALREADY_BOUND");
        }
        c.bindingOwner = user; c.externalCalendarId = target.id(); c.calendarName = target.name(); c.bindingGeneration++;
        c.backfillCursor = null; c.backfillPending = true; c.calendarConnectionId = ensureConnection(user).id;
        calendars.saveAndFlush(c);
        return new CalendarController.ProjectCalendar("BOUND", c.calendarName, null, true, true, true);
    }

    @Transactional public void disconnect(UUID projectId, UUID user) {
        access.lockProject(projectId);
        access.requireManager(projectId, user);
        var c = lockedCalendar(projectId).orElseThrow(NoSuchElementException::new);
        c.bindingOwner = null; c.externalCalendarId = null; c.calendarName = null; c.backfillCursor = null; c.backfillPending = false; c.bindingGeneration++;
        projections.invalidateClaims(c.id); calendars.saveAndFlush(c);
    }

    public CalendarController.Projection projection(UUID projectId, UUID scheduleId, UUID user) {
        access.role(projectId, user); schedules.requireInProject(projectId, scheduleId);
        return calendars.findByProjectId(projectId).flatMap(c -> projections.findByScheduleIdAndProjectCalendarId(scheduleId, c.id))
            .map(this::response).orElse(new CalendarController.Projection(null, scheduleId, "NOT_CONNECTED", "CONFIGURATION_REQUIRED", 0));
    }

    @Transactional public CalendarController.Projection retry(UUID projectId, UUID scheduleId, UUID user) {
        access.requireManager(projectId, user); schedules.requireInProject(projectId, scheduleId);
        var calendar = calendars.findByProjectId(projectId).orElseThrow(NoSuchElementException::new);
        var p = projections.findByScheduleIdAndProjectCalendarId(scheduleId, calendar.id).orElseThrow(NoSuchElementException::new);
        // The six-argument constructor is retained only for legacy unit fixtures. Spring production wiring always
        // uses the GoogleAccess/transaction constructor and routes delivery through CalendarDispatchWorker.
        if (google == null) {
            p.lastAttemptAt = Instant.now();
            try { p.externalEventId = adapter.deliver(p); p.status = "SYNCED"; p.retryClassification = null; }
            catch (CalendarAdapter.ReauthorizationRequired failure) { p.status = "REAUTH_REQUIRED"; p.retryClassification = "REAUTH_REQUIRED"; }
            catch (CalendarAdapter.PermanentFailure failure) { p.status = "FAILED"; p.retryClassification = "PERMANENT"; }
            catch (RuntimeException failure) { p.status = "FAILED"; p.retryClassification = "TRANSIENT"; }
            p.updatedAt = Instant.now(); projections.saveAndFlush(p); return response(p);
        }
        p.status = "PENDING"; p.retryClassification = adapter.configured() ? null : "CONFIGURATION_REQUIRED"; p.updatedAt = Instant.now(); projections.saveAndFlush(p);
        return response(p);
    }

    private CalendarConnectionEntity ensureConnection(UUID user) {
        return connections.findByUserAccountId(user).orElseGet(() -> { var n = new CalendarConnectionEntity(); n.id = UUID.randomUUID(); n.userAccountId = user; n.status = "PENDING"; n.updatedAt = Instant.now(); return connections.save(n); });
    }
    private java.util.Optional<ProjectCalendarEntity> lockedCalendar(UUID projectId) {
        var locked = calendars.findByProjectIdForUpdate(projectId);
        return locked.isPresent() ? locked : calendars.findByProjectId(projectId);
    }
    private CalendarController.CalendarPage map(CalendarAdapter.CalendarPage page) { return new CalendarController.CalendarPage(page.calendars().stream().map(c -> new CalendarController.Calendar(c.id(), c.name())).toList(), page.nextPageToken()); }
    private CalendarController.Projection response(CalendarProjectionEntity p) { return new CalendarController.Projection(p.id, p.scheduleId, p.status, p.retryClassification, p.businessRevision); }
    private static String googleStatus(GoogleAccess.Status status) { return switch (status) { case CONNECTED -> "SYNCED"; case REAUTH_REQUIRED -> "REAUTH_REQUIRED"; case PERMISSION_REQUIRED -> "FAILED"; default -> "NOT_CONNECTED"; }; }
}
