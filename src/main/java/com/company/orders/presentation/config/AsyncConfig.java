package com.company.orders.presentation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    /**
     * 🔥 Propagate SecurityContext across threads
     */
    @Bean
    public TaskDecorator securityContextTaskDecorator() {
        return runnable -> {
            SecurityContext context = SecurityContextHolder.getContext();

            return () -> {
                SecurityContext previous = SecurityContextHolder.getContext();
                try {
                    SecurityContextHolder.setContext(context);
                    runnable.run();
                } finally {
                    SecurityContextHolder.setContext(previous);
                }
            };
        };
    }

    /**
     * 🔥 Main executor used by Spring + GraphQL
     */
    @Bean
    public Executor applicationExecutor(TaskDecorator taskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("app-exec-");

        // 🔥 THIS is the key
        executor.setTaskDecorator(taskDecorator);

        executor.initialize();
        return executor;
    }
}