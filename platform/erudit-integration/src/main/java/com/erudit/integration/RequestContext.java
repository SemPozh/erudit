package com.erudit.integration;

public final class RequestContext {
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    private RequestContext() {}
    public static String requestId() { return REQUEST_ID.get(); }
    public static void withRequestId(String requestId, Runnable action) {
        try { if (requestId != null) REQUEST_ID.set(requestId); action.run(); }
        finally { REQUEST_ID.remove(); }
    }
}
