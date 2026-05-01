package com.company.orders.application.query;

import com.company.orders.bus.query.Query;
import com.company.orders.domain.model.Order;
import java.util.Optional;

public record GetOrderByIdQuery(String orderId) implements Query<Optional<Order>> {}
