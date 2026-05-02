-- ============================================================
-- V3 — Seed large-volume inventory data for k6 performance tests
-- ============================================================
-- 10 parent categories, 40 child categories
-- 5 000 products (125 per child category)
-- 10 warehouses
-- 50 000 inventory rows  (every product × every warehouse)
-- 100 000 transactions   (2 per inventory row on average)
-- ============================================================

-- Parent categories
INSERT INTO product_categories (category_id, name, parent_category_id)
SELECT
    'cat-p-' || n,
    'Category ' || n,
    NULL
FROM generate_series(1, 10) AS n
ON CONFLICT (category_id) DO NOTHING;

-- Child categories (4 children per parent)
INSERT INTO product_categories (category_id, name, parent_category_id)
SELECT
    'cat-c-' || n,
    'Sub-Category ' || n,
    'cat-p-' || ((n - 1) / 4 + 1)
FROM generate_series(1, 40) AS n
ON CONFLICT (category_id) DO NOTHING;

-- 10 warehouses spread across 4 regions
INSERT INTO warehouses (warehouse_id, name, location_code, region, is_active, created_at)
SELECT
    'wh-' || n,
    'Warehouse ' || n,
    'WH-' || LPAD(n::text, 3, '0'),
    CASE (n % 4)
        WHEN 0 THEN 'NORTH'
        WHEN 1 THEN 'SOUTH'
        WHEN 2 THEN 'EAST'
        ELSE       'WEST'
    END,
    true,
    NOW()
FROM generate_series(1, 10) AS n
ON CONFLICT (location_code) DO NOTHING;

-- 5 000 products distributed evenly across 40 child categories
INSERT INTO products (
    product_id, sku, name, category_id,
    unit_price, currency, is_active, created_at, updated_at
)
SELECT
    'prod-' || n,
    'SKU-' || LPAD(n::text, 6, '0'),
    'Product ' || n,
    'cat-c-' || ((n - 1) % 40 + 1),
    ROUND((10 + (n % 990))::numeric, 2),
    'USD',
    true,
    NOW() - ((n % 365) || ' days')::interval,
    NOW()
FROM generate_series(1, 5000) AS n
ON CONFLICT (product_id) DO NOTHING;

-- 50 000 inventory rows: every product exists in every warehouse
INSERT INTO inventory (
    inventory_id, product_id, warehouse_id,
    quantity_available, quantity_reserved, last_updated
)
SELECT
    'inv-' || p_n || '-' || w_n,
    'prod-' || p_n,
    'wh-' || w_n,
    50 + (p_n * w_n) % 1000,
    (p_n + w_n) % 50,
    NOW() - ((p_n % 30) || ' days')::interval
FROM generate_series(1, 5000) AS p_n,
     generate_series(1, 10)   AS w_n
ON CONFLICT (product_id, warehouse_id) DO NOTHING;

-- 100 000 inventory transactions — deliberately unindexed on (product_id, warehouse_id)
-- to create a measurable slow query for k6 benchmarking
INSERT INTO inventory_transactions (
    transaction_id, product_id, warehouse_id, order_id,
    transaction_type, quantity_delta, notes, created_at
)
SELECT
    'tx-' || n,
    'prod-' || ((n - 1) % 5000 + 1),
    'wh-'  || ((n - 1) % 10   + 1),
    CASE WHEN n % 3 = 0 THEN 'ord-seed-' || n ELSE NULL END,
    CASE (n % 5)
        WHEN 0 THEN 'RECEIVE'
        WHEN 1 THEN 'SHIP'
        WHEN 2 THEN 'RESERVE'
        WHEN 3 THEN 'RELEASE'
        ELSE        'ADJUST'
    END,
    CASE (n % 2)
        WHEN 0 THEN  (10 + n % 100)
        ELSE        -(1  + n % 50)
    END,
    'Seeded transaction ' || n,
    NOW() - ((n % 365) || ' days')::interval
FROM generate_series(1, 100000) AS n
ON CONFLICT (transaction_id) DO NOTHING;
