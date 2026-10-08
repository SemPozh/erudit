package com.erudit.events.config;

import com.erudit.events.service.AnalyticsEventPublisher;
import com.erudit.events.service.AnalyticsEventSink;
import com.erudit.events.service.QueuedAnalyticsEventPublisher;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnBean(AnalyticsEventSink.class)
public class AnalyticsEventPublisherConfiguration {
    @Bean(destroyMethod = "shutdown")
    ExecutorService analyticsEventExecutor(
            @Value("${events.publisher.workers:2}") int workers,
            @Value("${events.publisher.queue-capacity:1000}") int queueCapacity) {
        return new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().name("analytics-publisher-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    AnalyticsEventPublisher analyticsEventPublisher(AnalyticsEventSink sink,
                                                     ExecutorService analyticsEventExecutor) {
        return new QueuedAnalyticsEventPublisher(sink, analyticsEventExecutor, Clock.systemUTC());
    }
}
