package com.company.orders.application.query;

import com.company.orders.bus.query.Query;
import java.util.List;
import java.util.Set;

public record ListLowStockQuery(int threshold, int limit, Set<String> fields)
        implements
            Query<List<LowStockItem>> {

    /** REST callers: fetch every field. */
    public static ListLowStockQuery all(int threshold, int limit) {
        return new ListLowStockQuery(threshold, limit, Set.of());
    }
}
