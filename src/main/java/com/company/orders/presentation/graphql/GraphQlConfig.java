package com.company.orders.presentation.graphql;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphQlConfig {

  /**
   * Rejects queries whose total field-weight exceeds 100. Prevents clients from crafting expensive
   * nested queries that hammer the DB.
   */
  @Bean
  public Instrumentation maxComplexity() {
    return new MaxQueryComplexityInstrumentation(100);
  }

  /** Rejects queries nested deeper than 10 levels. Prevents circular fragment attacks. */
  @Bean
  public Instrumentation maxDepth() {
    return new MaxQueryDepthInstrumentation(10);
  }
}
