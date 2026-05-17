package com.company.orders.presentation.context;

import java.util.concurrent.Executor;
import org.jspecify.annotations.NonNull;

public class ContextAwareExecutor implements Executor {

    private final Executor delegate;
    private final ContextSnapshotFactory factory;

    public ContextAwareExecutor(Executor delegate, ContextSnapshotFactory factory) {
        this.delegate = delegate;
        this.factory = factory;
    }

    @Override
    public void execute(@NonNull Runnable command) {
        ContextSnapshot snapshot = factory.capture();
        delegate.execute(snapshot.wrap(command));
    }
}
