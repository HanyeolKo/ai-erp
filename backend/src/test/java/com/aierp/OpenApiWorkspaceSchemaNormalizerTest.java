package com.aierp;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

class OpenApiWorkspaceSchemaNormalizerTest {
    @TempDir Path temporary;

    @Test void normalizesExactGeneratedShapesAndIsIdempotent() throws Exception {
        var path=writeFixture(5,5);OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()});OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()});
        var text=Files.readString(path);assertThat(text).doesNotContain("'*':").contains("additionalProperties:").contains("oneOf:");
        @SuppressWarnings("unchecked") var root=(Map<String,Object>)new Yaml().load(text);assertThat(root).isNotEmpty();assertThat(text).contains("nullable: true");var unrelated=schema(root,"unrelated-schema");assertThat(unrelated).containsEntry("type","object").containsEntry("description","unrelated-marker");assertThat(unrelated).containsKey("example");assertThat(unrelated.get("example")).isNull();
    }

    @Test void rejectsUnexpectedGeneratorShapeCount() throws Exception {
        var path=writeFixture(4,5);var before=Files.readString(path);assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("filters=4, valueMaps=5");assertThat(Files.readString(path)).isEqualTo(before);
    }
    @Test void rejectsMissingManagementBoundMetadataWithoutChangingArtifact() throws Exception {
        var path=writeFixture(5,5);Files.delete(path.getParent().resolve("generated-snippets/project-management-history/resource.json"));var before=Files.readString(path);
        assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("Missing generated REST Docs resource");
        assertThat(Files.readString(path)).isEqualTo(before);
    }

    @Test void rejectsUnexpectedRawKeysWithoutChangingTheArtifact() throws Exception {
        var path=writeFixture(5,5);@SuppressWarnings("unchecked") var root=(Map<String,Object>)new Yaml().load(Files.readString(path));@SuppressWarnings("unchecked") var all=(List<Map<String,Object>>)schema(root,"api-v1-schedule-workspace-fixture").get("all");all.getFirst().put("items",Map.of("type","string"));Files.writeString(path,new Yaml().dump(root));var before=Files.readString(path);assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unexpected raw filter value schema");assertThat(Files.readString(path)).isEqualTo(before);
    }

    @Test void rejectsMetadataDefaultMismatchWithoutChangingTheArtifact() throws Exception {
        var path=writeFixture(5,5);mutateMetadata(path,"project-management-work","page",record->record.put("default",51));var before=Files.readString(path);
        assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("default");assertThat(Files.readString(path)).isEqualTo(before);
    }

    @Test void rejectsFractionalMetadataBoundsWithoutChangingTheArtifact() throws Exception {
        var path=writeFixture(5,5);mutateMetadata(path,"project-management-work","limit",record->constraint(record,"Min").put("value",1.5));var before=Files.readString(path);
        assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("Non-integer");assertThat(Files.readString(path)).isEqualTo(before);
    }

    @Test void rejectsConflictingDuplicateMetadataBoundsWithoutChangingTheArtifact() throws Exception {
        var path=writeFixture(5,5);mutateMetadata(path,"project-management-work","limit",record->{var attributes=(Map<String,Object>)record.get("attributes");var constraints=(List<Object>)attributes.get("validationConstraints");constraints.add(Map.of("name","Min","configuration",Map.of("value",2)));});var before=Files.readString(path);
        assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("Conflicting Min");assertThat(Files.readString(path)).isEqualTo(before);
    }

    @Test void rejectsFractionalGeneratedBoundsWithoutChangingTheArtifact() throws Exception {
        var path=writeFixture(5,5);mutateParameter(path,"/api/v1/me/work","page",parameter->parameterSchema(parameter).put("minimum",0.5));var before=Files.readString(path);
        assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("Non-integer");assertThat(Files.readString(path)).isEqualTo(before);
    }

    @Test void rejectsGeneratedDefaultMismatchWithoutChangingTheArtifact() throws Exception {
        var path=writeFixture(5,5);mutateParameter(path,"/api/v1/me/work","page",parameter->parameterSchema(parameter).put("default",1));var before=Files.readString(path);
        assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("default");assertThat(Files.readString(path)).isEqualTo(before);
    }

    @Test void rejectsLimitMetadataDefault51Against50WithoutChangingArtifact() throws Exception {
        var path=writeFixture(5,5);mutateMetadata(path,"project-management-work","limit",record->record.put("default",51));var before=Files.readAllBytes(path);
        assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("default");assertThat(Files.readAllBytes(path)).containsExactly(before);
    }

    @Test void rejectsFractionalMetadataMaxWithoutChangingArtifact() throws Exception {
        var path=writeFixture(5,5);mutateMetadata(path,"project-management-work","limit",record->constraint(record,"Max").put("value",100.5));var before=Files.readAllBytes(path);
        assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("Non-integer");assertThat(Files.readAllBytes(path)).containsExactly(before);
    }

    @Test void rejectsFractionalMetadataDefaultWithoutChangingArtifact() throws Exception {
        var path=writeFixture(5,5);mutateMetadata(path,"project-management-work","page",record->record.put("default",0.5));var before=Files.readAllBytes(path);
        assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("Non-integer");assertThat(Files.readAllBytes(path)).containsExactly(before);
    }

    @Test void rejectsConflictingDuplicateMetadataMaxWithoutChangingArtifact() throws Exception {
        var path=writeFixture(5,5);mutateMetadata(path,"project-management-work","limit",record->{var attributes=(Map<String,Object>)record.get("attributes");var constraints=(List<Object>)attributes.get("validationConstraints");constraints.add(Map.of("name","Max","configuration",Map.of("value",99)));});var before=Files.readAllBytes(path);
        assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("Conflicting Max");assertThat(Files.readAllBytes(path)).containsExactly(before);
    }

    @Test void rejectsFractionalGeneratedMaximum100_5WithoutChangingArtifact() throws Exception {
        var path=writeFixture(5,5);mutateParameter(path,"/api/v1/me/work","limit",parameter->parameterSchema(parameter).put("maximum",100.5));var before=Files.readAllBytes(path);
        assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("Non-integer");assertThat(Files.readAllBytes(path)).containsExactly(before);
    }

    @Test void rejectsFractionalGeneratedDefaultWithoutChangingArtifact() throws Exception {
        var path=writeFixture(5,5);mutateParameter(path,"/api/v1/me/work","page",parameter->parameterSchema(parameter).put("default",0.5));var before=Files.readAllBytes(path);
        assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("Non-integer");assertThat(Files.readAllBytes(path)).containsExactly(before);
    }

    @Test void rejectsGeneratedNonIntegerTypeWithoutChangingArtifact() throws Exception {
        var path=writeFixture(5,5);mutateParameter(path,"/api/v1/me/work","page",parameter->parameterSchema(parameter).put("type","string"));var before=Files.readAllBytes(path);
        assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unexpected management parameter metadata");assertThat(Files.readAllBytes(path)).containsExactly(before);
    }

    @Test void rejectsGeneratedRequiredTrueWithoutChangingArtifact() throws Exception {
        var path=writeFixture(5,5);mutateParameter(path,"/api/v1/me/work","page",parameter->parameter.put("required",true));var before=Files.readAllBytes(path);
        assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unexpected management parameter metadata");assertThat(Files.readAllBytes(path)).containsExactly(before);
    }

    @Test void rejectsOneMissingMetadataRecordWithoutChangingArtifact() throws Exception {
        var path=writeFixture(5,5);removeMetadata(path,"project-management-work","page");var before=Files.readAllBytes(path);
        assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("found 0");assertThat(Files.readAllBytes(path)).containsExactly(before);
    }

    @Test void rejectsOneMissingGeneratedQueryParameterWithoutChangingArtifact() throws Exception {
        var path=writeFixture(5,5);removeParameter(path,"/api/v1/me/work","page");var before=Files.readAllBytes(path);
        assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("Missing OpenAPI query parameter");assertThat(Files.readAllBytes(path)).containsExactly(before);
    }

    private Path writeFixture(int filterCount,int valueCount)throws Exception {
        var schemas=new ArrayList<Object>();
        for(int i=0;i<filterCount;i++)schemas.add(new LinkedHashMap<>(Map.of("type","object","description","String, number, boolean, or null according to field/operator","nullable",true)));
        for(int i=0;i<valueCount;i++){var scalar=new LinkedHashMap<String,Object>();scalar.put("type","object");scalar.put("description",i%2==0?"Typed scalar; null clears":"Typed scalar or null");scalar.put("nullable",true);var valueMap=new LinkedHashMap<String,Object>();valueMap.put("type","object");valueMap.put("description",i==0?"records[].values":"values");valueMap.put("properties",new LinkedHashMap<>(Map.of("*",scalar)));schemas.add(valueMap);}
        var path=temporary.resolve("openapi3.yaml");var allSchemas=new LinkedHashMap<String,Object>();allSchemas.put("api-v1-schedule-workspace-fixture",Map.of("all",schemas));var unrelated=new LinkedHashMap<String,Object>();unrelated.put("type","object");unrelated.put("description","unrelated-marker");unrelated.put("example",null);allSchemas.put("unrelated-schema",unrelated);
        var parameterPaths=new LinkedHashMap<String,Object>();parameterPaths.put("/api/v1/me/work",Map.of("get",Map.of("parameters",List.of(parameter("page",0),parameter("limit",50)))));parameterPaths.put("/api/v1/projects/{projectId}/management/history",Map.of("get",Map.of("parameters",List.of(parameter("page",0),parameter("limit",50)))));
        var root=new LinkedHashMap<String,Object>();root.put("components",Map.of("schemas",allSchemas));root.put("paths",parameterPaths);Files.writeString(path,new Yaml().dump(root));writeBoundResource(path.getParent().resolve("generated-snippets/project-management-work/resource.json"),0,1,100);writeBoundResource(path.getParent().resolve("generated-snippets/project-management-history/resource.json"),0,1,100);return path;
    }
    private static Map<String,Object> parameter(String name,int defaultValue){return Map.of("name",name,"in","query","required",false,"schema",Map.of("type","integer","default",defaultValue));}
    private static void writeBoundResource(Path path,int pageMin,int limitMin,int limitMax)throws Exception{Files.createDirectories(path.getParent());var page=Map.of("name","page","type","INTEGER","optional",true,"default",0,"attributes",Map.of("validationConstraints",List.of(Map.of("name","Min","configuration",Map.of("value",pageMin)))));var limit=Map.of("name","limit","type","INTEGER","optional",true,"default",50,"attributes",Map.of("validationConstraints",List.of(Map.of("name","Min","configuration",Map.of("value",limitMin)),Map.of("name","Max","configuration",Map.of("value",limitMax)))));Files.writeString(path,new Yaml().dump(Map.of("request",Map.of("queryParameters",List.of(page,limit)))));}
    @SuppressWarnings("unchecked") private static void mutateMetadata(Path path,String resource,String name,Consumer<Map<String,Object>> mutation)throws Exception{var file=path.getParent().resolve("generated-snippets").resolve(resource).resolve("resource.json");var root=(Map<String,Object>)new Yaml().load(Files.readString(file));var request=(Map<String,Object>)root.get("request");var parameters=(List<Map<String,Object>>)request.get("queryParameters");var record=parameters.stream().filter(value->name.equals(value.get("name"))).findFirst().orElseThrow();mutation.accept(record);Files.writeString(file,new Yaml().dump(root));}
    @SuppressWarnings("unchecked") private static void removeMetadata(Path path,String resource,String name)throws Exception{var file=path.getParent().resolve("generated-snippets").resolve(resource).resolve("resource.json");var root=(Map<String,Object>)new Yaml().load(Files.readString(file));var request=(Map<String,Object>)root.get("request");var parameters=(List<Map<String,Object>>)request.get("queryParameters");parameters.removeIf(value->name.equals(value.get("name")));Files.writeString(file,new Yaml().dump(root));}
    @SuppressWarnings("unchecked") private static void mutateParameter(Path path,String route,String name,Consumer<Map<String,Object>> mutation)throws Exception{var root=(Map<String,Object>)new Yaml().load(Files.readString(path));var paths=(Map<String,Object>)root.get("paths");var routeMap=(Map<String,Object>)paths.get(route);var get=(Map<String,Object>)routeMap.get("get");var parameters=(List<Map<String,Object>>)get.get("parameters");var parameter=parameters.stream().filter(value->name.equals(value.get("name"))).findFirst().orElseThrow();mutation.accept(parameter);Files.writeString(path,new Yaml().dump(root));}
    @SuppressWarnings("unchecked") private static void removeParameter(Path path,String route,String name)throws Exception{var root=(Map<String,Object>)new Yaml().load(Files.readString(path));var paths=(Map<String,Object>)root.get("paths");var routeMap=(Map<String,Object>)paths.get(route);var get=(Map<String,Object>)routeMap.get("get");var parameters=(List<Map<String,Object>>)get.get("parameters");parameters.removeIf(value->name.equals(value.get("name")));Files.writeString(path,new Yaml().dump(root));}
    @SuppressWarnings("unchecked") private static Map<String,Object> parameterSchema(Map<String,Object> parameter){return (Map<String,Object>)parameter.get("schema");}
    @SuppressWarnings("unchecked") private static Map<String,Object> constraint(Map<String,Object> record,String name){var attributes=(Map<String,Object>)record.get("attributes");var constraints=(List<Map<String,Object>>)attributes.get("validationConstraints");return constraints.stream().filter(value->name.equals(value.get("name"))).map(value->(Map<String,Object>)value.get("configuration")).findFirst().orElseThrow();}
    @SuppressWarnings("unchecked") private static Map<String,Object> schema(Map<String,Object> root,String name){return (Map<String,Object>)((Map<String,Object>)((Map<String,Object>)root.get("components")).get("schemas")).get(name);}
}
