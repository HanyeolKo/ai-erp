package com.aierp.schedule.api;

import com.aierp.schedule.ScheduleRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Schedule-owned existence contract for other modules. Caller must authorize project access first. */
@Service
@Transactional(readOnly = true)
public class ScheduleLookup {
    private final ScheduleRepository schedules;
    public ScheduleLookup(ScheduleRepository schedules) { this.schedules = schedules; }
    public void requireInProject(UUID projectId, UUID scheduleId) {
        if (!schedules.existsByIdAndProjectId(scheduleId, projectId)) throw new NoSuchElementException();
    }

    /** Returns one immutable schedule view for Calendar delivery; no schedule entity crosses the module boundary. */
    public CalendarSnapshot snapshot(UUID projectId, UUID scheduleId) {
        var schedule = schedules.findByIdAndProjectId(scheduleId, projectId).orElseThrow(NoSuchElementException::new);
        return toSnapshot(schedule);
    }

    /** Bounded, deterministic UUID-cursor export page. Drafts and zero-revision schedules are excluded. */
    public CalendarPage exportPage(UUID projectId, UUID cursor, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("CALENDAR_EXPORT_LIMIT_INVALID");
        var rows = schedules.findCalendarExportPage(projectId, cursor,
            PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "id")));
        var values = rows.stream().map(this::toSnapshot).toList();
        UUID next = values.size() == limit ? values.getLast().scheduleId() : null;
        return new CalendarPage(values, next, next != null);
    }
    public CalendarPage exportPage(UUID projectId, UUID cursor) { return exportPage(projectId, cursor, 100); }

    private CalendarSnapshot toSnapshot(com.aierp.schedule.ScheduleEntity schedule) {
        return new CalendarSnapshot(schedule.id, schedule.projectId, schedule.title, schedule.description,
            schedule.startsAt, schedule.endsAt, schedule.status.name(), schedule.businessRevision);
    }

    public record CalendarSnapshot(UUID scheduleId, UUID projectId, String title, String description,
                                   Instant startsAt, Instant endsAt, String status, long businessRevision) {
        public CalendarSnapshot {
            if (scheduleId == null || projectId == null || startsAt == null || endsAt == null || status == null)
                throw new IllegalArgumentException("CALENDAR_SNAPSHOT_INCOMPLETE");
        }
        public boolean cancelled() { return "CANCELLED".equals(status); }
    }

    public record CalendarPage(List<CalendarSnapshot> schedules, UUID nextCursor, boolean hasNext) {
        public CalendarPage { schedules = List.copyOf(schedules); }
    }
}
