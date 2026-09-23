package com.llmgateway.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void whenNoIncomingHeader_generatesAndEchoesANewRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isNotBlank();
        verify(chain).doFilter(request, response);
    }

    @Test
    void whenIncomingHeaderPresent_reusesItInsteadOfGeneratingANewOne() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "caller-supplied-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("caller-supplied-id");
    }

    @Test
    void mdcCarriesTheRequestIdDuringTheChainAndIsClearedAfterward() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> seenDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenDuringChain.set(MDC.get(RequestIdFilter.MDC_KEY));

        filter.doFilterInternal(request, response, chain);

        assertThat(seenDuringChain.get()).isNotBlank();
        // Not left behind for whatever request a reused thread handles next.
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
