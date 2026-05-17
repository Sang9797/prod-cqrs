package com.company.orders.presentation.config;

import com.company.orders.presentation.context.ContextAwareExecutor;
import com.company.orders.presentation.context.ContextSnapshotFactory;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsyncConfig {

    @Bean
    public ContextSnapshotFactory contextSnapshotFactory() {
        return new ContextSnapshotFactory();
    }

    @Bean
    public Executor applicationExecutor(ContextSnapshotFactory factory) {
        return new ContextAwareExecutor(Executors.newVirtualThreadPerTaskExecutor(), factory);
    }
}
