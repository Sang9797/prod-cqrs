package com.company.orders.presentation.graphql;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GraphQlLoggingInterceptor implements WebGraphQlInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(GraphQlLoggingInterceptor.class);

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        Instant start = Instant.now();
        return chain
                .next(request)
                .doOnNext(
                        response -> {
                            long ms = Duration.between(start, Instant.now()).toMillis();
                            var result = response.getExecutionResult();
                            String op = operationName(request);
                            String user = MDC.get("user");
                            if (user == null)
                                user = "anonymous";
                            if (result.getErrors().isEmpty()) {
                                LOG.info(
                                        "POST /graphql op={} → 200 [{}ms] user={} data={}",
                                        op,
                                        ms,
                                        user,
                                        result.getData());
                            } else {
                                LOG.warn(
                                        "POST /graphql op={} → 200 [{}ms] user={} errors={}",
                                        op,
                                        ms,
                                        user,
                                        result.getErrors());
                            }
                        });
    }

    private static String operationName(WebGraphQlRequest request) {
        String op = request.getOperationName();
        return (op != null && !op.isBlank()) ? op : "<anonymous>";
    }
}
