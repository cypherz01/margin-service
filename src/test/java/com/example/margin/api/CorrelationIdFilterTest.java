package com.example.margin.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    /** Runs the filter and returns the correlation ID that was in the MDC while the request was handled. */
    private String run(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<String> seenByHandler = new AtomicReference<>();
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                seenByHandler.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            }
        });
        return seenByHandler.get();
    }

    @Test
    void reusesWellFormedCallerSuppliedId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "req-123.abc_DEF");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String inMdc = run(request, response);

        assertThat(inMdc).isEqualTo("req-123.abc_DEF");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("req-123.abc_DEF");
    }

    @Test
    void generatesIdWhenNoneSupplied() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String inMdc = run(new MockHttpServletRequest(), response);

        assertThat(inMdc).matches("[0-9a-f-]{36}");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(inMdc);
    }

    /** Untrusted header values must never reach the logs, where they could forge or split entries. */
    @ParameterizedTest
    @ValueSource(strings = {"abc\nFAKE LOG LINE", "abc def", "<script>", "",
            "an-id-that-is-much-too-long-to-be-legitimate-0123456789-0123456789"})
    void replacesUnsafeCallerSuppliedId(String unsafe) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, unsafe);

        String inMdc = run(request, new MockHttpServletResponse());

        assertThat(inMdc).isNotEqualTo(unsafe).matches("[0-9a-f-]{36}");
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        run(new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
