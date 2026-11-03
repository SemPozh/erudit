package com.erudit.observability;

import io.micrometer.context.ContextRegistry;
import org.slf4j.MDC;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration(beforeName = "org.springframework.boot.webflux.autoconfigure.error.ErrorWebFluxAutoConfiguration")
public class ObservabilityAutoConfiguration {
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletConfiguration {
        @Bean
        FilterRegistrationBean<ServletRequestIdFilter> eruditRequestIdFilter() {
            var registration = new FilterRegistrationBean<>(new ServletRequestIdFilter());
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
        @Bean @ConditionalOnMissingBean(ServletTechnicalErrors.class)
        ServletTechnicalErrors eruditServletErrors() { return new ServletTechnicalErrors(); }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    static class ReactiveConfiguration {
        @Bean InitializingBean requestIdContextPropagation() {
            return () -> ContextRegistry.getInstance().registerThreadLocalAccessor(RequestIds.HEADER,
                    () -> MDC.get(RequestIds.MDC_KEY), value -> MDC.put(RequestIds.MDC_KEY, value),
                    () -> MDC.remove(RequestIds.MDC_KEY));
        }
        @Bean @ConditionalOnMissingBean(ReactiveRequestIdFilter.class)
        ReactiveRequestIdFilter eruditReactiveRequestIdFilter() { return new ReactiveRequestIdFilter(); }
        @Bean @ConditionalOnMissingBean(ErrorWebExceptionHandler.class)
        ReactiveTechnicalErrors eruditReactiveErrors(ObjectMapper mapper) { return new ReactiveTechnicalErrors(mapper); }
    }
}
