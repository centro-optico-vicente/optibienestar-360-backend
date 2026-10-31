package com.fenixcore.optibienestar360.core.web;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The public affiliation form is throttled per IP inside a sliding one-hour
 * window; other paths and methods are never touched.
 */
class PublicSubmissionRateLimitFilterTest {

    private final PublicSubmissionRateLimitFilter filter = new PublicSubmissionRateLimitFilter(new StaticMessageSource(), 3);

    private MockHttpServletResponse post(String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void overTheHourlyBudget_is429_perIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(post("/v1/public/affiliation-requests", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse throttled = post("/v1/public/affiliation-requests", "10.0.0.1");
        assertThat(throttled.getStatus()).isEqualTo(429);
        assertThat(throttled.getHeader("Retry-After")).isEqualTo("3600");
        // Another client is unaffected.
        assertThat(post("/v1/public/affiliation-requests", "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void anOversizedJsonBody_is413_beforeAnyParsing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/public/affiliation-requests");
        request.setContentType("application/json");
        request.setContent(new byte[70 * 1024]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void otherPaths_areNotThrottled() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(post("/v1/public/terms/AFILIADO/preview", "10.0.0.3").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void theWindowSlides_hitsOlderThanAnHourAreForgotten() {
        long start = 1_000_000L;
        for (int i = 0; i < 3; i++) {
            assertThat(filter.tryAcquire("10.0.0.4", start)).isTrue();
        }
        assertThat(filter.tryAcquire("10.0.0.4", start + 1)).isFalse();
        assertThat(filter.tryAcquire("10.0.0.4", start + Duration.ofHours(1).toMillis())).isTrue();
    }
}
