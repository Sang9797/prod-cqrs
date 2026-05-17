package com.company.orders.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryJpaRepository
        extends
            JpaRepository<ProductCategoryJpaEntity, String> {
}
