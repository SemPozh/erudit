package com.erudit.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestIdFilterTest {
    @Test void restoresPreviousMdcEvenWhenServletFails() {
        MDC.put(RequestIds.MDC_KEY, "outer");
        var request = new MockHttpServletRequest();
        request.addHeader(RequestIds.HEADER, "inner");
        try {
            assertThatThrownBy(() -> new ServletRequestIdFilter().doFilter(request, new MockHttpServletResponse(),
                    (wrapped, response) -> {
                        assertThat(MDC.get(RequestIds.MDC_KEY)).isEqualTo("inner");
                        throw new IllegalStateException("failure");
                    })).isInstanceOf(IllegalStateException.class);
            assertThat(MDC.get(RequestIds.MDC_KEY)).isEqualTo("outer");
        } finally { MDC.remove(RequestIds.MDC_KEY); }
    }
}
