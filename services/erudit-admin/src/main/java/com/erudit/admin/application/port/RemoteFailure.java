package com.erudit.admin.application.port;

public class RemoteFailure extends RuntimeException {
    private final int status;
    public RemoteFailure(int status, Throwable cause) { super(cause); this.status = status; }
    public int status() { return status; }
}
