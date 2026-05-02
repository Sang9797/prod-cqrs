package com.company.orders.application.handler.query;

import com.company.orders.application.query.GetProductInventoryQuery;
import com.company.orders.application.query.ProductStockItem;
import com.company.orders.bus.query.QueryHandler;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
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
public class GetProductInventoryQueryHandler
    implements QueryHandler<GetProductInventoryQuery, List<ProductStockItem>> {

  private static final Map<String, String> COLUMN_MAP = new LinkedHashMap<>();

  static {
    COLUMN_MAP.put("productId", "p.product_id");
    COLUMN_MAP.put("sku", "p.sku");
    COLUMN_MAP.put("productName", "p.name AS product_name");
    COLUMN_MAP.put("unitPrice", "p.unit_price");
    COLUMN_MAP.put("currency", "p.currency");
    COLUMN_MAP.put("categoryName", "pc.name AS category_name");
    COLUMN_MAP.put("warehouseId", "w.warehouse_id");
    COLUMN_MAP.put("warehouseName", "w.name AS warehouse_name");
    COLUMN_MAP.put("region", "w.region");
    COLUMN_MAP.put("quantityAvailable", "i.quantity_available");
    COLUMN_MAP.put("quantityReserved", "i.quantity_reserved");
    COLUMN_MAP.put("quantityFree", "(i.quantity_available - i.quantity_reserved) AS quantity_free");
    COLUMN_MAP.put("lastUpdated", "i.last_updated");
  }

  private static final String SQL_BODY =
      "  FROM products p"
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
    Set<String> fields = query.fields();
    String selectClause = buildSelect(fields);
    String sql = "SELECT " + selectClause + SQL_BODY;
    return jdbc.query(
        sql, Map.of("productId", query.productId()), (rs, rowNum) -> mapRow(rs, rowNum, fields));
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
    return cols.isEmpty() ? "1" : cols;
  }

  private static ProductStockItem mapRow(ResultSet rs, int rowNum, Set<String> fields)
      throws SQLException {
    boolean all = fields.isEmpty();
    Instant lastUpdated = null;
    if (all || fields.contains("lastUpdated")) {
      Timestamp ts = rs.getTimestamp("last_updated");
      lastUpdated = ts != null ? ts.toInstant() : Instant.now();
    }
    BigDecimal unitPrice = null;
    if (all || fields.contains("unitPrice")) {
      unitPrice = rs.getBigDecimal("unit_price");
    }
    return new ProductStockItem(
        str(rs, "product_id", all || fields.contains("productId")),
        str(rs, "sku", all || fields.contains("sku")),
        str(rs, "product_name", all || fields.contains("productName")),
        unitPrice,
        str(rs, "currency", all || fields.contains("currency")),
        str(rs, "category_name", all || fields.contains("categoryName")),
        str(rs, "warehouse_id", all || fields.contains("warehouseId")),
        str(rs, "warehouse_name", all || fields.contains("warehouseName")),
        str(rs, "region", all || fields.contains("region")),
        num(rs, "quantity_available", all || fields.contains("quantityAvailable")),
        num(rs, "quantity_reserved", all || fields.contains("quantityReserved")),
        num(rs, "quantity_free", all || fields.contains("quantityFree")),
        lastUpdated);
  }

  private static String str(ResultSet rs, String col, boolean selected) throws SQLException {
    return selected ? rs.getString(col) : null;
  }

  private static int num(ResultSet rs, String col, boolean selected) throws SQLException {
    return selected ? rs.getInt(col) : 0;
  }
}
