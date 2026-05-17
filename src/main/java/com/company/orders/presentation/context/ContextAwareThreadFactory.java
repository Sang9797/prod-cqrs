package com.company.orders.presentation.context;

import java.util.concurrent.ThreadFactory;

public class ContextAwareThreadFactory implements ThreadFactory {
    private final ContextSnapshotFactory contextSnapshotFactory;

    public ContextAwareThreadFactory(ContextSnapshotFactory contextSnapshotFactory) {
        this.contextSnapshotFactory = contextSnapshotFactory;
    }

    @Override
    public Thread newThread(Runnable r) {
        var contextSnapshot = contextSnapshotFactory.capture();
        var wrapped = contextSnapshot.wrap(r);
        return Thread.ofVirtual().unstarted(wrapped);
    }
}
