package com.aierp;
import com.aierp.identity.api.*;
import com.aierp.platform.web.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.mockito.Mockito.*;
@WebMvcTest({SessionController.class,com.aierp.notification.api.NotificationController.class,com.aierp.calendarintegration.api.CalendarController.class})
@Import({SecurityConfiguration.class,com.aierp.calendarintegration.CalendarService.class,com.aierp.calendarintegration.DisabledCalendarAdapter.class})
class SupportingApiTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aierp.notification.NotificationRepository notifications;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aierp.calendarintegration.CalendarConnectionRepository connections;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aierp.calendarintegration.ProjectCalendarRepository calendars;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aierp.calendarintegration.CalendarProjectionRepository projections;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aierp.project.api.ProjectAccess access;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aierp.schedule.api.ScheduleLookup scheduleLookup;
    @Autowired MockMvc mvc;
    final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(UUID.randomUUID(),"a@example.test",true),null,List.of());
    @Test void notificationsAreAnEmptyArrayForNewUser() throws Exception {
        mvc.perform(get("/api/v1/notifications").with(authentication(auth))).andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void calendarWithoutCredentialsReportsConfigurationRequired() throws Exception {
        mvc.perform(get("/api/v1/calendar/connection").with(authentication(auth))).andExpect(status().isOk()).andExpect(jsonPath("$.configurationRequired").value(true));
    }
    @Test void calendarProjectionForMissingOrWrongProjectScheduleReturnsProblem404() throws Exception {
        UUID project=UUID.randomUUID(),schedule=UUID.randomUUID();
        doThrow(new NoSuchElementException()).when(scheduleLookup).requireInProject(project,schedule);
        mvc.perform(get("/api/v1/projects/{projectId}/schedules/{id}/calendar",project,schedule).with(authentication(auth)))
            .andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verifyNoInteractions(calendars,projections);
    }
    @Test void notificationBoundsRejectExcessiveLimitWithProblem400() throws Exception {
        mvc.perform(get("/api/v1/notifications").param("limit","201").with(authentication(auth)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("limit"));
        verifyNoInteractions(notifications);
    }
}
