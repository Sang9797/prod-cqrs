package com.company.orders.presentation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

// Must run before Spring Security (order -100) so rejected requests are also logged
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final int MAX_BODY_BYTES = 2048;
    private static final String MDC_REQ_ID = "reqId";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // /actuator: noisy health-check traffic
        // /graphql: response is written asynchronously — GraphQlLoggingInterceptor handles it
        return uri.startsWith("/actuator") || "/graphql".equals(uri);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        String reqId = Optional.ofNullable(request.getHeader("X-Request-ID"))
                .filter(h -> !h.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString().replace("-", "").substring(0, 8));

        MDC.put(MDC_REQ_ID, reqId);
        response.setHeader("X-Request-ID", reqId);

        var req = new ContentCachingRequestWrapper(request, MAX_BODY_BYTES);
        var res = new ContentCachingResponseWrapper(response);
        Instant start = Instant.now();

        try {
            chain.doFilter(req, res);
        } finally {
            long ms = Duration.between(start, Instant.now()).toMillis();
            LOG.info(
                    "{} {}{} → {} [{}ms] user={} req={} res={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    queryString(request),
                    res.getStatus(),
                    ms,
                    resolveUser(),
                    extract(req.getContentAsByteArray()),
                    extract(res.getContentAsByteArray()));
            res.copyBodyToResponse();
            MDC.clear();
        }
    }

    private static String resolveUser() {
        String user = MDC.get("user");
        return user != null ? user : "anonymous";
    }

    private static String queryString(HttpServletRequest request) {
        String qs = request.getQueryString();
        return qs != null ? "?" + qs : "";
    }

    private static String extract(byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            return "-";
        String body = new String(bytes, StandardCharsets.UTF_8).stripTrailing();
        return body.length() > MAX_BODY_BYTES
                ? body.substring(0, MAX_BODY_BYTES) + "…[truncated]"
                : body;
    }
}
