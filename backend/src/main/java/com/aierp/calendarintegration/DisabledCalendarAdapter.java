package com.aierp.calendarintegration;
import org.springframework.stereotype.Component;
@Component
public class DisabledCalendarAdapter implements CalendarAdapter {
    public boolean configured() {return false;}
    public String deliver(CalendarProjectionEntity projection) {throw new IllegalStateException("CONFIGURATION_REQUIRED");}
}
