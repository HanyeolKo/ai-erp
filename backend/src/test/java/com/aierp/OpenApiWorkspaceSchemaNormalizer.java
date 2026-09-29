package com.aierp;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/** Repairs two restdocs-api-spec 0.20.1 VARIES limitations in generated workspace schemas. */
public final class OpenApiWorkspaceSchemaNormalizer {
    private static final String FILTER_DESCRIPTION="String, number, boolean, or null according to field/operator";
    private static final Set<String> VALUE_DESCRIPTIONS=Set.of("Typed scalar; null clears","Typed scalar or null");
    private static final int EXPECTED_FILTER_SCHEMAS=5;
    private static final int EXPECTED_VALUE_MAP_SCHEMAS=5;

    private OpenApiWorkspaceSchemaNormalizer() {}

    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Expected generated OpenAPI path");
        var path=Path.of(args[0]);
        if(!Files.isRegularFile(path))throw new IllegalStateException("Generated OpenAPI document is missing: "+path);
        Object loaded;
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){loaded=new Yaml().load(reader);}
        if(!(loaded instanceof Map<?,?> root))throw new IllegalStateException("Generated OpenAPI root is not an object");
        var schemas=map(map(root,"components"),"schemas");var counts=new Counts();for(var entry:schemas.entrySet()){if(entry.getKey().contains("schedule-workspace"))normalize(entry.getValue(),counts);}
        if(counts.filters!=EXPECTED_FILTER_SCHEMAS||counts.valueMaps!=EXPECTED_VALUE_MAP_SCHEMAS)throw new IllegalStateException("Unexpected workspace scalar schema count: filters="+counts.filters+", valueMaps="+counts.valueMaps);
        var rendered=new Yaml().dump(loaded);
        var temporary=path.resolveSibling(path.getFileName()+".tmp");
        Files.writeString(temporary,rendered,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }

    private static void normalize(Object node,Counts counts){
        if(node instanceof Map<?,?> raw){
            @SuppressWarnings("unchecked") var map=(Map<String,Object>)raw;
            if(FILTER_DESCRIPTION.equals(map.get("description"))){normalizeScalar(map,"filter value");counts.filters++;}
            if("object".equals(map.get("type"))){
                var properties=map.get("properties");
                if(properties instanceof Map<?,?> fields&&fields.size()==1&&fields.get("*") instanceof Map<?,?> star&&VALUE_DESCRIPTIONS.contains(star.get("description"))){
                    @SuppressWarnings("unchecked") var scalar=(Map<String,Object>)star;
                    requireRawValueMap(map);requireRawScalar(scalar,"value map");map.remove("properties");map.put("additionalProperties",scalarUnion((String)scalar.get("description")));counts.valueMaps++;
                }else if(map.get("additionalProperties") instanceof Map<?,?> scalar&&VALUE_DESCRIPTIONS.contains(scalar.get("description"))){
                    @SuppressWarnings("unchecked") var normalized=(Map<String,Object>)scalar;requireNormalizedValueMap(map);requireNormalizedScalar(normalized,"value map");counts.valueMaps++;
                }
            }
            for(var value:new ArrayList<>(map.values()))normalize(value,counts);
        }else if(node instanceof Collection<?> collection){for(var value:collection)normalize(value,counts);}
    }

    private static void normalizeScalar(Map<String,Object> schema,String label){
        if(schema.containsKey("oneOf")){requireNormalizedScalar(schema,label);return;}
        requireRawScalar(schema,label);var description=(String)schema.get("description");schema.remove("type");schema.remove("nullable");schema.put("oneOf",scalarTypes());schema.put("description",description);
    }
    private static void requireRawScalar(Map<String,Object> schema,String label){if(!schema.keySet().equals(Set.of("type","description","nullable"))||!"object".equals(schema.get("type"))||!Boolean.TRUE.equals(schema.get("nullable")))throw new IllegalStateException("Unexpected raw "+label+" schema: "+schema);}
    private static void requireNormalizedScalar(Map<String,Object> schema,String label){if(!schema.keySet().equals(Set.of("oneOf","description"))||!scalarTypes().equals(schema.get("oneOf")))throw new IllegalStateException("Unexpected normalized "+label+" schema: "+schema);}
    private static void requireRawValueMap(Map<String,Object> schema){if(!schema.keySet().equals(Set.of("type","properties","description"))||!(schema.get("description") instanceof String description)||!description.endsWith("values"))throw new IllegalStateException("Unexpected raw value-map container: "+schema);}
    private static void requireNormalizedValueMap(Map<String,Object> schema){if(!schema.keySet().equals(Set.of("type","additionalProperties","description"))||!(schema.get("description") instanceof String description)||!description.endsWith("values"))throw new IllegalStateException("Unexpected normalized value-map container: "+schema);}
    private static Map<String,Object> scalarUnion(String description){var schema=new LinkedHashMap<String,Object>();schema.put("oneOf",scalarTypes());schema.put("description",description);return schema;}
    private static List<Map<String,Object>> scalarTypes(){return List.of(Map.of("type","string","nullable",true),Map.of("type","number"),Map.of("type","boolean"));}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Map<?,?> parent,String key){var value=parent.get(key);if(!(value instanceof Map<?,?> result))throw new IllegalStateException("Generated OpenAPI "+key+" is not an object");return (Map<String,Object>)result;}
    private static final class Counts {int filters;int valueMaps;}
}
