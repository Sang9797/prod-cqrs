package com.company.orders.application.query;

import com.company.orders.bus.query.Query;
import java.util.List;
import java.util.Set;

public record GetProductInventoryQuery(String productId, Set<String> fields)
        implements
            Query<List<ProductStockItem>> {

    /** REST callers: fetch every field. */
    public static GetProductInventoryQuery all(String productId) {
        return new GetProductInventoryQuery(productId, Set.of());
    }
}
