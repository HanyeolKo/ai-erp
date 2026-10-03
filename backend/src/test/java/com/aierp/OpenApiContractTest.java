package com.aierp;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Runs after openapi3, inspecting parsed schemas and examples rather than source text. */
class OpenApiContractTest {
    static final JsonMapper mapper=new JsonMapper();
    static JsonNode document;
    static final String base="/api/v1/projects/{projectId}/schedules";
    @BeforeAll static void readGeneratedDocument() throws Exception {
        try(var reader=Files.newBufferedReader(Path.of("build/api-spec/openapi3.yaml"),StandardCharsets.UTF_8)) {
            document=mapper.valueToTree(new Yaml().load(reader));
        }
    }
    @Test void commandExamplesMatchActualStateTransitions() {
        assertState(example(base,"post","schedule-create"),"DRAFT",0);
        assertState(example(base+"/{id}/confirm","post","schedule-confirm"),"CONFIRMED",1);
        assertState(example(base+"/{id}","patch","schedule-update"),"CONFIRMED",2);
        assertState(example(base+"/{id}/cancel","post","schedule-cancel"),"CANCELLED",2);
        var ack=example(base+"/{id}/acknowledge","post","schedule-acknowledge");
        assertState(ack,"CONFIRMED",1);assertThat(ack.path("participants").get(0).path("acknowledged").asBoolean()).isTrue();
        assertThat(example("/api/v1/invitations/{token}/accept","post","invitation-accept").path("status").asText()).isEqualTo("ACCEPTED");
        assertThat(example("/api/v1/invitations/{token}/reject","post","invitation-reject").path("status").asText()).isEqualTo("REJECTED");
    }
    @Test void dashboardArraysHaveStrictItemAndParticipantSchemas() {
        var schema=responseSchema("/api/v1/projects/{projectId}/dashboard","get");
        for(var name:List.of("upcomingSchedules","actionQueue")) {
            var array=schema.path("properties").path(name);assertThat(array.path("type").asText()).isEqualTo("array");
            var item=resolve(array.path("items"));
            assertThat(item.path("properties").propertyNames()).contains("id","projectId","status","rowVersion","businessRevision","participants","description");
            assertThat(item.path("required").valueStream().map(JsonNode::asText).toList()).contains("id","status","businessRevision","participants");
            var participant=resolve(item.path("properties").path("participants").path("items"));
            assertThat(participant.path("properties").propertyNames()).contains("memberUserId","externalEmail","acknowledged");
            assertThat(participant.path("properties").path("acknowledged").path("type").asText()).isEqualTo("boolean");
        }
    }
    @Test void trueNullableFieldsRemainNullableInGeneratedSchemas() {
        assertNullable(responseSchema(base+"/{id}","get"),"description");
        var projection=responseSchema(base+"/{id}/calendar","get");
        assertNullable(projection,"id");assertNullable(projection,"retryClassification");
        assertNullable(responseSchema(base+"/{id}/calendar/retry","post"),"retryClassification");
        var notification=resolve(responseSchema("/api/v1/notifications","get").path("items"));
        assertNullable(notification,"readAt");assertNullable(notification,"link");
        assertNullable(responseSchema("/api/v1/system/configuration","get"),"loginUrl");
    }
    @Test void projectCreationOptionsAndCreateResponseSchemasAreExplicit() {
        var options=responseSchema("/api/v1/projects/creation-options","get");
        assertThat(options.path("type").asText()).isEqualTo("array");
        var option=resolve(options.path("items"));
        assertThat(option.path("required").valueStream().map(JsonNode::asText).toList()).contains("id","name","canCreate");
        assertThat(option.path("properties").path("canCreate").path("type").asText()).isEqualTo("boolean");
        assertNullable(option,"reason");
        var create=responseSchema("/api/v1/projects","post");
        assertThat(create.path("required").valueStream().map(JsonNode::asText).toList()).contains("id","groupId","name","role");
        assertThat(create.path("properties").path("role").path("type").asText()).isEqualTo("string");
        assertThat(example("/api/v1/projects","post","project-create").path("role").asText()).isEqualTo("MANAGER");
        var examples=example("/api/v1/projects/creation-options","get","project-creation-options");
        assertThat(examples.isArray()).isTrue();
        assertThat(examples.size()).isPositive();
        var createInput=operation("/api/v1/projects","post").path("requestBody").path("content").path("application/json;charset=UTF-8").path("schema");
        assertThat(createInput.has("$ref")).isTrue();
        assertThat(document.toString()).contains("requestId");
    }
    @Test void sharedInvitationRoutesAndSafeNullableStatesAreGenerated() {
        assertThat(document.path("paths").has("/api/v1/projects/{projectId}/share-invitation")).isTrue();
        assertThat(operation("/api/v1/projects/{projectId}/share-invitation","delete").path("responses").has("204")).isTrue();
        var manager=responseSchema("/api/v1/projects/{projectId}/share-invitation","get");
        assertNullable(manager,"code"); assertNullable(manager,"expiresAt");
        var preview=responseSchema("/api/v1/project-invitations/{code}","get");
        assertNullable(preview,"projectId"); assertNullable(preview,"projectName"); assertNullable(preview,"inviterName"); assertNullable(preview,"role");
    }
    @Test void projectManagementPublishesTypedWritesWorkRelationsHistoryAndErrors() {
        var definition="/api/v1/projects/{projectId}/management";
        assertThat(operation(definition,"get").path("responses").has("200")).isTrue();
        assertThat(operation(definition,"patch").path("responses").has("200")).isTrue();
        assertThat(required(requestSchema(definition,"patch"))).contains("rowVersion","requestId");
        assertThat(resolve(requestSchema(definition,"patch").path("properties").path("health")).path("enum").toString()).contains("ON_TRACK","WATCH","AT_RISK");
        var task="/api/v1/projects/{projectId}/management/tasks/{itemId}";
        assertThat(operation(task,"patch").path("responses").has("200")).isTrue();
        assertThat(required(requestSchema(task,"patch"))).contains("rowVersion","requestId");
        var work=resolve(responseSchema("/api/v1/me/work","get"));
        assertThat(work.path("properties").propertyNames()).contains("items","observedCount","asOfDate","timeZone");
        var workItem=resolve(work.path("properties").path("items").path("items"));
        assertThat(workItem.path("properties").propertyNames()).contains("item","execution","blockingReason");
        var item=resolve(workItem.path("properties").path("item"));
        assertThat(item.path("properties").propertyNames()).contains("predecessorIds","successorIds","blockerIds","summary");
        assertThat(operation("/api/v1/me/work","get").path("parameters").valueStream().map(p->p.path("name").asText()).toList())
                .containsExactlyInAnyOrder("range","timeZone","projectId","assignee","page","limit","q");
        assertThat(operation("/api/v1/me/work","get").path("parameters").valueStream()
                .filter(p->p.path("name").asText().equals("range")).findFirst().orElseThrow().path("schema").path("enum").toString())
                .contains("today","week","all");
        assertThat(operation("/api/v1/me/work","get").path("parameters").valueStream()
                .filter(p->p.path("name").asText().equals("assignee")).findFirst().orElseThrow().path("schema").path("enum").toString())
                .contains("mine","unassigned");
        var history=resolve(responseSchema("/api/v1/projects/{projectId}/management/history","get"));
        var entry=resolve(history.path("properties").path("entries").path("items"));
        assertThat(entry.path("properties").propertyNames()).contains("before","after","resourceType","resourceId");
        assertThat(operation("/api/v1/projects/{projectId}/management/history","get").path("parameters").valueStream().map(p->p.path("name").asText()).toList())
                .containsExactlyInAnyOrder("projectId","resourceType","resourceId","page","limit");
        assertThat(operation("/api/v1/projects/{projectId}/management/tasks","get").path("parameters").valueStream().map(p->p.path("name").asText()).toList())
                .containsExactlyInAnyOrder("projectId","itemIds");
        assertThat(resolve(requestSchema(task,"patch")).path("properties").path("priority").path("enum").toString()).contains("HIGH","MEDIUM","LOW");
        assertThat(resolve(responseSchema(task,"patch")).path("properties").path("priority").path("enum").toString()).contains("HIGH","MEDIUM","LOW");
        assertThat(resolve(responseSchema(definition,"get")).path("properties").path("health").path("enum").toString()).contains("ON_TRACK","WATCH","AT_RISK");
        assertThat(example(definition,"patch","project-management-definition-patch").path("health").asText()).isEqualTo("ON_TRACK");
        for (var status: List.of("400","403","404","409")) assertThat(operation(definition,"patch").path("responses").has(status)).as("definition PATCH %s",status).isTrue();
        for (var status: List.of("400","403","404","409")) assertThat(operation(task,"patch").path("responses").has(status)).as("task PATCH %s",status).isTrue();
        assertThat(operation("/api/v1/projects/{projectId}/management/requests/{requestId}","get").path("responses").has("404")).isTrue();
        var workOperation=operation("/api/v1/me/work","get");
        assertQuery(workOperation,"range",false,"today","string");
        assertQuery(workOperation,"timeZone",false,"UTC","string");
        assertQuery(workOperation,"assignee",false,"mine","string");
        assertQuery(workOperation,"page",false,"0","integer");
        assertThat(queryParameter(workOperation,"page").path("schema").path("minimum").asInt()).isEqualTo(0);
        assertQuery(workOperation,"limit",false,"50","integer");
        assertThat(queryParameter(workOperation,"limit").path("schema").path("minimum").asInt()).isEqualTo(1);
        assertThat(queryParameter(workOperation,"limit").path("schema").path("maximum").asInt()).isEqualTo(100);
        assertQuery(workOperation,"projectId",false,null,"string");
        assertQuery(workOperation,"q",false,null,"string");
        var historyOperation=operation("/api/v1/projects/{projectId}/management/history","get");
        assertQuery(historyOperation,"resourceType",true,null,"string");
        assertQuery(historyOperation,"resourceId",true,null,"string");
        assertQuery(historyOperation,"page",false,"0","integer");
        assertThat(queryParameter(historyOperation,"page").path("schema").path("minimum").asInt()).isEqualTo(0);
        assertQuery(historyOperation,"limit",false,"50","integer");
        assertThat(queryParameter(historyOperation,"limit").path("schema").path("minimum").asInt()).isEqualTo(1);
        assertThat(queryParameter(historyOperation,"limit").path("schema").path("maximum").asInt()).isEqualTo(100);
        var workSchema=resolve(responseSchema("/api/v1/me/work","get"));
        assertThat(workSchema.path("properties").path("complete").path("description").asText()).contains("10000");
    }
    @Test void queryBoundsArePartOfTheGeneratedContract() {
        for(var path:List.of("/api/v1/projects","/api/v1/projects/creation-options","/api/v1/projects/{projectId}/members","/api/v1/notifications",base)) {
            var query=operation(path,"get").path("parameters").valueStream().filter(p->p.path("in").asText().equals("query")).toList();
            assertThat(query).extracting(p->p.path("name").asText()).contains("page","limit");
            assertThat(query.stream().filter(p->p.path("name").asText().equals("limit")).findFirst().orElseThrow().path("schema").path("default").asInt()).isEqualTo(100);
        }
        assertThat(operation(base,"get").path("parameters").valueStream().map(p->p.path("name").asText()).toList()).contains("from","to");
        assertThat(operation(base+"/{id}","get").path("parameters").valueStream().map(p->p.path("name").asText()).toList()).contains("historyLimit");
    }
    @Test void staleConflictBelongsToAWriteAndHttpFailuresUseProblemMediaType() {
        assertThat(operation(base+"/{id}/cancel","post").path("responses").path("409").path("content").has("application/problem+json")).isTrue();
        assertThat(operation(base+"/{id}","get").path("responses").has("409")).isFalse();
        for(var failure:List.of(new Failure(base+"/{id}","put","405"),new Failure(base,"post","415"),new Failure(base+"/{id}","get","500"))) {
            var content=operation(failure.path,failure.method).path("responses").path(failure.status).path("content");
            assertThat(content.has("application/problem+json")).isTrue();
            var schema=resolve(content.path("application/problem+json").path("schema"));
            assertThat(schema.path("properties").propertyNames()).contains("code","traceId","fieldErrors");
        }
    }
    @Test void scheduleWorkspacePublishesAllElevenTypedOperations() {
        String w="/api/v1/projects/{projectId}/schedule-workspace";
        for(var route:List.of(new Failure(w,"get","200"),new Failure(w+"/properties","post","200"),new Failure(w+"/properties/{id}","patch","200"),new Failure(w+"/views","post","200"),new Failure(w+"/views/{id}","patch","200"),new Failure(w+"/dashboard","patch","200"),new Failure(w+"/query","post","200"),new Failure(w+"/records/{id}","get","200"),new Failure(w+"/records","post","200"),new Failure(w+"/records/{id}","patch","200"),new Failure(w+"/records/{id}/values","patch","200"))) {
            assertThat(operation(route.path,route.method).path("responses").has(route.status)).as(route.method+" "+route.path).isTrue();
        }
        var queryRequest=requestSchema(w+"/query","post");assertThat(required(queryRequest)).contains("config").doesNotContain("from","to","page","size");assertScalarUnion(queryRequest.path("properties").path("config").path("properties").path("filters").path("items").path("properties").path("value"));
        var query=resolve(responseSchema(w+"/query","post"));assertThat(query.path("properties").propertyNames()).contains("records","groups","total","hasMore","queriedAt");var record=resolve(query.path("properties").path("records").path("items"));assertTypedMap(record.path("properties").path("values"));assertThat(record.path("properties").path("schedule").path("properties").propertyNames()).contains("id","status","rowVersion","businessRevision","participants","changes");var group=resolve(query.path("properties").path("groups").path("items"));assertNullable(group,"optionId");assertNullable(group,"color");
        var workspace=resolve(responseSchema(w,"get"));var property=resolve(workspace.path("properties").path("properties").path("items"));assertThat(workspace.path("properties").propertyNames()).contains("properties","views","dashboardViewId","dashboardRowVersion");assertNullable(workspace,"dashboardViewId");assertThat(property.path("properties").propertyNames()).contains("id","type","rowVersion","options");
        var propertyCreate=requestSchema(w+"/properties","post");assertThat(required(propertyCreate)).contains("name","type").doesNotContain("position","archived","options","rowVersion");var propertyPatch=requestSchema(w+"/properties/{id}","patch");assertThat(required(propertyPatch)).contains("rowVersion").doesNotContain("name","type","position","archived","options");
        var dashboardRequest=requestSchema(w+"/dashboard","patch");assertThat(required(dashboardRequest)).contains("rowVersion").doesNotContain("viewId");assertNullable(dashboardRequest,"viewId");assertNullable(responseSchema(w+"/dashboard","patch"),"dashboardViewId");
        var createRecord=requestSchema(w+"/records","post");assertThat(required(createRecord)).contains("schedule");assertThat(required(createRecord.path("properties").path("schedule"))).doesNotContain("rowVersion");assertTypedMap(createRecord.path("properties").path("values"));var updateRecord=requestSchema(w+"/records/{id}","patch");assertThat(required(updateRecord.path("properties").path("schedule"))).contains("rowVersion");assertTypedMap(updateRecord.path("properties").path("values"));var valuesPatch=requestSchema(w+"/records/{id}/values","patch");assertThat(required(valuesPatch)).contains("rowVersion","values");assertTypedMap(valuesPatch.path("properties").path("values"));
    }
    private record Failure(String path,String method,String status) {}
    private static void assertState(JsonNode example,String state,long revision) {
        assertThat(example.path("status").asText()).isEqualTo(state);assertThat(example.path("businessRevision").asLong()).isEqualTo(revision);
    }
    private static void assertNullable(JsonNode schema,String field) {assertThat(schema.path("properties").path(field).path("nullable").asBoolean()).as(field).isTrue();}
    private static JsonNode operation(String path,String method) {return document.path("paths").path(path).path(method);}
    private static JsonNode requestSchema(String path,String method) {return resolve(operation(path,method).path("requestBody").path("content").path("application/json;charset=UTF-8").path("schema"));}
    private static JsonNode responseSchema(String path,String method) {return resolve(operation(path,method).path("responses").path("200").path("content").path("application/json").path("schema"));}
    private static List<String> required(JsonNode schema){return schema.path("required").valueStream().map(JsonNode::asText).toList();}
    private static void assertQuery(JsonNode operation,String name,boolean required,String defaultValue,String type){
        var parameter=queryParameter(operation,name);
        assertThat(parameter.path("required").asBoolean()).as(name+" required").isEqualTo(required);
        assertThat(parameter.path("schema").path("type").asText()).as(name+" type").isEqualTo(type);
        if(defaultValue!=null) assertThat(parameter.path("schema").path("default").asText()).as(name+" default").isEqualTo(defaultValue);
    }
    private static JsonNode queryParameter(JsonNode operation,String name){return operation.path("parameters").valueStream().filter(p->p.path("in").asText().equals("query")&&p.path("name").asText().equals(name)).findFirst().orElseThrow();}
    private static void assertTypedMap(JsonNode schema){assertThat(schema.path("type").asText()).isEqualTo("object");assertScalarUnion(schema.path("additionalProperties"));}
    private static void assertScalarUnion(JsonNode schema){assertThat(schema.has("type")).isFalse();assertThat(schema.has("nullable")).isFalse();var alternatives=schema.path("oneOf").valueStream().toList();assertThat(alternatives).extracting(n->n.path("type").asText()).containsExactlyInAnyOrder("string","number","boolean");assertThat(alternatives.stream().filter(n->n.path("nullable").asBoolean()).count()).isEqualTo(1);assertThat(alternatives.stream().filter(n->n.path("nullable").asBoolean()).findFirst().orElseThrow().path("type").asText()).isEqualTo("string");}
    private static JsonNode resolve(JsonNode schema) {return schema.has("$ref")?document.at(schema.path("$ref").asText().substring(1)):schema;}
    private static JsonNode example(String path,String method,String name) {
        var value=operation(path,method).path("responses").path("200").path("content").path("application/json").path("examples").path(name).path("value");
        return value.isString()?mapper.valueToTree(new Yaml().load(value.asText())):value;
    }
}
