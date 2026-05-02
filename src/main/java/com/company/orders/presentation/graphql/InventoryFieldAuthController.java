package com.company.orders.presentation.graphql;

import com.company.orders.application.query.InventoryReportItem;
import com.company.orders.application.query.ProductStockItem;
import java.util.Objects;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
public class InventoryFieldAuthController {

  private static final String PRICE_PERMISSION = "INVENTORY_PRICE";

  @SchemaMapping(typeName = "InventoryReportItem", field = "unitPrice")
  public Double unitPrice(InventoryReportItem item, Authentication auth) {
    if (!hasPermission(auth)) return null;
    return item.unitPrice() != null ? item.unitPrice().doubleValue() : null;
  }

  @SchemaMapping(typeName = "InventoryReportItem", field = "totalReceived")
  public Long totalReceived(InventoryReportItem item, Authentication auth) {
    return hasPermission(auth) ? item.totalReceived() : null;
  }

  @SchemaMapping(typeName = "InventoryReportItem", field = "totalShipped")
  public Long totalShipped(InventoryReportItem item, Authentication auth) {
    return hasPermission(auth) ? item.totalShipped() : null;
  }

  @SchemaMapping(typeName = "InventoryReportItem", field = "transactionCount")
  public Long transactionCount(InventoryReportItem item, Authentication auth) {
    return hasPermission(auth) ? item.transactionCount() : null;
  }

  @SchemaMapping(typeName = "ProductStockItem", field = "unitPrice")
  public Double unitPrice(ProductStockItem item, Authentication auth) {
    if (!hasPermission(auth)) return null;
    return item.unitPrice() != null ? item.unitPrice().doubleValue() : null;
  }

  private static boolean hasPermission(Authentication auth) {
    if (auth == null) return false;
    return auth.getAuthorities().stream()
        .anyMatch(a -> Objects.equals(a.getAuthority(), PRICE_PERMISSION));
  }
}
