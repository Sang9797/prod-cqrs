package com.company.orders.application.query;

import com.company.orders.bus.query.Query;
import java.util.List;
import java.util.Set;

public record GetInventoryReportQuery(
    String categoryId, String warehouseId, int minStock, int page, int pageSize, Set<String> fields)
    implements Query<List<InventoryReportItem>> {

  /** REST callers: fetch every field. */
  public static GetInventoryReportQuery all(
      String categoryId, String warehouseId, int minStock, int page, int pageSize) {
    return new GetInventoryReportQuery(categoryId, warehouseId, minStock, page, pageSize, Set.of());
  }
}
