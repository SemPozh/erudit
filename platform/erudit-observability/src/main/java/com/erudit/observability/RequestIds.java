package com.erudit.observability;

import java.util.UUID;
import java.util.regex.Pattern;

public final class RequestIds {
    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private RequestIds() { }
    public static String normalize(String supplied) {
        return supplied != null && VALID.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
    }
}
