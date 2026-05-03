package com.company.orders.presentation.graphql;

import com.company.orders.application.command.AdjustInventoryCommand;
import com.company.orders.application.command.ReleaseInventoryCommand;
import com.company.orders.application.command.ReserveInventoryCommand;
import com.company.orders.application.query.GetInventoryReportQuery;
import com.company.orders.application.query.GetProductInventoryQuery;
import com.company.orders.application.query.InventoryReportItem;
import com.company.orders.application.query.ListLowStockQuery;
import com.company.orders.application.query.LowStockItem;
import com.company.orders.application.query.ProductStockItem;
import com.company.orders.bus.command.CommandBus;
import com.company.orders.bus.query.QueryBus;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class InventoryGraphQlController {

  private final CommandBus commandBus;
  private final QueryBus queryBus;

  public InventoryGraphQlController(CommandBus commandBus, QueryBus queryBus) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
  }

  // ── Queries ───────────────────────────────────────────────────────────────

  @QueryMapping
  public List<InventoryReportItem> inventoryReport(
      @Argument String categoryId,
      @Argument String warehouseId,
      @Argument int minStock,
      @Argument int page,
      @Argument int pageSize,
      DataFetchingEnvironment env) {
    return queryBus.dispatch(
        new GetInventoryReportQuery(
            categoryId, warehouseId, minStock, page, pageSize, requestedFields(env)));
  }

  @QueryMapping
  public List<ProductStockItem> productStock(
      @Argument String productId, DataFetchingEnvironment env) {
    return queryBus.dispatch(new GetProductInventoryQuery(productId, requestedFields(env)));
  }

  @QueryMapping
  public List<LowStockItem> lowStock(
      @Argument int threshold, @Argument int limit, DataFetchingEnvironment env) {
    return queryBus.dispatch(new ListLowStockQuery(threshold, limit, requestedFields(env)));
  }

  // ── Mutations ─────────────────────────────────────────────────────────────

  @MutationMapping
  public boolean reserveInventory(@Argument ReserveInput input) {
    commandBus.dispatch(
        new ReserveInventoryCommand(
            input.productId(), input.warehouseId(), input.quantity(), input.orderId()));
    return true;
  }

  @MutationMapping
  public boolean releaseInventory(@Argument ReleaseInput input) {
    commandBus.dispatch(
        new ReleaseInventoryCommand(
            input.productId(), input.warehouseId(), input.quantity(), input.orderId()));
    return true;
  }

  @MutationMapping
  public boolean adjustInventory(@Argument AdjustInput input) {
    commandBus.dispatch(
        new AdjustInventoryCommand(
            input.productId(), input.warehouseId(), input.delta(), input.reason()));
    return true;
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private static Set<String> requestedFields(DataFetchingEnvironment env) {
    return env.getSelectionSet().getImmediateFields().stream()
        .map(SelectedField::getName)
        .collect(Collectors.toUnmodifiableSet());
  }

  // ── Input records ─────────────────────────────────────────────────────────

  public record ReserveInput(String productId, String warehouseId, int quantity, String orderId) {}

  public record ReleaseInput(String productId, String warehouseId, int quantity, String orderId) {}

  public record AdjustInput(String productId, String warehouseId, int delta, String reason) {}
}
