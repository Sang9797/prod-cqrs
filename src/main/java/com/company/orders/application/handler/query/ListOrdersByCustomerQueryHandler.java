package com.company.orders.application.handler.query;

import com.company.orders.application.query.ListOrdersByCustomerQuery;
import com.company.orders.bus.query.QueryHandler;
import com.company.orders.domain.model.Order;
import com.company.orders.infrastructure.persistence.OrderRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ListOrdersByCustomerQueryHandler
    implements QueryHandler<ListOrdersByCustomerQuery, List<Order>> {

  private final OrderRepository repository;

  public ListOrdersByCustomerQueryHandler(OrderRepository r) {
    this.repository = r;
  }

  @Override
  public Class<ListOrdersByCustomerQuery> queryType() {
    return ListOrdersByCustomerQuery.class;
  }

  @Override
  public List<Order> handle(ListOrdersByCustomerQuery query) {
    return repository.findByCustomerId(query.customerId());
  }
}
