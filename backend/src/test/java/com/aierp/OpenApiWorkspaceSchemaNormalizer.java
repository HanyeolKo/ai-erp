package com.aierp;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
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
        var schemas=map(map(root,"components"),"schemas");var counts=new Counts();for(var entry:schemas.entrySet()){if(entry.getKey().contains("schedule-workspace"))normalize(entry.getValue(),counts);}@SuppressWarnings("unchecked") var typedRoot=(Map<String,Object>)root;normalizeManagementBounds(typedRoot,path);
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
    private static void normalizeManagementBounds(Map<String,Object> root,Path specPath) throws Exception {
        var paths=map(root,"paths");
        var work=operation(paths,"/api/v1/me/work","get");
        var history=operation(paths,"/api/v1/projects/{projectId}/management/history","get");
        var snippets="api-spec".equals(String.valueOf(specPath.getParent().getFileName()))?specPath.getParent().resolveSibling("generated-snippets"):specPath.getParent().resolve("generated-snippets");
        var records=new ArrayList<Map<String,Object>>();
        records.addAll(queryMetadata(snippets.resolve("project-management-work/resource.json"),"page"));
        records.addAll(queryMetadata(snippets.resolve("project-management-work/resource.json"),"limit"));
        records.addAll(queryMetadata(snippets.resolve("project-management-history/resource.json"),"page"));
        records.addAll(queryMetadata(snippets.resolve("project-management-history/resource.json"),"limit"));
        if(records.size()!=4)throw new IllegalStateException("Expected four management bound metadata records, found "+records.size());
        bounds(parameter(work,"page"),records.get(0),0,0,null);
        bounds(parameter(work,"limit"),records.get(1),50,1,100);
        bounds(parameter(history,"page"),records.get(2),0,0,null);
        bounds(parameter(history,"limit"),records.get(3),50,1,100);
    }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> queryMetadata(Path file,String name)throws Exception {
        if(!Files.isRegularFile(file))throw new IllegalStateException("Missing generated REST Docs resource: "+file);
        Object value;try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){value=new Yaml().load(reader);}
        if(!(value instanceof Map<?,?> root))throw new IllegalStateException("Invalid generated REST Docs resource: "+file);
        var request=root.get("request");
        if(!(request instanceof Map<?,?> requestMap)||!(requestMap.get("queryParameters") instanceof List<?> parameters))throw new IllegalStateException("Missing generated query metadata: "+file);
        var matches=new ArrayList<Map<String,Object>>();
        for(var entry:parameters)if(entry instanceof Map<?,?> raw&&name.equals(raw.get("name")))matches.add((Map<String,Object>)raw);
        if(matches.size()!=1)throw new IllegalStateException("Expected one generated metadata record for "+name+" in "+file+", found "+matches.size());
        return matches;
    }
    @SuppressWarnings("unchecked") private static void bounds(Map<String,Object> parameter,Map<String,Object> metadata,int expectedDefault,int expectedMin,Integer expectedMax){
        validateMetadata(metadata,String.valueOf(metadata.get("name")),expectedDefault,expectedMin,expectedMax);
        var schema=parameter.get("schema");
        if(!(schema instanceof Map<?,?> raw))throw new IllegalStateException("Missing OpenAPI parameter schema: "+parameter);
        var typed=(Map<String,Object>)raw;
        if(!"integer".equals(typed.get("type"))||!Boolean.FALSE.equals(parameter.get("required")))throw new IllegalStateException("Unexpected management parameter metadata: "+parameter);
        if(!typed.containsKey("default")||integerValue(typed.get("default"),"generated default")!=expectedDefault)throw new IllegalStateException("Conflicting generated default: "+parameter);
        if(typed.containsKey("minimum")&&integerValue(typed.get("minimum"),"generated minimum")!=expectedMin)throw new IllegalStateException("Conflicting generated minimum: "+parameter);
        if(expectedMax==null&&typed.containsKey("maximum"))throw new IllegalStateException("Unexpected generated maximum: "+parameter);
        if(expectedMax!=null&&typed.containsKey("maximum")&&integerValue(typed.get("maximum"),"generated maximum")!=expectedMax)throw new IllegalStateException("Conflicting generated maximum: "+parameter);
        typed.put("minimum",expectedMin);if(expectedMax!=null)typed.put("maximum",expectedMax);
    }
    @SuppressWarnings("unchecked") private static void validateMetadata(Map<String,Object> record,String parameter,int expectedDefault,int expectedMin,Integer expectedMax){
        if(!"INTEGER".equals(record.get("type"))||!Boolean.TRUE.equals(record.get("optional")))throw new IllegalStateException("Unexpected generated metadata for "+parameter+": "+record);
        if(!record.containsKey("default")||integerValue(record.get("default"),"metadata default")!=expectedDefault)throw new IllegalStateException("Unexpected generated default for "+parameter+": "+record);
        var attributes=record.get("attributes");if(!(attributes instanceof Map<?,?> rawAttributes))throw new IllegalStateException("Missing validation metadata for "+parameter);
        var constraints=rawAttributes.get("validationConstraints");if(!(constraints instanceof List<?> list))throw new IllegalStateException("Missing validation metadata for "+parameter);
        var min=constraintValue(list,parameter,"Min");var max=constraintValue(list,parameter,"Max");
        if(min==null||min!=expectedMin)throw new IllegalStateException("Missing or unexpected min bound for "+parameter+": "+min);
        if(expectedMax==null&&max!=null)throw new IllegalStateException("Unexpected max bound for "+parameter+": "+max);
        if(expectedMax!=null&&(max==null||max!=expectedMax))throw new IllegalStateException("Missing or unexpected max bound for "+parameter+": "+max);
    }
    private static Integer constraintValue(List<?> constraints,String parameter,String expectedName){Integer found=null;for(var entry:constraints){if(!(entry instanceof Map<?,?> raw))throw new IllegalStateException("Invalid validation metadata for "+parameter);if(!expectedName.equalsIgnoreCase(String.valueOf(raw.get("name"))))continue;var config=raw.get("configuration");if(!(config instanceof Map<?,?> configMap))throw new IllegalStateException("Invalid "+expectedName+" metadata for "+parameter);var value=integerValue(configMap.get("value"),"metadata "+expectedName);if(found!=null&&!found.equals(value))throw new IllegalStateException("Conflicting "+expectedName+" metadata for "+parameter);found=value;}return found;}
    private static int integerValue(Object value,String label){if(!(value instanceof Number number))throw new IllegalStateException("Missing numeric "+label);try{return new BigDecimal(number.toString()).intValueExact();}catch(ArithmeticException failure){throw new IllegalStateException("Non-integer "+label+": "+value,failure);}}
    @SuppressWarnings("unchecked") private static Map<String,Object> operation(Map<String,Object> paths,String path,String method){var value=paths.get(path);if(!(value instanceof Map<?,?> raw))throw new IllegalStateException("Missing OpenAPI path: "+path);var operation=((Map<String,Object>)raw).get(method);if(!(operation instanceof Map<?,?> result))throw new IllegalStateException("Missing OpenAPI operation: "+method+" "+path);return (Map<String,Object>)result;}
    @SuppressWarnings("unchecked") private static Map<String,Object> parameter(Map<String,Object> operation,String name){var values=operation.get("parameters");if(!(values instanceof List<?> list))throw new IllegalStateException("Missing OpenAPI parameters for operation");for(var value:list){if(value instanceof Map<?,?> raw&&name.equals(raw.get("name"))&&"query".equals(raw.get("in")))return (Map<String,Object>)raw;}throw new IllegalStateException("Missing OpenAPI query parameter: "+name);}
    private static void requireRawScalar(Map<String,Object> schema,String label){if(!schema.keySet().equals(Set.of("type","description","nullable"))||!"object".equals(schema.get("type"))||!Boolean.TRUE.equals(schema.get("nullable")))throw new IllegalStateException("Unexpected raw "+label+" schema: "+schema);}
    private static void requireNormalizedScalar(Map<String,Object> schema,String label){if(!schema.keySet().equals(Set.of("oneOf","description"))||!scalarTypes().equals(schema.get("oneOf")))throw new IllegalStateException("Unexpected normalized "+label+" schema: "+schema);}
    private static void requireRawValueMap(Map<String,Object> schema){if(!schema.keySet().equals(Set.of("type","properties","description"))||!(schema.get("description") instanceof String description)||!description.endsWith("values"))throw new IllegalStateException("Unexpected raw value-map container: "+schema);}
    private static void requireNormalizedValueMap(Map<String,Object> schema){if(!schema.keySet().equals(Set.of("type","additionalProperties","description"))||!(schema.get("description") instanceof String description)||!description.endsWith("values"))throw new IllegalStateException("Unexpected normalized value-map container: "+schema);}
    private static Map<String,Object> scalarUnion(String description){var schema=new LinkedHashMap<String,Object>();schema.put("oneOf",scalarTypes());schema.put("description",description);return schema;}
    private static List<Map<String,Object>> scalarTypes(){return List.of(Map.of("type","string","nullable",true),Map.of("type","number"),Map.of("type","boolean"));}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Map<?,?> parent,String key){var value=parent.get(key);if(!(value instanceof Map<?,?> result))throw new IllegalStateException("Generated OpenAPI "+key+" is not an object");return (Map<String,Object>)result;}
    private static final class Counts {int filters;int valueMaps;}
}
