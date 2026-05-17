package com.company.orders.application.handler.query;

import com.company.orders.application.query.ListLowStockQuery;
import com.company.orders.application.query.LowStockItem;
import com.company.orders.bus.query.QueryHandler;
import com.company.orders.infrastructure.persistence.InventoryReadRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ListLowStockQueryHandler
        implements
            QueryHandler<ListLowStockQuery, List<LowStockItem>> {

    private final InventoryReadRepository inventoryReadRepository;

    public ListLowStockQueryHandler(InventoryReadRepository inventoryReadRepository) {
        this.inventoryReadRepository = inventoryReadRepository;
    }

    @Override
    public Class<ListLowStockQuery> queryType() {
        return ListLowStockQuery.class;
    }

    @Override
    public List<LowStockItem> handle(ListLowStockQuery query) {
        return inventoryReadRepository.findLowStock(query);
    }
}
