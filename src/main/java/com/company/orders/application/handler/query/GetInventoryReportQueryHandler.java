package com.company.orders.application.handler.query;

import com.company.orders.application.query.GetInventoryReportQuery;
import com.company.orders.application.query.InventoryReportItem;
import com.company.orders.bus.query.QueryHandler;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
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
 *
 * <p>When called from GraphQL, the tx JOIN is skipped entirely if the client did not request any
 * of: totalReceived, totalShipped, transactionCount, lastMovement. This is the primary GraphQL
 * performance advantage over REST for this endpoint.
 */
@Component
@Transactional(readOnly = true)
public class GetInventoryReportQueryHandler
    implements QueryHandler<GetInventoryReportQuery, List<InventoryReportItem>> {

  // Fields that require the inventory_transactions subquery JOIN
  private static final Set<String> TX_FIELDS =
      Set.of("totalReceived", "totalShipped", "transactionCount", "lastMovement");

  private static final Map<String, String> COLUMN_MAP = new LinkedHashMap<>();
  private static final Map<String, String> COLUMN_MAP_WITH_TX = new LinkedHashMap<>();

  static {
    COLUMN_MAP.put(
        "parentCategoryName", "COALESCE(pc_parent.name, 'Root') AS parent_category_name");
    COLUMN_MAP.put("categoryName", "pc.name AS category_name");
    COLUMN_MAP.put("productId", "p.product_id");
    COLUMN_MAP.put("sku", "p.sku");
    COLUMN_MAP.put("productName", "p.name AS product_name");
    COLUMN_MAP.put("unitPrice", "p.unit_price");
    COLUMN_MAP.put("currency", "p.currency");
    COLUMN_MAP.put("warehouseId", "w.warehouse_id");
    COLUMN_MAP.put("warehouseName", "w.name AS warehouse_name");
    COLUMN_MAP.put("region", "w.region");
    COLUMN_MAP.put("quantityAvailable", "i.quantity_available");
    COLUMN_MAP.put("quantityReserved", "i.quantity_reserved");
    COLUMN_MAP.put("quantityFree", "(i.quantity_available - i.quantity_reserved) AS quantity_free");
    COLUMN_MAP.put("lastMovement", "i.last_updated AS last_movement");

    // Same columns but with tx subquery available for the tx fields
    COLUMN_MAP_WITH_TX.putAll(COLUMN_MAP);
    COLUMN_MAP_WITH_TX.put("totalReceived", "COALESCE(tx.total_received, 0) AS total_received");
    COLUMN_MAP_WITH_TX.put("totalShipped", "COALESCE(tx.total_shipped, 0) AS total_shipped");
    COLUMN_MAP_WITH_TX.put(
        "transactionCount", "COALESCE(tx.transaction_count, 0) AS transaction_count");
    COLUMN_MAP_WITH_TX.put(
        "lastMovement", "COALESCE(tx.last_movement, i.last_updated) AS last_movement");
  }

  private static final String SQL_JOINS_NO_TX =
      "  FROM products p  JOIN product_categories pc ON p.category_id = pc.category_id  LEFT JOIN"
          + " product_categories pc_parent ON pc.parent_category_id = pc_parent.category_id  JOIN"
          + " inventory i ON p.product_id = i.product_id  JOIN warehouses w ON i.warehouse_id ="
          + " w.warehouse_id";

  private static final String TX_SUBQUERY =
      "  LEFT JOIN (    SELECT        product_id,        warehouse_id,        SUM(CASE WHEN"
          + " quantity_delta > 0 THEN quantity_delta ELSE 0 END) AS total_received,        SUM(CASE"
          + " WHEN quantity_delta < 0 THEN ABS(quantity_delta) ELSE 0 END) AS total_shipped,       "
          + " COUNT(*) AS transaction_count,        MAX(created_at) AS last_movement    FROM"
          + " inventory_transactions    GROUP BY product_id, warehouse_id  ) tx ON p.product_id ="
          + " tx.product_id AND i.warehouse_id = tx.warehouse_id";

  private static final String SQL_WHERE_AND_ORDER =
      "  WHERE p.is_active = true"
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
    Set<String> fields = query.fields();
    boolean needsTx = needsTxJoin(fields);

    Map<String, String> columnMap = needsTx ? COLUMN_MAP_WITH_TX : COLUMN_MAP;
    String selectClause = buildSelect(fields, columnMap);
    String sql =
        "SELECT "
            + selectClause
            + SQL_JOINS_NO_TX
            + (needsTx ? TX_SUBQUERY : "")
            + SQL_WHERE_AND_ORDER;

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("minStock", query.minStock())
            .addValue("categoryId", query.categoryId(), Types.VARCHAR)
            .addValue("warehouseId", query.warehouseId(), Types.VARCHAR)
            .addValue("pageSize", query.pageSize())
            .addValue("offset", (long) query.page() * query.pageSize());

    return jdbc.query(sql, params, (rs, rowNum) -> mapRow(rs, rowNum, fields, needsTx));
  }

  private static boolean needsTxJoin(Set<String> fields) {
    // Empty fields = REST caller fetching everything (always needs tx join for full report)
    if (fields.isEmpty()) {
      return true;
    }
    return fields.stream().anyMatch(TX_FIELDS::contains);
  }

  private static String buildSelect(Set<String> fields, Map<String, String> columnMap) {
    if (fields.isEmpty()) {
      return columnMap.values().stream().collect(Collectors.joining(", "));
    }
    String cols =
        columnMap.entrySet().stream()
            .filter(e -> fields.contains(e.getKey()))
            .map(Map.Entry::getValue)
            .collect(Collectors.joining(", "));
    return cols.isEmpty() ? "1" : cols;
  }

  private static InventoryReportItem mapRow(
      ResultSet rs, int rowNum, Set<String> fields, boolean hasTx) throws SQLException {
    boolean all = fields.isEmpty();

    BigDecimal unitPrice = null;
    if (all || fields.contains("unitPrice")) {
      unitPrice = rs.getBigDecimal("unit_price");
    }

    Instant lastMovement = null;
    if (all || fields.contains("lastMovement")) {
      Timestamp ts = rs.getTimestamp("last_movement");
      lastMovement = ts != null ? ts.toInstant() : Instant.now();
    }

    return new InventoryReportItem(
        str(rs, "parent_category_name", all || fields.contains("parentCategoryName")),
        str(rs, "category_name", all || fields.contains("categoryName")),
        str(rs, "product_id", all || fields.contains("productId")),
        str(rs, "sku", all || fields.contains("sku")),
        str(rs, "product_name", all || fields.contains("productName")),
        unitPrice,
        str(rs, "currency", all || fields.contains("currency")),
        str(rs, "warehouse_id", all || fields.contains("warehouseId")),
        str(rs, "warehouse_name", all || fields.contains("warehouseName")),
        str(rs, "region", all || fields.contains("region")),
        num(rs, "quantity_available", all || fields.contains("quantityAvailable")),
        num(rs, "quantity_reserved", all || fields.contains("quantityReserved")),
        num(rs, "quantity_free", all || fields.contains("quantityFree")),
        hasTx ? rs.getLong("total_received") : 0L,
        hasTx ? rs.getLong("total_shipped") : 0L,
        hasTx ? rs.getLong("transaction_count") : 0L,
        lastMovement);
  }

  private static String str(ResultSet rs, String col, boolean selected) throws SQLException {
    return selected ? rs.getString(col) : null;
  }

  private static int num(ResultSet rs, String col, boolean selected) throws SQLException {
    return selected ? rs.getInt(col) : 0;
  }
}
