package com.company.orders.application.handler.command;

import com.company.orders.application.command.CancelOrderCommand;
import com.company.orders.bus.command.CommandHandler;
import com.company.orders.domain.exception.OrderNotFoundException;
import com.company.orders.infrastructure.persistence.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class CancelOrderCommandHandler implements CommandHandler<CancelOrderCommand, Void> {

    private static final Logger LOG = LoggerFactory.getLogger(CancelOrderCommandHandler.class);
    private final OrderRepository repository;
    private final Counter ordersCancelled;

    public CancelOrderCommandHandler(OrderRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.ordersCancelled = Counter.builder("orders.cancelled.total")
                .description("Total number of orders cancelled")
                .register(meterRegistry);
    }

    @Override
    public Class<CancelOrderCommand> commandType() {
        return CancelOrderCommand.class;
    }

    @Override
    public Void handle(CancelOrderCommand cmd) {
        LOG.info("[CancelOrder] orderId={} reason={}", cmd.orderId(), cmd.reason());
        var order = repository
                .findById(cmd.orderId())
                .orElseThrow(() -> new OrderNotFoundException(cmd.orderId()));
        order.cancel(cmd.reason());
        repository.save(order);
        ordersCancelled.increment();
        return null;
    }
}
