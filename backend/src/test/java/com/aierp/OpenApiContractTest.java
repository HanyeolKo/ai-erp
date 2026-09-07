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
    private record Failure(String path,String method,String status) {}
    private static void assertState(JsonNode example,String state,long revision) {
        assertThat(example.path("status").asText()).isEqualTo(state);assertThat(example.path("businessRevision").asLong()).isEqualTo(revision);
    }
    private static void assertNullable(JsonNode schema,String field) {assertThat(schema.path("properties").path(field).path("nullable").asBoolean()).as(field).isTrue();}
    private static JsonNode operation(String path,String method) {return document.path("paths").path(path).path(method);}
    private static JsonNode responseSchema(String path,String method) {return resolve(operation(path,method).path("responses").path("200").path("content").path("application/json").path("schema"));}
    private static JsonNode resolve(JsonNode schema) {return schema.has("$ref")?document.at(schema.path("$ref").asText().substring(1)):schema;}
    private static JsonNode example(String path,String method,String name) {
        var value=operation(path,method).path("responses").path("200").path("content").path("application/json").path("examples").path(name).path("value");
        return value.isString()?mapper.valueToTree(new Yaml().load(value.asText())):value;
    }
}
