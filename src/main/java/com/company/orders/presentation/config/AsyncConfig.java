package com.company.orders.presentation.config;

import com.company.orders.presentation.context.ContextAwareExecutor;
import com.company.orders.presentation.context.ContextSnapshotFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    @Bean
    public ContextSnapshotFactory contextSnapshotFactory() {
        return new ContextSnapshotFactory();
    }

    @Bean
    public Executor applicationExecutor(ContextSnapshotFactory factory) {
        return new ContextAwareExecutor(
                Executors.newVirtualThreadPerTaskExecutor(),
                factory
        );
    }
}