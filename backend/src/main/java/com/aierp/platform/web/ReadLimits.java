package com.aierp.platform.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Every externally selected collection has a finite database limit. */
public final class ReadLimits {
    public static final int DEFAULT = 100;
    public static final int MAX = 200;
    public static final int HISTORY_DEFAULT = 50;
    private ReadLimits() {}
    public static int limit(Integer value) { return checked(value, DEFAULT, "limit"); }
    public static int history(Integer value) { return checked(value, HISTORY_DEFAULT, "historyLimit"); }
    private static int checked(Integer value, int fallback, String field) {
        if (value == null) return fallback;
        if (value < 1 || value > MAX) throw new ValidationFailure(field, "Must be between 1 and 200");
        return value;
    }
    public static PageRequest page(Integer page, Integer limit, Sort sort) {
        if (page != null && (page < 0 || page > 10000)) throw new ValidationFailure("page", "Must be between 0 and 10000");
        return PageRequest.of(page == null ? 0 : page, limit(limit), sort);
    }
}
