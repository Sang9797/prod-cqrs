package com.company.orders.application.handler.query;

import com.company.orders.application.query.GetInventoryReportQuery;
import com.company.orders.application.query.InventoryReportItem;
import com.company.orders.bus.query.QueryHandler;
import com.company.orders.infrastructure.persistence.InventoryReadRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class GetInventoryReportQueryHandler
        implements
            QueryHandler<GetInventoryReportQuery, List<InventoryReportItem>> {

    private final InventoryReadRepository inventoryReadRepository;

    public GetInventoryReportQueryHandler(InventoryReadRepository inventoryReadRepository) {
        this.inventoryReadRepository = inventoryReadRepository;
    }

    @Override
    public Class<GetInventoryReportQuery> queryType() {
        return GetInventoryReportQuery.class;
    }

    @Override
    public List<InventoryReportItem> handle(GetInventoryReportQuery query) {
        return inventoryReadRepository.findInventoryReport(query);
    }
}
