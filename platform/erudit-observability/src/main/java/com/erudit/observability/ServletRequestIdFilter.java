package com.erudit.observability;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class ServletRequestIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = RequestIds.normalize(request.getHeader(RequestIds.HEADER));
        String previous = MDC.get(RequestIds.MDC_KEY);
        MDC.put(RequestIds.MDC_KEY, id);
        response.setHeader(RequestIds.HEADER, id);
        try {
            chain.doFilter(new HttpServletRequestWrapper(request) {
                @Override public String getHeader(String name) {
                    return RequestIds.HEADER.equalsIgnoreCase(name) ? id : super.getHeader(name);
                }
                @Override public Enumeration<String> getHeaders(String name) {
                    return RequestIds.HEADER.equalsIgnoreCase(name) ? Collections.enumeration(Collections.singleton(id))
                            : super.getHeaders(name);
                }
            }, response);
        } finally {
            if (previous == null) { MDC.remove(RequestIds.MDC_KEY); }
            else { MDC.put(RequestIds.MDC_KEY, previous); }
        }
    }
}
