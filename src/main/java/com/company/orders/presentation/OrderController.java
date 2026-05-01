package com.company.orders.presentation;

import com.company.orders.application.command.CancelOrderCommand;
import com.company.orders.application.command.ConfirmOrderCommand;
import com.company.orders.application.command.PlaceOrderCommand;
import com.company.orders.application.query.GetOrderByIdQuery;
import com.company.orders.application.query.ListOrdersByCustomerQuery;
import com.company.orders.bus.command.CommandBus;
import com.company.orders.bus.query.QueryBus;
import com.company.orders.domain.model.Order;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PRESENTATION — OrderController
 *
 * <p>Only two dependencies: CommandBus and QueryBus. No service classes, no repositories, no
 * business logic.
 *
 * <p>Virtual thread note: With spring.threads.virtual.enabled=true, each HTTP request is handled by
 * a separate virtual thread. The JVM can spawn millions of virtual threads with minimal overhead,
 * so high concurrency comes for free.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(
    name = "Orders",
    description = "CQRS Order Management — Spring Boot 4 / Java 25 / Virtual Threads")
public class OrderController {

  private final CommandBus commandBus;
  private final QueryBus queryBus;

  public OrderController(CommandBus commandBus, QueryBus queryBus) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
  }

  @PostMapping
  @Operation(
      summary = "Place a new order",
      responses = {
        @ApiResponse(responseCode = "201", description = "Order created"),
        @ApiResponse(responseCode = "400", description = "Validation failed")
      })
  public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {

    Order order =
        commandBus.dispatch(
            new PlaceOrderCommand(
                request.customerId(),
                request.items().stream()
                    .map(
                        i ->
                            new PlaceOrderCommand.OrderItemCmd(
                                i.productId(),
                                i.productName(),
                                i.quantity(),
                                i.unitPrice(),
                                i.currency()))
                    .toList()));
    return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
  }

  @GetMapping("/{orderId}")
  @Operation(summary = "Get order by ID")
  public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
    Optional<Order> order = queryBus.dispatch(new GetOrderByIdQuery(orderId));
    return order
        .map(o -> ResponseEntity.ok(OrderResponse.from(o)))
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping
  @Operation(summary = "List orders by customer ID")
  public ResponseEntity<List<OrderResponse>> listOrders(@RequestParam String customerId) {
    List<Order> orders = queryBus.dispatch(new ListOrdersByCustomerQuery(customerId));
    return ResponseEntity.ok(orders.stream().map(OrderResponse::from).toList());
  }

  @PostMapping("/{orderId}/confirm")
  @Operation(summary = "Confirm a PENDING order")
  public ResponseEntity<Void> confirmOrder(@PathVariable String orderId) {
    commandBus.dispatch(new ConfirmOrderCommand(orderId));
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{orderId}")
  @Operation(summary = "Cancel a PENDING or CONFIRMED order")
  public ResponseEntity<Void> cancelOrder(
      @PathVariable String orderId, @Valid @RequestBody CancelRequest request) {
    commandBus.dispatch(new CancelOrderCommand(orderId, request.reason()));
    return ResponseEntity.noContent().build();
  }

  // ── Request DTOs ──────────────────────────────────────────────────────────

  public record PlaceOrderRequest(
      @NotBlank(message = "customerId is required") String customerId,
      @NotEmpty(message = "At least one item is required") @Valid List<ItemRequest> items) {}

  public record ItemRequest(
      @NotBlank(message = "productId is required") String productId,
      @NotBlank(message = "productName is required") String productName,
      @Min(value = 1, message = "quantity must be >= 1") int quantity,
      @DecimalMin(value = "0.01", message = "unitPrice must be > 0") double unitPrice,
      @NotBlank(message = "currency is required") String currency) {}

  public record CancelRequest(@NotBlank(message = "reason is required") String reason) {}
}
