package com.aierp;

import com.aierp.identity.api.*;
import com.aierp.project.*;
import com.aierp.project.api.*;
import com.aierp.schedule.*;
import com.aierp.schedule.api.*;
import com.aierp.notification.*;
import com.aierp.notification.api.*;
import com.aierp.calendarintegration.*;
import com.aierp.calendarintegration.api.*;
import com.aierp.dashboard.DashboardController;
import com.aierp.platform.web.*;
import com.epages.restdocs.apispec.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.restdocs.payload.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.mockito.Mockito.*;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;

@WebMvcTest({ProjectController.class,InvitationController.class,ScheduleController.class,NotificationController.class,
    CalendarController.class,SessionController.class,DashboardController.class})
@AutoConfigureRestDocs @Import({SecurityConfiguration.class,ApiExceptionHandler.class})
class Phase1ApiDocumentationTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProjectRepository projects;
    @MockitoBean ProjectMemberRepository members;
    @MockitoBean InvitationService invitations;
    @MockitoBean ScheduleService schedules;
    @MockitoBean NotificationRepository notifications;
    @MockitoBean CalendarService calendar;
    @MockitoBean ProjectAccess projectAccess;
    @MockitoBean ScheduleDashboard scheduleDashboard;
    @MockitoBean CalendarDashboard calendarDashboard;
    final UUID project=UUID.fromString("10000000-0000-0000-0000-000000000001"),user=UUID.fromString("20000000-0000-0000-0000-000000000002"),id=UUID.fromString("30000000-0000-0000-0000-000000000003");
    final String base="/api/v1/projects/{projectId}/schedules";
    final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(user,"member@example.test",true),null,List.of());
    final Instant starts=Instant.parse("2026-09-07T10:00:00Z");
    ScheduleController.ScheduleResponse schedule;
    @BeforeEach void setup() {
        var member=new ProjectMemberEntity();member.projectId=project;member.userAccountId=user;member.role=ProjectRole.MANAGER;
        when(members.findByProjectIdAndUserAccountId(project,user)).thenReturn(Optional.of(member));
        when(members.findByUserAccountId(user)).thenReturn(List.of(member));when(members.findByProjectId(project)).thenReturn(List.of(member));
        when(projects.findById(project)).thenReturn(Optional.of(new ProjectEntity(project,id,"Planning")));
        schedule=new ScheduleController.ScheduleResponse(id,project,"Planning",ScheduleEntity.Status.CONFIRMED,1,1,starts,starts.plusSeconds(3600),"Notes",user,
            List.of(new ScheduleController.Participant(user,null,false),new ScheduleController.Participant(null,"guest@example.test",false)),
            List.of(new ScheduleController.Change(1,"SCHEDULE_CONFIRMED",user,starts)));
        when(schedules.list(project,user)).thenReturn(List.of(schedule));when(schedules.detail(project,id,user)).thenReturn(schedule);
        when(schedules.create(eq(project),any(),eq(user))).thenReturn(schedule);when(schedules.update(eq(project),eq(id),any(),eq(user))).thenReturn(schedule);
        when(schedules.confirm(eq(project),eq(id),any(),eq(user))).thenReturn(schedule);when(schedules.cancel(eq(project),eq(id),any(),eq(user))).thenReturn(schedule);when(schedules.acknowledge(eq(project),eq(id),any(),eq(user))).thenReturn(schedule);
        var invitation=new InvitationController.InvitationResponse(id,project,"member@example.test","test-token",ProjectInvitationEntity.Status.PENDING,starts);
        when(invitations.invite(eq(id),any(),any())).thenReturn(invitation);when(invitations.detail(eq("test-token"),any())).thenReturn(invitation);when(invitations.resolve(eq("test-token"),any(),anyBoolean())).thenReturn(invitation);
        when(calendar.connection(user)).thenReturn(new CalendarController.Connection("NOT_CONNECTED",true));when(calendar.reconnect(user)).thenReturn(new CalendarController.Connection("NOT_CONNECTED",true));
        var projection=new CalendarController.Projection(id,id,"PENDING","CONFIGURATION_REQUIRED",1);
        when(calendar.projection(project,id,user)).thenReturn(projection);when(calendar.retry(project,id,user)).thenReturn(projection);
        when(scheduleDashboard.summary(project,user)).thenReturn(new ScheduleDashboard.Summary(0,0,List.of(),List.of()));
    }
    @Test void documentsProjectAndMemberInventory() throws Exception {
        success("projects",get("/api/v1/projects"),fields("[].id","[].groupId","[].name","[].role"));
        success("project-members",get("/api/v1/projects/{projectId}/members",project),fields("[].userId","[].role"));
        success("member-role",patch("/api/v1/projects/{projectId}/members/{userId}",project,user).content("{\"role\":\"VIEWER\"}"),fields("userId","role"),fields("role"));
        success("dashboard",get("/api/v1/projects/{projectId}/dashboard",project),fields("projectId","memberCount","scheduleCount","pendingAcknowledgementCount","calendarRiskCount","upcomingSchedules","actionQueue"));
    }
    @Test void documentsScheduleInventoryAndParticipantContract() throws Exception {
        success("schedule-list",get(base,project),scheduleFields("[]."));
        success("schedule-detail",get(base+"/{id}",project,id),scheduleFields(""));
        String write="{\"title\":\"Planning\",\"description\":\"Notes\",\"startsAt\":\"2026-09-07T10:00:00Z\",\"endsAt\":\"2026-09-07T11:00:00Z\",\"rowVersion\":1,\"memberParticipantIds\":[\""+user+"\"],\"externalAttendeeEmails\":[\"guest@example.test\"]}";
        var writeFields=fields("title","description","startsAt","endsAt","rowVersion","memberParticipantIds","externalAttendeeEmails");
        writeFields[5].attributes(org.springframework.restdocs.snippet.Attributes.key("itemsType").value("STRING"));
        writeFields[6].attributes(org.springframework.restdocs.snippet.Attributes.key("itemsType").value("STRING"));
        success("schedule-create",post(base,project).content(write),scheduleFields(""),writeFields);
        success("schedule-update",patch(base+"/{id}",project,id).content(write),scheduleFields(""),writeFields);
        success("schedule-confirm",post(base+"/{id}/confirm",project,id).content("{\"rowVersion\":1}"),scheduleFields(""),fields("rowVersion"));
        success("schedule-cancel",post(base+"/{id}/cancel",project,id).content("{\"rowVersion\":1}"),scheduleFields(""),fields("rowVersion"));
        success("schedule-acknowledge",post(base+"/{id}/acknowledge",project,id).content("{\"expectedBusinessRevision\":1}"),scheduleFields(""),fields("expectedBusinessRevision"));
    }
    @Test void documentsInvitationInventory() throws Exception {
        var fields=fields("id","projectId","email","token","status","expiresAt");
        success("invitation-create",post("/api/v1/groups/{groupId}/invitations",id).content("{\"projectId\":\""+project+"\",\"email\":\"member@example.test\"}"),fields,fields("projectId","email"));
        success("invitation-detail",get("/api/v1/invitations/{token}","test-token"),fields);
        success("invitation-accept",post("/api/v1/invitations/{token}/accept","test-token"),fields);
        success("invitation-reject",post("/api/v1/invitations/{token}/reject","test-token"),fields);
        success("invitation-resolution",post("/api/v1/invitations/{token}","test-token").content("{\"action\":\"ACCEPT\"}"),fields,fields("action"));
    }
    @Test void documentsNotificationCalendarAndSessionInventory() throws Exception {
        var n=new NotificationEntity();n.id=id;n.userAccountId=user;n.type="SCHEDULE_CONFIRMED";n.link="/projects/"+project+"/schedules/"+id;n.createdAt=starts;
        when(notifications.findTop100ByUserAccountIdOrderByCreatedAtDesc(user)).thenReturn(List.of(n));when(notifications.findByIdAndUserAccountId(id,user)).thenReturn(Optional.of(n));
        var nf=fields("[].id","[].type","[].link","[].readAt","[].createdAt");nf[3].optional().type(JsonFieldType.STRING);
        success("notification-list",get("/api/v1/notifications"),nf);
        success("notification-read",post("/api/v1/notifications/{id}/read",id),fields("id","type","link","readAt","createdAt"));
        success("calendar-connection",get("/api/v1/calendar/connection"),fields("status","configurationRequired"));
        success("calendar-reconnect",post("/api/v1/calendar/reconnect"),fields("status","configurationRequired"));
        success("calendar-projection",get(base+"/{id}/calendar",project,id),fields("id","scheduleId","status","retryClassification","businessRevision"));
        success("calendar-retry",post(base+"/{id}/calendar/retry",project,id),fields("id","scheduleId","status","retryClassification","businessRevision"));
        success("csrf-token",get("/api/v1/csrf"),fields("headerName","token"));
        var cf=fields("login","calendar","loginUrl");cf[2].optional().type(JsonFieldType.STRING);
        success("system-configuration",get("/api/v1/system/configuration"),cf);
        mvc.perform(post("/api/v1/logout").with(authentication(auth)).with(csrf())).andExpect(status().isNoContent())
            .andDo(document("logout",resource(ResourceSnippetParameters.builder().tag("Phase 1").description("Invalidate cookie session; CSRF required.").build())));
    }
    @Test void documentsUniformProblemContractsWithFieldErrorsAndTraceCorrelation() throws Exception {
        var bad=patch("/api/v1/projects/{projectId}/members/{userId}",project,user).content("{\"role\":null}");
        problem("problem-validation-400",bad,400,"VALIDATION_FAILED",true);
        problem("problem-malformed-400",patch(base+"/{id}",project,id).content("{"),400,"VALIDATION_FAILED",true);
        mvc.perform(get(base+"/{id}",project,id)).andExpect(status().isUnauthorized())
            .andDo(document("problem-authentication-401",resource(ResourceSnippetParameters.builder().responseFields(problemFields(false)).build())));
        mvc.perform(post(base,project).with(authentication(auth)).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andDo(document("problem-csrf-403",resource(ResourceSnippetParameters.builder().responseFields(problemFields(false)).build())));
        when(schedules.detail(project,id,user)).thenThrow(new org.springframework.security.access.AccessDeniedException("PROJECT_ACCESS_DENIED"));
        problem("problem-authorization-403",get(base+"/{id}",project,id),403,"FORBIDDEN",false);
        doThrow(new NoSuchElementException()).when(schedules).detail(project,id,user);
        problem("problem-not-found-404",get(base+"/{id}",project,id),404,"NOT_FOUND",false);
        doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(ScheduleEntity.class,id)).when(schedules).detail(project,id,user);
        problem("problem-stale-409",get(base+"/{id}",project,id),409,"STALE_ROW_VERSION",false);
        doThrow(new IllegalStateException("STATE_CONFLICT")).when(schedules).detail(project,id,user);
        problem("problem-state-409",get(base+"/{id}",project,id),409,"CONFLICT",false);
        doThrow(new org.springframework.security.access.AccessDeniedException("VIEWER_READ_ONLY")).when(schedules).acknowledge(eq(project),eq(id),any(),eq(user));
        problem("acknowledgement-viewer-403",post(base+"/{id}/acknowledge",project,id).content("{\"expectedBusinessRevision\":1}"),403,"FORBIDDEN",false);
    }
    private void success(String name,MockHttpServletRequestBuilder request,FieldDescriptor[] response,FieldDescriptor... input) throws Exception {
        mvc.perform(request.with(authentication(auth)).with(csrf()).contentType("application/json"))
            .andExpect(status().isOk()).andDo(document(name,resource(ResourceSnippetParameters.builder().tag("Phase 1")
                .description(name+". All writes require a cookie session and CSRF token. UUID identifiers and UTC timestamps.")
                .requestFields(input).responseFields(response).build())));
    }
    private void problem(String name,MockHttpServletRequestBuilder request,int expected,String code,boolean fieldErrors) throws Exception {
        var result=mvc.perform(request.with(authentication(auth)).with(csrf()).contentType("application/json"))
            .andExpect(status().is(expected)).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.traceId").isNotEmpty())
            .andDo(document(name,resource(ResourceSnippetParameters.builder().responseFields(problemFields(fieldErrors)).build()))).andReturn();
        var body=new tools.jackson.databind.json.JsonMapper().readTree(result.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(body.get("traceId").asText()).isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
    }
    private FieldDescriptor[] problemFields(boolean nested) {return nested?fields("code","traceId","fieldErrors","fieldErrors[].field","fieldErrors[].message"):fields("code","traceId","fieldErrors");}
    private FieldDescriptor[] fields(String... paths) {return Arrays.stream(paths).map(p->fieldWithPath(p).description(p)).toArray(FieldDescriptor[]::new);}
    private FieldDescriptor[] scheduleFields(String prefix) {
        var result=fields(Arrays.stream(new String[]{"id","projectId","title","status","rowVersion","businessRevision","startsAt","endsAt","description","createdBy","participants","participants[].memberUserId","participants[].externalEmail","participants[].acknowledged","changes","changes[].businessRevision","changes[].type","changes[].changedBy","changes[].createdAt"}).map(p->prefix+p).toArray(String[]::new));
        result[11].optional().type(JsonFieldType.STRING);result[12].optional().type(JsonFieldType.STRING);return result;
    }
}
