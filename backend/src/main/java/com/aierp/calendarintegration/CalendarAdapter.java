package com.aierp.calendarintegration;
import com.aierp.schedule.api.ScheduleLookup;
import java.util.List;
import java.util.UUID;
/** Calendar provider boundary. Implementations perform bounded HTTP only after local claims are committed. */
public interface CalendarAdapter {
    boolean configured();
    String deliver(CalendarProjectionEntity projection);

    default CalendarPage calendars(UUID userId, String pageToken) { throw new ConfigurationRequired(); }
    default void validateWritable(UUID userId, String calendarId) { throw new ConfigurationRequired(); }
    default CalendarInfo writableCalendar(UUID userId, String calendarId) { validateWritable(userId, calendarId); return null; }
    default DeliveryResult deliver(UUID userId, String calendarId, ScheduleLookup.CalendarSnapshot schedule,
                                   String previousEventId) { throw new ConfigurationRequired(); }
    default DeliveryResult deliver(UUID userId, String calendarId, ScheduleLookup.CalendarSnapshot schedule,
                                   String previousEventId, java.util.function.BooleanSupplier guard) {
        if (!guard.getAsBoolean()) throw new StaleClaim();
        return deliver(userId, calendarId, schedule, previousEventId);
    }

    record CalendarPage(List<CalendarInfo> calendars, String nextPageToken) {
        public CalendarPage { calendars = List.copyOf(calendars); }
    }
    record CalendarInfo(String id, String name) { }
    record DeliveryResult(String eventId, String etag) { }
    class ConfigurationRequired extends RuntimeException { public ConfigurationRequired() { super("CONFIGURATION_REQUIRED"); } }
    class ReauthorizationRequired extends RuntimeException {}
    class PermissionRequired extends RuntimeException {}
    class PermanentFailure extends RuntimeException {}
    class TransientFailure extends RuntimeException {}
    class StaleClaim extends RuntimeException {}
}
