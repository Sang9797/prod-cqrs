package com.company.orders.application.handler.command;

import com.company.orders.application.command.ConfirmOrderCommand;
import com.company.orders.bus.command.CommandHandler;
import com.company.orders.domain.exception.OrderNotFoundException;
import com.company.orders.infrastructure.persistence.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class ConfirmOrderCommandHandler implements CommandHandler<ConfirmOrderCommand, Void> {

  private static final Logger LOG = LoggerFactory.getLogger(ConfirmOrderCommandHandler.class);
  private final OrderRepository repository;

  public ConfirmOrderCommandHandler(OrderRepository repository) {
    this.repository = repository;
  }

  @Override
  public Class<ConfirmOrderCommand> commandType() {
    return ConfirmOrderCommand.class;
  }

  @Override
  public Void handle(ConfirmOrderCommand cmd) {
    LOG.info("[ConfirmOrder] orderId={}", cmd.orderId());
    var order =
        repository
            .findById(cmd.orderId())
            .orElseThrow(() -> new OrderNotFoundException(cmd.orderId()));
    order.confirm();
    repository.save(order);
    return null;
  }
}
