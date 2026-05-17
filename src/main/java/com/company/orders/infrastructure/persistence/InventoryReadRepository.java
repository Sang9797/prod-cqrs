package com.company.orders.infrastructure.persistence;

import com.company.orders.application.query.GetInventoryReportQuery;
import com.company.orders.application.query.GetProductInventoryQuery;
import com.company.orders.application.query.InventoryReportItem;
import com.company.orders.application.query.ListLowStockQuery;
import com.company.orders.application.query.LowStockItem;
import com.company.orders.application.query.ProductStockItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryReadRepository {

    private static final Set<String> TX_FIELDS = Set.of("totalReceived", "totalShipped",
            "transactionCount", "lastMovement");

    @PersistenceContext
    private EntityManager entityManager;

    public List<InventoryReportItem> findInventoryReport(GetInventoryReportQuery query) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();

        Root<InventoryJpaEntity> inventory = cq.from(InventoryJpaEntity.class);
        Root<ProductJpaEntity> product = cq.from(ProductJpaEntity.class);
        Root<WarehouseJpaEntity> warehouse = cq.from(WarehouseJpaEntity.class);
        Join<ProductJpaEntity, ProductCategoryJpaEntity> category = product.join("category");
        Join<ProductCategoryJpaEntity, ProductCategoryJpaEntity> parent = category.join("parent",
                JoinType.LEFT);

        Set<String> fields = query.fields();
        boolean all = fields.isEmpty();
        boolean needsTx = all || fields.stream().anyMatch(TX_FIELDS::contains);

        List<Selection<?>> selections = new ArrayList<>();
        select(selections, all, fields, "parentCategoryName",
                cb.coalesce(parent.get("name"), "Root"));
        select(selections, all, fields, "categoryName", category.get("name"));
        select(selections, all, fields, "productId", product.get("productId"));
        select(selections, all, fields, "sku", product.get("sku"));
        select(selections, all, fields, "productName", product.get("name"));
        select(selections, all, fields, "unitPrice", product.get("unitPrice"));
        select(selections, all, fields, "currency", product.get("currency"));
        select(selections, all, fields, "warehouseId", warehouse.get("warehouseId"));
        select(selections, all, fields, "warehouseName", warehouse.get("name"));
        select(selections, all, fields, "region", warehouse.get("region"));
        select(selections, all, fields, "quantityAvailable", inventory.get("quantityAvailable"));
        select(selections, all, fields, "quantityReserved", inventory.get("quantityReserved"));
        select(
                selections,
                all,
                fields,
                "quantityFree",
                cb.diff(inventory.get("quantityAvailable"), inventory.get("quantityReserved")));

        if (needsTx) {
            select(
                    selections,
                    all,
                    fields,
                    "totalReceived",
                    cb.coalesce(totalReceived(cq, cb, product, inventory), 0L));
            select(
                    selections,
                    all,
                    fields,
                    "totalShipped",
                    cb.coalesce(totalShipped(cq, cb, product, inventory), 0L));
            select(
                    selections,
                    all,
                    fields,
                    "transactionCount",
                    cb.coalesce(transactionCount(cq, cb, product, inventory), 0L));
            select(
                    selections,
                    all,
                    fields,
                    "lastMovement",
                    cb.coalesce(lastMovement(cq, cb, product, inventory),
                            inventory.get("lastUpdated")));
        } else {
            select(selections, all, fields, "lastMovement", inventory.get("lastUpdated"));
        }

        cq.multiselect(nonEmpty(selections, cb));
        cq.where(inventoryReportPredicates(query, cb, inventory, product, warehouse, category));
        cq.orderBy(
                cb.asc(cb.coalesce(parent.get("name"), "Root")),
                cb.asc(category.get("name")),
                cb.asc(product.get("name")),
                cb.asc(warehouse.get("name")));

        return entityManager
                .createQuery(cq)
                .setFirstResult(query.page() * query.pageSize())
                .setMaxResults(query.pageSize())
                .getResultStream()
                .map(tuple -> toInventoryReportItem(tuple, fields, needsTx))
                .toList();
    }

    public List<ProductStockItem> findProductStock(GetProductInventoryQuery query) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();

        Root<InventoryJpaEntity> inventory = cq.from(InventoryJpaEntity.class);
        Root<ProductJpaEntity> product = cq.from(ProductJpaEntity.class);
        Root<WarehouseJpaEntity> warehouse = cq.from(WarehouseJpaEntity.class);
        Join<ProductJpaEntity, ProductCategoryJpaEntity> category = product.join("category");

        Set<String> fields = query.fields();
        boolean all = fields.isEmpty();

        List<Selection<?>> selections = new ArrayList<>();
        select(selections, all, fields, "productId", product.get("productId"));
        select(selections, all, fields, "sku", product.get("sku"));
        select(selections, all, fields, "productName", product.get("name"));
        select(selections, all, fields, "unitPrice", product.get("unitPrice"));
        select(selections, all, fields, "currency", product.get("currency"));
        select(selections, all, fields, "categoryName", category.get("name"));
        select(selections, all, fields, "warehouseId", warehouse.get("warehouseId"));
        select(selections, all, fields, "warehouseName", warehouse.get("name"));
        select(selections, all, fields, "region", warehouse.get("region"));
        select(selections, all, fields, "quantityAvailable", inventory.get("quantityAvailable"));
        select(selections, all, fields, "quantityReserved", inventory.get("quantityReserved"));
        select(
                selections,
                all,
                fields,
                "quantityFree",
                cb.diff(inventory.get("quantityAvailable"), inventory.get("quantityReserved")));
        select(selections, all, fields, "lastUpdated", inventory.get("lastUpdated"));

        cq.multiselect(nonEmpty(selections, cb));
        cq.where(
                cb.equal(inventory.get("productId"), product.get("productId")),
                cb.equal(inventory.get("warehouseId"), warehouse.get("warehouseId")),
                cb.equal(product.get("productId"), query.productId()));
        cq.orderBy(cb.asc(warehouse.get("name")));

        return entityManager
                .createQuery(cq)
                .getResultStream()
                .map(tuple -> toProductStockItem(tuple, fields))
                .toList();
    }

    public List<LowStockItem> findLowStock(ListLowStockQuery query) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();

        Root<InventoryJpaEntity> inventory = cq.from(InventoryJpaEntity.class);
        Root<ProductJpaEntity> product = cq.from(ProductJpaEntity.class);
        Root<WarehouseJpaEntity> warehouse = cq.from(WarehouseJpaEntity.class);
        Expression<Integer> quantityFree = cb.diff(inventory.get("quantityAvailable"),
                inventory.get("quantityReserved"));

        Set<String> fields = query.fields();
        boolean all = fields.isEmpty();

        List<Selection<?>> selections = new ArrayList<>();
        select(selections, all, fields, "productId", product.get("productId"));
        select(selections, all, fields, "sku", product.get("sku"));
        select(selections, all, fields, "productName", product.get("name"));
        select(selections, all, fields, "warehouseId", warehouse.get("warehouseId"));
        select(selections, all, fields, "warehouseName", warehouse.get("name"));
        select(selections, all, fields, "region", warehouse.get("region"));
        select(selections, all, fields, "quantityAvailable", inventory.get("quantityAvailable"));
        select(selections, all, fields, "quantityReserved", inventory.get("quantityReserved"));
        select(selections, all, fields, "quantityFree", quantityFree);

        cq.multiselect(nonEmpty(selections, cb));
        cq.where(
                cb.equal(inventory.get("productId"), product.get("productId")),
                cb.equal(inventory.get("warehouseId"), warehouse.get("warehouseId")),
                cb.isTrue(product.get("active")),
                cb.lessThanOrEqualTo(quantityFree, query.threshold()));
        cq.orderBy(cb.asc(quantityFree), cb.asc(product.get("name")));

        return entityManager
                .createQuery(cq)
                .setMaxResults(query.limit())
                .getResultStream()
                .map(tuple -> toLowStockItem(tuple, fields))
                .toList();
    }

    private static Predicate[] inventoryReportPredicates(
            GetInventoryReportQuery query,
            CriteriaBuilder cb,
            Root<InventoryJpaEntity> inventory,
            Root<ProductJpaEntity> product,
            Root<WarehouseJpaEntity> warehouse,
            Join<ProductJpaEntity, ProductCategoryJpaEntity> category) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(inventory.get("productId"), product.get("productId")));
        predicates.add(cb.equal(inventory.get("warehouseId"), warehouse.get("warehouseId")));
        predicates.add(cb.isTrue(product.get("active")));
        predicates
                .add(cb.greaterThanOrEqualTo(inventory.get("quantityAvailable"), query.minStock()));
        if (query.categoryId() != null) {
            predicates.add(cb.equal(category.get("categoryId"), query.categoryId()));
        }
        if (query.warehouseId() != null) {
            predicates.add(cb.equal(warehouse.get("warehouseId"), query.warehouseId()));
        }
        return predicates.toArray(Predicate[]::new);
    }

    private static Subquery<Long> totalReceived(
            CriteriaQuery<?> cq,
            CriteriaBuilder cb,
            Root<ProductJpaEntity> product,
            Root<InventoryJpaEntity> inventory) {
        Subquery<Long> subquery = cq.subquery(Long.class);
        Root<InventoryTransactionJpaEntity> tx = subquery.from(InventoryTransactionJpaEntity.class);
        Expression<Integer> received = cb.<Integer>selectCase()
                .when(cb.gt(tx.get("quantityDelta"), 0), tx.get("quantityDelta"))
                .otherwise(0);
        subquery.select(cb.sum(received).as(Long.class));
        subquery.where(matchesInventory(cb, tx, product, inventory));
        return subquery;
    }

    private static Subquery<Long> totalShipped(
            CriteriaQuery<?> cq,
            CriteriaBuilder cb,
            Root<ProductJpaEntity> product,
            Root<InventoryJpaEntity> inventory) {
        Subquery<Long> subquery = cq.subquery(Long.class);
        Root<InventoryTransactionJpaEntity> tx = subquery.from(InventoryTransactionJpaEntity.class);
        Expression<Integer> shipped = cb.<Integer>selectCase()
                .when(cb.lt(tx.get("quantityDelta"), 0), cb.prod(tx.get("quantityDelta"), -1))
                .otherwise(0);
        subquery.select(cb.sum(shipped).as(Long.class));
        subquery.where(matchesInventory(cb, tx, product, inventory));
        return subquery;
    }

    private static Subquery<Long> transactionCount(
            CriteriaQuery<?> cq,
            CriteriaBuilder cb,
            Root<ProductJpaEntity> product,
            Root<InventoryJpaEntity> inventory) {
        Subquery<Long> subquery = cq.subquery(Long.class);
        Root<InventoryTransactionJpaEntity> tx = subquery.from(InventoryTransactionJpaEntity.class);
        subquery.select(cb.count(tx));
        subquery.where(matchesInventory(cb, tx, product, inventory));
        return subquery;
    }

    private static Subquery<Instant> lastMovement(
            CriteriaQuery<?> cq,
            CriteriaBuilder cb,
            Root<ProductJpaEntity> product,
            Root<InventoryJpaEntity> inventory) {
        Subquery<Instant> subquery = cq.subquery(Instant.class);
        Root<InventoryTransactionJpaEntity> tx = subquery.from(InventoryTransactionJpaEntity.class);
        subquery.select(cb.greatest(tx.<Instant>get("createdAt")));
        subquery.where(matchesInventory(cb, tx, product, inventory));
        return subquery;
    }

    private static Predicate[] matchesInventory(
            CriteriaBuilder cb,
            Root<InventoryTransactionJpaEntity> tx,
            Root<ProductJpaEntity> product,
            Root<InventoryJpaEntity> inventory) {
        return new Predicate[]{
                cb.equal(tx.get("productId"), product.get("productId")),
                cb.equal(tx.get("warehouseId"), inventory.get("warehouseId"))
        };
    }

    private static void select(
            List<Selection<?>> selections,
            boolean all,
            Set<String> fields,
            String fieldName,
            Expression<?> expression) {
        if (all || fields.contains(fieldName)) {
            selections.add(expression.alias(fieldName));
        }
    }

    private static List<Selection<?>> nonEmpty(List<Selection<?>> selections, CriteriaBuilder cb) {
        if (selections.isEmpty()) {
            return List.of(cb.literal(1).alias("_ignored"));
        }
        return selections;
    }

    private static InventoryReportItem toInventoryReportItem(
            Tuple tuple, Set<String> fields, boolean hasTx) {
        boolean all = fields.isEmpty();
        return new InventoryReportItem(
                text(tuple, all, fields, "parentCategoryName"),
                text(tuple, all, fields, "categoryName"),
                text(tuple, all, fields, "productId"),
                text(tuple, all, fields, "sku"),
                text(tuple, all, fields, "productName"),
                value(tuple, all, fields, "unitPrice", BigDecimal.class),
                text(tuple, all, fields, "currency"),
                text(tuple, all, fields, "warehouseId"),
                text(tuple, all, fields, "warehouseName"),
                text(tuple, all, fields, "region"),
                number(tuple, all, fields, "quantityAvailable"),
                number(tuple, all, fields, "quantityReserved"),
                number(tuple, all, fields, "quantityFree"),
                hasTx ? longNumber(tuple, all, fields, "totalReceived") : 0L,
                hasTx ? longNumber(tuple, all, fields, "totalShipped") : 0L,
                hasTx ? longNumber(tuple, all, fields, "transactionCount") : 0L,
                value(tuple, all, fields, "lastMovement", Instant.class));
    }

    private static ProductStockItem toProductStockItem(Tuple tuple, Set<String> fields) {
        boolean all = fields.isEmpty();
        return new ProductStockItem(
                text(tuple, all, fields, "productId"),
                text(tuple, all, fields, "sku"),
                text(tuple, all, fields, "productName"),
                value(tuple, all, fields, "unitPrice", BigDecimal.class),
                text(tuple, all, fields, "currency"),
                text(tuple, all, fields, "categoryName"),
                text(tuple, all, fields, "warehouseId"),
                text(tuple, all, fields, "warehouseName"),
                text(tuple, all, fields, "region"),
                number(tuple, all, fields, "quantityAvailable"),
                number(tuple, all, fields, "quantityReserved"),
                number(tuple, all, fields, "quantityFree"),
                value(tuple, all, fields, "lastUpdated", Instant.class));
    }

    private static LowStockItem toLowStockItem(Tuple tuple, Set<String> fields) {
        boolean all = fields.isEmpty();
        return new LowStockItem(
                text(tuple, all, fields, "productId"),
                text(tuple, all, fields, "sku"),
                text(tuple, all, fields, "productName"),
                text(tuple, all, fields, "warehouseId"),
                text(tuple, all, fields, "warehouseName"),
                text(tuple, all, fields, "region"),
                number(tuple, all, fields, "quantityAvailable"),
                number(tuple, all, fields, "quantityReserved"),
                number(tuple, all, fields, "quantityFree"));
    }

    private static String text(Tuple tuple, boolean all, Set<String> fields, String fieldName) {
        return value(tuple, all, fields, fieldName, String.class);
    }

    private static int number(Tuple tuple, boolean all, Set<String> fields, String fieldName) {
        Number value = value(tuple, all, fields, fieldName, Number.class);
        return value == null ? 0 : value.intValue();
    }

    private static long longNumber(Tuple tuple, boolean all, Set<String> fields, String fieldName) {
        Number value = value(tuple, all, fields, fieldName, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private static <T> T value(
            Tuple tuple, boolean all, Set<String> fields, String fieldName, Class<T> type) {
        if (!all && !fields.contains(fieldName)) {
            return null;
        }
        return tuple.get(fieldName, type);
    }
}
