package com.erudit.events;

@FunctionalInterface
public interface AnalyticsEventSink {
    void write(AnalyticsEvent event) throws Exception;
}
