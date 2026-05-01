-- ============================================================
-- V1 — Initial schema
-- ============================================================
CREATE TABLE IF NOT EXISTS orders (
    order_id      VARCHAR(36)   NOT NULL,
    customer_id   VARCHAR(36)   NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    total_amount  DECIMAL(12,2) NOT NULL CHECK (total_amount >= 0),
    currency      VARCHAR(3)    NOT NULL DEFAULT 'USD',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_orders PRIMARY KEY (order_id),
    CONSTRAINT chk_order_status CHECK (
        status IN ('PENDING','CONFIRMED','SHIPPED','DELIVERED','CANCELLED'))
);

CREATE TABLE IF NOT EXISTS order_items (
    item_id       VARCHAR(36)   NOT NULL,
    order_id      VARCHAR(36)   NOT NULL,
    product_id    VARCHAR(36)   NOT NULL,
    product_name  VARCHAR(255)  NOT NULL,
    quantity      INTEGER       NOT NULL CHECK (quantity > 0),
    unit_price    DECIMAL(12,2) NOT NULL CHECK (unit_price >= 0),
    currency      VARCHAR(3)    NOT NULL DEFAULT 'USD',
    CONSTRAINT pk_order_items  PRIMARY KEY (item_id),
    CONSTRAINT fk_items_order  FOREIGN KEY (order_id)
        REFERENCES orders(order_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_orders_customer_id  ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status        ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at    ON orders(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);
