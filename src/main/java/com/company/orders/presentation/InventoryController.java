package com.company.orders.presentation;

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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory", description = "Inventory management — products, warehouses, stock levels")
public class InventoryController {

  private final CommandBus commandBus;
  private final QueryBus queryBus;

  public InventoryController(CommandBus commandBus, QueryBus queryBus) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
  }

  @GetMapping("/report")
  @Operation(
      summary = "Full inventory report (slow query — use for k6 baseline benchmarking)",
      description =
          "Joins products, categories, warehouses, inventory, and transaction aggregates."
              + " Deliberately unoptimised: no composite index on"
              + " inventory_transactions(product_id, warehouse_id).")
  public ResponseEntity<List<InventoryReportItem>> getReport(
      @RequestParam(required = false) String categoryId,
      @RequestParam(required = false) String warehouseId,
      @RequestParam(defaultValue = "0") @Min(0) int minStock,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "100") @Min(1) @Max(500) int pageSize) {
    List<InventoryReportItem> items =
        queryBus.dispatch(
            GetInventoryReportQuery.all(categoryId, warehouseId, minStock, page, pageSize));
    return ResponseEntity.ok(items);
  }

  @GetMapping("/products/{productId}/stock")
  @Operation(summary = "Get stock levels for a specific product across all warehouses")
  public ResponseEntity<List<ProductStockItem>> getProductStock(@PathVariable String productId) {
    List<ProductStockItem> items = queryBus.dispatch(GetProductInventoryQuery.all(productId));
    return ResponseEntity.ok(items);
  }

  @GetMapping("/low-stock")
  @Operation(summary = "List products with free stock at or below the threshold")
  public ResponseEntity<List<LowStockItem>> getLowStock(
      @RequestParam(defaultValue = "10") @Min(0) int threshold,
      @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
    List<LowStockItem> items = queryBus.dispatch(ListLowStockQuery.all(threshold, limit));
    return ResponseEntity.ok(items);
  }

  @PostMapping("/reserve")
  @Operation(summary = "Reserve stock for an order")
  public ResponseEntity<Void> reserve(@Valid @RequestBody ReserveRequest request) {
    commandBus.dispatch(
        new ReserveInventoryCommand(
            request.productId(), request.warehouseId(), request.quantity(), request.orderId()));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/release")
  @Operation(summary = "Release previously reserved stock")
  public ResponseEntity<Void> release(@Valid @RequestBody ReleaseRequest request) {
    commandBus.dispatch(
        new ReleaseInventoryCommand(
            request.productId(), request.warehouseId(), request.quantity(), request.orderId()));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/adjust")
  @Operation(summary = "Manual stock adjustment (positive = receive, negative = write-off)")
  public ResponseEntity<Void> adjust(@Valid @RequestBody AdjustRequest request) {
    commandBus.dispatch(
        new AdjustInventoryCommand(
            request.productId(), request.warehouseId(), request.delta(), request.reason()));
    return ResponseEntity.noContent().build();
  }

  // ── Request DTOs ──────────────────────────────────────────────────────────

  public record ReserveRequest(
      @NotBlank String productId,
      @NotBlank String warehouseId,
      @Positive int quantity,
      @NotBlank String orderId) {}

  public record ReleaseRequest(
      @NotBlank String productId,
      @NotBlank String warehouseId,
      @Positive int quantity,
      @NotBlank String orderId) {}

  public record AdjustRequest(
      @NotBlank String productId,
      @NotBlank String warehouseId,
      int delta,
      @NotBlank String reason) {}
}
