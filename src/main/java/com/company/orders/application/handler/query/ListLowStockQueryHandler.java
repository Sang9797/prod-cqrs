package com.company.orders.application.handler.query;

import com.company.orders.application.query.ListLowStockQuery;
import com.company.orders.application.query.LowStockItem;
import com.company.orders.bus.query.QueryHandler;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ListLowStockQueryHandler
    implements QueryHandler<ListLowStockQuery, List<LowStockItem>> {

  private static final String LOW_STOCK_SQL =
      "SELECT"
          + "    p.product_id, p.sku, p.name AS product_name,"
          + "    w.warehouse_id, w.name AS warehouse_name, w.region,"
          + "    i.quantity_available, i.quantity_reserved,"
          + "    (i.quantity_available - i.quantity_reserved) AS quantity_free"
          + "  FROM inventory i"
          + "  JOIN products p ON i.product_id = p.product_id"
          + "  JOIN warehouses w ON i.warehouse_id = w.warehouse_id"
          + "  WHERE p.is_active = true"
          + "    AND (i.quantity_available - i.quantity_reserved) <= :threshold"
          + "  ORDER BY (i.quantity_available - i.quantity_reserved) ASC, p.name"
          + "  LIMIT :limit";

  private final NamedParameterJdbcTemplate jdbc;

  public ListLowStockQueryHandler(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Class<ListLowStockQuery> queryType() {
    return ListLowStockQuery.class;
  }

  @Override
  public List<LowStockItem> handle(ListLowStockQuery query) {
    return jdbc.query(
        LOW_STOCK_SQL,
        Map.of("threshold", query.threshold(), "limit", query.limit()),
        ListLowStockQueryHandler::mapRow);
  }

  private static LowStockItem mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new LowStockItem(
        rs.getString("product_id"),
        rs.getString("sku"),
        rs.getString("product_name"),
        rs.getString("warehouse_id"),
        rs.getString("warehouse_name"),
        rs.getString("region"),
        rs.getInt("quantity_available"),
        rs.getInt("quantity_reserved"),
        rs.getInt("quantity_free"));
  }
}
