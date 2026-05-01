package com.company.orders.application.handler.query;

import com.company.orders.application.query.GetInventoryReportQuery;
import com.company.orders.application.query.InventoryReportItem;
import com.company.orders.bus.query.QueryHandler;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SLOW QUERY — deliberately unoptimised for k6 performance benchmarking.
 *
 * <p>The subquery over inventory_transactions performs a full table scan because no composite index
 * exists on (product_id, warehouse_id). After establishing a k6 baseline, add:
 *
 * <pre>
 *   CREATE INDEX idx_inv_tx_product_warehouse
 *       ON inventory_transactions(product_id, warehouse_id);
 * </pre>
 *
 * then re-run k6 to measure the improvement.
 */
@Component
@Transactional(readOnly = true)
public class GetInventoryReportQueryHandler
    implements QueryHandler<GetInventoryReportQuery, List<InventoryReportItem>> {

  private static final String REPORT_SQL =
      "SELECT"
          + "    COALESCE(pc_parent.name, 'Root') AS parent_category_name,"
          + "    pc.name                           AS category_name,"
          + "    p.product_id,"
          + "    p.sku,"
          + "    p.name                            AS product_name,"
          + "    p.unit_price,"
          + "    p.currency,"
          + "    w.warehouse_id,"
          + "    w.name                            AS warehouse_name,"
          + "    w.region,"
          + "    i.quantity_available,"
          + "    i.quantity_reserved,"
          + "    (i.quantity_available - i.quantity_reserved) AS quantity_free,"
          + "    COALESCE(tx.total_received, 0)    AS total_received,"
          + "    COALESCE(tx.total_shipped, 0)     AS total_shipped,"
          + "    COALESCE(tx.transaction_count, 0) AS transaction_count,"
          + "    COALESCE(tx.last_movement, i.last_updated) AS last_movement"
          + "  FROM products p"
          + "  JOIN product_categories pc"
          + "    ON p.category_id = pc.category_id"
          + "  LEFT JOIN product_categories pc_parent"
          + "    ON pc.parent_category_id = pc_parent.category_id"
          + "  JOIN inventory i"
          + "    ON p.product_id = i.product_id"
          + "  JOIN warehouses w"
          + "    ON i.warehouse_id = w.warehouse_id"
          + "  LEFT JOIN ("
          + "    SELECT"
          + "        product_id,"
          + "        warehouse_id,"
          + "        SUM(CASE WHEN quantity_delta > 0 THEN quantity_delta ELSE 0 END)"
          + "            AS total_received,"
          + "        SUM(CASE WHEN quantity_delta < 0 THEN ABS(quantity_delta) ELSE 0 END)"
          + "            AS total_shipped,"
          + "        COUNT(*) AS transaction_count,"
          + "        MAX(created_at) AS last_movement"
          + "    FROM inventory_transactions"
          + "    GROUP BY product_id, warehouse_id"
          + "  ) tx ON p.product_id = tx.product_id AND i.warehouse_id = tx.warehouse_id"
          + "  WHERE p.is_active = true"
          + "    AND i.quantity_available >= :minStock"
          + "    AND (:categoryId IS NULL OR pc.category_id = :categoryId)"
          + "    AND (:warehouseId IS NULL OR w.warehouse_id = :warehouseId)"
          + "  ORDER BY pc_parent.name NULLS LAST, pc.name, p.name, w.name"
          + "  LIMIT :pageSize OFFSET :offset";

  private final NamedParameterJdbcTemplate jdbc;

  public GetInventoryReportQueryHandler(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Class<GetInventoryReportQuery> queryType() {
    return GetInventoryReportQuery.class;
  }

  @Override
  public List<InventoryReportItem> handle(GetInventoryReportQuery query) {
    Map<String, Object> params = new HashMap<>();
    params.put("minStock", query.minStock());
    params.put("categoryId", query.categoryId());
    params.put("warehouseId", query.warehouseId());
    params.put("pageSize", query.pageSize());
    params.put("offset", (long) query.page() * query.pageSize());
    return jdbc.query(REPORT_SQL, params, GetInventoryReportQueryHandler::mapRow);
  }

  private static InventoryReportItem mapRow(ResultSet rs, int rowNum) throws SQLException {
    Timestamp lastMovementTs = rs.getTimestamp("last_movement");
    Instant lastMovement = lastMovementTs != null ? lastMovementTs.toInstant() : Instant.now();
    return new InventoryReportItem(
        rs.getString("parent_category_name"),
        rs.getString("category_name"),
        rs.getString("product_id"),
        rs.getString("sku"),
        rs.getString("product_name"),
        rs.getBigDecimal("unit_price"),
        rs.getString("currency"),
        rs.getString("warehouse_id"),
        rs.getString("warehouse_name"),
        rs.getString("region"),
        rs.getInt("quantity_available"),
        rs.getInt("quantity_reserved"),
        rs.getInt("quantity_free"),
        rs.getLong("total_received"),
        rs.getLong("total_shipped"),
        rs.getLong("transaction_count"),
        lastMovement);
  }
}
