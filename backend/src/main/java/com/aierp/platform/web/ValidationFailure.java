package com.aierp.platform.web;
public class ValidationFailure extends IllegalArgumentException {
    public final String field;
    public ValidationFailure(String field, String message) { super(message); this.field=field; }
}
