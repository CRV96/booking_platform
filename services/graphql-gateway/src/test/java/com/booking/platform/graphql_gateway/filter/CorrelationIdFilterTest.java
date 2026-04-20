package com.booking.platform.graphql_gateway.filter;

import com.booking.platform.common.grpc.context.CorrelationIdContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void doFilter_noHeader_generatesUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String correlationId = response.getHeader(CorrelationIdContext.HEADER_NAME);
        assertThat(correlationId).isNotBlank();
        // Should be a valid UUID
        assertThat(correlationId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void doFilter_existingHeader_usesExistingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdContext.HEADER_NAME, "my-correlation-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdContext.HEADER_NAME)).isEqualTo("my-correlation-id-123");
    }

    @Test
    void doFilter_blankHeader_generatesNewId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdContext.HEADER_NAME, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String correlationId = response.getHeader(CorrelationIdContext.HEADER_NAME);
        assertThat(correlationId).isNotBlank();
        assertThat(correlationId).isNotEqualTo("   ");
    }

    @Test
    void doFilter_addsCorrelationIdToResponseHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdContext.HEADER_NAME)).isNotNull();
    }

    @Test
    void doFilter_alwaysCallsNextFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // MockFilterChain records that doFilter was invoked on it
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void doFilter_differentRequestsGetDifferentIds() throws Exception {
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        MockHttpServletResponse response2 = new MockHttpServletResponse();

        filter.doFilter(request1, response1, new MockFilterChain());
        filter.doFilter(request2, response2, new MockFilterChain());

        String id1 = response1.getHeader(CorrelationIdContext.HEADER_NAME);
        String id2 = response2.getHeader(CorrelationIdContext.HEADER_NAME);
        assertThat(id1).isNotEqualTo(id2);
    }
}
