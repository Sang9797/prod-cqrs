package com.company.orders.presentation.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Automatic Persisted Queries (APQ) — Apollo/Netflix protocol.
 *
 * <p>
 * First request: client sends hash + full query → server stores the mapping. Subsequent requests:
 * client sends hash only → server looks up and executes. Unknown hash with no query → returns
 * PersistedQueryNotFound so client retries with full query.
 */
@Component
@Order(1)
public class PersistedQueryFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, String> registry = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/graphql".equals(request.getRequestURI());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        byte[] bodyBytes = request.getInputStream().readAllBytes();
        Map<String, Object> body = mapper.readValue(bodyBytes, Map.class);

        Map<String, Object> extensions = (Map<String, Object>) body.get("extensions");
        if (extensions == null || !extensions.containsKey("persistedQuery")) {
            chain.doFilter(new CachedBodyRequest(request, bodyBytes), response);
            return;
        }

        Map<String, Object> pq = (Map<String, Object>) extensions.get("persistedQuery");
        String hash = (String) pq.get("sha256Hash");
        String query = (String) body.get("query");

        if (query != null && !query.isBlank()) {
            // Client sending hash + full query — store and proceed
            registry.put(hash, query);
            chain.doFilter(new CachedBodyRequest(request, bodyBytes), response);
            return;
        }

        // Client sending hash only — look up
        String stored = registry.get(hash);
        if (stored == null) {
            response.setStatus(200);
            response.setContentType("application/json");
            response
                    .getWriter()
                    .write(
                            "{\"errors\":[{\"message\":\"PersistedQueryNotFound\","
                                    + "\"extensions\":{\"code\":\"PERSISTED_QUERY_NOT_FOUND\"}}]}");
            return;
        }

        body.put("query", stored);
        chain.doFilter(new CachedBodyRequest(request, mapper.writeValueAsBytes(body)), response);
    }

    private static class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            var stream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return stream.read();
                }

                @Override
                public boolean isFinished() {
                    return stream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream()));
        }
    }
}
