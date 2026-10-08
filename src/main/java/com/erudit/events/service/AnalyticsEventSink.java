package com.erudit.events.service;

import com.erudit.events.model.AnalyticsEvent;

@FunctionalInterface
public interface AnalyticsEventSink {
    void write(AnalyticsEvent event) throws Exception;
}
