package com.company.orders.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, String> {

    /** Single SQL with JOIN FETCH — avoids N+1 when loading items. */
    @Query("SELECT DISTINCT o FROM OrderJpaEntity o LEFT JOIN FETCH o.items WHERE o.orderId = :id")
    Optional<OrderJpaEntity> findByIdWithItems(@Param("id") String id);

    /** Loads all orders for a customer newest first, items included. */
    @Query("SELECT DISTINCT o FROM OrderJpaEntity o LEFT JOIN FETCH o.items "
            + "WHERE o.customerId = :cid ORDER BY o.createdAt DESC")
    List<OrderJpaEntity> findByCustomerIdWithItems(@Param("cid") String customerId);
}
