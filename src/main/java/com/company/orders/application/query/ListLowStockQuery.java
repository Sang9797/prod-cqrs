package com.company.orders.application.query;

import com.company.orders.bus.query.Query;
import java.util.List;

public record ListLowStockQuery(int threshold, int limit) implements Query<List<LowStockItem>> {}
