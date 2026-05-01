package com.company.orders.application.query;

import com.company.orders.bus.query.Query;
import java.util.List;

public record GetProductInventoryQuery(String productId) implements Query<List<ProductStockItem>> {}
