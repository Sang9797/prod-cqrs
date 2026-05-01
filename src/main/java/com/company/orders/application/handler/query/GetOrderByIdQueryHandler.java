package com.company.orders.application.handler.query;

import com.company.orders.application.query.GetOrderByIdQuery;
import com.company.orders.bus.query.QueryHandler;
import com.company.orders.domain.model.Order;
import com.company.orders.infrastructure.persistence.OrderRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class GetOrderByIdQueryHandler implements QueryHandler<GetOrderByIdQuery, Optional<Order>> {

  private final OrderRepository repository;

  public GetOrderByIdQueryHandler(OrderRepository repository) {
    this.repository = repository;
  }

  @Override
  public Class<GetOrderByIdQuery> queryType() {
    return GetOrderByIdQuery.class;
  }

  @Override
  public Optional<Order> handle(GetOrderByIdQuery query) {
    return repository.findById(query.orderId());
  }
}
