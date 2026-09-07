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
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest({ProjectController.class,InvitationController.class,ScheduleController.class,NotificationController.class,
    CalendarController.class,SessionController.class,DashboardController.class})
@AutoConfigureRestDocs @Import({SecurityConfiguration.class,ApiExceptionHandler.class})
class Phase1ApiDocumentationTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProjectRepository projects;
    @MockitoBean ProjectMemberRepository members;
    @MockitoBean com.aierp.group.api.GroupAccess groupAccess;
    @MockitoBean InvitationService invitations;
    @MockitoBean ScheduleService schedules;
    @MockitoBean NotificationRepository notifications;
    @MockitoBean CalendarService calendar;
    @MockitoBean ProjectAccess projectAccess;
    @MockitoBean ScheduleDashboard scheduleDashboard;
    @MockitoBean CalendarDashboard calendarDashboard;
    final UUID project=UUID.fromString("10000000-0000-0000-0000-000000000001"),user=UUID.fromString("20000000-0000-0000-0000-000000000002"),id=UUID.fromString("30000000-0000-0000-0000-000000000003");
    final UUID ownerGroup = UUID.fromString("40000000-0000-0000-0000-000000000001"), adminGroup = UUID.fromString("40000000-0000-0000-0000-000000000002"), memberGroup = UUID.fromString("40000000-0000-0000-0000-000000000003");
    final String base="/api/v1/projects/{projectId}/schedules";
    final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(user,"member@example.test",true),null,List.of());
    final Instant starts=Instant.parse("2026-09-07T10:00:00Z");
    ScheduleController.ScheduleResponse schedule;
    @BeforeEach void setup() {
        var member=new ProjectMemberEntity();member.projectId=project;member.userAccountId=user;member.role=ProjectRole.MANAGER;
        when(members.findByProjectIdAndUserAccountId(project,user)).thenReturn(Optional.of(member));
        when(members.findByUserAccountId(eq(user),any())).thenReturn(List.of(member));when(members.findByProjectId(eq(project),any())).thenReturn(List.of(member));
        when(projects.findAllById(any())).thenReturn(List.of(new ProjectEntity(project,id,"Planning")));
        when(projects.findById(project)).thenReturn(Optional.of(new ProjectEntity(project,id,"Planning")));
        schedule=new ScheduleController.ScheduleResponse(id,project,"Planning",ScheduleEntity.Status.CONFIRMED,1,1,starts,starts.plusSeconds(3600),"Notes",user,
            List.of(new ScheduleController.Participant(user,null,false),new ScheduleController.Participant(null,"guest@example.test",false)),
            List.of(new ScheduleController.Change(1,"SCHEDULE_CONFIRMED",user,starts)));
        var summary=new ScheduleController.ScheduleResponse(id,project,schedule.title(),schedule.status(),1,1,starts,schedule.endsAt(),null,user,schedule.participants(),List.of());
        when(schedules.list(eq(project),eq(user),any(),any(),any(),any())).thenReturn(List.of(summary));when(schedules.detail(eq(project),eq(id),eq(user),any())).thenReturn(schedule);
        // Command examples execute the actual service against isolated repository fixtures.
        when(schedules.create(eq(project),any(),eq(user))).thenAnswer(i->commandFixture(ScheduleEntity.Status.DRAFT).create(project,i.getArgument(1),user));
        when(schedules.update(eq(project),eq(id),any(),eq(user))).thenAnswer(i->commandFixture(ScheduleEntity.Status.CONFIRMED).update(project,id,i.getArgument(2),user));
        when(schedules.confirm(eq(project),eq(id),any(),eq(user))).thenAnswer(i->commandFixture(ScheduleEntity.Status.DRAFT).confirm(project,id,i.getArgument(2),user));
        when(schedules.cancel(eq(project),eq(id),any(),eq(user))).thenAnswer(i->commandFixture(ScheduleEntity.Status.CONFIRMED).cancel(project,id,i.getArgument(2),user));
        when(schedules.acknowledge(eq(project),eq(id),any(),eq(user))).thenAnswer(i->commandFixture(ScheduleEntity.Status.CONFIRMED).acknowledge(project,id,i.getArgument(2),user));
        var invitation=new InvitationController.InvitationResponse(id,project,"member@example.test","test-token",ProjectInvitationEntity.Status.PENDING,starts);
        when(invitations.invite(eq(id),any(),any())).thenReturn(invitation);when(invitations.detail(eq("test-token"),any())).thenReturn(invitation);
        when(invitations.resolve(eq("test-token"),any(),anyBoolean())).thenAnswer(i->invitationFixture().resolve("test-token",i.getArgument(1),i.getArgument(2)));
        when(calendar.connection(user)).thenReturn(new CalendarController.Connection("NOT_CONNECTED",true));when(calendar.reconnect(user)).thenReturn(new CalendarController.Connection("NOT_CONNECTED",true));
        var projection=new CalendarController.Projection(id,id,"PENDING","CONFIGURATION_REQUIRED",1);
        when(calendar.projection(project,id,user)).thenReturn(projection);when(calendar.retry(project,id,user)).thenReturn(projection);
        when(scheduleDashboard.summary(project,user)).thenReturn(new ScheduleDashboard.Summary(1,1,List.of(summary),List.of(summary)));
        when(projectAccess.memberCount(project)).thenReturn(1L);
    }
    @Test void documentsProjectAndMemberInventory() throws Exception {
        success("projects",get("/api/v1/projects"),fields("[].id","[].groupId","[].name","[].role"));
        success("project-members",get("/api/v1/projects/{projectId}/members",project),fields("[].userId","[].role"));
        success("member-role",patch("/api/v1/projects/{projectId}/members/{userId}",project,user).content("{\"role\":\"VIEWER\"}"),fields("userId","role"),fields("role"));
        var dashboardFields=new ArrayList<>(List.of(fields("projectId","memberCount","scheduleCount","pendingAcknowledgementCount","calendarRiskCount","upcomingSchedules","actionQueue")));
        dashboardFields.addAll(List.of(summaryFields("upcomingSchedules[].")));dashboardFields.addAll(List.of(summaryFields("actionQueue[].")));
        success("dashboard",get("/api/v1/projects/{projectId}/dashboard",project),dashboardFields.toArray(FieldDescriptor[]::new));
    }
    @Test void documentsProjectCreationContracts() throws Exception {
        when(groupAccess.creationOptions(eq(user),eq(0),eq(100))).thenReturn(List.of(
            new com.aierp.group.api.GroupAccess.CreationOption(ownerGroup,"Owner",true,null),
            new com.aierp.group.api.GroupAccess.CreationOption(adminGroup,"Admin",true,null),
            new com.aierp.group.api.GroupAccess.CreationOption(memberGroup,"Member",false,"GROUP_ROLE_REQUIRED")
        ));
        when(projects.save(any())).thenAnswer(i -> i.getArgument(0));
        when(members.save(any())).thenAnswer(i -> i.getArgument(0));
        success("project-creation-options",get("/api/v1/projects/creation-options").queryParam("page","0").queryParam("limit","100"),
            optionFields());
        success("project-create", post("/api/v1/projects").content("{\"groupId\":\""+ownerGroup+"\",\"name\":\"Project\"}"),fields("id","groupId","name","role"),fields("groupId","name"))
            .andExpect(jsonPath("$.role").value("MANAGER"));
    }
    private FieldDescriptor[] optionFields() {
        var result=fields("[].id","[].name","[].canCreate","[].reason");
        result[3].optional().type(JsonFieldType.STRING);
        return result;
    }
    @Test void documentsScheduleInventoryAndParticipantContract() throws Exception {
        success("schedule-list",get(base,project).queryParam("from","2026-09-01T00:00:00Z").queryParam("to","2026-10-01T00:00:00Z").queryParam("limit","20").queryParam("page","0"),summaryFields("[]."));
        success("schedule-detail",get(base+"/{id}",project,id),scheduleFields(""));
        String write="{\"title\":\"Planning\",\"description\":\"Notes\",\"startsAt\":\"2026-09-07T10:00:00Z\",\"endsAt\":\"2026-09-07T11:00:00Z\",\"rowVersion\":1,\"memberParticipantIds\":[\""+user+"\"],\"externalAttendeeEmails\":[\"guest@example.test\"]}";
        var writeFields=fields("title","description","startsAt","endsAt","rowVersion","memberParticipantIds","externalAttendeeEmails");
        writeFields[1].optional().type(JsonFieldType.STRING);
        writeFields[5].optional().type(JsonFieldType.ARRAY);writeFields[6].optional().type(JsonFieldType.ARRAY);
        writeFields[5].attributes(org.springframework.restdocs.snippet.Attributes.key("itemsType").value("STRING"));
        writeFields[6].attributes(org.springframework.restdocs.snippet.Attributes.key("itemsType").value("STRING"));
        var createFields=writeFields.clone();createFields[4]=fieldWithPath("rowVersion").optional().type(JsonFieldType.NUMBER).description("Ignored for creation; may be omitted or null.");
        success("schedule-create",post(base,project).content(write.replace("\"rowVersion\":1","\"rowVersion\":null")),scheduleFields(""),createFields)
            .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.businessRevision").value(0));
        success("schedule-update",patch(base+"/{id}",project,id).content(write.replace("Planning","Updated planning")),scheduleFields(""),writeFields)
            .andExpect(jsonPath("$.businessRevision").value(2));
        success("schedule-confirm",post(base+"/{id}/confirm",project,id).content("{\"rowVersion\":0}"),scheduleFields(""),fields("rowVersion"))
            .andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.businessRevision").value(1));
        success("schedule-cancel",post(base+"/{id}/cancel",project,id).content("{\"rowVersion\":1}"),scheduleFields(""),fields("rowVersion"))
            .andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.businessRevision").value(2));
        success("schedule-acknowledge",post(base+"/{id}/acknowledge",project,id).content("{\"expectedBusinessRevision\":1}"),scheduleFields(""),fields("expectedBusinessRevision"))
            .andExpect(jsonPath("$.participants[0].acknowledged").value(true));
    }
    @Test void documentsInvitationInventory() throws Exception {
        var fields=fields("id","projectId","email","token","status","expiresAt");
        success("invitation-create",post("/api/v1/groups/{groupId}/invitations",id).content("{\"projectId\":\""+project+"\",\"email\":\"member@example.test\"}"),fields,fields("projectId","email"));
        success("invitation-detail",get("/api/v1/invitations/{token}","test-token"),fields);
        success("invitation-accept",post("/api/v1/invitations/{token}/accept","test-token"),fields)
            .andExpect(jsonPath("$.status").value("ACCEPTED"));
        success("invitation-reject",post("/api/v1/invitations/{token}/reject","test-token"),fields)
            .andExpect(jsonPath("$.status").value("REJECTED"));
        success("invitation-resolution",post("/api/v1/invitations/{token}","test-token").content("{\"action\":\"ACCEPT\"}"),fields,fields("action"));
    }
    @Test void documentsNotificationCalendarAndSessionInventory() throws Exception {
        var n=new NotificationEntity();n.id=id;n.userAccountId=user;n.type="SCHEDULE_CONFIRMED";n.link="/projects/"+project+"/schedules/"+id;n.createdAt=starts;
        when(notifications.findByUserAccountId(eq(user),any())).thenReturn(List.of(n));when(notifications.findByIdAndUserAccountId(id,user)).thenReturn(Optional.of(n));
        var nf=fields("[].id","[].type","[].link","[].readAt","[].createdAt");nf[2].optional().type(JsonFieldType.STRING);nf[3].optional().type(JsonFieldType.STRING);
        success("notification-list",get("/api/v1/notifications"),nf);
        var readFields=fields("id","type","link","readAt","createdAt");readFields[2].optional().type(JsonFieldType.STRING);
        success("notification-read",post("/api/v1/notifications/{id}/read",id),readFields);
        success("calendar-connection",get("/api/v1/calendar/connection"),fields("status","configurationRequired"));
        success("calendar-reconnect",post("/api/v1/calendar/reconnect"),fields("status","configurationRequired"));
        success("calendar-projection",get(base+"/{id}/calendar",project,id),projectionFields());
        success("calendar-retry",post(base+"/{id}/calendar/retry",project,id),projectionFields());
        when(calendar.projection(project,id,user)).thenReturn(new CalendarController.Projection(null,id,"NOT_CONNECTED","CONFIGURATION_REQUIRED",0));
        success("calendar-projection-not-connected",get(base+"/{id}/calendar",project,id),projectionFields()).andExpect(jsonPath("$.id").isEmpty());
        when(calendar.retry(project,id,user)).thenReturn(new CalendarController.Projection(id,id,"SYNCED",null,1));
        success("calendar-retry-synced",post(base+"/{id}/calendar/retry",project,id),projectionFields()).andExpect(jsonPath("$.retryClassification").isEmpty());
        success("csrf-token",get("/api/v1/csrf"),fields("headerName","token"));
        var cf=fields("login","calendar","loginUrl");cf[2].optional().type(JsonFieldType.STRING);
        success("system-configuration",get("/api/v1/system/configuration"),cf);
        mvc.perform(post("/api/v1/logout").with(authentication(auth)).with(csrf().asHeader())).andExpect(status().isNoContent())
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
        when(schedules.detail(eq(project),eq(id),eq(user),any())).thenThrow(new org.springframework.security.access.AccessDeniedException("PROJECT_ACCESS_DENIED"));
        problem("problem-authorization-403",get(base+"/{id}",project,id),403,"FORBIDDEN",false);
        doThrow(new NoSuchElementException()).when(schedules).detail(eq(project),eq(id),eq(user),any());
        problem("problem-not-found-404",get(base+"/{id}",project,id),404,"NOT_FOUND",false);
        // Real commandFixture service checks rowVersion; no conflict is fabricated on a read.
        problem("problem-stale-409",post(base+"/{id}/cancel",project,id).content("{\"rowVersion\":0}"),409,"STALE_ROW_VERSION",false);
        when(schedules.confirm(eq(project),eq(id),any(),eq(user))).thenAnswer(i->commandFixture(ScheduleEntity.Status.CONFIRMED).confirm(project,id,i.getArgument(2),user));
        problem("problem-state-409",post(base+"/{id}/confirm",project,id).content("{\"rowVersion\":1}"),409,"CONFLICT",false);
        problem("problem-method-405",put(base+"/{id}",project,id),405,"METHOD_NOT_ALLOWED",false);
        var unsupported=mvc.perform(post(base,project).with(authentication(auth)).with(csrf().asHeader()).contentType("text/plain").content("payload"))
            .andExpect(status().isUnsupportedMediaType()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
            .andDo(document("problem-media-415",resource(ResourceSnippetParameters.builder().responseFields(problemFields(false)).build()))).andReturn();
        assertTrace(unsupported);
        doThrow(new RuntimeException("internal database diagnostic")).when(schedules).detail(eq(project),eq(id),eq(user),any());
        problem("problem-unexpected-500",get(base+"/{id}",project,id),500,"INTERNAL_ERROR",false);
        doThrow(new org.springframework.security.access.AccessDeniedException("VIEWER_READ_ONLY")).when(schedules).acknowledge(eq(project),eq(id),any(),eq(user));
        problem("acknowledgement-viewer-403",post(base+"/{id}/acknowledge",project,id).content("{\"expectedBusinessRevision\":1}"),403,"FORBIDDEN",false);
    }
    private ResultActions success(String name,MockHttpServletRequestBuilder request,FieldDescriptor[] response,FieldDescriptor... input) throws Exception {
        var parameters=ResourceSnippetParameters.builder().tag("Phase 1")
            .description(name+". Cookie session and CSRF are required for writes. UUID identifiers; ISO-8601 UTC timestamps.")
            .requestFields(input).responseFields(response);
        if(Set.of("projects","project-members","notification-list","schedule-list","project-creation-options").contains(name)) {
            var query=new ArrayList<ParameterDescriptorWithType>();
            query.add(parameterWithName("page").type(SimpleType.NUMBER).optional().defaultValue(0).description("Zero-based page, 0..10000."));
            query.add(parameterWithName("limit").type(SimpleType.NUMBER).optional().defaultValue(100).description("Maximum rows per page, 1..200; default 100."));
            if(name.equals("schedule-list")) {
                query.add(parameterWithName("from").type(SimpleType.STRING).optional().description("UTC interval lower bound, exclusive on endsAt; default 0001-01-01T00:00:00Z."));
                query.add(parameterWithName("to").type(SimpleType.STRING).optional().description("UTC interval upper bound, exclusive on startsAt; default 9999-12-31T23:59:59Z; must follow from."));
                parameters.description("Bounded schedule summaries ordered by startsAt and id. Participants contain current ACK state. changes is empty in lists; use detail for the most recent history.");
            }
            parameters.queryParameters(query);
        }
        if(name.equals("schedule-detail")) parameters.queryParameters(parameterWithName("historyLimit").type(SimpleType.NUMBER).optional().defaultValue(50).description("Most recent history entries, 1..200; default 50, returned chronologically."));
        if(name.equals("dashboard")) parameters.description("Database totals across this project. Pending ACK excludes current VIEWER/nonmembers. Upcoming schedules overlap now through 14 days, exclude cancelled, and are capped at 100. Action queue is capped at 100, and empty for VIEWER. Lists ordered by startsAt and id; changes is empty; open detail for history.");
        if(name.equals("project-creation-options")) parameters.description("Owner/Admin group memberships can create; MEMBER and null-role memberships cannot.");
        return mvc.perform(request.with(authentication(auth)).with(csrf().asHeader()).contentType("application/json;charset=UTF-8"))
            .andExpect(status().isOk()).andDo(document(name,resource(parameters.build())));
    }
    private void problem(String name,MockHttpServletRequestBuilder request,int expected,String code,boolean fieldErrors) throws Exception {
        var result=mvc.perform(request.with(authentication(auth)).with(csrf().asHeader()).contentType("application/json"))
            .andExpect(status().is(expected)).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.traceId").isNotEmpty())
            .andDo(document(name,resource(ResourceSnippetParameters.builder().responseFields(problemFields(fieldErrors)).build()))).andReturn();
        assertTrace(result);
    }
    private void assertTrace(MvcResult result) throws Exception {
        var body=new tools.jackson.databind.json.JsonMapper().readTree(result.getResponse().getContentAsString());
        assertThat(body.get("traceId").asText()).isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
    }
    private FieldDescriptor[] projectionFields() {
        var f=fields("id","scheduleId","status","retryClassification","businessRevision");
        f[0].optional().type(JsonFieldType.STRING);f[3].optional().type(JsonFieldType.STRING);return f;
    }
    private FieldDescriptor[] problemFields(boolean nested) {return nested?fields("code","traceId","fieldErrors","fieldErrors[].field","fieldErrors[].message"):fields("code","traceId","fieldErrors");}
    private FieldDescriptor[] fields(String... paths) {return Arrays.stream(paths).map(p->fieldWithPath(p).description(p)).toArray(FieldDescriptor[]::new);}
    private FieldDescriptor[] scheduleFields(String prefix) {
        var result=fields(Arrays.stream(new String[]{"id","projectId","title","status","rowVersion","businessRevision","startsAt","endsAt","description","createdBy","participants","participants[].memberUserId","participants[].externalEmail","participants[].acknowledged","changes","changes[].businessRevision","changes[].type","changes[].changedBy","changes[].createdAt"}).map(p->prefix+p).toArray(String[]::new));
        result[8].optional().type(JsonFieldType.STRING);result[11].optional().type(JsonFieldType.STRING);result[12].optional().type(JsonFieldType.STRING);
        result[15].type(JsonFieldType.NUMBER);result[16].type(JsonFieldType.STRING);result[17].type(JsonFieldType.STRING);result[18].type(JsonFieldType.STRING);
        return result;
    }
    private FieldDescriptor[] summaryFields(String prefix) {
        return Arrays.stream(scheduleFields(prefix)).filter(f->!f.getPath().startsWith(prefix+"changes[].")).toArray(FieldDescriptor[]::new);
    }
    private ScheduleService commandFixture(ScheduleEntity.Status status) {
        var repository=mock(ScheduleRepository.class);var participants=mock(ScheduleParticipantRepository.class);
        var acks=mock(ScheduleAcknowledgementRepository.class);var changes=mock(ScheduleChangeRepository.class);
        var s=new ScheduleEntity();s.id=id;s.projectId=project;s.createdBy=user;s.title="Planning";s.description="Notes";
        s.startsAt=starts;s.endsAt=starts.plusSeconds(3600);s.status=status;s.businessRevision=status==ScheduleEntity.Status.DRAFT?0:1;s.rowVersion=s.businessRevision;
        when(repository.findByIdAndProjectId(id,project)).thenReturn(Optional.of(s));when(repository.lockByIdAndProjectId(id,project)).thenReturn(Optional.of(s));
        when(repository.saveAndFlush(any())).thenAnswer(i->{ScheduleEntity saved=i.getArgument(0);if(saved==s)saved.rowVersion++;return saved;});
        var member=new ScheduleParticipantEntity();member.scheduleId=id;member.memberUserAccountId=user;
        var external=new ScheduleParticipantEntity();external.scheduleId=id;external.externalEmail="guest@example.test";
        var ps=new ArrayList<>(List.of(member,external));
        when(participants.findByScheduleId(any())).thenAnswer(i->List.copyOf(ps));
        doAnswer(i->{ps.clear();return null;}).when(participants).deleteByScheduleId(any());
        when(participants.save(any())).thenAnswer(i->{ScheduleParticipantEntity p=i.getArgument(0);ps.add(p);return p;});
        var ackRows=new ArrayList<ScheduleAcknowledgementEntity>();
        when(acks.findByScheduleIdAndBusinessRevision(any(),anyLong())).thenAnswer(i->List.copyOf(ackRows));
        when(acks.save(any())).thenAnswer(i->{ScheduleAcknowledgementEntity a=i.getArgument(0);ackRows.add(a);return a;});
        var history=new ArrayList<ScheduleChangeEntity>();
        when(changes.save(any())).thenAnswer(i->{ScheduleChangeEntity c=i.getArgument(0);history.add(c);return c;});
        when(changes.findByScheduleId(any(),any())).thenAnswer(i->List.copyOf(history.reversed()));
        return new ScheduleService(repository,participants,acks,changes,mock(ProjectAccess.class),mock(com.aierp.platform.events.EventJournal.class));
    }
    private InvitationService invitationFixture() {
        var repository=mock(ProjectInvitationRepository.class);var ps=mock(ProjectRepository.class);
        var invitation=new ProjectInvitationEntity();invitation.id=id;invitation.projectId=project;invitation.email="member@example.test";
        invitation.token="test-token";invitation.status=ProjectInvitationEntity.Status.PENDING;invitation.expiresAt=Instant.now().plusSeconds(86400);
        when(repository.findProjectIdByToken("test-token")).thenReturn(Optional.of(project));when(repository.lockByToken("test-token")).thenReturn(Optional.of(invitation));
        when(ps.lockById(project)).thenReturn(Optional.of(new ProjectEntity(project,id,"Planning")));
        return new InvitationService(repository,ps,members,mock(com.aierp.group.api.GroupAccess.class),mock(com.aierp.platform.events.EventJournal.class));
    }
}
