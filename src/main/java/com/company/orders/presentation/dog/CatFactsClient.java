package com.company.orders.presentation.dog;

import org.springframework.web.service.annotation.GetExchange;

public interface CatFactsClient {
    @GetExchange(value = "https://catfact.ninja/fact", accept = "application/json")
    CatFact facts();
}
