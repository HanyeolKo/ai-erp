package com.aierp;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
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

    @Test void rejectsUnexpectedRawKeysWithoutChangingTheArtifact() throws Exception {
        var path=writeFixture(5,5);@SuppressWarnings("unchecked") var root=(Map<String,Object>)new Yaml().load(Files.readString(path));@SuppressWarnings("unchecked") var all=(List<Map<String,Object>>)schema(root,"api-v1-schedule-workspace-fixture").get("all");all.getFirst().put("items",Map.of("type","string"));Files.writeString(path,new Yaml().dump(root));var before=Files.readString(path);assertThatThrownBy(()->OpenApiWorkspaceSchemaNormalizer.main(new String[]{path.toString()})).isInstanceOf(IllegalStateException.class).hasMessageContaining("Unexpected raw filter value schema");assertThat(Files.readString(path)).isEqualTo(before);
    }

    private Path writeFixture(int filterCount,int valueCount)throws Exception {
        var schemas=new ArrayList<Object>();
        for(int i=0;i<filterCount;i++)schemas.add(new LinkedHashMap<>(Map.of("type","object","description","String, number, boolean, or null according to field/operator","nullable",true)));
        for(int i=0;i<valueCount;i++){var scalar=new LinkedHashMap<String,Object>();scalar.put("type","object");scalar.put("description",i%2==0?"Typed scalar; null clears":"Typed scalar or null");scalar.put("nullable",true);var valueMap=new LinkedHashMap<String,Object>();valueMap.put("type","object");valueMap.put("description",i==0?"records[].values":"values");valueMap.put("properties",new LinkedHashMap<>(Map.of("*",scalar)));schemas.add(valueMap);}
        var path=temporary.resolve("openapi3.yaml");var allSchemas=new LinkedHashMap<String,Object>();allSchemas.put("api-v1-schedule-workspace-fixture",Map.of("all",schemas));var unrelated=new LinkedHashMap<String,Object>();unrelated.put("type","object");unrelated.put("description","unrelated-marker");unrelated.put("example",null);allSchemas.put("unrelated-schema",unrelated);Files.writeString(path,new Yaml().dump(Map.of("components",Map.of("schemas",allSchemas))));return path;
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> schema(Map<String,Object> root,String name){return (Map<String,Object>)((Map<String,Object>)((Map<String,Object>)root.get("components")).get("schemas")).get(name);}
}
