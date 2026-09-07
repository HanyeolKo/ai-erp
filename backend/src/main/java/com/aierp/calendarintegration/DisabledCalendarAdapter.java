package com.aierp.calendarintegration;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component
@ConditionalOnProperty(name = "APP_GOOGLE_WORKSPACE_ENABLED", havingValue = "false", matchIfMissing = true)
public class DisabledCalendarAdapter implements CalendarAdapter {
    public boolean configured() {return false;}
    public String deliver(CalendarProjectionEntity projection) {throw new IllegalStateException("CONFIGURATION_REQUIRED");}
}
