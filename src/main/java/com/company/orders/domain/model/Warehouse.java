package com.company.orders.domain.model;

import java.time.Instant;
import java.util.Objects;

public class Warehouse {

  private final String warehouseId;
  private final String name;
  private final String locationCode;
  private final String region;
  private final boolean active;
  private final Instant createdAt;

  public Warehouse(
      String warehouseId,
      String name,
      String locationCode,
      String region,
      boolean active,
      Instant createdAt) {
    Objects.requireNonNull(warehouseId, "warehouseId required");
    Objects.requireNonNull(name, "name required");
    Objects.requireNonNull(locationCode, "locationCode required");
    Objects.requireNonNull(region, "region required");
    this.warehouseId = warehouseId;
    this.name = name;
    this.locationCode = locationCode;
    this.region = region;
    this.active = active;
    this.createdAt = createdAt;
  }

  public String getWarehouseId() {
    return warehouseId;
  }

  public String getName() {
    return name;
  }

  public String getLocationCode() {
    return locationCode;
  }

  public String getRegion() {
    return region;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
