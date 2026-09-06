package com.aierp.calendarintegration;
/** Credentials and HTTP delivery belong to a future enabled adapter, never a schedule transaction. */
public interface CalendarAdapter {
    boolean configured();
    String deliver(CalendarProjectionEntity projection);
    class ReauthorizationRequired extends RuntimeException {}
    class PermanentFailure extends RuntimeException {}
}
