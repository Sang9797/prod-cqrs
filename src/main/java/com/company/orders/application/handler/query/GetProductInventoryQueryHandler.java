package com.company.orders.application.handler.query;

import com.company.orders.application.query.GetProductInventoryQuery;
import com.company.orders.application.query.ProductStockItem;
import com.company.orders.bus.query.QueryHandler;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class GetProductInventoryQueryHandler
    implements QueryHandler<GetProductInventoryQuery, List<ProductStockItem>> {

  private static final String PRODUCT_STOCK_SQL =
      "SELECT"
          + "    p.product_id, p.sku, p.name AS product_name,"
          + "    p.unit_price, p.currency,"
          + "    pc.name AS category_name,"
          + "    w.warehouse_id, w.name AS warehouse_name, w.region,"
          + "    i.quantity_available, i.quantity_reserved,"
          + "    (i.quantity_available - i.quantity_reserved) AS quantity_free,"
          + "    i.last_updated"
          + "  FROM products p"
          + "  JOIN product_categories pc ON p.category_id = pc.category_id"
          + "  JOIN inventory i ON p.product_id = i.product_id"
          + "  JOIN warehouses w ON i.warehouse_id = w.warehouse_id"
          + "  WHERE p.product_id = :productId"
          + "  ORDER BY w.name";

  private final NamedParameterJdbcTemplate jdbc;

  public GetProductInventoryQueryHandler(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Class<GetProductInventoryQuery> queryType() {
    return GetProductInventoryQuery.class;
  }

  @Override
  public List<ProductStockItem> handle(GetProductInventoryQuery query) {
    return jdbc.query(
        PRODUCT_STOCK_SQL,
        Map.of("productId", query.productId()),
        GetProductInventoryQueryHandler::mapRow);
  }

  private static ProductStockItem mapRow(ResultSet rs, int rowNum) throws SQLException {
    Timestamp lastUpdatedTs = rs.getTimestamp("last_updated");
    Instant lastUpdated = lastUpdatedTs != null ? lastUpdatedTs.toInstant() : Instant.now();
    return new ProductStockItem(
        rs.getString("product_id"),
        rs.getString("sku"),
        rs.getString("product_name"),
        rs.getBigDecimal("unit_price"),
        rs.getString("currency"),
        rs.getString("category_name"),
        rs.getString("warehouse_id"),
        rs.getString("warehouse_name"),
        rs.getString("region"),
        rs.getInt("quantity_available"),
        rs.getInt("quantity_reserved"),
        rs.getInt("quantity_free"),
        lastUpdated);
  }
}
