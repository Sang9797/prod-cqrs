-- ============================================================
-- V2 — Inventory Management Schema
-- ============================================================
-- Tables: product_categories, products, warehouses, inventory,
--         inventory_transactions
--
-- NOTE: No composite index on inventory_transactions(product_id, warehouse_id)
-- is intentional — the report query is deliberately slow for performance
-- testing with k6. To optimise, add:
--   CREATE INDEX idx_inv_tx_product_warehouse
--       ON inventory_transactions(product_id, warehouse_id);
-- ============================================================

CREATE TABLE IF NOT EXISTS product_categories (
    category_id        VARCHAR(36)  NOT NULL,
    name               VARCHAR(100) NOT NULL,
    parent_category_id VARCHAR(36)  NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_product_categories PRIMARY KEY (category_id),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_category_id)
        REFERENCES product_categories(category_id)
);

CREATE TABLE IF NOT EXISTS products (
    product_id  VARCHAR(36)   NOT NULL,
    sku         VARCHAR(50)   NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    category_id VARCHAR(36)   NOT NULL,
    unit_price  DECIMAL(12,2) NOT NULL CHECK (unit_price >= 0),
    currency    VARCHAR(3)    NOT NULL DEFAULT 'USD',
    is_active   BOOLEAN       NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_products       PRIMARY KEY (product_id),
    CONSTRAINT uq_products_sku   UNIQUE (sku),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id)
        REFERENCES product_categories(category_id)
);

CREATE TABLE IF NOT EXISTS warehouses (
    warehouse_id  VARCHAR(36) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    location_code VARCHAR(20)  NOT NULL,
    region        VARCHAR(20)  NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_warehouses              PRIMARY KEY (warehouse_id),
    CONSTRAINT uq_warehouses_location_code UNIQUE (location_code)
);

CREATE TABLE IF NOT EXISTS inventory (
    inventory_id       VARCHAR(36) NOT NULL,
    product_id         VARCHAR(36) NOT NULL,
    warehouse_id       VARCHAR(36) NOT NULL,
    quantity_available INTEGER     NOT NULL DEFAULT 0 CHECK (quantity_available >= 0),
    quantity_reserved  INTEGER     NOT NULL DEFAULT 0 CHECK (quantity_reserved >= 0),
    last_updated       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_inventory PRIMARY KEY (inventory_id),
    CONSTRAINT uq_inventory_product_warehouse UNIQUE (product_id, warehouse_id),
    CONSTRAINT fk_inventory_product   FOREIGN KEY (product_id)
        REFERENCES products(product_id),
    CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id)
        REFERENCES warehouses(warehouse_id)
);

CREATE TABLE IF NOT EXISTS inventory_transactions (
    transaction_id   VARCHAR(36)  NOT NULL,
    product_id       VARCHAR(36)  NOT NULL,
    warehouse_id     VARCHAR(36)  NOT NULL,
    order_id         VARCHAR(36)  NULL,
    transaction_type VARCHAR(20)  NOT NULL,
    quantity_delta   INTEGER      NOT NULL,
    notes            VARCHAR(500) NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_inventory_transactions PRIMARY KEY (transaction_id),
    CONSTRAINT chk_inv_tx_type CHECK (
        transaction_type IN ('RECEIVE','SHIP','RESERVE','RELEASE','ADJUST')),
    CONSTRAINT fk_inv_tx_product   FOREIGN KEY (product_id)
        REFERENCES products(product_id),
    CONSTRAINT fk_inv_tx_warehouse FOREIGN KEY (warehouse_id)
        REFERENCES warehouses(warehouse_id)
);

-- Standard single-column indexes
CREATE INDEX IF NOT EXISTS idx_products_category_id    ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_is_active      ON products(is_active) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_inventory_product_id    ON inventory(product_id);
CREATE INDEX IF NOT EXISTS idx_inventory_warehouse_id  ON inventory(warehouse_id);
CREATE INDEX IF NOT EXISTS idx_inv_tx_order_id
    ON inventory_transactions(order_id) WHERE order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_inv_tx_created_at
    ON inventory_transactions(created_at DESC);

-- DELIBERATELY MISSING: idx_inv_tx_product_warehouse
-- Add this to optimise the report query after baseline k6 benchmark:
--   CREATE INDEX idx_inv_tx_product_warehouse
--       ON inventory_transactions(product_id, warehouse_id);
