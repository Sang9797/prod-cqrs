package com.company.orders.application.query;

import com.company.orders.bus.query.Query;
import java.util.List;

public record GetInventoryReportQuery(
    String categoryId, String warehouseId, int minStock, int page, int pageSize)
    implements Query<List<InventoryReportItem>> {}
