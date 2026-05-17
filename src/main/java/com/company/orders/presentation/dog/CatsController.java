package com.company.orders.presentation.dog;

import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.service.registry.ImportHttpServices;

@RestController
@ImportHttpServices(CatFactsClient.class)
public class CatsController {

    private final CatFactsClient client;

    public CatsController(CatFactsClient catFactsClient) {
        this.client = catFactsClient;
    }

    @ConcurrencyLimit(10)
    @GetMapping("/cats")
    CatFact facts() {
        return this.client.facts();
    }
}
