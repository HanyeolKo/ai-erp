package com.aierp;

import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.projectplan.*;
import com.aierp.projectplan.api.ProjectPlanController;
import com.aierp.projectplan.api.ProjectPlanController.*;
import com.aierp.platform.web.*;
import com.epages.restdocs.apispec.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectPlanController.class) @org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs
@Import({SecurityConfiguration.class,ApiExceptionHandler.class})
class ProjectPlanApiDocumentationTest {
    @Autowired MockMvc mvc; @MockitoBean ProjectPlanService plans;
    final UUID project=UUID.fromString("10000000-0000-0000-0000-000000000001"),user=UUID.fromString("20000000-0000-0000-0000-000000000002"),item=UUID.fromString("30000000-0000-0000-0000-000000000003");
    final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(new ApplicationPrincipal(user,"member@example.test",true),null,List.of());
    @Test void documentsPlanSnapshotAndWrites() throws Exception {
        var summary=new Summary(1,0,0,0,1,1,0.0,null,null,ForecastState.INCOMPLETE,false);
        var itemResponse=new ItemResponse(item,project,null,PlanItemKind.TASK,"Implement API",null,null,PlanItemState.IN_PROGRESS,null,null,null,10,List.of("api"),0,user,Instant.parse("2026-09-27T01:00:00Z"),List.of(),List.of(),List.of(),summary);
        var snapshot=new Snapshot(project,0,null,null,LocalDate.of(2026,9,27),List.of(itemResponse),List.of(item),summary,true,1);
        when(plans.snapshot(eq(project),eq(user),any(),any(),anyBoolean(),any(),anyBoolean(),any(),any(),any(),any(),any())).thenReturn(snapshot);
        when(plans.updateTarget(eq(project),any(),eq(user))).thenReturn(snapshot);
        mvc.perform(get("/api/v1/projects/{projectId}/plan",project).with(authentication(auth)))
            .andExpect(status().isOk()).andDo(document("project-plan-snapshot",resource(ResourceSnippetParameters.builder().queryParameters(
                parameterWithName("kind").type(SimpleType.STRING).optional().description("Comma-separated plan item kinds"),
                parameterWithName("state").type(SimpleType.STRING).optional().description("Exact item state"),
                parameterWithName("includeCancelled").type(SimpleType.BOOLEAN).optional().description("Include cancelled items"),
                parameterWithName("assigneeId").type(SimpleType.STRING).optional().description("Assignee filter"),
                parameterWithName("unassigned").type(SimpleType.BOOLEAN).optional().description("Only unassigned items"),
                parameterWithName("scopeId").type(SimpleType.STRING).optional().description("Selected item subtree"),
                parameterWithName("from").type(SimpleType.STRING).optional().description("Inclusive date lower bound"),
                parameterWithName("to").type(SimpleType.STRING).optional().description("Inclusive date upper bound"),
                parameterWithName("q").type(SimpleType.STRING).optional().description("Title or description search"),
                parameterWithName("label").type(SimpleType.STRING).optional().description("Label filter")
            ).responseFields(snapshotFields()).build())));
        mvc.perform(patch("/api/v1/projects/{projectId}/plan",project).with(authentication(auth)).with(csrf()).contentType("application/json").content("{\"targetStart\":\"2026-10-01\",\"targetEnd\":null,\"rowVersion\":0,\"reason\":\"baseline\"}"))
            .andExpect(status().isOk()).andDo(document("project-plan-target-update",resource(ResourceSnippetParameters.builder().requestFields(targetWriteFields()).responseFields(snapshotFields()).build())));
        when(plans.create(eq(project),any(),eq(user))).thenReturn(itemResponse);when(plans.update(eq(project),eq(item),any(),eq(user))).thenReturn(itemResponse);
        mvc.perform(post("/api/v1/projects/{projectId}/plan/items",project).with(authentication(auth)).with(csrf()).contentType("application/json").content("{\"parentId\":null,\"kind\":\"TASK\",\"title\":\"Implement API\",\"description\":null,\"assigneeId\":null,\"state\":\"IN_PROGRESS\",\"targetStart\":null,\"targetEnd\":null,\"deadline\":null,\"sortOrder\":10,\"labels\":[],\"rowVersion\":null,\"predecessorIds\":[],\"requestId\":\"30000000-0000-0000-0000-000000000004\",\"reason\":null}"))
            .andExpect(status().isOk()).andDo(document("project-plan-item-create",resource(ResourceSnippetParameters.builder().requestFields(itemWriteFields()).responseFields(itemFields()).build())));
        mvc.perform(patch("/api/v1/projects/{projectId}/plan/items/{itemId}",project,item).with(authentication(auth)).with(csrf()).contentType("application/json").content("{\"parentId\":null,\"kind\":\"TASK\",\"title\":\"Implement API\",\"description\":null,\"assigneeId\":null,\"state\":\"DONE\",\"targetStart\":null,\"targetEnd\":null,\"deadline\":null,\"sortOrder\":10,\"labels\":[],\"rowVersion\":0,\"predecessorIds\":[],\"requestId\":null,\"reason\":null}"))
            .andExpect(status().isOk()).andDo(document("project-plan-item-update",resource(ResourceSnippetParameters.builder().requestFields(itemWriteFields()).responseFields(itemFields()).build())));
        when(plans.history(eq(project),eq(item),anyInt(),anyInt(),eq(user))).thenReturn(new HistoryPage(List.of(new HistoryEntry(UUID.randomUUID(),user,Instant.parse("2026-09-27T01:00:00Z"),1,"created",Map.of(),Map.of("title","Implement API"))),false,0,50));
        mvc.perform(get("/api/v1/projects/{projectId}/plan/items/{itemId}/history",project,item).with(authentication(auth)))
            .andExpect(status().isOk()).andDo(document("project-plan-item-history",resource(ResourceSnippetParameters.builder().responseFields(historyFields()).build())));
    }
    private static FieldDescriptor fd(String path,JsonFieldType type,String description){return fieldWithPath(path).type(type).description(description);}
    private static FieldDescriptor nfd(String path,JsonFieldType type,String description){return fieldWithPath(path).optional().type(type).description(description);}
    private static FieldDescriptor[] snapshotFields(){var f=new ArrayList<FieldDescriptor>(Arrays.asList(fd("projectId",JsonFieldType.STRING,"Project identifier"),fd("rowVersion",JsonFieldType.NUMBER,"Snapshot version"),nfd("targetStart",JsonFieldType.STRING,"Manual start"),nfd("targetEnd",JsonFieldType.STRING,"Manual end"),fd("asOfDate",JsonFieldType.STRING,"UTC aggregate date"),fd("items",JsonFieldType.ARRAY,"Complete item list"),fd("items[].id",JsonFieldType.STRING,"Item identifier"),fd("items[].projectId",JsonFieldType.STRING,"Project identifier"),nfd("items[].parentId",JsonFieldType.STRING,"Parent item"),fd("items[].kind",JsonFieldType.STRING,"Item kind"),fd("items[].title",JsonFieldType.STRING,"Title"),nfd("items[].description",JsonFieldType.STRING,"Description"),nfd("items[].assigneeId",JsonFieldType.STRING,"Assignee"),fd("items[].state",JsonFieldType.STRING,"State"),nfd("items[].targetStart",JsonFieldType.STRING,"Target start"),nfd("items[].targetEnd",JsonFieldType.STRING,"Target end"),nfd("items[].deadline",JsonFieldType.STRING,"Deadline"),fd("items[].sortOrder",JsonFieldType.NUMBER,"Sort order"),fd("items[].labels",JsonFieldType.ARRAY,"Labels"),fd("items[].rowVersion",JsonFieldType.NUMBER,"Version"),fd("items[].createdBy",JsonFieldType.STRING,"Creator"),fd("items[].updatedAt",JsonFieldType.STRING,"Updated timestamp"),fd("items[].predecessorIds",JsonFieldType.ARRAY,"Predecessors"),fd("items[].successorIds",JsonFieldType.ARRAY,"Successors"),fd("items[].blockerIds",JsonFieldType.ARRAY,"Blockers"),fd("items[].summary",JsonFieldType.OBJECT,"Item summary"),fd("matchedIds",JsonFieldType.ARRAY,"IDs matching filters"),fd("summary",JsonFieldType.OBJECT,"Project summary"),fd("complete",JsonFieldType.BOOLEAN,"Full snapshot marker"),fd("totalCount",JsonFieldType.NUMBER,"Total count")));f.addAll(Arrays.asList(summaryFields("items[].summary.")));f.addAll(Arrays.asList(summaryFields("summary.")));return f.toArray(FieldDescriptor[]::new);}
    private static FieldDescriptor[] summaryFields(String prefix){return new FieldDescriptor[]{fd(prefix+"taskCount",JsonFieldType.NUMBER,"Task count"),fd(prefix+"doneCount",JsonFieldType.NUMBER,"Done count"),fd(prefix+"blockedCount",JsonFieldType.NUMBER,"Blocked count"),fd(prefix+"overdueCount",JsonFieldType.NUMBER,"Overdue count"),fd(prefix+"unplannedCount",JsonFieldType.NUMBER,"Unplanned count"),fd(prefix+"unassignedCount",JsonFieldType.NUMBER,"Unassigned count"),nfd(prefix+"progressPercent",JsonFieldType.NUMBER,"Progress percent"),nfd(prefix+"forecastStart",JsonFieldType.STRING,"Forecast start"),nfd(prefix+"forecastEnd",JsonFieldType.STRING,"Forecast end"),fd(prefix+"forecastState",JsonFieldType.STRING,"Forecast state"),fd(prefix+"outsideTarget",JsonFieldType.BOOLEAN,"Outside target")};}
    private static FieldDescriptor[] itemWriteFields(){return new FieldDescriptor[]{nfd("parentId",JsonFieldType.STRING,"Parent item"),fd("kind",JsonFieldType.STRING,"Item kind"),fd("title",JsonFieldType.STRING,"Title"),nfd("description",JsonFieldType.STRING,"Description"),nfd("assigneeId",JsonFieldType.STRING,"Assignee"),fd("state",JsonFieldType.STRING,"State"),nfd("targetStart",JsonFieldType.STRING,"Target start"),nfd("targetEnd",JsonFieldType.STRING,"Target end"),nfd("deadline",JsonFieldType.STRING,"Deadline"),fd("sortOrder",JsonFieldType.NUMBER,"Sort order"),fd("labels",JsonFieldType.ARRAY,"Labels"),nfd("rowVersion",JsonFieldType.NUMBER,"Expected version"),fd("predecessorIds",JsonFieldType.ARRAY,"Predecessors"),nfd("requestId",JsonFieldType.STRING,"Idempotency key"),nfd("reason",JsonFieldType.STRING,"Change reason")};}
    private static FieldDescriptor[] targetWriteFields(){return new FieldDescriptor[]{nfd("targetStart",JsonFieldType.STRING,"Manual start"),nfd("targetEnd",JsonFieldType.STRING,"Manual end"),fd("rowVersion",JsonFieldType.NUMBER,"Expected version"),nfd("reason",JsonFieldType.STRING,"Change reason")};}
    private static FieldDescriptor[] itemFields(){var fields=new ArrayList<FieldDescriptor>(Arrays.asList(fd("id",JsonFieldType.STRING,"Item identifier"),fd("projectId",JsonFieldType.STRING,"Project identifier"),nfd("parentId",JsonFieldType.STRING,"Parent item"),fd("kind",JsonFieldType.STRING,"Item kind"),fd("title",JsonFieldType.STRING,"Title"),nfd("description",JsonFieldType.STRING,"Description"),nfd("assigneeId",JsonFieldType.STRING,"Assignee"),fd("state",JsonFieldType.STRING,"State"),nfd("targetStart",JsonFieldType.STRING,"Target start"),nfd("targetEnd",JsonFieldType.STRING,"Target end"),nfd("deadline",JsonFieldType.STRING,"Deadline"),fd("sortOrder",JsonFieldType.NUMBER,"Sort order"),fd("labels",JsonFieldType.ARRAY,"Labels"),fd("rowVersion",JsonFieldType.NUMBER,"Version"),fd("createdBy",JsonFieldType.STRING,"Creator"),fd("updatedAt",JsonFieldType.STRING,"Updated timestamp"),fd("predecessorIds",JsonFieldType.ARRAY,"Predecessors"),fd("successorIds",JsonFieldType.ARRAY,"Successors"),fd("blockerIds",JsonFieldType.ARRAY,"Blockers"),fd("summary",JsonFieldType.OBJECT,"Item summary")));fields.addAll(Arrays.asList(summaryFields("summary.")));return fields.toArray(FieldDescriptor[]::new);}
    private static FieldDescriptor[] historyFields(){return new FieldDescriptor[]{fd("entries",JsonFieldType.ARRAY,"History entries"),fd("entries[].id",JsonFieldType.STRING,"History identifier"),fd("entries[].actorId",JsonFieldType.STRING,"Actor"),fd("entries[].occurredAt",JsonFieldType.STRING,"Occurred timestamp"),fd("entries[].version",JsonFieldType.NUMBER,"Version"),fd("entries[].reason",JsonFieldType.STRING,"Reason"),fd("entries[].before",JsonFieldType.OBJECT,"Before values"),fd("entries[].after",JsonFieldType.OBJECT,"After values"),fd("entries[].after.title",JsonFieldType.STRING,"Changed title"),fd("hasNext",JsonFieldType.BOOLEAN,"More pages"),fd("page",JsonFieldType.NUMBER,"Page index"),fd("limit",JsonFieldType.NUMBER,"Page size")};}
}
