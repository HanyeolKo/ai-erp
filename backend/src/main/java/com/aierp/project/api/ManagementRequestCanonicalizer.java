package com.aierp.project.api;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Stable, unambiguous canonical request representation shared by project mutations. */
public final class ManagementRequestCanonicalizer {
    private ManagementRequestCanonicalizer() { }

    public static String definition(String operation, UUID resourceId,
            String purpose, String successCriteria, UUID responsibleManagerId,
            String health, String healthReason, LocalDate healthAsOf, Long rowVersion) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("purpose", normalize(purpose));
        fields.put("successCriteria", normalize(successCriteria));
        fields.put("responsibleManagerId", responsibleManagerId == null ? null : responsibleManagerId.toString());
        fields.put("health", normalize(health));
        fields.put("healthReason", normalize(healthReason));
        fields.put("healthAsOf", healthAsOf == null ? null : healthAsOf.toString());
        fields.put("rowVersion", rowVersion);
        return encode(operation, resourceId, fields);
    }

    public static String taskExecution(String operation, UUID resourceId,
            String priority, String completionCriterion, Long rowVersion) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("priority", normalize(priority));
        fields.put("completionCriterion", normalize(completionCriterion));
        fields.put("rowVersion", rowVersion);
        return encode(operation, resourceId, fields);
    }

    private static String encode(String operation, UUID resourceId, Map<String, Object> fields) {
        var out = new StringBuilder("{");
        append(out, "operation", operation);
        out.append(',');
        append(out, "resourceId", resourceId == null ? null : resourceId.toString());
        out.append(",\"fields\":{");
        boolean first = true;
        for (var entry : fields.entrySet()) {
            if (!first) out.append(',');
            first = false;
            append(out, entry.getKey(), entry.getValue());
        }
        return out.append("}}").toString();
    }

    private static void append(StringBuilder out, String key, Object value) {
        out.append('"').append(escape(key)).append("\":");
        if (value == null) out.append("null");
        else out.append('"').append(escape(String.valueOf(value))).append('"');
    }

    private static String normalize(String value) {
        if (value == null) return null;
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}
