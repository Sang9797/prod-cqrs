package com.company.orders.presentation.context;

import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public class ContextSnapshot {

    private final SecurityContext securityContext;
    private final Map<String, String> mdcContext;

    public ContextSnapshot(SecurityContext securityContext, Map<String, String> mdcContext) {
        this.securityContext = securityContext;
        this.mdcContext = mdcContext;
    }

    public Runnable wrap(Runnable task) {
        return () -> {
            SecurityContext previousSecurity = SecurityContextHolder.getContext();
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();

            try {
                SecurityContextHolder.setContext(securityContext);

                if (mdcContext != null) {
                    MDC.setContextMap(mdcContext);
                } else {
                    MDC.clear();
                }

                task.run();

            } finally {
                SecurityContextHolder.setContext(previousSecurity);

                if (previousMdc != null) {
                    MDC.setContextMap(previousMdc);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    public <T> Callable<T> wrap(Callable<T> task) {
        return () -> {
            SecurityContext previousSecurity = SecurityContextHolder.getContext();
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();

            try {
                SecurityContextHolder.setContext(securityContext);

                if (mdcContext != null) {
                    MDC.setContextMap(mdcContext);
                } else {
                    MDC.clear();
                }

                return task.call();

            } finally {
                SecurityContextHolder.setContext(previousSecurity);

                if (previousMdc != null) {
                    MDC.setContextMap(previousMdc);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
