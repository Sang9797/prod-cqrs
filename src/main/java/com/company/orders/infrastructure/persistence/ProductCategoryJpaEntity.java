package com.company.orders.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "product_categories")
public class ProductCategoryJpaEntity {

    @Id
    @Column(name = "category_id", length = 36)
    private String categoryId;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    private ProductCategoryJpaEntity parent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ProductCategoryJpaEntity() {
    }

    public ProductCategoryJpaEntity(
            String categoryId, String name, ProductCategoryJpaEntity parent, Instant createdAt) {
        this.categoryId = categoryId;
        this.name = name;
        this.parent = parent;
        this.createdAt = createdAt;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    public ProductCategoryJpaEntity getParent() {
        return parent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
