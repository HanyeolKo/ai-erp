package com.aierp.calendarintegration;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
@Component @ConditionalOnMissingBean(CalendarAdapter.class)
public class DisabledCalendarAdapter implements CalendarAdapter {
    public boolean configured() {return false;}
    public String deliver(CalendarProjectionEntity projection) {throw new IllegalStateException("CONFIGURATION_REQUIRED");}
}
