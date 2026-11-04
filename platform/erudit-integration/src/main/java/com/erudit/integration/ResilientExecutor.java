package com.erudit.integration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.function.Supplier;
public final class ResilientExecutor {
 private ResilientExecutor() {}
 public static <T>T execute(CircuitBreaker breaker,Supplier<T> action,Supplier<T> fallback){try{return CircuitBreaker.decorateSupplier(breaker,action).get();}catch(RuntimeException failure){return fallback.get();}}
}
