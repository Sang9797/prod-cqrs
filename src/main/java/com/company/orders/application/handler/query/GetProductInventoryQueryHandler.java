package com.company.orders.application.handler.query;

import com.company.orders.application.query.GetProductInventoryQuery;
import com.company.orders.application.query.ProductStockItem;
import com.company.orders.bus.query.QueryHandler;
import com.company.orders.infrastructure.persistence.InventoryReadRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class GetProductInventoryQueryHandler
        implements
            QueryHandler<GetProductInventoryQuery, List<ProductStockItem>> {

    private final InventoryReadRepository inventoryReadRepository;

    public GetProductInventoryQueryHandler(InventoryReadRepository inventoryReadRepository) {
        this.inventoryReadRepository = inventoryReadRepository;
    }

    @Override
    public Class<GetProductInventoryQuery> queryType() {
        return GetProductInventoryQuery.class;
    }

    @Override
    public List<ProductStockItem> handle(GetProductInventoryQuery query) {
        return inventoryReadRepository.findProductStock(query);
    }
}
