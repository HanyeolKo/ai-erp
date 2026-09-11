package com.aierp;

import com.aierp.calendarintegration.*;
import com.aierp.identity.api.GoogleAccess;
import com.aierp.identity.api.IdentityProfiles;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalendarStartupRegressionTest {
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = "com.aierp.calendarintegration", useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = CalendarAdapter.class))
    static class AdapterScan {}

    @Test void componentScanRegistersExactlyOneDisabledAdapterWithoutExplicitImport() {
        try (var context = new AnnotationConfigApplicationContext(AdapterScan.class)) {
            assertThat(context.getBeansOfType(CalendarAdapter.class)).hasSize(1);
            assertThat(context.getBean(CalendarAdapter.class).configured()).isFalse();
        }
    }
    @Test void productionCalendarPackageScanStartsServiceWithoutAdapterImport() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(CalendarConnectionRepository.class,()->mock(CalendarConnectionRepository.class));
            context.registerBean(ProjectCalendarRepository.class,()->mock(ProjectCalendarRepository.class));
            context.registerBean(CalendarProjectionRepository.class,()->mock(CalendarProjectionRepository.class));
            context.registerBean(com.aierp.project.api.ProjectAccess.class,()->mock(com.aierp.project.api.ProjectAccess.class));
            context.registerBean(com.aierp.schedule.api.ScheduleLookup.class,()->mock(com.aierp.schedule.api.ScheduleLookup.class));
            context.registerBean(GoogleAccess.class,()->mock(GoogleAccess.class));
            context.registerBean(IdentityProfiles.class,()->mock(IdentityProfiles.class));
            context.registerBean(org.springframework.transaction.PlatformTransactionManager.class,()->mock(org.springframework.transaction.PlatformTransactionManager.class));
            context.scan("com.aierp.calendarintegration");context.refresh();
            assertThat(context.getBeansOfType(CalendarAdapter.class)).hasSize(1);
            assertThat(context.getBean(CalendarService.class).connection(java.util.UUID.randomUUID()).configurationRequired()).isTrue();
        }
    }
}
