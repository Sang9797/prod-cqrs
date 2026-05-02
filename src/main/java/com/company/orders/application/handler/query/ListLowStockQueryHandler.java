package com.company.orders.application.handler.query;

import com.company.orders.application.query.ListLowStockQuery;
import com.company.orders.application.query.LowStockItem;
import com.company.orders.bus.query.QueryHandler;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ListLowStockQueryHandler
    implements QueryHandler<ListLowStockQuery, List<LowStockItem>> {

  // GraphQL field name → SQL expression (order matters for readability, not correctness)
  private static final Map<String, String> COLUMN_MAP = new LinkedHashMap<>();

  static {
    COLUMN_MAP.put("productId", "p.product_id");
    COLUMN_MAP.put("sku", "p.sku");
    COLUMN_MAP.put("productName", "p.name AS product_name");
    COLUMN_MAP.put("warehouseId", "w.warehouse_id");
    COLUMN_MAP.put("warehouseName", "w.name AS warehouse_name");
    COLUMN_MAP.put("region", "w.region");
    COLUMN_MAP.put("quantityAvailable", "i.quantity_available");
    COLUMN_MAP.put("quantityReserved", "i.quantity_reserved");
    COLUMN_MAP.put("quantityFree", "(i.quantity_available - i.quantity_reserved) AS quantity_free");
  }

  private static final String SQL_BODY =
      "  FROM inventory i"
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
    Set<String> fields = query.fields();
    String selectClause = buildSelect(fields);
    String sql = "SELECT " + selectClause + SQL_BODY;
    return jdbc.query(
        sql,
        Map.of("threshold", query.threshold(), "limit", query.limit()),
        (rs, rowNum) -> mapRow(rs, rowNum, fields));
  }

  private static String buildSelect(Set<String> fields) {
    if (fields.isEmpty()) {
      return COLUMN_MAP.values().stream().collect(Collectors.joining(", "));
    }
    String cols =
        COLUMN_MAP.entrySet().stream()
            .filter(e -> fields.contains(e.getKey()))
            .map(Map.Entry::getValue)
            .collect(Collectors.joining(", "));
    // Always need at least one column; fall back to a constant if client sent no valid fields.
    return cols.isEmpty() ? "1" : cols;
  }

  private static LowStockItem mapRow(ResultSet rs, int rowNum, Set<String> fields)
      throws SQLException {
    boolean all = fields.isEmpty();
    return new LowStockItem(
        str(rs, "product_id", all || fields.contains("productId")),
        str(rs, "sku", all || fields.contains("sku")),
        str(rs, "product_name", all || fields.contains("productName")),
        str(rs, "warehouse_id", all || fields.contains("warehouseId")),
        str(rs, "warehouse_name", all || fields.contains("warehouseName")),
        str(rs, "region", all || fields.contains("region")),
        num(rs, "quantity_available", all || fields.contains("quantityAvailable")),
        num(rs, "quantity_reserved", all || fields.contains("quantityReserved")),
        num(rs, "quantity_free", all || fields.contains("quantityFree")));
  }

  private static String str(ResultSet rs, String col, boolean selected) throws SQLException {
    return selected ? rs.getString(col) : null;
  }

  private static int num(ResultSet rs, String col, boolean selected) throws SQLException {
    return selected ? rs.getInt(col) : 0;
  }
}
