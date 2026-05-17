package com.company.orders.application.handler.command;

import com.company.orders.application.command.PlaceOrderCommand;
import com.company.orders.bus.command.CommandHandler;
import com.company.orders.domain.model.Money;
import com.company.orders.domain.model.Order;
import com.company.orders.domain.model.OrderItem;
import com.company.orders.infrastructure.persistence.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class PlaceOrderCommandHandler implements CommandHandler<PlaceOrderCommand, Order> {

    private static final Logger LOG = LoggerFactory.getLogger(PlaceOrderCommandHandler.class);
    private final OrderRepository repository;
    private final Counter ordersPlaced;

    public PlaceOrderCommandHandler(OrderRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.ordersPlaced = Counter.builder("orders.placed.total")
                .description("Total number of orders placed")
                .register(meterRegistry);
    }

    @Override
    public Class<PlaceOrderCommand> commandType() {
        return PlaceOrderCommand.class;
    }

    @Override
    public Order handle(PlaceOrderCommand cmd) {
        LOG.info("[PlaceOrder] customerId={} items={}", cmd.customerId(), cmd.items().size());
        List<OrderItem> items = cmd.items().stream()
                .map(
                        i -> new OrderItem(
                                i.productId(),
                                i.productName(),
                                i.quantity(),
                                new Money(BigDecimal.valueOf(i.unitPrice()), i.currency())))
                .toList();
        var saved = repository.save(Order.create(cmd.customerId(), items));
        ordersPlaced.increment();
        LOG.info("[PlaceOrder] created orderId={}", saved.getOrderId());
        return saved;
    }
}
