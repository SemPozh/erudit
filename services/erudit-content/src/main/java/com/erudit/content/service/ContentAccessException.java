package com.erudit.content.service;
public class ContentAccessException extends RuntimeException {
    public ContentAccessException() { super("Active premium subscription is required"); }
}
