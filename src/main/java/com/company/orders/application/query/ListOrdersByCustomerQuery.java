package com.company.orders.application.query;

import com.company.orders.bus.query.Query;
import com.company.orders.domain.model.Order;
import java.util.List;

public record ListOrdersByCustomerQuery(String customerId) implements Query<List<Order>> {}
