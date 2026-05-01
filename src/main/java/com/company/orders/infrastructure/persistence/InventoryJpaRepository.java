package com.company.orders.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryJpaRepository extends JpaRepository<InventoryJpaEntity, String> {

  @Query(
      "SELECT i FROM InventoryJpaEntity i"
          + " WHERE i.productId = :productId AND i.warehouseId = :warehouseId")
  Optional<InventoryJpaEntity> findByProductIdAndWarehouseId(
      @Param("productId") String productId, @Param("warehouseId") String warehouseId);
}
