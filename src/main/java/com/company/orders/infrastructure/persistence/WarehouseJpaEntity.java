package com.company.orders.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "warehouses")
public class WarehouseJpaEntity {

    @Id
    @Column(name = "warehouse_id", length = 36)
    private String warehouseId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "location_code", nullable = false, length = 20, unique = true)
    private String locationCode;

    @Column(nullable = false, length = 20)
    private String region;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public WarehouseJpaEntity() {
    }

    public WarehouseJpaEntity(
            String warehouseId,
            String name,
            String locationCode,
            String region,
            boolean active,
            Instant createdAt) {
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
