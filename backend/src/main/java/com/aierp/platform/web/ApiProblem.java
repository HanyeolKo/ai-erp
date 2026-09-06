package com.aierp.platform.web;

import java.util.List;

public record ApiProblem(String code, String traceId, List<FieldError> fieldErrors) {
    public record FieldError(String field, String message) { }
}
