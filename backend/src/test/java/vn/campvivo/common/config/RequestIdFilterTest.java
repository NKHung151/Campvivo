package vn.campvivo.common.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    /** Chạy filter, trả về requestId mà code phía sau nhìn thấy trong MDC. */
    private String run(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(MDC.get(RequestIdFilter.MDC_KEY));
            }
        };
        filter.doFilter(request, response, chain);
        return seen.get();
    }

    @Test
    void generatesIdWhenHeaderMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = run(new MockHttpServletRequest(), response);

        assertThat(seen).isNotBlank();
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(seen);
    }

    @Test
    void reusesValidIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "abc-123_XYZ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = run(request, response);

        assertThat(seen).isEqualTo("abc-123_XYZ");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("abc-123_XYZ");
    }

    @Test
    void replacesUnsafeIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "bad id\nFAKE LOG LINE");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = run(request, response);

        assertThat(seen).doesNotContain("\n").doesNotContain(" ").hasSizeLessThanOrEqualTo(64);
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        run(new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
